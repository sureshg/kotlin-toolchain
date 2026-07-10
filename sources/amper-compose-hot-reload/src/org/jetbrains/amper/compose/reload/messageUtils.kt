/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(InternalHotReloadApi::class)

package org.jetbrains.amper.compose.reload

import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.Environment
import org.jetbrains.compose.reload.core.Logger
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.tinylog.Level

internal fun createLogMessage(
    level: Level,
    message: String,
    throwable: Throwable?,
) = OrchestrationMessage.LogMessage(
    environment = Environment.build,
    loggerName = KOTLIN_BUILD_TAG,
    threadName = Thread.currentThread().name,
    timestamp = System.currentTimeMillis(),
    level = when (level) {
        Level.WARN -> Logger.Level.Warn
        Level.ERROR -> Logger.Level.Error
        else -> Logger.Level.Info
    },
    message = message,
    throwableClassName = throwable?.javaClass?.name,
    throwableMessage = throwable?.message,
    throwableStacktrace = throwable?.stackTrace?.toList(),
)

internal fun createBuildTaskResultFailure(
    failureMessagePrefix: String,
    error: Throwable,
) = OrchestrationMessage.BuildTaskResult(
    taskId = KOTLIN_BUILD_TAG,
    isSuccess = false,
    isSkipped = false,
    startTime = null,
    endTime = null,
    failures = [
        OrchestrationMessage.BuildTaskResult.BuildTaskFailure(
            message = error.message?.let { "$failureMessagePrefix: $it" } ?: failureMessagePrefix,
            description = null,
        ),
    ],
)

internal fun createBuildTaskResultSuccess() = OrchestrationMessage.BuildTaskResult(
    taskId = KOTLIN_BUILD_TAG,
    isSuccess = true,
    isSkipped = false,
    startTime = null,
    endTime = null,
    failures = [],
)

private const val KOTLIN_BUILD_TAG = "Kotlin Build"
