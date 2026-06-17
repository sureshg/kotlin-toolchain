/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.native

import org.jetbrains.amper.ProcessRunner
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.compilation.KotlinArtifactsDownloader
import org.jetbrains.amper.compilation.KotlinNativeCompiler
import org.jetbrains.amper.compilation.downloadNativeCompiler
import org.jetbrains.amper.concurrency.mapConcurrently
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.engine.GenerateKlibsForIdeTask
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.dr.resolver.native.commonizedPlatformsIdentifier
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.jdk.provisioning.JdkProvider
import org.jetbrains.amper.processes.ArgsMode
import org.jetbrains.amper.processes.LoggingProcessOutputListener
import org.jetbrains.amper.processes.runJava
import org.jetbrains.amper.stdlib.io.path.cleanDirectoryExcept
import org.jetbrains.amper.tasks.EmptyTaskResult
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.artifacts.ArtifactTaskBase
import org.jetbrains.amper.tasks.artifacts.CinteropCommonizedKlibArtifact
import org.jetbrains.amper.tasks.artifacts.CinteropKlibsArtifact
import org.jetbrains.amper.tasks.artifacts.api.ArtifactSelector
import org.jetbrains.amper.tasks.artifacts.api.ArtifactType
import org.jetbrains.amper.tasks.artifacts.api.Quantifier
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

class CommonizeCInteropKlibsTask(
    buildOutputRoot: AmperBuildOutputRoot,
    private val userCacheRoot: AmperUserCacheRoot,
    private val tempRoot: AmperProjectTempRoot,
    private val incrementalCache: IncrementalCache,
    private val jdkProvider: JdkProvider,
    private val processRunner: ProcessRunner,
    override val taskName: TaskName,
    val fragment: Fragment,
) : ArtifactTaskBase(), GenerateKlibsForIdeTask {
    init {
        require(fragment !is LeafFragment) { "Nothing to commonize for a leaf fragment ${fragment.name}" }
    }

    private val kotlinVersion = fragment.settings.kotlin.version
    private val kotlinDownloader = KotlinArtifactsDownloader(userCacheRoot, incrementalCache)

    val cinteropKlibs by ArtifactSelector(
        type = ArtifactType(CinteropKlibsArtifact::class),
        predicate = {
            it.module == fragment.module &&
                    it.isTest == fragment.isTest &&
                    it.platform in fragment.platforms
        },
        description = "All cinterop klibs from ${fragment.module} that target any of ${fragment.platforms}",
        quantifier = Quantifier.AnyOrNone,
    )

    val output by CinteropCommonizedKlibArtifact(
        buildOutputRoot = buildOutputRoot,
        fragment = fragment,
        conventionPath = buildOutputRoot.path / "cinterop" / "commonized" / fragment.module.userReadableName / fragment.name,
    )

    override suspend fun run(
        dependenciesResult: List<TaskResult>,
        executionContext: TaskGraphExecutionContext,
    ): TaskResult {
        val compiler = downloadNativeCompiler(kotlinVersion, userCacheRoot, jdkProvider)
        val commonizerClasspath = kotlinDownloader.downloadKotlinCommonizerEmbeddable(kotlinVersion)

        // cinterop "name" -> all the corresponding klibs from all the leaf platforms.
        val klibsGroupedByName: Map<String, List<CinteropKlibsArtifact.Klib>> = cinteropKlibs.flatMap { klibsDir ->
            klibsDir.allKlibs().filter { it.defOriginFragmentName == fragment.name }
        }.groupBy { it.name }

        if (klibsGroupedByName.isEmpty()) {
            logger.debug("No relevant .klibs found to commonize, bailing out")
            output.path.deleteRecursively()
            return EmptyTaskResult
        }

        output.path.createDirectories()
        val relevantOutputs = klibsGroupedByName.entries.mapConcurrently { [name, klibs] ->
            commonize(compiler, commonizerClasspath, name, klibs)
        }

        cleanDirectoryExcept(output.path, relevantOutputs)

        return EmptyTaskResult
    }

    private suspend fun commonize(
        compiler: KotlinNativeCompiler,
        commonizerClasspath: List<Path>,
        name: String,
        klibs: List<CinteropKlibsArtifact.Klib>,
    ): Path {
        val target = fragment.platforms.commonizedPlatformsIdentifier()

        val dependencies = buildList {
            // Commonized platform libs (we depend on the corresponding task)
            addAll((compiler.commonizedPath / target).listLibraries())
            // Leaf ("un-commonized") platform libs are required as well.
            fragment.platforms.forEach { platform ->
                addAll((compiler.platformPath / platform.nameForCompiler).listLibraries())
            }
        }

        val commonizerArgs: MutableList<String> = [
            "native-klib-commonize",
            "-distribution-path",
            compiler.kotlinNativeHome.absolutePathString(),
            "-output-path",
            output.path.absolutePathString(),
            "-input-libraries",
            klibs.joinToString(";") { it.path.absolutePathString() },
            "-output-targets",
            target,
        ]
        if (dependencies.isNotEmpty()) {
            commonizerArgs += "-dependency-libraries"
            commonizerArgs += dependencies.joinToString(";") { it.absolutePathString() }
        }

        return incrementalCache.execute(
            key = "${taskName.id.value}-$name",
            inputValues = mapOf("commonizerArgs" to commonizerArgs.joinToString()),
            inputFiles = buildList {
                add(compiler.kotlinNativeHome)
                klibs.mapTo(this) { it.path }
                addAll(commonizerClasspath)
            }
        ) {
            logger.info("Commonizing '$name' (from ${klibs.size} klibs)...")
            val result = processRunner.runJava(
                jdk = compiler.jdk,
                workingDir = Path("."),
                mainClass = "org.jetbrains.kotlin.commonizer.cli.CommonizerCLI",
                classpath = commonizerClasspath,
                programArgs = commonizerArgs,
                argsMode = ArgsMode.ArgFile(tempRoot = tempRoot),
                outputListener = LoggingProcessOutputListener(logger),
            )
            if (result.exitCode != 0) {
                userReadableError("cinterop commonization failed, see the errors above")
            }
            val actualResult = output.path / target / name
            check(actualResult.isDirectory()) {
                "Expected $actualResult to exist after commonization"
            }
            IncrementalCache.ExecutionResult([actualResult])
        }.outputFiles.single()
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    private fun Path.listLibraries() = listDirectoryEntries()
        .filter { it.isDirectory() || it.extension == "klib" }
}