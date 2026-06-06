/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder.plugins.taskGraph

import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.aomBuilder.plugins.PluginDiagnosticId
import org.jetbrains.amper.frontend.api.TraceablePath
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.messages.PsiBuildProblemSource
import org.jetbrains.amper.frontend.plugins.TaskFromPluginDescription
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.problems.reporting.MultipleLocationsBuildProblemSource
import org.jetbrains.amper.problems.reporting.ProblemReporter
import java.nio.file.Path

context(reporter: ProblemReporter)
internal fun diagnoseConflictingTasksOutputs(
    tasks: List<TaskFromPluginDescription>,
) {
    val taskOutputs: List<Pair<TraceablePath, TaskFromPluginDescription>> = tasks.flatMap { task ->
        task.outputs.map { it.path to task }
    }
    taskOutputs.groupByRoots(
        pathSelector = { [path, _] -> path.value },
    ).forEach { [root: Path, taskOutputs] ->
        val tasksToOutputs = taskOutputs.groupBy(
            keySelector = { [_, task] -> task },
            valueTransform = { [path, _] -> path },
        )
        if (tasksToOutputs.size > 1) {
            // conflicting outputs
            val source = MultipleLocationsBuildProblemSource(
                sources = tasksToOutputs.values.map { paths ->
                    // We choose `first()` here because conflicting paths per single tasks are reported elsewhere
                    paths.first().asBuildProblemSource() as PsiBuildProblemSource
                },
                groupingMessage = SchemaBundle.message("plugin.tasks.output.produced.by.multiple.grouping")
            )
            val taskNames = tasksToOutputs.keys.joinToString {
                FrontendTaskGraphBundle.message("task.graph.node.task",
                    it.name, it.enabledIn.userReadableName, it.pluginId.value)
            }
            reporter.reportBundleError(
                source = source,
                diagnosticId = PluginDiagnosticId.TaskOutputProducedByMultipleTasks,
                messageKey = "plugin.tasks.output.produced.by.multiple",
                root,
                taskNames,
            )
        }
    }
}
