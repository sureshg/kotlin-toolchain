/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands.ide

import com.github.ajalt.clikt.core.Context
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.commands.AmperProjectAwareCommand
import org.jetbrains.amper.cli.project.preparePluginsAndReadModel
import org.jetbrains.amper.cli.withBackend
import org.jetbrains.amper.tasks.ios.ManageXCodeProjectTask

/**
 * Generates or checks already present Xcode projects for all `ios/app` modules.
 */
internal class ManageXcodeCommand : AmperProjectAwareCommand("manage-xcode") {
    override fun help(context: Context): String =
        "Generates or checks already present Xcode projects for all `ios/app` modules"

    override suspend fun run(cliContext: CliContext) {
        withBackend(cliContext, model = cliContext.preparePluginsAndReadModel()) { backend ->
            val xcodeTasks = backend.taskGraph.tasks
                .filterIsInstance<ManageXCodeProjectTask>()
                .mapTo(mutableSetOf()) { it.taskName.id }
            if (xcodeTasks.isEmpty()) return@withBackend
            backend.runTasks(xcodeTasks)
        }
    }
}