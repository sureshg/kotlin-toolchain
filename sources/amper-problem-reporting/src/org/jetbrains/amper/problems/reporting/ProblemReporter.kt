/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.problems.reporting

interface ProblemReporter {

    fun reportMessage(message: BuildProblem)
}

/**
 * A [ProblemReporter] that does nothing.
 */
object NoopProblemReporter : ProblemReporter {
    override fun reportMessage(message: BuildProblem) = Unit
}

