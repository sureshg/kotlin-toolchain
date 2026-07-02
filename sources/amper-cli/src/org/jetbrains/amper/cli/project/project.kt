/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.project

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import org.jetbrains.amper.buildinfo.AmperBuild
import org.jetbrains.amper.cli.UserReadableError
import org.jetbrains.amper.cli.context.ProjectCliContext
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.aomBuilder.readProjectModel
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.plugins.prepareMavenPlugins
import org.jetbrains.amper.plugins.preparePlugins
import org.jetbrains.amper.problems.reporting.CollectingProblemReporter
import org.jetbrains.amper.problems.reporting.anyErrorsReported
import org.jetbrains.amper.problems.reporting.plus
import org.jetbrains.amper.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.use
import kotlin.io.path.div
import kotlin.io.path.pathString

/**
 * Reads the [Model] from the Amper project files in this [ProjectCliContext].
 *
 * @throws UserReadableError if any error (or fatal error) is diagnosed in the model
 */
internal suspend fun ProjectCliContext.preparePluginsAndReadModel(): Model {
    val pluginData = spanBuilder("Prepare plugins")
        .use { preparePlugins(context = this@preparePluginsAndReadModel) }
    val mavenPluginsWithXmls = spanBuilder("Prepare Maven plugins").use {
        prepareMavenPlugins(
            projectContext = projectContext,
            incrementalCache = mavenPluginsIncrementalCache(
                projectContext,
                GlobalOpenTelemetry.get(),
                AmperBuild.mavenVersion
            ),
        )
    }

    val collecting = CollectingProblemReporter()
    val model = spanBuilder("Read model from Kotlin project files").use {
        with(collecting + problemReporter) {
            projectContext.readProjectModel(
                pluginData = pluginData,
                mavenPluginXmls = mavenPluginsWithXmls,
            )
        }
    }

    // In CLI, we immediately stop the build if we had any error, because the model could be incorrect otherwise
    if (collecting.anyErrorsReported) {
        userReadableError("failed to read Kotlin project model, refer to the errors above")
    }

    checkUniqueModuleNames(model.modules)
    return model
}

/**
 * Dedicated incremental cache for downloading maven plugins meta-information.
 */
private fun mavenPluginsIncrementalCache(
    projectContext: AmperProjectContext,
    openTelemetry: OpenTelemetry,
    amperVersion: String,
): IncrementalCache = IncrementalCache(
    stateRoot = projectContext.projectBuildDir / "maven.plugins.incremental.state",
    codeVersion = amperVersion,
    openTelemetry = openTelemetry,
)

private fun checkUniqueModuleNames(modules: List<AmperModule>) {
    for ([moduleUserReadableName, moduleList] in modules.groupBy { it.userReadableName }) {
        if (moduleList.size > 1) {
            val joinToString = moduleList.joinToString("\n") {
                it.source.buildFile.pathString
            }
            userReadableError("Module name '${moduleUserReadableName}' is not unique, it's declared in:\n$joinToString")
        }
    }
}
