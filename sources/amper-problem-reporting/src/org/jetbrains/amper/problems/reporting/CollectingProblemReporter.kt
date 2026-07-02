/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.problems.reporting

/**
 * A [ProblemReporter] that collects problems so they can be queried later.
 *
 * Note: This class is not thread-safe.
 */
class CollectingProblemReporter : ProblemReporter {
    /**
     * All problems recorded so far.
     */
    val problems: List<BuildProblem>
        field = mutableListOf<BuildProblem>()

    override fun reportMessage(message: BuildProblem) {
        problems.add(message)
    }
}

/**
 * `true` if any problems with the [Level.Error] severity have been recorded so far;
 * `false` otherwise.
 */
val CollectingProblemReporter.anyErrorsReported get() = problems.any { it.level == Level.Error }

/**
 * `true` if any diagnositcs have been recorded so far;
 * `false` otherwise.
 */
val CollectingProblemReporter.anyProblemsReported get() = problems.isNotEmpty()

/**
 * `false` if any diagnositcs have been recorded so far;
 * `true` otherwise.
 */
val CollectingProblemReporter.noProblemsReported get() = problems.isEmpty()

/**
 * Report all collected problems from the current reporter to the given [other] reporter.
 */
fun CollectingProblemReporter.replayProblemsTo(other: ProblemReporter) =
    problems.forEach { other.reportMessage(it) }