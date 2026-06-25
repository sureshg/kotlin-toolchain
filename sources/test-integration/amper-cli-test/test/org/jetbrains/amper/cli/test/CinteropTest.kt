/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStderrContains
import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.jetbrains.amper.test.LinuxOnly
import org.jetbrains.amper.test.MacOnly
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CinteropTest : AmperCliTestBase() {
    @Test
    @MacOnly
    fun `single app - run macosArm64`() = runSlowTest {
        runCli(
            projectDir = testProject("cinterop/single-app-curl"),
            "run", "--platform=macosArm64",
        ).assertStdoutContains(EXAMPLE_COM_RESPONSE_TEXT)
    }

    @Test
    @MacOnly
    fun `lib + app - run macosArm64`() = runSlowTest {
        runCli(
            projectDir = testProject("cinterop/lib-and-app-curl"),
            "run", "--module=app-mac", "--platform=macosArm64",
        ).assertStdoutContains(EXAMPLE_COM_RESPONSE_TEXT)
    }

    @Test
    @MacOnly
    fun `ide sync - commonize common cinterop for ios platforms`() = runSlowTest {
        val result = runCli(
            projectDir = testProject("cinterop/ios-cinterop"),
            "ide-integration", "generate-klibs",
        )

        val custom = result.buildDir / "generated/ios-cinterop/common/cinterop/custom"
        assertTrue(custom.isDirectory())
        assertTrue(custom.resolve("default/manifest").isRegularFile())
        assertTrue(custom.resolve("default/linkdata").isDirectory())
    }

    @Test
    @MacOnly
    fun `ide sync - no commonization for a single platform`() = runSlowTest {
        val result = runCli(
            projectDir = testProject("cinterop/single-platform"),
            "ide-integration", "generate-klibs",
        )

        val singleKlib = result.buildDir.resolve("generated").walk().joinToString("\n") {
            it.absolutePathString()
        }
        assertEquals(
            actual = singleKlib,
            expected = (result.buildDir / "generated/single-platform/macosArm64/cinterop/custom@common.klib")
                .absolutePathString(),
        )
    }

    @Test
    @MacOnly
    fun `ide sync - errors are ignored during cinterop klib gen`() = runSlowTest {
        val result = runCli(
            projectDir = testProject("cinterop/mac-and-win"),
            "ide-integration", "generate-klibs",
            assertEmptyStdErr = false,
        )
        result.assertStdoutContains(
            "Warning: No libraries found for target mingw_x64. This target will be excluded from commonization.",
        )
        val generated  = result.buildDir / "generated"
        val klib = generated / "mac-and-win/macosArm64/cinterop/libcurl@common.klib"
        val commonized = generated / "mac-and-win/common/cinterop/libcurl"
        assertTrue { klib.isRegularFile() }
        assertTrue { commonized.isDirectory() }
        klib.deleteExisting()
        commonized.deleteRecursively()
        assertEquals(expected = [], actual = generated.walk().toList(), "No more files are expected")
    }

    @Test
    @MacOnly
    fun `build - errors are honored during cinterop klib gen`() = runSlowTest {
        val result = runCli(
            projectDir = testProject("cinterop/mac-and-win"),
            "build",
            assertEmptyStdErr = false,
            expectedExitCode = 1,
        )
        result.assertStderrContains("cinterop processing failed for MINGW_X64, see the errors above")
    }

    @Test
    @LinuxOnly
    fun `lib + app - run linuxX64`() = runSlowTest {
        runCli(
            projectDir = testProject("cinterop/lib-and-app-curl"),
            "run", "--module=app-linux",
        ).assertStdoutContains(EXAMPLE_COM_RESPONSE_TEXT)
    }

    @Test
    @MacOnly
    fun `via plugin`() = runSlowTest {
        runCli(
            projectDir = testProject("cinterop/cinterop-plugin"),
            "run", "--platform=macosArm64",
        )
    }
}

private const val EXAMPLE_COM_RESPONSE_TEXT = "<title>Example Domain</title>"
