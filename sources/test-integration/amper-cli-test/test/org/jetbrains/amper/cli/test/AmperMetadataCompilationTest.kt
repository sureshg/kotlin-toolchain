/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.runSlowTest
import kotlin.test.Test
import kotlin.test.assertTrue

// todo (AB) : [AMPER-721]
//  - add cinterop (direct and in refined fragment) to check that
//    commonized cinterop klibs are added as an input to the native metadata compilation
class AmperMetadataCompilationTest : AmperCliTestBase() {

    /**
     * This test checks that metadata compilation of common fragment of multiplatform module,
     * that targets mixed native and non-native platforms,
     * is successful.
     */
    @Test
    fun `run metadata compilation of mixed native and non-native common fragment`() = runSlowTest {
        val r = runCli(projectDir = testProject("multiplatform-library-template-main"),
            "task",
            ":library:compileMetadataCommon",
            assertEmptyStdErr = true)
    }

    /**
     * This test checks that metadata compilation correctly process expected/actual fragment refinement.
     *
     * Test performs metadata compilation for the 'linux' fragment of the multiplatform native-only module.
     * It differs from the previous test that checks common compilation, because
     * 'linux' fragment refines other fragments within the same module ('common', 'native')
     * and provides actuals for expected declaration there.
     * So metadata-compilation should be called with parameter `-Xrefines-paths` initialized properly.
     */
    @Test
    fun `run metadata compilation of mixed native and non-native intermediate linux fragment`() = runSlowTest {
        val r = runCli(projectDir = testProject("multiplatform-library-template-main"),
            "task",
            ":library:compileMetadataLinux",
            assertEmptyStdErr = true)
    }

    /**
     * This test checks that metadata compilation of a multiplatform native-only module is successful.
     * The module depends on:
     *  - Another local shared module ('library'),
     *  - KMP library ('kotlinx-coroutines-core')
     */
    @Test
    fun `run metadata compilation of native only common fragment with dependencies`() = runSlowTest {
        val r = runCli(projectDir = testProject("multiplatform-library-template-main"),
            "task",
            ":nativeShared:compileMetadataCommon",
            assertEmptyStdErr = true)
    }

    /**
     * This test checks that metadata compilation of a multiplatform native-only module is successful.
     *
     * Test performs metadata compilation for the 'linux' fragment.
     *
     * 'linux' fragment depends on:
     *  - KMP library with cinterop source sets applicable to the fragment ('crypto-rand')
     *  - Another local shared module ('library'),
     *  - Another fragments within the same module ('common', 'native')
     *  - KMP library ('kotlinx-coroutines-core')
     */
    @Test
    fun `run metadata compilation of native only intermediate fragment with cinterop dependencies`() = runSlowTest {
        val r = runCli(projectDir = testProject("multiplatform-library-template-main"),
            "task",
            ":nativeShared:compileMetadataLinux",
            assertEmptyStdErr = true)
    }

    /**
     * This test checks that metadata compilation of a multiplatform native-only shared fragment
     * that uses commonized platform API is successful.
     */
    @Test
    fun `run native metadata compilation platform API`() = runSlowTest {
        val r = runCli(projectDir = testProject("multiplatform-library-template-main"),
            "task",
            ":nativePlatform:compileMetadataCommon",
            assertEmptyStdErr = false
        )

        assertTrue(
            actual = r.stderr.replace("ERROR logging: using Kotlin home directory dist\\kotlinc", "").isBlank(),
            message = """
                    Process stderr must be empty for the Kotlin CLI call (PID ${r.pid}):
                    "kotlin task :nativePlatform:compileMetadataCommon",
                    Kotlin Toolchain STDERR:
                    ${r.stderr.prependIndent("                    ")}
                """.trimMargin(),
        )
    }

    /**
     * This test checks that metadata compilation of a multiplatform native-only module
     * depending on another exported module transitively is successful.
     *
     * Test performs metadata compilation for the 'linux' fragment of module 'linuxWindowsShared'.
     * That fragment uses symbols defined in the common fragment of 'library' module.
     * Module 'linuxWindowsShared' depends on 'library' transitively via exported dependency declared in the module 'nativeShared'
     */
    @Test
    fun `run metadata compilation of linux intermediate fragment depending on another exported local module`() = runSlowTest {
        val r = runCli(projectDir = testProject("multiplatform-library-template-main"),
            "task",
            ":linuxWindowsShared:compileMetadataLinux",
            assertEmptyStdErr = true)
    }
}
