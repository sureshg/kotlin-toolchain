/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("SSBasedInspection")

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.amper.fswatching.PathsChangedEvent
import org.jetbrains.amper.fswatching.common.WatchServicePathWatchingService
import org.jetbrains.amper.system.info.OsFamily
import org.jetbrains.amper.system.info.SystemInfo
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.relativeTo
import kotlin.io.path.setAttribute
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.asserter
import kotlin.time.Duration.Companion.seconds

class PathWatchingServiceTest {
    @TempDir
    lateinit var tempDir: Path

    private val events = Channel<PathsChangedEvent>(capacity = 128)

    @Test
    fun `watch service-based implementation skips hidden files correctly`() = runTest {
        val root = tempDir.resolve("root").createDirectory()
        val dir1 = root.resolve("dir1").createDirectory()
        val subDir1 = dir1.resolve("sub").createDirectory()
        val dir2 = root.resolve("dir2").createDirectory()
        val subDir2 = dir2.resolve("sub").createDirectory()

        watchTest(
            eventFlow = {
                WatchServicePathWatchingService.watchPaths([dir1, dir2])
            }
        ) {
            dir1.resolve(".hidden").createHiddenFile().writeText("hidden")
            dir2.resolve("non-hidden").writeText("normal")

            waitForPathsChanged(dir2, caseDescription = "Only non-hidden trigger changes")

            subDir1.resolve(".hidden").createHiddenFile().writeText("hidden")
            subDir2.resolve("non-hidden").writeText("normal")

            waitForPathsChanged(dir2, caseDescription = "Only non-hidden trigger changes")
        }
    }

    @Test
    fun `watch service-based implementation works correctly`() = runTest {
        val root = tempDir.resolve("root").createDirectory()
        val projectFile = root.resolve("project.yaml").createFile()
        val moduleDir = root.resolve("module").createDirectory()
        val sourceDir = moduleDir.resolve("src").createDirectory()
        val testDir = moduleDir.resolve("test").createDirectory()
        val moduleFile = moduleDir.resolve("module.yaml").createFile()

        watchTest(
            eventFlow = {
                WatchServicePathWatchingService.watchPaths([projectFile, sourceDir, moduleFile])
            },
        ) {
            // These are unrelated changes that should not show up in any following events
            val build = root.resolve("build").createDirectory()
            build.resolve("output.txt").createFile()
            testDir.resolve("com/example/test.kt").createParentDirectories().writeText("some kotlin")

            // Related changes
            moduleFile.writeText("foo")
            waitForPathsChanged(moduleFile, caseDescription = "Change in the existing file")

            moduleFile.writeText("bar")
            waitForPathsChanged(moduleFile, caseDescription = "Repeated change in the existing file")

            projectFile.writeText("baz")
            waitForPathsChanged(projectFile, caseDescription = "Change in another existing file")

            val sourceFile1 = sourceDir.resolve("com/example/foo.kt").createParentDirectories()
            sourceFile1.writeText("some kotlin")
            sourceDir.resolve("com/example/core/foo2.kt").createParentDirectories().writeText("some kotlin")
            waitForPathsChanged(sourceDir, caseDescription = "Create deeply nested file in a tracked directory")

            sourceFile1.writeText("foo")
            waitForPathsChanged(sourceDir, caseDescription = "Change and deeply nested file")

            sourceDir.deleteRecursively()
            waitForPathsChanged(sourceDir, caseDescription = "Remove tracked directory with all the content")

            root.deleteRecursively()
            waitForPathsChanged(projectFile, sourceDir, moduleFile, caseDescription = "Remove multiple tracked dirs/files")

            projectFile.createParentDirectories().writeText("project")
            moduleFile.createParentDirectories().writeText("module")

            // This tests that tracked file creation is detected even if their immediate parent was non-existent too.
            waitForPathsChanged(projectFile, moduleFile, caseDescription = "Create tracked files again")
        }
    }

    private suspend fun watchTest(
        eventFlow: context(CoroutineScope) () -> SharedFlow<PathsChangedEvent>,
        testBlock: suspend () -> Unit,
    ) = coroutineScope {
        // We use real execution because we don't need the delay-less test execution.
        // We use a new local executor so that it doesn't interfere with the other tests.
        Executors.newSingleThreadExecutor().use {
            withContext(it.asCoroutineDispatcher()) {
                val started = CompletableDeferred<Unit>()
                val job = launch {
                    val flow = eventFlow()
                    started.complete(Unit)
                    flow.collect { event ->
                        events.send(event)
                    }
                }
                started.await()
                try {
                    testBlock()
                } finally {
                    job.cancelAndJoin()
                }
            }
        }
    }

    private suspend fun waitForPathsChanged(
        vararg paths: Path,
        caseDescription: String,
    ) {
        // Completes ~instantly on Linux/Windows but takes ~2 sec on macOS.
        // See sun.nio.fs.PollingWatchService.POLLING_INTERVAL
        withTimeout(20.seconds) {
            println("Testing for: $caseDescription")
            val expectedPaths = paths.map { it.relativeTo(tempDir) }.toSet()
            val allRecordedChanges = mutableSetOf<Path>()
            while (true) {
                val event = try {
                    events.receive()
                } catch (_: TimeoutCancellationException) {
                    assertEquals(
                        expected = expectedPaths,
                        actual = allRecordedChanges,
                        message = "$caseDescription: Timed out waiting for the expected paths to be reported",
                    )
                    break
                }
                allRecordedChanges.addAll(event.affectedPaths.map { it.relativeTo(tempDir) })
                if (allRecordedChanges == expectedPaths) break // Good
                val unexpectedPaths = allRecordedChanges - expectedPaths
                if (unexpectedPaths.isNotEmpty()) {
                    asserter.fail("$caseDescription: Unexpected changes reported: $unexpectedPaths")
                }
            }
            println("$caseDescription: OK")
        }
    }

    private fun Path.createHiddenFile(): Path = apply {
        check(name.startsWith(".")) {
            "Hidden file name must start with the dot, got $nameWithoutExtension"
        }
        createFile()
        if (SystemInfo.CurrentHost.family == OsFamily.Windows) {
            setAttribute("dos:hidden", true)
        }
    }
}