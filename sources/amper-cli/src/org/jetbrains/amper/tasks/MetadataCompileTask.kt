/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import kotlinx.serialization.json.Json
import org.jetbrains.amper.CliReportingMavenResolver
import org.jetbrains.amper.ProcessRunner
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.telemetry.setAmperModule
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.compilation.KotlinArtifactsDownloader
import org.jetbrains.amper.compilation.KotlinCompilationType
import org.jetbrains.amper.compilation.KotlinUserSettings
import org.jetbrains.amper.compilation.downloadCompilerPlugins
import org.jetbrains.amper.compilation.downloadNativeCompiler
import org.jetbrains.amper.compilation.kotlinMetadataCompilerArgs
import org.jetbrains.amper.compilation.kotlinModuleName
import org.jetbrains.amper.compilation.kotlinNativeCompilerArgs
import org.jetbrains.amper.compilation.serializableKotlinSettings
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.engine.BuildTask
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.dr.resolver.ModuleDependencies
import org.jetbrains.amper.frontend.dr.resolver.ModuleDependencies.Companion.toRepository
import org.jetbrains.amper.frontend.dr.resolver.ModuleDependencyNode
import org.jetbrains.amper.frontend.dr.resolver.flow.toPlatform
import org.jetbrains.amper.frontend.dr.resolver.flow.toResolutionPlatform
import org.jetbrains.amper.frontend.friends
import org.jetbrains.amper.frontend.mavenResolveRepositories
import org.jetbrains.amper.frontend.refinedFragments
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.jdk.provisioning.Jdk
import org.jetbrains.amper.jdk.provisioning.JdkProvider
import org.jetbrains.amper.jvm.getJdkOrUserError
import org.jetbrains.amper.processes.ArgsMode
import org.jetbrains.amper.processes.LoggingProcessOutputListener
import org.jetbrains.amper.processes.runJava
import org.jetbrains.amper.tasks.artifacts.ArtifactTaskBase
import org.jetbrains.amper.tasks.artifacts.KotlinJavaSourceDirArtifact
import org.jetbrains.amper.tasks.artifacts.Selectors
import org.jetbrains.amper.tasks.artifacts.api.Quantifier
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.use
import org.jetbrains.amper.util.BuildType
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlin.io.path.walk

