/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.widgets

import com.github.ajalt.mordant.animation.coroutines.animateInCoroutine
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Spinner
import com.github.ajalt.mordant.widgets.progress.progressBarLayout
import com.github.ajalt.mordant.widgets.progress.spinner
import com.github.ajalt.mordant.widgets.progress.text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Displays the given [message] with an infinitely spinning spinner while the given [block] is running.
 * The message is removed as soon as [block] completes (successfully or not).
 *
 * If this [Terminal] is not interactive (e.g. on CI, or when redirecting the output), the [messageNonInteractive] is
 * simply printed before running [block]. By default, [messageNonInteractive] is the same as [message].
 * You can disable this behavior by setting [messageNonInteractive] to null explicitly. In this case, in non-interactive
 * mode, only [block] will be executed and no message will be shown.
 */
suspend fun <T> Terminal.withIndeterminateProgress(
    message: String,
    messageNonInteractive: String? = message,
    block: suspend CoroutineScope.() -> T,
): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        returnsResultOf(block)
    }
    return coroutineScope {
        if (terminalInfo.interactive) {
            val animationJob = launchIndeterminateProgress(message)
            try {
                block()
            } finally {
                animationJob.cancel()
            }
        } else {
            // TODO reconsider the need for this once we have structured output: this message is probably never noise when
            //  the reader is a human, and tools should read structured output, not this.
            if (messageNonInteractive != null) {
                println(messageNonInteractive)
            }
            block()
        }
    }
}

/**
 * Displays the given [message] with an infinitely spinning spinner.
 *
 * To stop the spinner and hide the message, cancel the [Job] returned by this function.
 */
context(scope: CoroutineScope)
private fun Terminal.launchIndeterminateProgress(message: String): Job {
    val platformProgress = PlatformProgressReporter(terminal = this)

    val animator = progressBarLayout(align = TextAlign.LEFT) {
        spinner(Spinner.Dots(theme.success))
        text(message)
    }.animateInCoroutine(terminal = this)

    return scope.launch {
        try {
            platformProgress.update(PlatformProgressReporter.Progress.Indeterminate)
            animator.execute()
        } finally {
            platformProgress.update(PlatformProgressReporter.Progress.Hidden)
            animator.clear()
        }
    }
}
