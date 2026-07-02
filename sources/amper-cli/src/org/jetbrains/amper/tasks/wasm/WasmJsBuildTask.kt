/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.wasm

import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.context.AmperProjectTempRoot
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.extract.extractZip
import org.jetbrains.amper.engine.BuildTask
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.engine.requireSingleDependency
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.web.WebLinkTask
import org.jetbrains.amper.util.BuildType
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

class WasmJsBuildTask(
    override val platform: Platform,
    override val module: AmperModule,
    override val buildType: BuildType,
    private val taskOutputPath: TaskOutputRoot,
    override val taskName: TaskName,
    private val tempRoot: AmperProjectTempRoot,
) : BuildTask {
    init {
        require(platform.isLeaf)
        require(platform.isDescendantOf(Platform.WASM_JS))
    }

    override val isTest: Boolean
        get() = false

    context(executionContext: TaskGraphExecutionContext)
    override suspend fun run(
        dependenciesResult: List<TaskResult>,
    ): TaskResult {
        val linkedDir = dependenciesResult.requireSingleDependency<WebLinkTask.Result>().linkedBinary
            ?: userReadableError("Build an application without sources is not possible.")

        BuildPrimitives.copy(
            from = linkedDir,
            to = taskOutputPath.path,
            overwrite = true,
        )

        val fragments = module.fragments.filter {
            it.platforms.contains(platform) && it.isTest == isTest
        }

        fragments
            .map { it.resourcesPath }
            .filter { it.exists() }
            .map { it.toAbsolutePath() }
            .forEach { resource ->
                BuildPrimitives.copy(
                    from = resource,
                    to = taskOutputPath.path,
                    overwrite = true,
                )
            }

        copySkikoWasmRuntime(dependenciesResult)

        return Result(taskOutputPath.path)
    }

    private suspend fun copySkikoWasmRuntime(dependenciesResult: List<TaskResult>) {
        dependenciesResult
            .filterIsInstance<ResolveExternalDependenciesTask.Result>()
            .flatMap { it.runtimeClasspath }
            .distinct()
            .filter { it.extension == "jar" }
            .singleOrNull { it.nameWithoutExtension.startsWith(SKIKO_WASM_RUNTIME) }
            ?.let { skikoWasmRuntime ->
                val skikoWasmRuntimeExtracted = tempRoot.path.resolve(SKIKO_WASM_RUNTIME)

                extractZip(
                    skikoWasmRuntime,
                    skikoWasmRuntimeExtracted,
                    stripRoot = false,
                )

                skikoWasmRuntimeExtracted
                    .listDirectoryEntries()
                    .filter { it.name in SKIKO_WASM_RUNTIME_FILES }
                    .forEach {
                        BuildPrimitives.copy(
                            from = it,
                            to = taskOutputPath.path.resolve(it.fileName),
                            overwrite = true,
                        )
                    }
            }
    }

    class Result(
        val appPath: Path,
    ) : TaskResult
}

private const val SKIKO_WASM_RUNTIME = "skiko-js-wasm-runtime"
private const val SKIKO_MJS = "skiko.mjs"
private const val SKIKO_WASM = "skiko.wasm"
private val SKIKO_WASM_RUNTIME_FILES = setOf(
    SKIKO_MJS,
    SKIKO_WASM,
)