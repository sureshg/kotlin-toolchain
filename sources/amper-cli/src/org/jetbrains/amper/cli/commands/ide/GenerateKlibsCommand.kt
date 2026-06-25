/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands.ide

import com.github.ajalt.clikt.core.Context
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.commands.AmperProjectAwareCommand
import org.jetbrains.amper.cli.project.preparePluginsAndReadModel
import org.jetbrains.amper.cli.withBackend
import org.jetbrains.amper.tasks.CinteropGenSettings

/**
 * Required for the IDE to make sure commonization/cinterop is up to date to add the relevant `klib`s
 * to the project structure and ensure the code is analyzed correctly in the intermediate fragments.
 *
 * All the results are going to be in [org.jetbrains.amper.frontend.Fragment.generatedCinteropKlibsDirPath].
 *
 * @see org.jetbrains.amper.engine.GenerateKlibsForIdeTask
 * @see org.jetbrains.amper.tasks.native.CommonizeCInteropKlibsTask
 */
internal class GenerateKlibsCommand : AmperProjectAwareCommand(name = "generate-klibs") {
    override suspend fun run(cliContext: CliContext) {
        withBackend(
            cliContext = cliContext,
            cinteropGenSettings = CinteropGenSettings(
                // Needed to not fail the sync when some platform-specific libs are missing in the user's dev setup
                ignorePlatformFailures = true,
            ),
            model = cliContext.preparePluginsAndReadModel(),
        ) { backend ->
            backend.generateKlibsForIde()
        }
    }

    override fun help(context: Context): String =
        "Generates cinterop Klibs and their commonized variants so IDE can analyze such code usage and provide correct code insight"
}
