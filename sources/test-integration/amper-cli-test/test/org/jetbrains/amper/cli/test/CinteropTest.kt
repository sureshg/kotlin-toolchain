/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStderrContains
import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.aomBuilder.readProjectModel
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.problems.reporting.NoopProblemReporter
import org.jetbrains.amper.test.AmperCliResult
import org.jetbrains.amper.test.LinuxOnly
import org.jetbrains.amper.test.MacOnly
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo
import kotlin.test.Test
import kotlin.test.assertEquals

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

        assertCinteropModel(
            result = result,
            expectedRepresentation = """
                module: ios-cinterop
                 fragment: apple
                  - generated/ios-cinterop/common/cinterop/custom: directory
                 fragment: common
                  - generated/ios-cinterop/common/cinterop/custom: directory
                 fragment: ios
                  - generated/ios-cinterop/common/cinterop/custom: directory
                 fragment: iosArm64
                  - generated/ios-cinterop/iosArm64/cinterop/custom@common.klib: regular-file
                 fragment: iosSimulatorArm64
                  - generated/ios-cinterop/iosSimulatorArm64/cinterop/custom@common.klib: regular-file
                 fragment: iosX64
                  - generated/ios-cinterop/iosX64/cinterop/custom@common.klib: regular-file
                 fragment: native
                  - generated/ios-cinterop/common/cinterop/custom: directory
            """.trimIndent(),
        )
    }

    @Test
    @MacOnly
    fun `ide sync - no commonization for a single platform`() = runSlowTest {
        val result = runCli(
            projectDir = testProject("cinterop/single-platform"),
            "ide-integration", "generate-klibs",
        )

        assertCinteropModel(
            result = result,
            expectedRepresentation = """
                module: single-platform
                 fragment: apple
                  - generated/single-platform/macosArm64/cinterop/custom@common.klib: regular-file
                 fragment: common
                  - generated/single-platform/macosArm64/cinterop/custom@common.klib: regular-file
                 fragment: macos
                  - generated/single-platform/macosArm64/cinterop/custom@common.klib: regular-file
                 fragment: macosArm64
                  - generated/single-platform/macosArm64/cinterop/custom@common.klib: regular-file
                 fragment: native
                  - generated/single-platform/macosArm64/cinterop/custom@common.klib: regular-file
            """.trimIndent(),
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
        assertCinteropModel(
            result = result,
            expectedRepresentation = """
                module: mac-and-win
                 fragment: apple
                  - generated/mac-and-win/macosArm64/cinterop/libcurl@common.klib: regular-file
                 fragment: common
                  - generated/mac-and-win/common/cinterop/libcurl: directory
                 fragment: macos
                  - generated/mac-and-win/macosArm64/cinterop/libcurl@common.klib: regular-file
                 fragment: macosArm64
                  - generated/mac-and-win/macosArm64/cinterop/libcurl@common.klib: regular-file
                 fragment: mingw
                  - generated/mac-and-win/mingwX64/cinterop/libcurl@common.klib: missing
                 fragment: mingwX64
                  - generated/mac-and-win/mingwX64/cinterop/libcurl@common.klib: missing
                 fragment: native
                  - generated/mac-and-win/common/cinterop/libcurl: directory
            """.trimIndent(),
        )
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

    private fun assertCinteropModel(
        result: AmperCliResult,
        expectedRepresentation: String,
    ) = assertEquals(
        actual = buildString {
            val model = readProjectModel(result.projectDir)
            for (module in model.modules.sortedBy { it.userReadableName }) {
                appendLine("module: ${module.userReadableName}")
                for (fragment in module.fragments.sortedBy { it.name }) {
                    val paths = fragment.generatedCinteropKlibPaths(result.buildDir)
                    if (paths.isEmpty()) continue
                    appendLine(" fragment: ${fragment.name}")
                    for (path in paths.sorted()) {
                        append("  - ${path.relativeTo(result.buildDir)}: ")
                        appendLine(
                            when {
                                path.isRegularFile() -> "regular-file"
                                path.isDirectory() -> "directory"
                                path.exists() -> "exists"
                                else -> "missing"
                            }
                        )
                    }
                }
            }
        }.trim(),
        expected = expectedRepresentation,
    )

    private fun readProjectModel(root: Path): Model = context(NoopProblemReporter) {
        val projectContext = AmperProjectContext.create(rootDir = root, buildDir = null)
            ?: error("Invalid project root: $root")
        projectContext.readProjectModel(pluginData = emptyList(), mavenPluginXmls = emptyList())
    }
}

private const val EXAMPLE_COM_RESPONSE_TEXT = "<title>Example Domain</title>"
