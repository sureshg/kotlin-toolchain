/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.kotlin.plugins

import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Shorthand
import org.jetbrains.amper.frontend.api.TraceableString

class PowerAssertSettings : SchemaNode() {
    @Shorthand
    @SchemaDoc("Enables the Kotlin power-assert compiler plugin")
    val enabled by value(false)

    @SchemaDoc("A list of fully-qualified function names that the Power-assert plugin should transform. " +
            "If not specified, only kotlin.assert() calls will be transformed by default.")
    val functions by value(listOf(TraceableString("kotlin.assert", DefaultTrace)))
}
