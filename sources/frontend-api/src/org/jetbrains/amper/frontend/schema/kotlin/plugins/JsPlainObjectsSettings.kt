/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.kotlin.plugins

import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Shorthand

class JsPlainObjectsSettings : SchemaNode() {
    @Shorthand
    @SchemaDoc(
        "Enables the [Kotlin JS plain objects](https://kotlinlang.org/docs/js-plain-objects.html) compiler plugin.",
    )
    val enabled by value(false)
}
