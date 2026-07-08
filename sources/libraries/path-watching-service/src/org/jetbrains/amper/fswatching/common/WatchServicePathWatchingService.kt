/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.fswatching.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.amper.fswatching.PathsChangedEvent
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService
import kotlin.io.path.PathWalkOption
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.walk

internal class WatchServicePathWatchingService private constructor(
    private val paths: Set<Path>,
) {
    private val service: WatchService = paths.first().fileSystem.newWatchService()

    private val entriesByTrackedPath = mutableMapOf<TrackedPath, TrackedPathEntry>()
    private val entriesByWatchedDir = mutableMapOf<WatchedDirectory, WatchedDirectoryEntry>()

    private val pathChangedEvents = MutableSharedFlow<PathsChangedEvent>()

    init {
        for (path in paths) {
            updatePathTracking(TrackedPath(path))
        }
        // By the time constructor returns, all the paths are tracked.
    }

    suspend fun watch() {
        service.use {
            while (true) {
                val changedPaths = buildSet {
                    onKeySignaled(
                        changedPaths = this,
                        // Suspend until a relevant change is detected
                        key = service.takeSuspend(),
                    )
                }

                if (changedPaths.isNotEmpty()) {
                    pathChangedEvents.emit(PathsChangedEvent(changedPaths))
                }
            }
        }
    }

    private fun onKeySignaled(
        changedPaths: MutableSet<in Path>,
        key: WatchKey,
    ) {
        val watchedDir = WatchedDirectory(key.watchable() as Path)
        val watchedDirEntry = entriesByWatchedDir[watchedDir] ?: return

        val events = key.pollEvents()
        for (event in events) {
            if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                // Conservatively assume every value under this dir is changed
                watchedDirEntry.trackedViaThis.forEach { changedPaths.add(it.value) }
                continue
            }
            val changedFileName = event.context() as? Path ?: continue
            val changedPath = watchedDir.value.resolve(changedFileName).toAbsolutePath().normalize()
            logger.debug("Detected changes ({}) via '{}' | '{}'", event.kind(), watchedDir.value, changedPath)

            // Check which input paths are affected by this change
            for (trackedPath in watchedDirEntry.trackedViaThis) {
                if (changedPath != trackedPath.value && changedPath.existsAndHiddenSafe()) {
                    // Ignore changes to a hidden path (file or dir) if it's not being directly tracked.
                    logger.debug("Ignoring changes to hidden {}", changedPath)
                    continue
                }

                val isRelevant = when (val entry = entriesByTrackedPath[trackedPath]!!) {
                    is TrackedPathEntry.RecursiveDirectory -> {
                        if (changedPath.isDirectory()) {
                            if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                                // New child directory created - update tracking
                                // TODO: Leverage the `ExtendedWatchEventModifier.FILE_TREE` where it's supported (Windows?)
                                updatePathTracking(trackedPath)
                                true
                            } else if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                                // Ignore MODIFY events for directories themselves.
                                // There is another event specifically about his modification -
                                //  we'll look at it instead (maybe it's a hidden file)
                                false
                            } else {
                                // DELETE assumed.
                                // No need to update tracking explicitly; the key will be reported as invalid below.
                                true
                            }
                        } else {
                            // Any change to a file inside a watched directory is counted as a change
                            true
                        }
                    }
                    is TrackedPathEntry.ViaParent -> {
                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE &&
                            entry.trackedPath.value.startsWith(changedPath)
                        ) {
                            // Closer ancestor was created, update the tracking
                            updatePathTracking(trackedPath)
                            // relevant if the file itself was created as well.
                            // We know it didn't exist yet because its parent path has just been created.
                            trackedPath.value.exists()
                        } else {
                            // `true` if the created value was the trackedPath itself
                            changedPath == trackedPath.value
                        }
                    }
                }

                if (isRelevant) {
                    changedPaths.add(trackedPath.value)
                }
            }
        }

        if (!key.reset() /* ~ is key no longer valid? */) {
            // Maybe the directory got deleted, update watching for all the paths
            logger.debug("Invalid key for {} (had ${events.size} events)", watchedDir.value)
            entriesByWatchedDir[watchedDir]?.let { entry ->
                entry.trackedViaThis.forEach { trackedPath ->
                    // FIXME: What if we are in the `ViaParent` entry and the tracked path didn't exist before at all?
                    //  then we are going to report a false-positive change
                    changedPaths.add(trackedPath.value)
                    updatePathTracking(trackedPath)
                }
            }
        }
    }

    private fun updatePathTrackingImpl(
        newEntry: TrackedPathEntry,
    ) {
        val existingEntry = entriesByTrackedPath[newEntry.trackedPath]
        check(existingEntry == null || existingEntry.trackedPath == newEntry.trackedPath)

        if (existingEntry == newEntry) {
            // Already watched in the way necessary, nothing to change
            return
        }

        val toUntrack = existingEntry?.let { it.trackedVia - newEntry.trackedVia } ?: []
        toUntrack.forEach { watchedVia ->
            val watchedDirEntry = entriesByWatchedDir[watchedVia]!!
            if (watchedDirEntry.trackedViaThis.singleOrNull() == newEntry.trackedPath) {
                // If it was just this value left, need to stop watching the old dir altogether
                entriesByWatchedDir.remove(watchedVia)
                watchedDirEntry.key.cancel()
            } else {
                entriesByWatchedDir[watchedVia] = watchedDirEntry.copy(
                    // Just remove the trackedPath from the records of that entry so it no longer influences it
                    trackedViaThis = watchedDirEntry.trackedViaThis - newEntry.trackedPath,
                )
            }
        }

        // Track new directories
        val toTrack = existingEntry?.let { newEntry.trackedVia - it.trackedVia } ?: newEntry.trackedVia
        toTrack.forEach { trackedVia ->
            entriesByWatchedDir[trackedVia] = entriesByWatchedDir[trackedVia]?.let {
                it.copy(trackedViaThis = it.trackedViaThis + newEntry.trackedPath)
            } ?: WatchedDirectoryEntry(
                key = trackedVia.value.register(
                    service,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                ),
                trackedViaThis = [newEntry.trackedPath],
            )
        }

        // Add/replace the entry
        entriesByTrackedPath[newEntry.trackedPath] = newEntry
    }

    private fun updatePathTracking(
        path: TrackedPath,
    ) {
        val entry = when {
            // Watch the directory itself for any changes in its subtree
            path.value.isDirectory() -> {
                TrackedPathEntry.RecursiveDirectory(
                    trackedPath = path,
                    trackedVia = path.value.walk(PathWalkOption.INCLUDE_DIRECTORIES)
                        .filter(Path::isDirectory)
                        .mapTo(mutableSetOf(), ::WatchedDirectory),
                )
            }

            // For existing files watch the parent directory (obv. guaranteed to exist)
            path.value.exists() -> TrackedPathEntry.ViaParent(
                trackedPath = path,
                trackedParent = WatchedDirectory(checkNotNull(path.value.parent) { "Can't watch FS roots" }),
            )

            // Non-existent value, track the first existing parent directory
            else -> TrackedPathEntry.ViaParent(
                trackedPath = path,
                trackedParent =
                    WatchedDirectory(generateSequence(path.value, Path::getParent).drop(1).first(Path::isDirectory))
            )
        }

        updatePathTrackingImpl(entry)
    }

    /** a directory that is watched (has its [WatchKey] associated) */
    @JvmInline
    private value class WatchedDirectory(val value: Path)

    /** a path from [paths] that is tracked */
    @JvmInline
    private value class TrackedPath(val value: Path)

    private data class WatchedDirectoryEntry(
        val key: WatchKey,
        val trackedViaThis: Set<TrackedPath>,
    )

    private sealed interface TrackedPathEntry {
        val trackedPath: TrackedPath
        val trackedVia: Set<WatchedDirectory>

        /** an existing file or non-existent path are tracked via their first existing parent directory */
        data class ViaParent(
            override val trackedPath: TrackedPath,
            val trackedParent: WatchedDirectory,
        ) : TrackedPathEntry {
            override val trackedVia: Set<WatchedDirectory> = [trackedParent]
        }

        /** an existing directory is tracked via its and its child directories (recursively) */
        data class RecursiveDirectory(
            override val trackedPath: TrackedPath,
            override val trackedVia: Set<WatchedDirectory>,
        ) : TrackedPathEntry
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        context(scope: CoroutineScope)
        fun watchPaths(
            paths: Set<Path>,
        ): SharedFlow<PathsChangedEvent> {
            val service = WatchServicePathWatchingService(paths)
            scope.launch {
                service.watch()
            }
            return service.pathChangedEvents
        }
    }
}