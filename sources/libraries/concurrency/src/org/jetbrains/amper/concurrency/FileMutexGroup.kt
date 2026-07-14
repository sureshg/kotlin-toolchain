/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.concurrency

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * A group of [Mutex]es associated with files.
 *
 * This is useful in the context of double-locking, to acquire a JVM level mutex associated to a lock file before
 * acquiring a process level OS lock on the file.
 */
interface FileMutexGroup {

    /**
     * Gets the [Mutex] corresponding to the given [path].
     *
     * * Invocations with the same [path] must return the same [Mutex].
     * * Invocations with different paths that point to the same file must return the same [Mutex].
     * * Invocations with paths pointing to different files generally shouldn't return the same [Mutex], but they may.
     *   In short, some mutexes can be shared between multiple files.
     */
    fun getMutex(path: Path): Mutex

    companion object {

        /**
         * A default [FileMutexGroup], backed by a [StripedMutex]. See [StripedFileMutexGroup] for details.
         */
        val Default: FileMutexGroup = StripedFileMutexGroup(stripeCount = 512)
    }
}

/**
 * A fine-grained [FileMutexGroup] that provides one [Mutex] per file.
 *
 * This type of [FileMutexGroup] is maximally granular, but also takes up unbounded memory.
 * If such a [FileMutexGroup] is used to lock on more and more files, it will grow indefinitely.
 *
 * It is also impossible to pre-allocate [Mutex]es here, because we can't know in advance how many files will
 * be locked on. So the [Mutex]es are created on-the-fly in a concurrent map, which means that there may be
 * some synchronization overhead.
 */
// TODO maybe we should provide some eviction mechanism? We would need to be very careful about doing this safely. It is
//   not trivial (or maybe impossible?) without reference counting. Removing the lock from the map while under the lock
//   still leaves the possibility for a concurrent thread to get a reference to the lock without acquiring it, and
//   acquire the lock when it's no longer in the map. That means any other concurrent thread can create a new lock and
//   now we have 2 threads executing our protected code in parallel - BOOM.
class FineGrainedFileMutexGroup : FileMutexGroup {
    private val locks = ConcurrentHashMap<Path, Mutex>()

    override fun getMutex(path: Path): Mutex {
        return locks.computeIfAbsent(path) { Mutex() }
    }
}

/**
 * A [FileMutexGroup] backed by a [StripedMutex] with the given [stripeCount].
 *
 * This type of [FileMutexGroup] pre-allocates all [Mutex]es, and thus takes up a fixed amount of memory (directly
 * proportional to [stripeCount]), and doesn't need any synchronization overhead to access the [Mutex]es.
 *
 * The trade-off is that it reuses the same mutex for multiple files, which means that it protects "too much":
 * two unrelated files can end up in the same stripe and use the same mutex, so locking on one of these files
 * prevents access to the other. This could potentially lead to deadlocks if these locking attempts are nested.
 *
 * **IMPORTANT:** do not use this type of [FileMutexGroup] if you have a lot of nesting between the locked areas, and a
 * good potential for collisions, otherwise you might end up with deadlocks for unrelated keys.
 */
class StripedFileMutexGroup(stripeCount: Int) : FileMutexGroup {
    private val stripedMutex = StripedMutex(stripeCount = stripeCount)

    override fun getMutex(path: Path): Mutex {
        // we use the absolute path to make sure we consider all "equivalent" paths the same
        val hash = path.toAbsolutePath().normalize().hashCode()
        return stripedMutex.getMutex(hash)
    }
}

/**
 * Runs the given [block] under the lock identified by the given [path].
 *
 * * Invocations with the same [path] must use the same [Mutex].
 * * Invocations with different paths that point to the same file must use the same [Mutex].
 * * Invocations with paths pointing to different files generally shouldn't use the same [Mutex], but they may.
 *   In short, some mutexes can be shared between multiple files.
 *
 * When the given [owner] is non-null and the mutex is already locked with the same owner (same identity),
 * this function throws [IllegalStateException] (useful for debugging unexpected re-entries).
 */
suspend inline fun <T> FileMutexGroup.withLock(path: Path, owner: Any? = null, block: () -> T): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
//         returnsResultOf(block)
    }
    return getMutex(path).withLock(owner, block)
}
