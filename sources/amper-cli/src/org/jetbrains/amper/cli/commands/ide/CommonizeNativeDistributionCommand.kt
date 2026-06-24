/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands.ide

import com.github.ajalt.clikt.core.Context
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.commands.AmperProjectAwareCommand
import org.jetbrains.amper.cli.project.preparePluginsAndReadModel
import org.jetbrains.amper.cli.withBackend
import org.jetbrains.amper.tasks.native.CommonizeNativeDistributionTask

/**
 * Commonizes Kotlin/Native distribution to the subset of platforms present in the project.
 */
internal class CommonizeNativeDistributionCommand : AmperProjectAwareCommand("commonize-native-distribution") {
    override fun help(context: Context): String =
        "Commonizes Kotlin/Native distribution to the subset of platforms present in the project"

    override suspend fun run(cliContext: CliContext) {
        withBackend(cliContext, model = cliContext.preparePluginsAndReadModel()) { backend ->
            val commonizeTask = CommonizeNativeDistributionTask.TASK_NAME
            backend.runTask(commonizeTask.id)
        }
    }
}