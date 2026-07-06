/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.context

import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.ProcessRunner
import org.jetbrains.amper.cli.AmperVersion
import org.jetbrains.amper.cli.telemetry.TelemetryEnvironment
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.problems.reporting.ProblemReporter
import kotlin.io.path.div

class GlobalCliContext(
    override val commandName: String,
    override val userCacheRoot: AmperUserCacheRoot,
    override val terminal: Terminal,
    override val problemReporter: ProblemReporter,
) : CliContext() {

    override val processRunner: ProcessRunner by lazy {
        ProcessRunner(telemetryDir = TelemetryEnvironment.userLevelTracesDir(userCacheRoot))
    }

    /**
     * An incremental cache shared between projects using the same Amper version.
     *
     * **Note:** using this incremental cache introduces a lot of directories in the shared cache, especially when
     * developing the Kotlin Toolchain locally. Make sure you only use it when it is impossible to use
     * the project-specific incremental cache.
     */
    override val incrementalCache: IncrementalCache by lazy {
        IncrementalCache(
            stateRoot = userCacheRoot.path / "incremental.state" / AmperVersion.codeIdentifier,
            codeVersion = AmperVersion.codeIdentifier,
            openTelemetry = openTelemetry,
        )
    }
}
