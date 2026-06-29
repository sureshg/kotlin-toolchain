/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation

/**
 * Collects compiler messages to be able to cache them.
 *
 * @see org.jetbrains.amper.tasks.jvm.JvmCompileTask.replayCachedCompilerBuildProblems
 */
internal class CollectingCompilerBuildProblemProcessor : CompilerBuildProblemProcessor {
    private val collectedMessages = mutableListOf<CompilerBuildProblem>()

    val messages: List<CompilerBuildProblem>
        get() = collectedMessages.toList()

    override fun process(problem: CompilerBuildProblem) {
        collectedMessages += problem
    }
}
