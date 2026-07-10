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

class AllOpenSettings : SchemaNode() {
    @Shorthand
    @SchemaDoc("Enables the [Kotlin all-open](https://kotlinlang.org/docs/all-open-plugin.html) compiler plugin")
    val enabled by value(false)

    @SchemaDoc("List of annotations that the plugin should trigger on. " +
            "Classes/methods annotated with these annotations will be automatically made open. " +
            "See also `presets` to automatically configure the right set of annotations for some known frameworks.")
    val annotations by nullableValue<List<TraceableString>>()

    @SchemaDoc("Predefined sets of annotations for common frameworks. " +
            "Each preset automatically includes annotations specific to that framework.")
    val presets by nullableValue<List<AllOpenPreset>>()
}

@SchemaDoc("Preset options for the all-open compiler plugin")
enum class AllOpenPreset(override val schemaValue: String, override val outdated: Boolean = false) : SchemaEnum {
    @SchemaDoc("Automatically adds annotations used by the Spring framework")
    Spring("spring"),

    @SchemaDoc("Automatically adds annotations used by the Micronaut framework")
    Micronaut("micronaut"),

    @SchemaDoc("Automatically adds annotations used by the Quarkus framework")
    Quarkus("quarkus");

    companion object Index : EnumMap<AllOpenPreset, String>(AllOpenPreset::values, AllOpenPreset::schemaValue)
}
