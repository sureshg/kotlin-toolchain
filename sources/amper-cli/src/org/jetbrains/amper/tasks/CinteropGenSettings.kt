/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

data class CinteropGenSettings(
    /**
     * If `true`, errors during cinterop klib generation for a leaf platform are reported, but the task is not failed.
     * This is used when there is a need to generate all cinterop klibs on the best effort basis for the tooling needs.
     */
    val ignorePlatformFailures: Boolean = false,
)
