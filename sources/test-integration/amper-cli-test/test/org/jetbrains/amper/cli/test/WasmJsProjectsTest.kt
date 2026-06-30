/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertFileExists
import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.getTaskOutputPath
import org.jetbrains.amper.cli.test.utils.runSlowTest
import kotlin.io.path.div
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals

class WasmJsProjectsTest : AmperCliTestBase() {

    @Test
    fun `wasm js app compose should build`() = runSlowTest {
        val result = runCli(
            projectDir = testProject("wasm-js-app-with-compose"),
            "build",
        )

        val buildWasmJs = result.getTaskOutputPath(":wasm-js-app-with-compose:buildWasmJsAppWasmJsDebug")

        val baseName = "wasm-js-app-with-compose"

        val wasmFile = buildWasmJs / "$baseName.wasm"
        val mainMjsFile = buildWasmJs / "$baseName.mjs"
        val importObjectMjsFile = buildWasmJs / "$baseName.import-object.mjs"
        val jsBuiltinsMjs = buildWasmJs / "$baseName.js-builtins.mjs"
        val indexHtml = buildWasmJs / "index.html"
        val skikoMjs = buildWasmJs / "skiko.mjs"
        val skikoWasm = buildWasmJs / "skiko.wasm"

        assertFileExists(wasmFile)
        assertFileExists(mainMjsFile)
        assertFileExists(importObjectMjsFile)
        assertFileExists(jsBuiltinsMjs)
        assertFileExists(indexHtml)
        assertFileExists(skikoMjs)
        assertFileExists(skikoWasm)

        val vendors = buildWasmJs / "vendors"

        assertFileExists(vendors)
        assertFileExists(vendors / "@js-joda/core")

        result.assertStdoutContains(
            "pnpm install completed successfully"
        )

        assertFileExists(buildWasmJs / "vendors" / "@js-joda" / "core")
        assertEquals(
            (buildWasmJs / "import-map-loader.js").readText(),
            """
                const script = document.createElement('script');
                script.type = 'importmap';
                script.textContent = JSON.stringify({
                    "imports": {
                        "@js-joda/core": "./vendors/@js-joda/core/dist/js-joda.esm.js"
                    }
                });
                document.currentScript.after(script);
            """.trimIndent()
        )
    }
}