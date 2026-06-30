/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.web

import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.outputStream
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExtractAllNpmDependenciesTest {

    @Test
    fun singleNpmDependency() {
        val klib = createKlibWithDependencies(
            "single.klib",
            mapOf("lodash" to "4.17.21"),
        )

        val warnings = mutableListOf<String>()

        val result = extractAllNpmDependencies(listOf(klib)) { msg ->
            warnings.add(msg)
        }

        assertEquals(mapOf("lodash" to "4.17.21"), result)
        assertTrue(warnings.isEmpty(), "No warnings expected")
    }

    @Test
    fun multipleNpmDependencies() {
        val klib1 = createKlibWithDependencies(
            "first.klib",
            mapOf("lodash" to "4.17.21"),
        )
        val klib2 = createKlibWithDependencies(
            "second.klib",
            mapOf("react" to "18.2.0", "react-dom" to "18.2.0"),
        )

        val warnings = mutableListOf<String>()

        val result = extractAllNpmDependencies(listOf(klib1, klib2)) { msg ->
            warnings.add(msg)
        }

        assertEquals(
            mapOf(
                "lodash" to "4.17.21",
                "react" to "18.2.0",
                "react-dom" to "18.2.0",
            ),
            result,
        )

        assertTrue(warnings.isEmpty(), "No warnings expected")
    }

    @Test
    fun conflictingVersionsLogsWarning() {
        val firstVersion = "4.17.20"
        val klib1 = createKlibWithDependencies(
            "old.klib",
            mapOf("lodash" to firstVersion),
        )
        val secondVersion = "4.17.21"
        val klib2 = createKlibWithDependencies(
            "new.klib",
            mapOf("lodash" to secondVersion),
        )

        val warnings = mutableListOf<String>()

        val result = extractAllNpmDependencies(listOf(klib1, klib2)) { msg ->
            warnings.add(msg)
        }

        assertEquals(mapOf("lodash" to secondVersion), result)
        assertEquals(1, warnings.size)
        assertContains(
            warnings.single(),
            "Conflicting npm dependency versions for 'lodash': " +
                    "'$firstVersion' (from ${klib1.pathString} vs " +
                    "'$secondVersion' (from ${klib2.pathString}). " +
                    "Using '$secondVersion'."
        )
    }

    private fun createKlibWithDependencies(name: String, dependencies: Map<String, String>): Path {
        val dir = createTempDirectory("npm-test").also { it.createDirectories() }
        val klibPath = dir.resolve(name)

        val packageJsonString = buildPackageJson(name, dependencies)

        ZipOutputStream(klibPath.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("package.json"))
            zos.write(packageJsonString.toByteArray())
            zos.closeEntry()
        }

        return klibPath
    }
}