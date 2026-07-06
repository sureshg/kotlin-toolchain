/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import org.jetbrains.amper.cli.context.CliContext
import org.jetbrains.amper.cli.context.GlobalCliContext
import org.jetbrains.amper.cli.context.ProjectCliContext
import org.jetbrains.amper.cli.options.ProjectLayoutOptions
import org.jetbrains.amper.cli.options.UserJvmArgsOption
import org.jetbrains.amper.cli.options.buildTypeOption
import org.jetbrains.amper.cli.options.leafPlatformOption
import org.jetbrains.amper.cli.options.userJvmArgsOption
import org.jetbrains.amper.cli.project.preparePluginsAndReadModel
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.cli.withBackend
import org.jetbrains.amper.compilation.compiler.provisionKotlinCompilerCli
import org.jetbrains.amper.frontend.schema.DefaultVersions
import org.jetbrains.amper.frontend.schema.DiscouragedDirectDefaultVersionAccess
import org.jetbrains.amper.jvm.getJdkOrUserError
import org.jetbrains.amper.processes.PrintToTerminalProcessOutputListener
import org.jetbrains.amper.tasks.AllRunSettings
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.notExists

internal class RunCommand : AmperSubcommand(name = "run") {

    private val module by option("-m", "--module", help = "Specific module to run (run the `show modules` command to get the modules list)")

    private val platform by leafPlatformOption(
        help = "Run the app on specified platform. This option is only necessary if the module has multiple main " +
                "functions for different platforms",
    )

    private val deviceId by option(
        "-d", "--device-id",
        help = """
            Platform specific device ID of the device to install and run on. 
            Only Android and iOS platforms are currently supported.
            - Android: use `adb devices` command to list connected devices and emulators
            - iOS: use `xcrun devicectl list devices` command to list available devices or `xcrun simctl list devices` to list available simulators.
        """.trimIndent(),
    )

    private val variant by buildTypeOption(
        help = "Run the specified variant of the app. Debug variant is launched by default.",
    )

    private val jvmArgs by userJvmArgsOption(
        help = """
            The JVM arguments to pass to the JVM running the application, separated by spaces.
            These arguments only affect the JVM used to run the application, and don't affect non-JVM applications.
            
            If the `${UserJvmArgsOption}` option is repeated, the arguments contained in all occurrences are passed
            to the JVM in the order they were specified. The JVM decides how it handles duplicate arguments.
        """.trimIndent()
    )

    private val jvmMainClass by option("--main-class", help = "The fully-qualified name of the main class to run. This option is only applicable for JVM applications. By default, the main class is read from the module configuration file, or is determined automatically by convention, searching for a main.kt file.")

    private val workingDir by option("--working-dir", help = "The working directory for the application run. " +
            "By default, the current directory is used. This option is only applicable for JVM and native desktop " +
            "applications, and Kotlin scripts. The working directory is not customizable for web applications or " +
            "mobile emulator runs.")
        .path(mustExist = true, canBeFile = false, canBeDir = true)
        .default(Path("."))

    private val composeHotReloadMode by option("--compose-hot-reload-mode", help = "Enable Compose Hot Reload " +
            "mode for Compose Multiplatform applications (for desktop applications and libraries which have jvm platform). " +
            "This mode makes the application reloadable while running, which significantly reduces the development round-trip" +
            " to see code changes in action. \n\n" +
            "Note: in this mode, the Java runtime is overridden to the JetBrains Runtime, which is required for Compose Hot Reload to work.")
        .flag()

    private val port by option(
        "--port",
        help = """
            Run a server with web application on the specified port. Default port is 8080.
            
            Only Wasm/JS platform is currently supported.
        """.trimIndent(),
    ).int()

    private val layoutOptions by ProjectLayoutOptions()

    private val scriptOptions by ScriptOptions()

    private val programArguments by argument(name = "app_arguments").multiple()

    override fun help(context: Context): String =
        "Run an application module in your project, or a standalone Kotlin script"

