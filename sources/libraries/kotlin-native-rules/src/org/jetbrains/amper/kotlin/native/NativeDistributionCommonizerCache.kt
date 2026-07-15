/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("SameParameterValue")

package org.jetbrains.amper.kotlin.native

import org.jetbrains.amper.concurrency.FileMutexGroup
import org.jetbrains.amper.concurrency.withDoubleLock
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

/**
 * The logic inside this cache is copied from
 * [the KGP](https://github.com/JetBrains/kotlin/blob/master/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/targets/native/internal/NativeDistributionCommonizerCache.kt)
 * to align our commonizer caching logic (with some adjustments).
 *
 * Ideally, this library should be shared so the code is maintained in a single place to ensure the logic stays in sync.
 */
class NativeDistributionCommonizerCache(private val commonizedPath: Path) {

    /**
     * Calls [writeCacheAction] for uncached targets and marks them as cached if it succeeds
     */
    suspend fun writeCacheForUncachedTargets(
        outputTargets: Set<CommonizerTarget>,
        writeCacheAction: suspend (todoTargets: Set<CommonizerTarget>) -> Unit
    ) {
        // IMPORTANT: Do not use another file for locking without a reason.
        // This locking behavior is aligned with the Kotlin Gradle Plugin, which ideally will re-use this library at
        // some point. For now, it must stay aligned with their logic. See:
        // https://github.com/JetBrains/kotlin/blob/cf4e556a02d9c1cb67d19c2422fdae02c743c499/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/targets/native/internal/NativeDistributionCommonizerCache.kt#L99
        // https://github.com/JetBrains/kotlin/blob/cf4e556a02d9c1cb67d19c2422fdae02c743c499/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/targets/native/internal/KotlinInterprocessDirectoryLock.kt#L28
        val lockFile = commonizedPath / ".lock"
        lockFile.createParentDirectories()
        FileMutexGroup.Default.withDoubleLock(lockFile) {
            val todoOutputTargets = todoTargets(outputTargets)
            if (todoOutputTargets.isEmpty()) return@withDoubleLock

            writeCacheAction(todoOutputTargets)

            todoOutputTargets
                .map { outputTarget -> commonizedPath.resolve(outputTarget.dirName) }
                .filter { commonizedDirectory -> commonizedDirectory.isDirectory() }
                .forEach { commonizedDirectory -> commonizedDirectory.resolve(".success").createFile() }
        }
    }

    private fun todoTargets(
        outputTargets: Set<CommonizerTarget>
    ): Set<CommonizerTarget> {
        logInfo("Calculating cache state for $outputTargets")

        val cachedOutputTargets = outputTargets
            .filter { outputTarget -> isCached(commonizedPath.resolve(outputTarget.dirName)) }
            .onEach { outputTarget -> logInfo("Cache hit: $outputTarget already commonized") }
            .toSet()

        val todoOutputTargets = outputTargets - cachedOutputTargets

        if (todoOutputTargets.isEmpty()) {
            logInfo("All available targets are commonized already – nothing to do")
            if (todoOutputTargets.isNotEmpty()) {
                logInfo("Platforms cannot be commonized, because of missing platform libraries: $todoOutputTargets")
            }
            return []
        }

        return todoOutputTargets
    }

    private fun isCached(directory: Path): Boolean {
        val successMarkerFile = directory.resolve(".success")
        return successMarkerFile.isRegularFile()
    }

    private fun logInfo(message: String) = logger.info(message)

    private val logger = LoggerFactory.getLogger(javaClass)
}
