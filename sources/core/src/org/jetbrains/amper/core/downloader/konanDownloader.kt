/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core.downloader

import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.UsedInIdePlugin
import java.nio.file.Path

/**
 * Downloads and extracts current system specific kotlin native.
 * Returns null if kotlin native is not supported on current system/arch.
 */
@Deprecated(
    message = "Moved to the amper-kotlin-native module",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith(
        expression = "Downloader.downloadAndExtractKotlinNative(version, userCacheRoot)",
        imports = [
            "org.jetbrains.amper.core.downloader.Downloader",
            "org.jetbrains.amper.kotlin.native.downloadAndExtractKotlinNative",
        ]
    )
)
@UsedInIdePlugin
suspend fun downloadAndExtractKotlinNative(
    version: String,
    userCacheRoot: AmperUserCacheRoot,
): Path? {
    TODO("Deprecated at error level, this shouldn't be reachable (we don't have binary compatibility concerns here, " +
            "this function is just here to help with the transition in the IDE libraries")
}
