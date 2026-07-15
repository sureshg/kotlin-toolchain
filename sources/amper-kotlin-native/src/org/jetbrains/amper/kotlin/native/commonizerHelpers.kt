/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.kotlin.native

import org.jetbrains.amper.frontend.Platform
import java.nio.file.Path

/**
 * The commonized klibs of the Kotlin/Native distribution (stdlib and platform libraries) for the given set of
 * [platforms].
 */
fun KonanDistribution.commonizedKlibs(platforms: List<Platform>): List<Path> =
    commonizedKlibs(platforms.asCommonizerTarget())

/**
 * Returns a [CommonizerTarget] that uniquely identifies this set of platforms in a commonizer-specific way.
 */
fun Collection<Platform>.asCommonizerTarget(): CommonizerTarget = map { it.toKonanPlatform() }.asCommonizerTarget()

/**
 * Returns the [KonanPlatform] that corresponds to this [Platform], so it can be used in the Konan library.
 */
fun Platform.toKonanPlatform(): KonanPlatform = KonanPlatform(nameForCompiler)
