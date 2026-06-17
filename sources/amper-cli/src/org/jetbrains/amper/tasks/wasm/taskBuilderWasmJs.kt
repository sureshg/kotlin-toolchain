/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.wasm

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.tasks.LinkTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.tasks.TaskNameFactory
import org.jetbrains.amper.tasks.getTaskName

fun ProjectTasksBuilder.setupWasmJsTasks() {
    setupWasmTasks(
        Platform.WASM_JS,
        ::WasmJsCompileKlibTask,
        ::WasmJsLinkTask,
    )

    allModules()
        .alsoPlatforms(Platform.WASM_JS)
        .alsoBuildTypes()
        .filterNot {
            it.isTest
        }
        .filter { needsLinkedExecutable(it.module, isTest = false) }
        .withEach {
            val linkAppTaskName = LinkTaskType.getTaskName(module, platform, isTest, buildType)

            val buildAppTaskName = WasmJsTaskType.BuildWasmJsApp.getTaskName(module, platform, isTest, buildType)
            tasks.registerTask(
                task = WasmJsBuildTask(
                    platform = platform,
                    module = module,
                    buildType = buildType,
                    taskOutputPath = context.getTaskOutputPath(buildAppTaskName),
                    taskName = buildAppTaskName,
                ),
                dependsOn = listOf(linkAppTaskName)
            )

            val runTaskName = WasmJsTaskType.RunWasmJsApp.getTaskName(module, platform, isTest = false, buildType)
            tasks.registerTask(
                task = BrowserRunTask(
                    taskName = runTaskName,
                    platform = platform,
                    buildType = buildType,
                    module = module,
                    runSettings = runSettings,
                ),
                dependsOn = listOf(buildAppTaskName)
            )
        }
}

internal enum class WasmJsTaskType(
    override val internalName: String,
    override val operationMoniker: String,
) : TaskNameFactory.LeafPlatform {
    BuildWasmJsApp("buildWasmJsApp", "building Wasm JS app"),
    RunWasmJsApp("runWasmJsApp", "running Wasm JS app"),
}