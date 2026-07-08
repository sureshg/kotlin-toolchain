/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.fswatching

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.amper.fswatching.common.WatchServicePathWatchingService
import java.nio.file.Path

/**
 * See [watchPaths].
 */
data class PathsChangedEvent(
    /**
     * A set of changed paths.
     * It is always a subset of `paths` that were passed to the [watchPaths] call.
     */
    val affectedPaths: Set<Path>,
)

/**
 * Starts watching the given [paths] and returns the [SharedFlow] of the [PathsChangedEvent].
 * When this function returns, [paths] are guaranteed to be already watched.
 *
 * The granularity of events (i.e., their frequency and the size of the [PathsChangedEvent.affectedPaths] set)
 * is implementation-dependent.
 *
 * @param scope a coroutine scope to launch the watching service in.
 *  It has to be canceled to stop watching and free underlying resources.
 * @param paths a set of normalized absolute paths to watch.
 *  Each path can point to an arbitrary place, e.g., denote *files*, *directories* or be non-existent.
 *  Each path thus can fall into three cases. It may
 *  1. Be an existing file. Then only the **changes to that file** are observed.
 *  2. Be an existing directory. Then the **changes for the whole subtree (recursively)** are observed.
 *  3. Not exist. In that case the first event will be issued when something appears on that path;
 *     then it will be classified either as (1) or (2).
 *
 * @throws IllegalArgumentException if the [paths] are empty
 * @throws java.io.IOException when failed to start watching
 */
context(scope: CoroutineScope)
fun watchPaths(
    paths: Set<Path>,
): SharedFlow<PathsChangedEvent> {
    require(paths.isNotEmpty()) { "paths can't be empty" }
    // TODO: Implement an optimized version for macOS
    return WatchServicePathWatchingService.watchPaths(paths)
}
