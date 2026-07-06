/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.context

import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import org.jetbrains.amper.ProcessRunner
import org.jetbrains.amper.cli.AmperVersion
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.amper.util.DateTimeFormatForFilenames
import org.jetbrains.amper.util.nowInDefaultTimezone
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.jvm.optionals.getOrNull

class ProjectCliContext(
    override val commandName: String,
    override val userCacheRoot: AmperUserCacheRoot,
    override val terminal: Terminal,
    override val problemReporter: ProblemReporter,
    val projectContext: AmperProjectContext,
) : CliContext() {

    val projectRoot: AmperProjectRoot = AmperProjectRoot(projectContext.projectRoot.path)

    val buildOutputRoot: AmperBuildOutputRoot by lazy {
        AmperBuildOutputRoot(projectContext.projectBuildDir.createDirectories())
    }

    val projectTempRoot: AmperProjectTempRoot by lazy {
        AmperProjectTempRoot((projectContext.projectBuildDir / "temp").createDirectories())
    }

    /**
     * The root directory containing all logs for all Amper executions in the current project.
     */
    val projectLogsRoot: AmperProjectLogsRoot by lazy {
        AmperProjectLogsRoot((projectContext.projectBuildDir / "logs").createDirectories())
    }

    /**
     * The logs directory for the current Amper execution.
     */
    val currentLogsRoot: AmperBuildLogsRoot by lazy {
        val currentTimestamp = LocalDateTime.nowInDefaultTimezone().format(DateTimeFormatForFilenames)
        val currentProcess = ProcessHandle.current()

        val pid = currentProcess.pid() // avoid clashes with concurrent Amper processes

        /*
        On linux/macOS, the wrapper script uses 'exec java' to start Amper, which replaces the process and uses the
         same PID.

        On Windows, there is no equivalent, so child processes are created.
        That happens twice: once the wrapper calls the launcher and once more when the launcher calls java.exe.
        The "wrapper -> launcher" call is done via the busybox*.exe binary,
         which then does an `exec` for java.exe. But in Windowns busybox port `exec` still spawns the child process
         as there is no other way.

        Callers are only aware of the wrapper process ID,
         so to allow the automated discovery of the logs dir, we also record the parent process ID for Windows users.
        */
        val grandParentPid = currentProcess.parent().getOrNull()?.parent()?.getOrNull()?.pid()

        val currentLogsPath = projectLogsRoot.path.resolve("amper_${currentTimestamp}_${pid}-${grandParentPid}_$commandName")
        AmperBuildLogsRoot(currentLogsPath.createDirectories())
    }

    /**
     * The incremental cache for the current project.
     */
    override val incrementalCache: IncrementalCache by lazy {
        IncrementalCache(
            stateRoot = buildOutputRoot.path.resolve("incremental.state"),
            codeVersion = AmperVersion.codeIdentifier,
            openTelemetry = openTelemetry,
        )
    }

    override val processRunner: ProcessRunner by lazy {
        ProcessRunner(telemetryDir = currentLogsRoot.telemetryPath)
    }

    companion object {
        /**
         * An absolute path to the wrapper script that the process currently runs under.
         */
        val wrapperScriptPath: Path by lazy {
            System.getenv("KOTLIN_CLI_WRAPPER_PATH")?.takeIf { it.isNotBlank() }?.let(::Path)
                ?: error("Missing `KOTLIN_CLI_WRAPPER_PATH` env var. Is your Kotlin Toolchain distribution intact?")
        }
    }
}