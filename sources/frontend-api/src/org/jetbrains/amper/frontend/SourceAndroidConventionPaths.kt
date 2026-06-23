/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import java.nio.file.Path

/**
 * Represents paths to source files specific to Android.
 *
 * @see Fragment.sourceAndroidConventionPaths
 */
data class SourceAndroidConventionPaths(
    /**
     * Path to the Android manifest file.
     */
    val manifestPath: Path,
    /**
     * Path to the Android resources directory.
     *
     * Differs from regular JVM resources as it's accessible via `R` class.
     * https://developer.android.com/guide/topics/resources/providing-resources
     */
    val resourcesPath: Path,
    /**
     * Path to the Android assets directory.
     *
     * Assets is a low-level API retrieved via `AssetManager` instead of using `R` class.
     */
    val assetsPath: Path,
    /**
     * Path to the JNI libs directory.
     */
    val jniLibsPath: Path,
)
