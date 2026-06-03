/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.plugins

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.project.AmperFrontendProjectRoot
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.tree.completeTree
import org.jetbrains.amper.frontend.tree.instance
import org.jetbrains.amper.frontend.tree.reading.UnknownPropertiesParsingMode
import org.jetbrains.amper.frontend.tree.reading.readTree
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.frontend.types.generated.*
import org.jetbrains.amper.problems.reporting.NoopProblemReporter

data class PluginManifest(
    val id: String,
    val description: String?,
    val settingsClass: String?,
)

/**
 * Parses [PluginManifest] from the `module.yaml` file that is allegedly a plugin.
 * Does no error reporting, and the result is obtained on the best effort basis.
 *
 * @return plugin manifest on the best effort basis or `null` is this `module.yaml` file is not a plugin for sure.
 */
context(_: FrontendPathResolver, _: AmperFrontendProjectRoot)
fun parsePluginManifestFromModuleFile(
    moduleFile: VirtualFile,
) : PluginManifest? {
    context(NoopProblemReporter, SchemaTypingContext()) {
        val pluginModuleTree = readTree(
            file = moduleFile,
            declaration = DeclarationOfMinimalPluginModule,
            unknownPropertiesMode = UnknownPropertiesParsingMode.SkipSilently,
            parseContexts = false,
        )
        val moduleHeader = TreeRefiner().refineTree(pluginModuleTree, EmptyContexts)
            .completeTree()?.instance<MinimalPluginModule>()
            ?: return null

        if (moduleHeader.product.type != ProductType.JVM_AMPER_PLUGIN)
            return null

        @Suppress("DEPRECATION") // we fall back to the deprecated description for a transition period
        return PluginManifest(
            id = moduleHeader.pluginInfo.id?.value ?: moduleFile.parent.name,
            description = moduleHeader.description ?: moduleHeader.pluginInfo.description,
            settingsClass = moduleHeader.pluginInfo.settingsClass
        )
    }
}