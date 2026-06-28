/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder.plugins

import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.aomBuilder.ModuleBuildCtx
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.frontend.types.generated.*
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.problems.reporting.FileBuildProblemSource
import org.jetbrains.amper.problems.reporting.MultipleLocationsBuildProblemSource
import org.jetbrains.amper.problems.reporting.ProblemReporter

context(problemReporter: ProblemReporter, types: SchemaTypingContext, projectContext: AmperProjectContext)
internal fun readPlugins(
    modules: List<ModuleBuildCtx>,
    pluginData: List<PluginData>,
): List<AmperPluginImpl> {
    val seenPluginIds = hashMapOf<PluginData.Id, MutableList<Traceable>>()
    val pluginReaders = projectContext.enabledLocalAmperPluginModuleFiles.mapNotNull mapPlugins@{ pluginModuleFile ->
        val pluginModule = modules.find { it.moduleFile == pluginModuleFile }
            ?: return@mapPlugins null

        // Report invalid product types
        val product = pluginModule.moduleCtxModule.product
        if (product.type != ProductType.JVM_AMPER_PLUGIN) {
            problemReporter.reportBundleError(
                source = product.asBuildProblemSource(),
                diagnosticId = PluginDiagnosticId.UnexpectedPluginProductType,
                messageKey = "plugin.unexpected.product.type",
                ProductType.JVM_AMPER_PLUGIN.value,
                product.type,
            )
            return@mapPlugins null
        }

        val pluginInfo = pluginModule.moduleCtxModule.pluginInfo!! // safe - default is always set for plugins
        val pluginId = pluginInfo.idDelegate
        if (pluginId.value in seenPluginIds) {
            seenPluginIds[pluginId.value]!!.add(pluginId)
            return@mapPlugins null // Skip the duplicate
        } else {
            seenPluginIds[pluginId.value] = mutableListOf(pluginId)
        }

        val pluginData = pluginData.find { it.id == pluginId.value }
            ?: return@mapPlugins null

        AmperPluginImpl(
            projectContext = projectContext,
            pluginData = pluginData,
            pluginModule = pluginModule.module,
            problemReporter = problemReporter,
            types = types,
        )
    }

    for ([id, traceableIds] in seenPluginIds) {
        if (traceableIds.size < 2) continue
        val source = MultipleLocationsBuildProblemSource(
            sources = traceableIds.map { it.asBuildProblemSource() as FileBuildProblemSource },
            groupingMessage = SchemaBundle.message("plugin.id.duplicate.grouping", id.value)
        )
        problemReporter.reportBundleError(
            source = source,
            diagnosticId = PluginDiagnosticId.PluginDuplicateId,
            messageKey = "plugin.id.duplicate",
        )
    }

    return pluginReaders
}