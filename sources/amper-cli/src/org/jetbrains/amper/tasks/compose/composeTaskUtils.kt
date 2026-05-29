/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.compose

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.tasks.rootFragment

private val AmperModule.commonSettings get() = rootFragment.settings

// TODO Fix that with new frontend!
internal fun isComposeEnabledFor(module: AmperModule) =
    module.commonSettings.compose.enabled

/**
 * Shared user-visible task moniker for Compose Resources-related tasks
 * It's actually irrelevant to the user what exactly happens under the hood.
 * We present all the operations under the single moniker.
 */
internal const val COMPOSE_RESOURCES_TASKS_MONIKER = "processing compose resources"
