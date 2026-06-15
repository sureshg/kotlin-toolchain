/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.stdlib.io.path

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CleanDirectoryExceptTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `empty directory is kept when keepPaths is empty`() {
        cleanDirectoryExcept(tempDir, [])

        assertTrue(tempDir.exists(), "The root directory itself must survive")
    }

    @Test
    fun `all files are deleted when keepPaths is empty`() {
        val file1 = tempDir.resolve("a.txt").createFile()
        val file2 = tempDir.resolve("b.txt").createFile()

        cleanDirectoryExcept(tempDir, [])

        assertTrue(tempDir.exists(), "The root directory itself must survive")
        assertFalse(file1.exists(), "a.txt should be deleted")
        assertFalse(file2.exists(), "b.txt should be deleted")
    }

    @Test
    fun `nested directory is deleted when keepPaths is empty`() {
        val subDir = tempDir.resolve("sub").createDirectory()
        subDir.resolve("nested.txt").createFile()

        cleanDirectoryExcept(tempDir, [])

        assertTrue(tempDir.exists(), "The root directory itself must survive")
        assertFalse(subDir.exists(), "sub/ should be deleted")
    }

    @Test
    fun `kept file survives, others are deleted`() {
        val kept = tempDir.resolve("keep.txt").createFile()
        val deleted = tempDir.resolve("delete.txt").createFile()

        cleanDirectoryExcept(tempDir, [kept])

        assertTrue(kept.exists(), "keep.txt should survive")
        assertFalse(deleted.exists(), "delete.txt should be deleted")
    }

    @Test
    fun `multiple kept files all survive`() {
        val kept1 = tempDir.resolve("keep1.txt").createFile()
        val kept2 = tempDir.resolve("keep2.txt").createFile()
        val deleted = tempDir.resolve("delete.txt").createFile()

        cleanDirectoryExcept(tempDir, [kept1, kept2])

        assertTrue(kept1.exists(), "keep1.txt should survive")
        assertTrue(kept2.exists(), "keep2.txt should survive")
        assertFalse(deleted.exists(), "delete.txt should be deleted")
    }

    @Test
    fun `kept directory and its entire subtree survive`() {
        val keptDir = tempDir.resolve("kept").createDirectory()
        val keptChild = keptDir.resolve("child.txt").createFile()
        val deletedDir = tempDir.resolve("deleted").createDirectory()
        deletedDir.resolve("other.txt").createFile()

        cleanDirectoryExcept(tempDir, [keptDir])

        assertTrue(keptDir.exists(), "kept/ should survive")
        assertTrue(keptChild.exists(), "kept/child.txt should survive")
        assertFalse(deletedDir.exists(), "deleted/ should be removed")
    }

    @Test
    fun `deeply nested kept directory survives with its subtree`() {
        val level1 = tempDir.resolve("level1").createDirectory()
        val level2 = level1.resolve("level2").createDirectory()
        val keptDir = level2.resolve("kept").createDirectory()
        val keptFile = keptDir.resolve("data.txt").createFile()

        val siblingOfLevel1 = tempDir.resolve("sibling.txt").createFile()
        val siblingOfLevel2 = level1.resolve("sibling2.txt").createFile()

        cleanDirectoryExcept(tempDir, [keptDir])

        assertTrue(level1.exists(), "level1/ should survive as ancestor of a kept path")
        assertTrue(level2.exists(), "level2/ should survive as ancestor of a kept path")
        assertTrue(keptDir.exists(), "level1/level2/kept/ should survive")
        assertTrue(keptFile.exists(), "level1/level2/kept/data.txt should survive")
        assertFalse(siblingOfLevel1.exists(), "sibling.txt should be deleted")
        assertFalse(siblingOfLevel2.exists(), "sibling2.txt should be deleted")
    }

    @Test
    fun `sibling files inside ancestor directories of a kept path are deleted`() {
        val sub = tempDir.resolve("sub").createDirectory()
        val keptFile = sub.resolve("keep.txt").createFile()
        val deletedFile = sub.resolve("delete.txt").createFile()

        cleanDirectoryExcept(tempDir, [keptFile])

        assertTrue(sub.exists(), "sub/ must survive because it is an ancestor of the kept file")
        assertTrue(keptFile.exists(), "sub/keep.txt should survive")
        assertFalse(deletedFile.exists(), "sub/delete.txt should be deleted")
    }

    @Test
    fun `keepPath outside the directory is ignored`() {
        val file = tempDir.resolve("file.txt").createFile()
        val outsidePath = tempDir.parent.resolve("outside.txt")

        cleanDirectoryExcept(tempDir, [outsidePath])

        assertTrue(tempDir.exists(), "The root directory itself must survive")
        assertFalse(file.exists(), "file.txt should be deleted because the outside path is ignored")
    }

    @Test
    fun `non-existent keepPath is ignored`() {
        val file = tempDir.resolve("file.txt").createFile()
        val nonExistent = tempDir.resolve("file.txt/ghost.txt") // not created on disk

        cleanDirectoryExcept(tempDir, [nonExistent])

        assertFalse(file.exists(), "file.txt should be deleted because the non-existent keepPath is ignored")
    }

    @Test
    fun `un-normalised keepPath with redundant segments is still honoured`() {
        val sub = tempDir.resolve("sub").createDirectory()
        val kept = sub.resolve("keep.txt").createFile()
        val deleted = sub.resolve("delete.txt").createFile()

        // e.g. tempDir/sub/../sub/keep.txt normalises to tempDir/sub/keep.txt
        val unnormalised = tempDir.resolve("sub/../sub/keep.txt")
        cleanDirectoryExcept(tempDir, listOf(unnormalised))

        assertTrue(kept.exists(), "keep.txt should survive via a normalised equivalent path")
        assertFalse(deleted.exists(), "delete.txt should be deleted")
    }
}