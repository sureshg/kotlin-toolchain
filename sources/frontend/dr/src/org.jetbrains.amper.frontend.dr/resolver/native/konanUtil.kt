/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver.native

import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.kotlin.native.KonanDistribution
import java.nio.file.Path
import org.jetbrains.amper.kotlin.native.commonizedKlibs as newCommonizedKlibs

@Deprecated(
    message = "Moved to 'amper-kotlin-native' module",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith(
        "this.commonizedKlibs(compilerPlatforms)",
        "org.jetbrains.amper.kotlin.native.commonizedKlibs",
    ),
)
@UsedInIdePlugin
fun KonanDistribution.commonizedKlibs(compilerPlatforms: List<Platform>): List<Path> =
    newCommonizedKlibs(compilerPlatforms)
