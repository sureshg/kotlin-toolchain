/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation

/**
 * Message processor that forwards to multiple processors.
 */
internal class CombiningCompilerBuildProblemProcessor(
    private val processors: List<CompilerBuildProblemProcessor>,
) : CompilerBuildProblemProcessor {
    override fun process(problem: CompilerBuildProblem) {
        processors.forEach { processor -> processor.process(problem) }
    }
}
