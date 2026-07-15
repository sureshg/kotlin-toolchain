/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.kotlin.native

import java.security.MessageDigest
import kotlin.io.encoding.Base64

/**
 * Identifies a set of platforms for the commonizer.
 */
data class CommonizerTarget(
    /**
     * The value of `-output-targets` commonizer option that correspond to this set of platforms.
     */
    val targetNameForCompiler: String,
    /**
     * Name of the directory to store the commonized klibs in the Kotlin/Native distribution.
     * It respects a convention with a max length, and a special way to trim the length.
     */
    val dirName: String,
    /**
     * The set of platforms that this target represents.
     */
    val platforms: Set<KonanPlatform>,
)

/**
 * Returns a [CommonizerTarget] that uniquely identifies this set of platforms in a commonizer-specific way.
 */
fun Collection<KonanPlatform>.asCommonizerTarget(): CommonizerTarget {
    val sortedPlatforms = mapTo(sortedSetOf()) { it.nameForCompiler }
    // https://github.com/JetBrains/kotlin/blob/cf4e556a02d9c1cb67d19c2422fdae02c743c499/native/commonizer-api/src/org/jetbrains/kotlin/commonizer/CommonizerTarget.kt#L69-L75
    val fullId = sortedPlatforms.joinToString(
        prefix = "(",
        separator = ", ",
        postfix = ")",
    )
    return CommonizerTarget(targetNameForCompiler = fullId, dirName = fullId.trimToMaxLength(), platforms = toSet())
}

// Respects the convention in
// https://github.com/JetBrains/kotlin/blob/cf4e556a02d9c1cb67d19c2422fdae02c743c499/native/commonizer-api/src/org/jetbrains/kotlin/commonizer/TargetLibrariesLayout.kt#L13
private const val maxFileNameLength = 150

/**
 * Trims this string to the max allowed length for directory names of commonized libraries.
 *
 * See the code [on commonizer side](https://github.com/JetBrains/kotlin/blob/cf4e556a02d9c1cb67d19c2422fdae02c743c499/native/commonizer-api/src/org/jetbrains/kotlin/commonizer/TargetLibrariesLayout.kt#L22)
 */
private fun String.trimToMaxLength(): String {
    if (length > maxFileNameLength) {
        val hashSuffix = "[--${sha1Base64(this)}]"
        return this.take(maxFileNameLength - hashSuffix.length) + hashSuffix
    }
    return this
}

private fun sha1Base64(value: String): String {
    val sha = MessageDigest.getInstance("SHA-1")
    return Base64.UrlSafe.encode(sha.digest(value.encodeToByteArray()))
}
