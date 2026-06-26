/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.context

import io.opentelemetry.api.GlobalOpenTelemetry
import org.jetbrains.amper.cli.AmperVersion
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.util.DelicateAmperApi
import kotlin.io.path.div

/**
 * An incremental cache shared between projects using the same Amper version.
 *
 * **Important:** using this incremental cache introduces a lot of directories in the Amper cache, especially when
 * developing Amper locally. Make sure you only use it when it is impossible to use the project-specific incremental
 * cache.
 */
@DelicateAmperApi
internal fun AmperUserCacheRoot.sharedIncrementalCache(): IncrementalCache = IncrementalCache(
    stateRoot = path / "incremental.state" / AmperVersion.codeIdentifier,
    codeVersion = AmperVersion.codeIdentifier,
    openTelemetry = GlobalOpenTelemetry.get(),
)
