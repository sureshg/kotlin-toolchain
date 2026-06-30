/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.web

import kotlinx.serialization.Serializable

@Serializable
class PackageJson(
    val name: String,
    val version: String,
    val private: Boolean = false,
    val dependencies: Map<String, String> = emptyMap(),
    val module: String? = null,
    val main: String? = null,
)