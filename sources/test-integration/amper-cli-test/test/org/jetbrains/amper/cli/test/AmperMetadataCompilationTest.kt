/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.runSlowTest
import kotlin.test.Test

class AmperMetadataCompilationTest : AmperCliTestBase() {

    @Test
    fun `run metadata compilation`() = runSlowTest {
        val r = runCli(projectDir = testProject("multiplatform-library-template-main"),
            "task",
            ":library:compileMetadataCommon",
            assertEmptyStdErr = true)
    }

    // todo (AB) :
    //  - add native platform dependencies from commonized platform API
    //    (to check that dependency on commonized platform is specified as an input to the native metadata compilation)
    //  - add cinterop (direct and in refined fragment) to check that
    //    commonized cinterop klibs are added as an input to the native metadata compilation
    @Test
    fun `run native metadata compilation`() = runSlowTest {
        val r = runCli(projectDir = testProject("multiplatform-library-template-main"),
            "task",
            ":nativeShared:compileMetadataCommon",
            assertEmptyStdErr = true)
    }
}
