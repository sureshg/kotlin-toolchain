/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SdkInstallManagerTest {

    // A realistic slice of the platform packages currently published in the Android SDK repository.
    // Note that "platforms;android-37" is intentionally absent: API 37 is only published as 37.0.
    private val availablePlatforms = listOf(
        "platforms;android-35",
        "platforms;android-35-ext14",
        "platforms;android-36",
        "platforms;android-36-ext18",
        "platforms;android-36-ext19",
        "platforms;android-36.1",
        "platforms;android-37.0",
        "platforms;android-CinnamonBun",
        "platforms;android-CinnamonBun-ext23",
    )

    @Test
    fun exactMatchIsPreferred() {
        // android-36 exists as a plain package, so it must win over android-36.1
        assertEquals(
            "platforms;android-36",
            selectBestMatchingPackagePath("platforms;android-36", availablePlatforms),
        )
    }

    @Test
    fun minorVersionedVariantIsResolvedWhenPlainIsAbsent() {
        // android-37 has no plain package, only android-37.0
        assertEquals(
            "platforms;android-37.0",
            selectBestMatchingPackagePath("platforms;android-37", availablePlatforms),
        )
    }

    @Test
    fun highestMinorVersionIsChosen() {
        val available = listOf(
            "platforms;android-37.0",
            "platforms;android-37.1",
            "platforms;android-37.2",
        )
        assertEquals(
            "platforms;android-37.2",
            selectBestMatchingPackagePath("platforms;android-37", available),
        )
    }

    @Test
    fun extensionLevelsAndCodenamesAreNeverMatched() {
        val available = listOf(
            "platforms;android-37-ext19",
            "platforms;android-CinnamonBun",
            "platforms;android-CinnamonBun-ext23",
        )
        assertNull(selectBestMatchingPackagePath("platforms;android-37", available))
    }

    @Test
    fun prefixOfAnApiLevelIsNotMatched() {
        // "android-3" must not match "android-37"/"android-37.0" (and "android-370" has no minor dot)
        val available = listOf("platforms;android-37", "platforms;android-37.0", "platforms;android-370")
        assertNull(selectBestMatchingPackagePath("platforms;android-3", available))
    }

    @Test
    fun buildToolsExactMatchIsResolved() {
        val available = listOf("build-tools;36.0.0", "build-tools;36.1.0", "build-tools;37.0.0")
        assertEquals(
            "build-tools;37.0.0",
            selectBestMatchingPackagePath("build-tools;37.0.0", available),
        )
    }

    @Test
    fun returnsNullWhenNothingMatches() {
        assertNull(selectBestMatchingPackagePath("platforms;android-99", availablePlatforms))
    }
}
