/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.concurrency

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Returns a list containing the results of applying the given [transform] function to each element in the original
 * collection.
 *
 * All elements are transformed concurrently (in individual coroutines). To control parallelism, use the coroutine
 * context.
 */
suspend fun <T, U> Iterable<T>.mapConcurrently(transform: suspend (T) -> U): List<U> = coroutineScope {
    map {
        async { transform(it) }
    }.awaitAll()
}

/**
 * Returns a list containing the results of applying the given [transform] function to each element in the original
 * collection, and flattening the results.
 *
 * All elements are transformed concurrently (in individual coroutines). To control parallelism, use the coroutine
 * context.
 */
suspend fun <T, U> Iterable<T>.flatMapConcurrently(transform: suspend (T) -> Iterable<U>): List<U> = coroutineScope {
    map {
        async { transform(it) }
    }.awaitAll().flatten()
}
