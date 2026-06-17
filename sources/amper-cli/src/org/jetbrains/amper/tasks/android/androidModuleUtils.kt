/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import org.jetbrains.amper.android.SYNTHETIC_ROOT_ANDROID_PROJECT_PATH
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.frontend.AmperModule
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.relativeTo

/**
 * Returns the Gradle path of this module, as if it were part of a Gradle project with root [projectRoot].
 */
internal fun AmperModule.gradlePath(projectRoot: AmperProjectRoot): String {
    val moduleDir = source.moduleDir
    val relativeModuleDir = moduleDir.relativeTo(projectRoot.path).normalize().invariantSeparatorsPathString
    // A module at the project root would map to the root Gradle project (":"), but AGP 9+ can't be applied
    // to the root project. Such a module is delegated to a synthetic subproject instead (see the constant docs).
    if (relativeModuleDir.isEmpty() || relativeModuleDir == ".") {
        return SYNTHETIC_ROOT_ANDROID_PROJECT_PATH
    }
    return ":" + relativeModuleDir.replace('/', ':')
}
