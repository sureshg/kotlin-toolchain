/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.native

import org.jetbrains.amper.ProcessRunner
import org.jetbrains.amper.cli.UserReadableError
import org.jetbrains.amper.cli.context.AmperBuildOutputRoot
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.compilation.downloadNativeCompiler
import org.jetbrains.amper.compilation.serializableKotlinSettings
import org.jetbrains.amper.concurrency.mapConcurrently
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.engine.BuildTask
import org.jetbrains.amper.engine.GenerateKlibsForIdeTask
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.jdk.provisioning.JdkProvider
import org.jetbrains.amper.stdlib.collections.distinctBy
import org.jetbrains.amper.stdlib.io.path.cleanDirectoryExcept
import org.jetbrains.amper.tasks.EmptyTaskResult
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.artifacts.ArtifactTaskBase
import org.jetbrains.amper.tasks.artifacts.CinteropDefFileArtifact
import org.jetbrains.amper.tasks.artifacts.CinteropKlibsArtifact
import org.jetbrains.amper.tasks.artifacts.Selectors
import org.jetbrains.amper.tasks.artifacts.api.Quantifier
import org.jetbrains.amper.util.BuildType
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString

internal class NativeCInteropGenerateKlibTask(
    override val module: AmperModule,
    override val platform: Platform,
    private val fragments: List<Fragment>,
    private val buildOutputRoot: AmperBuildOutputRoot,
    private val userCacheRoot: AmperUserCacheRoot,
    private val incrementalCache: IncrementalCache,
    override val taskName: TaskName,
    private val jdkProvider: JdkProvider,
    private val processRunner: ProcessRunner,
    private val ignorePlatformFailures: Boolean,
) : ArtifactTaskBase(), BuildTask, GenerateKlibsForIdeTask {
    init {
        require(platform.isLeaf) { "Expected a leaf platform, got: $platform" }
    }

    private val leafFragment = module.leafFragments.first { it.platform == platform && !isTest }

    private val defFileArtifacts by Selectors.fromMatchingFragments(
        type = CinteropDefFileArtifact::class,
        module = module,
        isTest = false,
        hasPlatforms = setOf(platform),
        quantifier = Quantifier.AnyOrNone,
    )

    private val outputKlibsDirectoryArtifact by CinteropKlibsArtifact(
        buildOutputRoot = buildOutputRoot,
        module = module,
        platform = platform,
        path = leafFragment.generatedCinteropKlibsDirPath(buildOutputRoot.path)!!,
    )

    override suspend fun run(
        dependenciesResult: List<TaskResult>,
        executionContext: TaskGraphExecutionContext,
    ): TaskResult {
        data class CinteropEntry(
            val defFile: Path,
            val associatedWith: Fragment,
        )

        val inputDefFiles = buildList {
            defFileArtifacts.forEach {
                add(CinteropEntry(it.path, it.fragment))
            }
            for (fragment in fragments) {
                val path = fragment.cinteropPath?.takeIf { it.isDirectory() } ?: continue
                for (defFile in path.listDirectoryEntries("*.def")) {
                    add(CinteropEntry(defFile, fragment))
                }
            }
        }

        inputDefFiles.distinctBy(
            selector = { it.defFile.nameWithoutExtension },
            onDuplicates = { name, entries ->
                val conflictingPaths = entries.joinToString { it.defFile.absolutePathString() }
                userReadableError("Got multiple cinterop definitions with the name `$name`: $conflictingPaths")
            }
        )

        if (inputDefFiles.isEmpty()) {
            logger.debug("No .def files found, bailing out")
            cleanDirectory(outputKlibsDirectoryArtifact.path)
            return EmptyTaskResult
        }

        val targetLeafFragment = module.leafFragments.single { it.platform == platform && !it.isTest }
        val kotlinCompilerVersion = targetLeafFragment.serializableKotlinSettings().compilerVersion

        val results = inputDefFiles.mapConcurrently { (defFile, associatedWith) ->
            try {
                incrementalCache.execute(
                    key = "${taskName.id.value}-${defFile.nameWithoutExtension}",
                    inputValues = mapOf(
                        "target" to platform.nameForCompiler,
                        "kotlinVersion" to kotlinCompilerVersion,
                    ),
                    inputFiles = listOf(defFile),
                ) {
                    val cinteropName = defFile.nameWithoutExtension
                    val outputKlib = outputKlibsDirectoryArtifact.getPathForKlib(
                        name = cinteropName,
                        defOriginFragment = associatedWith,
                    )
                    cleanDirectory(outputKlib.parent)

                    val nativeCompiler =
                        downloadNativeCompiler(kotlinCompilerVersion, userCacheRoot, jdkProvider)
                    val args = buildList {
                        add("-def")
                        add(defFile.pathString)
                        add("-target")
                        add(platform.nameForCompiler)
                        add("-o")
                        add(outputKlib.pathString)
                    }

                    logger.info("Running cinterop '$cinteropName' for platform '${platform.pretty}'...")
                    nativeCompiler.cinterop(processRunner, args)

                    IncrementalCache.ExecutionResult(listOf(outputKlib))
                }.outputFiles.single().let { Result.success(it) }
            } catch (e: UserReadableError) {
                if (ignorePlatformFailures) {
                    logger.warn(e.message)
                } else {
                    logger.error(e.message)
                }
                Result.failure(e)
            }
        }

        if (!ignorePlatformFailures && results.any { it.exceptionOrNull() is UserReadableError }) {
            userReadableError("cinterop processing failed for ${leafFragment.platform}, see the errors above")
        }

        // Clean any stale output Klibs
        val relevantOutputs = results.mapNotNull { it.getOrNull() }
        cleanDirectoryExcept(outputKlibsDirectoryArtifact.path, relevantOutputs)

        return EmptyTaskResult
    }

    override val buildType: BuildType?
        get() = null

    override val isTest: Boolean
        get() = false

    companion object {
        private val logger = LoggerFactory.getLogger(NativeCInteropGenerateKlibTask::class.java)
    }
}
