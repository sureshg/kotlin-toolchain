/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class ScriptRunTest : AmperCliTestBase() {

    /**
     * A temporary directory outside the Kotlin Toolchain's own project, so we can test the global context behavior.
     */
    @TempDir
    private lateinit var tempDirOutOfProject: Path

    //language=kotlin
    private val helloWorldScript = "println(\"Hello, world!\")"

    //language=kotlin
    private val argPrinterScript = """
        println("Args:")
        args.forEach {
            println(it)
        }
    """.trimIndent()

    @Test
    fun `run with --script option`() = runSlowTest {
        val scriptPath = tempRoot.resolve("hello.script.kts")
        scriptPath.writeText(helloWorldScript)

        val result = runCli(
            projectDir = tempRoot,
            args = ["run", "--script=${scriptPath.pathString}"],
            wrapperMode = WrapperMode.GlobalIntrinsicVersion,
        )
        result.assertStdoutContains("Hello, world!")
    }

    @Test
    fun `run with --script option and script args`() = runSlowTest {
        val scriptPath = tempRoot.resolve("arg-printer.script.kts")
        scriptPath.writeText(argPrinterScript)

        val result = runCli(
            projectDir = tempRoot,
            args = ["run", "--script=${scriptPath.pathString}", "foo", "bar", "baz"],
            wrapperMode = WrapperMode.GlobalIntrinsicVersion,
        )
        result.assertStdoutContains("Args:\nfoo\nbar\nbaz")
    }

    @Test
    fun `run with script as positional argument`() = runSlowTest {
        val scriptPath = tempDirOutOfProject.resolve("hello.script.kts")
        scriptPath.writeText(helloWorldScript)

        val result = runCli(
            projectDir = tempDirOutOfProject,
            args = ["run", scriptPath.pathString],
            wrapperMode = WrapperMode.Global,
        )
        result.assertStdoutContains("Hello, world!")
    }

    @Test
    fun `run with script as positional argument and script args`() = runSlowTest {
        val scriptPath = tempDirOutOfProject.resolve("arg-printer.script.kts")
        scriptPath.writeText(argPrinterScript)

        val result = runCli(
            projectDir = tempDirOutOfProject,
            args = ["run", scriptPath.pathString, "foo", "bar", "baz"],
            wrapperMode = WrapperMode.Global,
        )
        result.assertStdoutContains("Args:\nfoo\nbar\nbaz")
    }
}