    override fun helpEpilog(context: Context): String = """
        _Note: use `--` to separate the application's arguments from the Kotlin CLI options.
        It is required if any application argument looks like a CLI option (starts with '-')._
        
        **Example 1:** Run an application module of your project 
        (when there is only one, or only one that can run on the current host)
        ```
        kotlin run
        kotlin run -- arg1 arg2
        ```
        
        **Example 2:** Run a specific application module of your project (when there are several candidates):
        ```
        # Disambiguate using the module's name
        kotlin run -m my-module
        kotlin run -m my-module -- arg1 arg2
        
        # Disambiguate using the platform to run
        kotlin run -p jvm
        kotlin run -p android 
        ```
        
        **Example 3:** Run a simple standalone `.main.kts` script:
        ```
        kotlin run --script my-script.main.kts
        kotlin run --script my-script.main.kts -- arg1 arg2
        ```
        
        **Example 4:** Simpler form to run scripts outside any project:
        ```
        kotlin run my-script.main.kts
        kotlin run my-script.main.kts -- arg1 arg2
        ```
    """.trimIndent()

    override suspend fun run() {
        val script = scriptOptions.scriptPath
        val cliContext = findCliContext(layoutOptions)
        when {
            script != null -> cliContext.runScript(script, programArguments)
            cliContext is ProjectCliContext -> runProjectApp(cliContext)
            cliContext is GlobalCliContext -> {
                val scriptPath = programArguments.firstOrNull()?.let(::Path)
                    ?: userReadableError(
                        "The 'run' command is executed outside of a project. Expected a --script or a " +
                                "positional argument with the path of a .main.kts script to run."
                    )
                cliContext.runScript(scriptPath = scriptPath, args = programArguments.drop(1))
            }
        }
    }

    private suspend fun runProjectApp(cliContext: ProjectCliContext) {
        setProjectSpecificState(cliContext)
        withBackend(
            cliContext = cliContext,
            model = cliContext.preparePluginsAndReadModel(),
            runSettings = AllRunSettings(
                programArgs = programArguments,
                workingDir = workingDir,
                userJvmArgs = jvmArgs,
                userJvmMainClass = jvmMainClass,
                deviceId = deviceId,
                composeHotReloadMode = composeHotReloadMode,
                port = port,
            ),
        ) { backend ->
            backend.runApplication(moduleName = module, platform = platform, buildType = variant)
        }
    }

    private suspend fun CliContext.runScript(scriptPath: Path, args: List<String>) {
        if (scriptPath.notExists()) {
            userReadableError("'$scriptPath' does not exist, cannot run it as a Kotlin script")
        }
        if (scriptPath.extension != "kts") {
            userReadableError("The given file is not a Kotlin script, please provide a file with .kts extension")
        }
        val kotlinCompiler = context(userCacheRoot, processRunner) {
            provisionKotlinCompilerCli(scriptOptions.kotlinVersion)
        }
        val jdk = context(problemReporter) {
            jdkProvider.getJdkOrUserError(majorVersion = scriptOptions.jdkMajorVersion)
        }
        kotlinCompiler.runKotlinScript(
            scriptPath = scriptPath,
            workingDir = workingDir,
            jdkHome = jdk.homeDir,
            args = args,
            outputListener = PrintToTerminalProcessOutputListener(terminal),
        )
    }
}

@OptIn(DiscouragedDirectDefaultVersionAccess::class)
private class ScriptOptions : OptionGroup(
    name = "Script-specific options",
) {
    val scriptPath by option("--script", help = "Executes the given Kotlin script (.main.kts)")
        .path(mustExist = true, canBeFile = true, canBeDir = false)

    val kotlinVersion by option(
        "--script-kotlin-version",
        help = "The Kotlin compiler version to use to compile and run Kotlin scripts (.main.kts)",
    ).default(DefaultVersions.kotlin)

    val jdkMajorVersion by option(
        "--script-jdk-version",
        help = "The major JDK version to use to run Kotlin scripts (.main.kts)",
    ).int().default(DefaultVersions.jdk)
}
