/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import org.jetbrains.amper.cli.context.ProjectCliContext
import org.jetbrains.amper.cli.project.preparePluginsAndReadModel
import org.jetbrains.amper.frontend.Model

/**
 * An [AmperProjectAwareCommand] that also needs a valid project model.
 */
internal abstract class AmperModelAwareCommand(name: String) : AmperProjectAwareCommand(name) {

    final override suspend fun run(cliContext: ProjectCliContext) {
        run(cliContext, cliContext.preparePluginsAndReadModel())
    }

    abstract suspend fun run(cliContext: ProjectCliContext, model: Model)
}
