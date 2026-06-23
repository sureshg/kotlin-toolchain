/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands.ide

import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.commands.AmperProjectAwareCommand
import org.jetbrains.amper.cli.project.preparePluginsAndReadModel
import org.jetbrains.amper.cli.withBackend

/**
 * Task that allows preparing Compose resources during the sync to generate all the necessary classes required for
 * code insight.
 *
 * Also converts value XML into `.cvr` files and lays them out into conventional directories.
 *
 * @see org.jetbrains.amper.tasks.compose.GenerateResourceAccessorsTask
 * @see org.jetbrains.amper.tasks.compose.GenerateResClassTask
 * @see org.jetbrains.amper.tasks.compose.PrepareComposeResourcesTask
 * @see org.jetbrains.amper.frontend.Fragment.preparedComposeResourcesConventionPath
 */
internal class PrepareComposeResourcesCommand : AmperProjectAwareCommand(name = "prepare-compose-resources") {
    override suspend fun run(cliContext: CliContext) {
        withBackend(cliContext, model = cliContext.preparePluginsAndReadModel()) { backend ->
            backend.prepareComposeResourcesForIde()
        }
    }
}