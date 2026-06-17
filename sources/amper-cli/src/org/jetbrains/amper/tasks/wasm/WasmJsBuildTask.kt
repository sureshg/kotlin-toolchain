/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.wasm

import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.engine.BuildTask
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.engine.requireSingleDependency
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.web.WebLinkTask
import org.jetbrains.amper.util.BuildType
import java.nio.file.Path
import kotlin.io.path.exists

class WasmJsBuildTask(
    override val platform: Platform,
    override val module: AmperModule,
    override val buildType: BuildType,
    private val taskOutputPath: TaskOutputRoot,
    override val taskName: TaskName,
) : BuildTask {
    init {
        require(platform.isLeaf)
        require(platform.isDescendantOf(Platform.WASM_JS))
    }

    override val isTest: Boolean
        get() = false

    override suspend fun run(
        dependenciesResult: List<TaskResult>,
        executionContext: TaskGraphExecutionContext,
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

        return Result(taskOutputPath.path)
    }

    class Result(
        val appPath: Path,
    ) : TaskResult
}