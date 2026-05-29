/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.tasks.plugins.meta.BuildAmperPluginInfoTask
import org.jetbrains.amper.tasks.plugins.meta.PreProcessAmperPluginsTask

fun ProjectTasksBuilder.setupAmperPluginTasks() {
    val allPluginModules = model.modules.filter { it.type == ProductType.JVM_AMPER_PLUGIN }
    if (allPluginModules.isEmpty()) return

    val registeredPluginModules = model.amperPlugins.mapTo(hashSetOf()) { it.pluginModule }

    // We gather all the plugins in the project that are not registered in the `project.yaml`.
    // That means they are not included in the `preparePlugins` phase.
    val unregisteredPluginModules = allPluginModules.filter {
        it !in registeredPluginModules
    }

    // We process unregistered plugins separately in a "global task", in batch, as it's more efficient.
    val preProcessUnregisteredPluginsTaskName = TaskName(
        "preProcessUnregisteredPlugins", "pre-processing unregistered plugins",
    )
    if (unregisteredPluginModules.isNotEmpty()) {
        tasks.registerTask(
            PreProcessAmperPluginsTask(
                taskName = preProcessUnregisteredPluginsTaskName,
                projectRoot = context.projectRoot,
                incrementalCache = context.incrementalCache,
                processRunner = context.processRunner,
                unregisteredPluginModules = unregisteredPluginModules,
            )
        )
    }

    for (module in allPluginModules) {
        val isRegistered = module in registeredPluginModules
        val taskName = ModuleTaskTypes.BuildAmperPluginInfo.getTaskName(module)
        tasks.registerTask(
            BuildAmperPluginInfoTask(
                projectContext = context.projectContext,
                module = module,
                isRegistered = isRegistered,
                taskName = taskName,
            ),
            dependsOn = buildList {
                if (!isRegistered) {
                    add(preProcessUnregisteredPluginsTaskName)
                }
            }
        )
    }
}
