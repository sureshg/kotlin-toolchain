/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.js

import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.tasks.CommonTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.tasks.getTaskName
import org.jetbrains.amper.tasks.native.NativeTaskType
import org.jetbrains.amper.util.BuildType

fun ProjectTasksBuilder.setupJsTasks() {

    allModules()
        .alsoPlatforms(Platform.JS)
        .alsoTests()
        .withEach {
            val compileKLibTaskName = CommonTaskType.Compile.getTaskName(module, platform, isTest)
            tasks.registerTask(
                task = JsCompileKlibTask(
                    module = module,
                    platform = platform,
                    userCacheRoot = context.userCacheRoot,
                    taskOutputRoot = context.getTaskOutputPath(compileKLibTaskName),
                    incrementalCache = context.incrementalCache,
                    taskName = compileKLibTaskName,
                    tempRoot = context.projectTempRoot,
                    isTest = isTest,
                    jdkProvider = context.jdkProvider,
                    processRunner = context.processRunner,
                    terminal = context.terminal,
                ),
                dependsOn = buildList {
                    add(CommonTaskType.Dependencies.getTaskName(module, platform, isTest))
                    if (isTest) {
                        // todo (AB) : Check if this is required for test KLib compilation
                        add(CommonTaskType.Compile.getTaskName(module, platform, isTest = false))
                    }
                },
            )

            if (needsLinkedExecutable(module, isTest)) {
                val linkAppTaskName = NativeTaskType.Link.getTaskName(module, platform, isTest)
                tasks.registerTask(
                    task = JsLinkTask(
                        module = module,
                        platform = platform,
                        userCacheRoot = context.userCacheRoot,
                        taskOutputRoot = context.getTaskOutputPath(linkAppTaskName),
                        incrementalCache = context.incrementalCache,
                        taskName = linkAppTaskName,
                        tempRoot = context.projectTempRoot,
                        isTest = isTest,
                        jdkProvider = context.jdkProvider,
                        compileKLibTaskId = compileKLibTaskName.id,
                        processRunner = context.processRunner,
                        buildType = BuildType.Release,
                    ),
                    dependsOn = buildList {
                        add(compileKLibTaskName)
                        add(CommonTaskType.Dependencies.getTaskName(module, platform, isTest))
                        if (isTest) {
                            add(CommonTaskType.Compile.getTaskName(module, platform, isTest = false))
                        }
                    }
                )
            }
        }

    allModules()
        .alsoPlatforms(Platform.JS)
        .alsoTests()
        .selectModuleDependencies(ResolutionScope.RUNTIME).withEach {
            tasks.registerDependency(
                CommonTaskType.Compile.getTaskName(module, platform, isTest),
                CommonTaskType.Compile.getTaskName(dependsOn, platform, false)
            )

            if (needsLinkedExecutable(module, isTest)) {
                tasks.registerDependency(
                    NativeTaskType.Link.getTaskName(module, platform, isTest),
                    CommonTaskType.Compile.getTaskName(dependsOn, platform, false)
                )
            }
        }
}

private fun needsLinkedExecutable(module: AmperModule, isTest: Boolean) =
    module.type.isApplication() || isTest
