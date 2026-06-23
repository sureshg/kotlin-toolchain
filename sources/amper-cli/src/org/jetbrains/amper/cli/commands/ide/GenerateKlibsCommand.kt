/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands.ide

import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.commands.AmperProjectAwareCommand
import org.jetbrains.amper.cli.project.preparePluginsAndReadModel
import org.jetbrains.amper.cli.withBackend

/**
 * Required for the IDE to make sure commonization/cinterop is up to date to add the relevant `klib`s
 * to the project structure and ensure the code is analyzed correctly in the intermediate fragments.
 *
 * @see org.jetbrains.amper.engine.GenerateKlibsForIdeTask
 */
internal class GenerateKlibsCommand : AmperProjectAwareCommand(name = "generate-klibs") {
    override suspend fun run(cliContext: CliContext) {
        withBackend(cliContext, model = cliContext.preparePluginsAndReadModel()) { backend ->
            backend.generateKlibsForIde()
        }
    }
}
