/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins

import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.coroutineScope
import org.jetbrains.amper.ProcessRunner
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.CliProblemReporter
import org.jetbrains.amper.cli.logging.infoNoConsole
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.cli.widgets.withIndeterminateProgress
import org.jetbrains.amper.frontend.plugins.PluginManifest
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.incrementalcache.executeForSerializable
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.problems.reporting.Level
import org.slf4j.LoggerFactory
import java.nio.file.Path

internal suspend fun doPreparePlugins(
    terminal: Terminal,
    projectRoot: AmperProjectRoot,
    incrementalCache: IncrementalCache,
    plugins: Map<Path, PluginManifest>,
    processRunner: ProcessRunner,
): List<PluginData> = coroutineScope {
    require(plugins.isNotEmpty())

    val pluginDataWithDiagnostics = incrementalCache.executeForSerializable(
        key = "prepare-plugins",
        inputValues = mapOf(
            "plugins" to plugins.values.joinToString()
        ),
        inputFiles = plugins.keys.toList(),
    ) {
        logger.infoNoConsole("Processing local plugin schema for [${plugins.values.joinToString { it.id.value }}]...")

        terminal.withIndeterminateProgress(
            message = terminal.theme.muted("Pre-processing local plugins (will be cached)"),
            messageNonInteractive = null, // no message in case of redirected output, to avoid polluting the "real data"
        ) {
            runAmperSchemaProcessor(
                projectRoot = projectRoot,
                plugins = plugins,
                processRunner = processRunner,
            )
        }
    }

    val allProblems = pluginDataWithDiagnostics.flatMap { it.diagnostics }
    allProblems.forEach(CliProblemReporter::reportMessage)
    if (allProblems.any { it.level.atLeastAsSevereAs(Level.Error) }) {
        userReadableError("Local plugins pre-processing failed, see the errors above.")
    }

    pluginDataWithDiagnostics.map { it.pluginData }
}

private val logger = LoggerFactory.getLogger("preparePlugins")
