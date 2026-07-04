/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.plugins.meta

import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.engine.BuildTask
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.engine.requireSingleDependency
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.aomBuilder.plugins.AmperPluginImpl
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.problems.reporting.CollectingProblemReporter
import org.jetbrains.amper.problems.reporting.anyErrorsReported
import org.jetbrains.amper.problems.reporting.plus
import org.jetbrains.amper.tasks.EmptyTaskResult
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.util.BuildType
import org.slf4j.LoggerFactory

class BuildAmperPluginInfoTask(
    private val projectContext: AmperProjectContext,
    override val module: AmperModule,
    /**
     * Indicates whether the plugin is registered in the project
     */
    private val isRegistered: Boolean,
    override val taskName: TaskName,
) : BuildTask {
    override val isTest: Boolean = false
    override val platform: Platform = Platform.JVM
    override val buildType: BuildType? = null

    private val logger = LoggerFactory.getLogger(javaClass)

    context(executionContext: TaskGraphExecutionContext)
    override suspend fun run(
        dependenciesResult: List<TaskResult>,
    ): TaskResult {
        if (isRegistered) {
            // Do nothing for now, as we don't know the "plugin binary format" yet.
            // TODO: fetch plugin data from the model
            return EmptyTaskResult
        }

        val globalResult = dependenciesResult.requireSingleDependency<PreProcessAmperPluginsTask.Result>()
        val modulePath = PluginData.Source.Local(module.source.moduleDir)
        val (pluginData, diagnostics) = globalResult.result.first { it.pluginData.source == modulePath }

        val collecting = CollectingProblemReporter()
        val reporter = collecting + executionContext

        diagnostics.forEach(reporter::reportMessage)
        if (collecting.anyErrorsReported) {
            userReadableError("Plugin Kotlin schema processing failed, see the errors above.")
        }

        // TODO: It's not super beautiful to create `AmperPluginImpl` directly here; we'll see what can be done later
        AmperPluginImpl(
            projectContext = projectContext,
            pluginModule = module,
            pluginData = pluginData,
            types = SchemaTypingContext(
                pluginData = listOf(pluginData),
            ),
            problemReporter = reporter,
        )
        if (collecting.anyErrorsReported) {
            userReadableError("`plugin.yaml` processing failed, see the errors above.")
        }

        logger.info("Kotlin Toolchain plugin passed validation - OK")

        // TODO: Actually save something

        return EmptyTaskResult
    }
}