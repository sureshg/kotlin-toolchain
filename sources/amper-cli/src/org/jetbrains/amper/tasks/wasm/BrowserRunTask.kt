/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.wasm

import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import org.jetbrains.amper.engine.RunTask
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.engine.requireSingleDependency
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.tasks.EmptyTaskResult
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.WebRunSettings
import org.jetbrains.amper.util.BuildType

private const val defaultWebBrowserRunPort = 8080

class BrowserRunTask(
    override val taskName: TaskName,
    override val platform: Platform,
    override val buildType: BuildType,
    override val module: AmperModule,
    private val runSettings: WebRunSettings,
) : RunTask {
    override suspend fun run(
        dependenciesResult: List<TaskResult>,
        executionContext: TaskGraphExecutionContext,
    ): TaskResult {
        val port = runSettings.port ?: defaultWebBrowserRunPort
        val builtApp = dependenciesResult.requireSingleDependency<WasmJsBuildTask.Result>().appPath

        embeddedServer(Netty, port = port) {
            routing {
                staticFiles("/", builtApp.toFile())
            }
        }.start(wait = true)

        return EmptyTaskResult
    }
}
