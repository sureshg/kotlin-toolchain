/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

object LinkTaskType : TaskNameFactory.LeafPlatform {
    override val internalName: String
        get() = "link"

    override val operationMoniker: String
        get() = "linking"
}