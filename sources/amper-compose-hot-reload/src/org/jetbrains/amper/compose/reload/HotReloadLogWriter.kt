/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compose.reload

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.slf4j.MDC
import org.tinylog.core.LogEntry
import org.tinylog.core.LogEntryValue
import org.tinylog.writers.Writer
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

private const val bufferCapacity = 1024

/**
 * Must be manually registered in the CLI's `tinylog.properties` file.
 */
internal class HotReloadLogWriter(@Suppress("UNUSED_PARAMETER") properties: Map<String, String>): Writer {
    override fun getRequiredLogEntryValues(): Collection<LogEntryValue> = emptyList()

    override fun write(entry: LogEntry) {
        if (entry.context[CONTEXT_KEY] == CONTEXT_VALUE) {
            logFlow.tryEmit(entry)
        }
    }

    override fun flush() = Unit
    override fun close() = Unit

    companion object {
        private const val CONTEXT_KEY = "use"
        private const val CONTEXT_VALUE = "hotReload"

        suspend fun <R> captureLogsForHotReload(
            block: suspend CoroutineScope.() -> R,
        ): R {
            contract {
                callsInPlace(block, InvocationKind.EXACTLY_ONCE)
            }
            val contextMap = buildMap {
                putAll(MDC.getCopyOfContextMap())
                put(CONTEXT_KEY, CONTEXT_VALUE)
            }

            return withContext(MDCContext(contextMap)) {
                block()
            }
        }

        val logFlow: SharedFlow<LogEntry>
            field = MutableSharedFlow<LogEntry>(
                replay = 0,
                extraBufferCapacity = bufferCapacity,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
    }
}
