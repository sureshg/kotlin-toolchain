/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.fswatching.common

import kotlinx.coroutines.delay
import java.io.IOException
import java.nio.file.Path
import java.nio.file.WatchKey
import java.nio.file.WatchService
import kotlin.io.path.exists
import kotlin.io.path.isHidden
import kotlin.time.Duration.Companion.milliseconds

internal suspend fun WatchService.takeSuspend(): WatchKey {
    while (true) {
        poll()?.let { return it }
        delay(30.milliseconds)
    }
}

internal fun Path.existsAndHiddenSafe(): Boolean = try {
    exists() && isHidden()
} catch (_: IOException) {
    false // File might be deleted just after the `exists()` check
}