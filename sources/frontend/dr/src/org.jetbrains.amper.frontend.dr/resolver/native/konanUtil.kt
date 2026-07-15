/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver.native

import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.kotlin.native.CommonizerTarget
import org.jetbrains.amper.kotlin.native.KonanDistribution
import org.jetbrains.amper.kotlin.native.KonanPlatform
import org.jetbrains.amper.kotlin.native.asCommonizerTarget
import java.nio.file.Path

@UsedInIdePlugin
fun KonanDistribution.commonizedKlibs(compilerPlatforms: List<Platform>): List<Path> =
    commonizedKlibs(compilerPlatforms.asCommonizerTarget())

/**
 * Returns a [CommonizerTarget] that uniquely identifies this set of platforms in a commonizer-specific way.
 */
fun Collection<Platform>.asCommonizerTarget(): CommonizerTarget = map { it.toKonanPlatform() }.asCommonizerTarget()

/**
 * Returns the [KonanPlatform] that corresponds to this [Platform], so it can be used in the Konan library.
 */
fun Platform.toKonanPlatform(): KonanPlatform = KonanPlatform(nameForCompiler)
