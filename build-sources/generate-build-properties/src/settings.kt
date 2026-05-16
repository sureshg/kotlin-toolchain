import org.jetbrains.amper.plugins.Configurable

/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@Configurable
interface Settings {
    /**
     * Simple class name of the generated class (doesn't include package)
     */
    val classSimpleName: String

    /**
     * Package of the generated class
     */
    val classPackage: String

    /**
     * Specifies whether to add URL to documentation or not
     */
    val addDocumentationUrl: Boolean get() = true
}