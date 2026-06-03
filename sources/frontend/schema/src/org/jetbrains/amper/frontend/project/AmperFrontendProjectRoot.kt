/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.project

import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

/**
 * A holder-class for the project root path in the frontend.
 *
 * Both [virtualFile] and [path] are available.
 */
class AmperFrontendProjectRoot(
    val virtualFile: VirtualFile,
) {
    val path: Path = virtualFile.toNioPath()
}
