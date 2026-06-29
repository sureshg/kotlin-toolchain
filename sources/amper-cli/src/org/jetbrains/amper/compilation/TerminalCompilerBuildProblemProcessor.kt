/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation

import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.io.IOException
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.problems.reporting.Level
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.relativeToOrSelf
import kotlin.io.path.useLines

private val internalLogger = LoggerFactory.getLogger(TerminalCompilerBuildProblemProcessor::class.java)

/**
 * Processor that formats compiler messages for the terminal output by adding the highlighted snippet of code
 * to the error message and prints them.
 */
internal class TerminalCompilerBuildProblemProcessor(
    private val terminal: Terminal,
    private val projectRoot: Path,
    private val module: AmperModule,
) : CompilerBuildProblemProcessor {
    override fun process(problem: CompilerBuildProblem) {
        val fileSource = problem.source as? CompilerBuildProblemSource

        val locationStr = fileSource?.let {
            val relativePath = it.file.relativeToOrSelf(projectRoot)
            "$relativePath:${it.line}:${it.column} (${module.userReadableName})"
        }

        val severityStyle = when (problem.level) {
            Level.Error -> terminal.theme.danger
            Level.Warning -> terminal.theme.warning
            Level.WeakWarning -> terminal.theme.warning
        }
        val muted = terminal.theme.muted

        val snippet = fileSource?.let { resolveSnippet(it) }
        val locationWithHyperlink = locationStr?.let {
            TextStyles.hyperlink("file://${fileSource.file}")(it)
        }

        terminal.println(buildString {
            if (fileSource != null && snippet != null) {
                val maxLineNo = fileSource.line + snippet.size - 1
                // At least 3, so most diagnostics are aligned
                val gutterWidth = maxLineNo.toString().length.coerceAtLeast(3)
                val borderPrefix = " ".repeat(gutterWidth + 1)
                val isMultiLine = snippet.size > 1
                append(muted("$borderPrefix╭─ "))
                append(severityStyle(TextStyles.bold("${problem.level.name.uppercase()}: ")))
                append(TextStyles.bold(problem.message))
                appendLine()

                locationWithHyperlink?.let {
                    append(muted("$borderPrefix│ → "))
                    append(it)
                    appendLine()
                }

                appendLine(muted("$borderPrefix│"))

                if (isMultiLine) {
                    append(muted("$borderPrefix│ "))
                    append(severityStyle(buildTopPointer(fileSource, snippet.first())))
                    appendLine()
                }

                snippet.forEachIndexed { i, line ->
                    val lineNo = (fileSource.line + i).toString().padStart(gutterWidth)
                    append(muted("$lineNo │ "))
                    append(highlightRange(line, fileSource, i, snippet.size, severityStyle))
                    appendLine()
                }

                append(muted("$borderPrefix│ "))
                append(severityStyle(buildBottomPointer(fileSource, isMultiLine)))
                appendLine()

                append(muted("$borderPrefix╰─"))
            } else {
                append(severityStyle(TextStyles.bold("${problem.level.name.uppercase()}: ")))
                append(TextStyles.bold(problem.message))
                locationWithHyperlink?.let {
                    appendLine()
                    append("  - $it")
                }
            }
        }, stderr = problem.level == Level.Error)
    }

    private fun resolveSnippet(source: CompilerBuildProblemSource): List<String>? {
        val lines = try {
            source.file.useLines { linesSequence ->
                linesSequence
                    // Lines in CompilerBuildProblemSource are 1-based
                    .drop(source.line - 1)
                    .take(source.lineEnd - source.line + 1)
                    .toList()
            }
        } catch (e: IOException) {
            internalLogger.error(
                "Failed to read file snippet for location: ${source.file}:${source.line}-${source.lineEnd}", e
            )
            return null
        }
        return lines.takeIf { it.isNotEmpty() }
    }

    private fun buildTopPointer(
        source: CompilerBuildProblemSource,
        firstLine: String,
    ): String {
        val padding = source.column - 1
        val length = firstLine.length - padding
        return " ".repeat(padding) + "⌄".repeat(length)
    }

    private fun buildBottomPointer(
        source: CompilerBuildProblemSource,
        isMultiLine: Boolean,
    ): String = if (isMultiLine) {
        val length = (source.columnEnd - 1).coerceAtLeast(1)
        "⌃".repeat(length)
    } else {
        val padding = source.column - 1
        val length = if (source.columnEnd > source.column) {
            source.columnEnd - source.column
        } else {
            1
        }
        " ".repeat(padding) + "⌃".repeat(length)
    }

    private fun highlightRange(
        line: String,
        source: CompilerBuildProblemSource,
        lineIndex: Int,
        totalLines: Int,
        style: TextStyle,
    ): String {
        val start: Int
        val end: Int
        when {
            totalLines == 1 -> {
                start = (source.column - 1).coerceIn(0, line.length)
                end = if (source.columnEnd > source.column) {
                    (source.columnEnd - 1).coerceIn(start, line.length)
                } else {
                    (start + 1).coerceAtMost(line.length)
                }
            }
            lineIndex == 0 -> {
                start = (source.column - 1).coerceIn(0, line.length)
                end = line.length
            }
            lineIndex == totalLines - 1 -> {
                start = 0
                end = if (source.columnEnd > 0) {
                    (source.columnEnd - 1).coerceIn(0, line.length)
                } else {
                    line.length
                }
            }
            else -> {
                start = 0
                end = line.length
            }
        }
        return line.substring(0, start) + style(line.substring(start, end)) + line.substring(end)
    }
}
