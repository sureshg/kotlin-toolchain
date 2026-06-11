/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.logging

import org.slf4j.Logger
import org.slf4j.MDC
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal inline fun <T> withMDCEntry(key: String, value: String, block: () -> T): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        returnsResultOf(block)
    }
    return MDC.putCloseable(key, value).use { block() }
}

/**
 * Logs the given [message] at INFO level, but only to the logs file, not to the console.
 */
internal fun Logger.infoNoConsole(message: String) {
    withoutConsoleLogging {
        info(message)
    }
}
