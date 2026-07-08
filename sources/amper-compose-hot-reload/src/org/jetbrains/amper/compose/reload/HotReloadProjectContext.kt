/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compose.reload

import io.opentelemetry.api.OpenTelemetry
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.incrementalcache.IncrementalCache

/**
 * Project context for [HotReloadLoop] needs.
 */
interface HotReloadProjectContext {
    val incrementalCache: IncrementalCache
    val userCacheRoot: AmperUserCacheRoot
    val openTelemetry: OpenTelemetry
}
