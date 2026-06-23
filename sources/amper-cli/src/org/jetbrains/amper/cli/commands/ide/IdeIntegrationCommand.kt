/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands.ide

import com.github.ajalt.clikt.core.subcommands
import org.jetbrains.amper.cli.commands.AmperSubcommand

/**
 * Entrypoint for different commands required for IDE integration.
 *
 * Tasks performed by these commands are usually performed during the build pipeline, but their execution
 * is needed for code insight or IDE features (e.g., Compose preview).
 */
internal class IdeIntegrationCommand : AmperSubcommand(name = "ide-integration") {
    init {
        subcommands(
            GenerateKlibsCommand(),
            PrepareComposeResourcesCommand(),
        )
    }

    override val hiddenFromHelp: Boolean = true

    override suspend fun run() = Unit
}