internal class MetadataCompileTask(
    override val taskName: TaskName,
    override val module: AmperModule,
    private val moduleDependencies: ModuleDependencies,
    private val fragment: Fragment,
    private val userCacheRoot: AmperUserCacheRoot,
    private val tempRoot: AmperProjectTempRoot,
    private val taskOutputRoot: TaskOutputRoot,
    private val incrementalCache: IncrementalCache,
    private val kotlinArtifactsDownloader: KotlinArtifactsDownloader =
        KotlinArtifactsDownloader(userCacheRoot, incrementalCache),
    private val jdkProvider: JdkProvider,
    private val processRunner: ProcessRunner,
): ArtifactTaskBase(), BuildTask {

    // todo (AB) : [AMPER-721] What is about build type for native metadata compilation.
    //  KGP provides option `-g` that is equivalent to DEBUG
    override val buildType: BuildType get() = BuildType.Debug
    override val platform: Platform = Platform.COMMON
    override val isTest: Boolean = fragment.isTest

    private val additionalKotlinJavaSourceDirs by Selectors.fromFragment(
        type = KotlinJavaSourceDirArtifact::class,
        fragment = fragment,
        quantifier = Quantifier.AnyOrNone,
    )

    private val mavenResolver by lazy {
        CliReportingMavenResolver(userCacheRoot, incrementalCache)
    }

    /**
     * Metadata compilation of shared fragment accepts the following input:
     * - fragment sources (cinterop sources are not included)
     * - fragment external compile dependencies
     *   -- all suitable source sets from a dependnecy's common KLib, including commonized cinterop source sets
     *   -- compile dependencies of a shared fragment that targets native platfroms ONLY, include transitive local module dependencies as well.
     * - metadata compilation result of all same-module fragments refining this one (dependesOn closure)
     * - commonized cinterop sources of all target platforms
     */
    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): Result {
        checkFragment()

        logger.info("compile metadata for '${module.userReadableName}' -- ${fragment.name}")

        val kotlinSettings = fragment.serializableKotlinSettings()
        val fragmentPlatforms = fragment.platforms.mapNotNull { it.toResolutionPlatform() }.toSet()

        // todo (AB) : [AMPER-721] Wrap the classpath into incremental cache to avoid deserializing module-graph.
        val resolvedGraph = mavenResolver.resolve(moduleDependencies = moduleDependencies, isTest = isTest, leafPlatformsOnly = false)
        val mavenClasspath = resolvedGraph.root.children.filter {
            it is ModuleDependencyNode
                    && it.isForTests == isTest
                    && it.resolutionConfig.platforms == fragment.platforms.mapNotNull { it.toResolutionPlatform() }.toSet()
                    && it.resolutionConfig.scope == ResolutionScope.COMPILE
        }.let {
            it.singleOrNull() as? ModuleDependencyNode
                ?: error("Expected single ${ModuleDependencyNode::class.simpleName}, found ${it.size}")
        }

        val localDependencies = dependenciesResult.filterIsInstance<Result>()

        // includes this module's fragment dependencies and other source fragment deps from other local modules
        val localClasspath = localDependencies.mapNotNull {
            it.metadataOutputRoot.takeIf { !it.isEmptyDirectory() }
        }

        return if (fragmentPlatforms.all { it.nativeTarget != null })   {
            compileNativeMetadata(localClasspath, mavenClasspath, localDependencies, kotlinSettings, fragmentPlatforms)
        } else {
            compileCommonMetadata(localClasspath, mavenClasspath, localDependencies, kotlinSettings, fragmentPlatforms)
        }
    }

    fun Path.isEmptyDirectory(): Boolean {
        return isDirectory() && Files.newDirectoryStream(this).use { !it.iterator().hasNext() }/*.isEmpty()*/
    }

    private suspend fun compileCommonMetadata(
        localClasspath: List<Path>,
        mavenClasspath: ModuleDependencyNode,
        localDependencies: List<Result>,
        kotlinSettings: KotlinUserSettings,
        fragmentPlatforms: Set<ResolutionPlatform>,
    ): Result {
        val classpath = localClasspath + mavenClasspath.dependencyPaths()
        val refinesPaths =
            fragment.refinedFragments.map { localDependencies.findMetadataResultForFragment(it).metadataOutputRoot }

        // todo (AB) : [AMPER-721] This is a closed party, friends are not invited I am afraid.
        //  Test fragments are also not invited, neither are single-platform fragments (see check above)
        val friendPaths =
            fragment.friends.map { localDependencies.findMetadataResultForFragment(it).metadataOutputRoot }

        val jdk = jdkProvider.getJdkOrUserError(jdkSettings = fragment.settings.jvm.jdk)

        val inputValues = mapOf(
            "jdk.version" to jdk.version,
            "jdk.home" to jdk.homeDir.pathString,
            "user.settings" to Json.encodeToString(kotlinSettings),
            "task.output.root" to taskOutputRoot.path.pathString,
        )

        val sourceDirs =
            fragment.sourceRoots.map { it.toAbsolutePath() } + additionalKotlinJavaSourceDirs.map { it.path }
        val inputFiles = sourceDirs + classpath + refinesPaths + friendPaths

        incrementalCache.execute(taskName.id.value, inputValues, inputFiles) {
            cleanDirectory(taskOutputRoot.path)

            val existingSourceDirs = sourceDirs.filter { it.exists() }
            if (existingSourceDirs.isNotEmpty()) {
                existingSourceDirs.forEach {
                    if (!it.isDirectory()) {
                        userReadableError("Source directory '$it' exists, but it's not a directory, this is currently unsupported")
                    }
                }
                compileCommonSources(
                    jdk = jdk,
                    kotlinUserSettings = kotlinSettings,
                    sourceDirectories = sourceDirs,
                    additionalSourceRoots = additionalKotlinJavaSourceDirs.map { SourceRoot(it.fragmentName, it.path) },
                    classpath = classpath,
                    friendPaths = friendPaths,
                    refinesPaths = refinesPaths,
                    fragmentPlatforms = fragmentPlatforms,
                )
            } else {
                logger.debug("No sources were found for ${fragment.identificationPhrase()}, skipping compilation")
            }

            return@execute IncrementalCache.ExecutionResult(listOf(taskOutputRoot.path.toAbsolutePath()))
        }

        return Result(
            metadataOutputRoot = taskOutputRoot.path.toAbsolutePath(),
            module = module,
            fragment = fragment,
        )
    }

    private fun List<Result>.findMetadataResultForFragment(f: Fragment) =
        // can't use identity check because some fragments are wrapped, and equals is not overridden
        firstOrNull { it.module.userReadableName == f.module.userReadableName && it.fragment.name == f.name }
            ?: error("Metadata compilation result not found for dependency fragment ${f.module.userReadableName}:" +
                    "${f.name} of this fragment ${module.userReadableName}:${fragment.name}. Actual results: " +
                    map { "${it.module.userReadableName}:${it.fragment.name}" })

    private suspend fun compileNativeSharedSources(
        jdk: Jdk,
        kotlinUserSettings: KotlinUserSettings,
        sourceDirectories: List<Path>,
        additionalSourceRoots: List<SourceRoot>,
        classpath: List<Path>,
        friendPaths: List<Path>,
        refinesPaths: List<Path>,
        fragmentPlatforms: Set<ResolutionPlatform>,
    ) {
        val kotlinSourceFiles = sourceDirectories.flatMap { it.walk() }.filter { it.extension == "kt" }.toList()
        if (kotlinSourceFiles.isEmpty()) {
            return
        }

        val nativeCompiler = downloadNativeCompiler(kotlinUserSettings.compilerVersion, userCacheRoot, jdkProvider)

        // Starting with 2.2.20,
        // the kotlin-stdlib metadata JSON descriptor doesn't map common sourceSet to nativeApiElements variant.
        // This way dependency on kotlin-stdlib is not resolved if at least one target platform is native.
        // Fortunately, the commonMain metadata of kotlin-stdlib is shipped as a prebuilt klib with K/Native compiler.
        // todo (AB) common sourceSet of kotlin-stdlib if still resolved for Native+non-native set of platforms
        //  (check that this is expected, maybe it shouldn't and kotlin-stdlib metadata should be taken from platform commonizer output in this case)
        val kotlinStdKlibCommonPath = nativeCompiler.kotlinNativeHome / "klib" / "common" / "stdlib"

        val libraryPaths = classpath + listOf(kotlinStdKlibCommonPath)

        val compilerPlugins = kotlinArtifactsDownloader.downloadCompilerPlugins(
            plugins = kotlinUserSettings.compilerPlugins,
            repositories = module.mavenResolveRepositories.map { it.toRepository() },
        )

        val compilerArgs = kotlinNativeCompilerArgs(
            buildType = buildType,
            kotlinUserSettings = kotlinUserSettings,
            compilerPlugins = compilerPlugins,
            entryPoint = null,
            libraryPaths = libraryPaths,
            exportedLibraryPaths = emptyList(),
            fragments = listOf(fragment),
            sourceFiles = sourceDirectories.flatMap { it.walk() }.toList(),
            additionalSourceRoots = additionalSourceRoots,
            refinesPaths = refinesPaths,
            outputPath = taskOutputRoot.path,
            compilationType = KotlinCompilationType.LIBRARY,
            binaryOptions = emptyMap(),
            include = null,
            fragmentPlatforms = fragmentPlatforms.map { it.toPlatform() }.toSet(),
        )

        spanBuilder("kotlin-native-metadata-compilation")
            .setAmperModule(module)
            .setListAttribute("source-dirs", sourceDirectories.map { it.pathString })
            .setAttribute("compiler-version", kotlinUserSettings.compilerVersion)
            .setListAttribute("compiler-args", compilerArgs)
            .use {
                logger.info("Compiling Native Kotlin metadata for module '${module.userReadableName}(:${fragment.name})'...")
                nativeCompiler.compile(processRunner, compilerArgs, tempRoot, module)
            }
    }

    private suspend fun compileCommonSources(
        jdk: Jdk,
        kotlinUserSettings: KotlinUserSettings,
        sourceDirectories: List<Path>,
        additionalSourceRoots: List<SourceRoot>,
        classpath: List<Path>,
        friendPaths: List<Path>,
        refinesPaths: List<Path>,
        fragmentPlatforms: Set<ResolutionPlatform>,
    ) {
        val kotlinSourceFiles = sourceDirectories.flatMap { it.walk() }.filter { it.extension == "kt" }.toList()
        if (kotlinSourceFiles.isEmpty()) {
            return
        }

        val compilerJars = kotlinArtifactsDownloader.downloadKotlinCompilerEmbeddable(
            version = kotlinUserSettings.compilerVersion,
        )
        val compilerPlugins = kotlinArtifactsDownloader.downloadCompilerPlugins(
            plugins = kotlinUserSettings.compilerPlugins,
            repositories = module.mavenResolveRepositories.map { it.toRepository() },
        )
        val compilerArgs = kotlinMetadataCompilerArgs(
            kotlinUserSettings = kotlinUserSettings,
            moduleName = module.kotlinModuleName(isTest),
            classpath = classpath,
            compilerPlugins = compilerPlugins,
            outputPath = taskOutputRoot.path,
            friendPaths = friendPaths,
            refinesPaths = refinesPaths,
            fragments = listOf(fragment),
            // in Kotlin >= 2.2, we need to list all source files (not just dirs)
            sourceFiles = sourceDirectories.flatMap { it.walk() }.toList(),
            additionalSourceRoots = additionalSourceRoots,
            fragmentPlatforms = fragmentPlatforms,
        )
        spanBuilder("kotlin-common-metadata-compilation")
            .setAmperModule(module)
            .setListAttribute("source-dirs", sourceDirectories.map { it.pathString })
            .setAttribute("compiler-version", kotlinUserSettings.compilerVersion)
            .setListAttribute("compiler-args", compilerArgs)
            .use {
                logger.info("Compiling Kotlin metadata for module '${module.userReadableName}'...")
                val result = processRunner.runJava(
                    jdk = jdk,
                    workingDir = Path("."),
                    mainClass = "org.jetbrains.kotlin.cli.metadata.KotlinMetadataCompiler",
                    classpath = compilerJars,
                    programArgs = compilerArgs,
                    argsMode = ArgsMode.ArgFile(tempRoot = tempRoot),
                    outputListener = LoggingProcessOutputListener(logger),
                )
                if (result.exitCode != 0) {
                    userReadableError("Kotlin metadata compilation failed (see errors above)")
                }
            }
    }

    private suspend fun compileNativeMetadata(
        localDepsMetadataCompilations: List<Path>,
        mavenClasspath: ModuleDependencyNode,
        localDependencies: List<Result>,
        kotlinSettings: KotlinUserSettings,
        fragmentPlatforms: Set<ResolutionPlatform>,
    ): Result {
        val classpath = localDepsMetadataCompilations + mavenClasspath.dependencyPaths()
        val refinesPaths =
            fragment.refinedFragments.map { localDependencies.findMetadataResultForFragment(it).metadataOutputRoot }

        // todo (AB) : [AMPER-721] This is a closed party, friends are not invited I am afraid.
        //  Test fragments are also not invited, neither are single-platform fragments (see check above)
        val friendPaths =
            fragment.friends.map { localDependencies.findMetadataResultForFragment(it).metadataOutputRoot }

        val jdk = jdkProvider.getJdkOrUserError(jdkSettings = fragment.settings.jvm.jdk)

        val inputValues = mapOf(
            "jdk.version" to jdk.version,
            "jdk.home" to jdk.homeDir.pathString,
            "user.settings" to Json.encodeToString(kotlinSettings),
            "task.output.root" to taskOutputRoot.path.pathString,
        )

        val sourceDirs =
            fragment.sourceRoots.map { it.toAbsolutePath() } + additionalKotlinJavaSourceDirs.map { it.path }
        val inputFiles = sourceDirs + classpath + refinesPaths + friendPaths

        incrementalCache.execute(taskName.id.value, inputValues, inputFiles) {
            cleanDirectory(taskOutputRoot.path)

            val existingSourceDirs = sourceDirs.filter { it.exists() }
            if (existingSourceDirs.isNotEmpty()) {
                existingSourceDirs.forEach {
                    if (!it.isDirectory()) {
                        userReadableError("Source directory '$it' exists, but it's not a directory, this is currently unsupported")
                    }
                }
                compileNativeSharedSources(
                    jdk = jdk,
                    kotlinUserSettings = kotlinSettings,
                    sourceDirectories = sourceDirs,
                    additionalSourceRoots = additionalKotlinJavaSourceDirs.map { SourceRoot(it.fragmentName, it.path) },
                    classpath = classpath,
                    friendPaths = friendPaths,
                    refinesPaths = refinesPaths,
                    fragmentPlatforms = fragmentPlatforms,
                )
            } else {
                logger.debug("No sources were found for ${fragment.identificationPhrase()}, skipping compilation")
            }

            return@execute IncrementalCache.ExecutionResult(listOf(taskOutputRoot.path.toAbsolutePath()))
        }

        return Result(
            metadataOutputRoot = taskOutputRoot.path.toAbsolutePath(),
            module = module,
            fragment = fragment,
        )
    }

    private fun checkFragment() {
        if (fragment.isTest)
            error("Metadata compilation is not supported for test fragments: ${fragment.module.userReadableName}#${fragment.name}")
        if (fragment.platforms.size == 1)
            error("Metadata compilation is not supported for single-platform fragments: ${fragment.module.userReadableName}#${fragment.name}")
    }

    class Result(
        val metadataOutputRoot: Path,
        val module: AmperModule,
        val fragment: Fragment,
    ) : TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}
