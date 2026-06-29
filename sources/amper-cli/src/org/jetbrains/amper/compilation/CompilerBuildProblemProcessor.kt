/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation

import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.NonIdealDiagnostic
import org.jetbrains.kotlin.buildtools.api.CompilerMessageRenderer
import kotlin.io.path.Path

/**
 * Processor for [CompilerBuildProblem].
 *
 * Currently, it is expected to be used as a bridge between Kotlin Toolchain build problem API
 * and Kotlin Build Tools API.
 */
internal interface CompilerBuildProblemProcessor {
    fun process(problem: CompilerBuildProblem)
}

/**
 * Adapts an [CompilerBuildProblemProcessor] to the Kotlin Build Tools API.
 */
internal fun CompilerBuildProblemProcessor.asBuildToolsCompilerMessageRenderer(): CompilerMessageRenderer =
    object : CompilerMessageRenderer {
        override fun render(
            severity: CompilerMessageRenderer.Severity,
            message: String,
            location: CompilerMessageRenderer.SourceLocation?,
        ): String {
            val level = severity.toLevel()
            if (level != null) {
                val problem = if (location == null) {
                    @OptIn(NonIdealDiagnostic::class)
                    GlobalCompilerBuildProblem(
                        message = message,
                        level = level,
                    )
                } else {
                    FileCompilerBuildProblem(
                        message = message,
                        level = level,
                        source = CompilerBuildProblemSource(
                            file = Path(location.path),
                            line = location.line,
                            column = location.column,
                            lineEnd = location.lineEnd,
                            columnEnd = location.columnEnd,
                        )
                    )
                }
                process(problem)
            }
            return defaultBuildToolsRenderedMessage(message, location)
        }

        private fun CompilerMessageRenderer.Severity.toLevel(): Level? = when (this) {
            CompilerMessageRenderer.Severity.ERROR -> Level.Error
            CompilerMessageRenderer.Severity.WARNING -> Level.Warning
            // Do not process INFO and DEBUG messages.
            //
            // We don't process INFO logs to the console because there is too much noise here from several places:
            //  - the incremental Kotlin compilation prints INFO logs when the classpath snapshot doesn't exist
            //    (to say it's going to compile non-incrementally)
            //  - the dataframe compiler plugin prints random schema data as INFO logs during compilation (AMPER-5414)
            CompilerMessageRenderer.Severity.INFO,
            CompilerMessageRenderer.Severity.DEBUG,
                -> null
        }

        private fun defaultBuildToolsRenderedMessage(
            message: String,
            location: CompilerMessageRenderer.SourceLocation?,
        ): String {
            val locationString = location?.let { "${it.path}:${it.line}:${it.column}" }
            return if (locationString == null) message else "$locationString: $message"
        }
    }