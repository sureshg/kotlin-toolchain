/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.kotlin.plugins

import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Shorthand
import org.jetbrains.amper.frontend.api.TraceableString

@SchemaDoc("Preset options for the no-arg compiler plugin")
enum class NoArgPreset(override val schemaValue: String, override val outdated: Boolean = false) : SchemaEnum {
    @SchemaDoc("Automatically adds no-arg constructors to JPA entity classes")
    Jpa("jpa");

    companion object Index : EnumMap<NoArgPreset, String>(NoArgPreset::values, NoArgPreset::schemaValue)
}

class NoArgSettings : SchemaNode() {
    @Shorthand
    @SchemaDoc("Enables the Kotlin no-arg compiler plugin")
    val enabled by value(false)

    @SchemaDoc("List of annotations that trigger no-arg constructor generation. Classes annotated with these annotations will have a no-arg constructor generated.")
    val annotations by nullableValue<List<TraceableString>>()

    @SchemaDoc("Whether to call initializers in the synthesized constructor. By default, initializers are not called.")
    val invokeInitializers by value(false)

    @SchemaDoc("Predefined sets of annotations. Currently only 'jpa' preset is supported, which automatically includes JPA entity annotations.")
    val presets by nullableValue<List<NoArgPreset>>()
}
