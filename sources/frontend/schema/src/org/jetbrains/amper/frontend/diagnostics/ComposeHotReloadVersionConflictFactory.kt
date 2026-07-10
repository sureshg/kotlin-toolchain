/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.types.generated.*
import org.jetbrains.amper.problems.reporting.BuildProblem
import org.jetbrains.amper.problems.reporting.BuildProblemSource
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.FileBuildProblemSource
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.MultipleLocationsBuildProblemSource
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.annotations.Nls

/**
 * MCP server is launched per-project, not per-module, so we issue a warning if the compose-hot-reload version is
 * inconsistent between the modules.
 */
object ComposeHotReloadVersionConflictFactory : AomModelDiagnosticFactory {
    override fun analyze(
        model: Model,
        problemReporter: ProblemReporter,
    ) {
        val versions = model.modules.filter {
            it.type == ProductType.JVM_APP
        }.mapNotNull { module ->
            module.leafFragments.find { it.platform == Platform.JVM && !it.isTest }
                ?.takeIf { it.settings.compose.enabled }
        }.map { jvmAppFragment ->
            jvmAppFragment.settings.compose.experimental.hotReload.versionDelegate
        }.groupBy { it.value }

        if (versions.size > 1) {
            problemReporter.reportMessage(Problem(versions))
        }
    }

    class Problem(
        val versions: Map<String, List<Traceable>>,
    ) : BuildProblem {
        override val source: BuildProblemSource = MultipleLocationsBuildProblemSource(
            sources = versions.values.flatten()
                .mapNotNull { it.trace.asBuildProblemSource() as? FileBuildProblemSource },
            groupingMessage = "compose.hot.reload.version.inconsistent.across.project.grouping",
        )

        override val diagnosticId get() = FrontendDiagnosticId.ComposeHotReloadVersionMismatch
        override val level get() = Level.Warning
        override val type get() = BuildProblemType.InconsistentConfiguration

        override val message: @Nls String = SchemaBundle.message(
            "compose.hot.reload.version.inconsistent.across.project",
            versions.keys.joinToString { "`$it`" },
        )
    }
}
