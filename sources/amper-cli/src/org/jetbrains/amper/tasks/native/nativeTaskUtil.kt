/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.native

import org.jetbrains.amper.frontend.Platform
import java.nio.file.Path
import kotlin.io.path.extension

internal fun Iterable<Path>.filterKLibs() = filter {
    when(it.extension) {
        "klib" -> true
        "jar", "aar" -> false  // FIXME: AMPER-3862, should also be "not reached".
        else -> error("Unexpected file '${it.fileName}' in the native classpath. A bug in the DR?")
    }
}

/**
 * Special format to uniquely identify a set of platforms in a compiler-specific way.
 * Required for interoperability with the commonization mechanisms.
 */
internal fun Collection<Platform>.formatCompilerPlatformSetId(): String =
    // native/commonizer-api/src/org/jetbrains/kotlin/commonizer/CommonizerTarget.kt
    mapTo(sortedSetOf()) { it.nameForCompiler }
        .joinToString(prefix = "(", separator = ", ", postfix = ")")
