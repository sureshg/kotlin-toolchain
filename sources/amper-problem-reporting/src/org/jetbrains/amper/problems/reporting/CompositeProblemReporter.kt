/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.problems.reporting

/**
 * Messages are going to be reported to all of the [delegates] sequentially.
 * Use [plus] to conveniently create the instance of this class.
 */
class CompositeProblemReporter(
    val delegates: List<ProblemReporter>,
) : ProblemReporter {
    override fun reportMessage(message: BuildProblem) {
        delegates.forEach { it.reportMessage(message) }
    }
}

/**
 * Creates a [CompositeProblemReporter] from the given reporters.
 */
operator fun ProblemReporter.plus(another: ProblemReporter): ProblemReporter {
    return CompositeProblemReporter(buildList(2) {
        this@plus.unwrapComposite()
        another.unwrapComposite()
    })
}

// Ensures we flatten CompositeProblemReporter when creating a new one instead of creating 2-tree-like structures
context(into: MutableList<in ProblemReporter>)
private fun ProblemReporter.unwrapComposite() = when (this) {
    is CompositeProblemReporter -> into.addAll(delegates)
    else -> into.add(this)
}
