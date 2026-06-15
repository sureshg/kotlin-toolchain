/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.stdlib.io.path

import java.nio.file.FileVisitResult
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.visitFileTree

/**
 * Cleans the [directory] but keeps the files/subtrees from the [keepPaths].
 *
 * [keepPaths] can be arbitrarily nested within the [directory].
 * The ones that are not inside the [directory] or do not exist are ignored.
 */
fun cleanDirectoryExcept(
    directory: Path,
    keepPaths: List<Path>,
) {
    if (!directory.exists())
        return

    check(directory.isDirectory()) { "Expected a directory at '$directory'" }

    val directory = directory.normalize()
    val keepPaths = keepPaths.mapNotNull { path ->
        path.normalize().takeIf { it.startsWith(directory) && it.exists() }
    }

    directory.visitFileTree {
        onPreVisitDirectory dir@ { dir, _ ->
            if (dir == directory)
                return@dir FileVisitResult.CONTINUE
            for (keepPath in keepPaths) {
                if (keepPath == dir)
                    return@dir FileVisitResult.SKIP_SUBTREE
                if (keepPath.startsWith(dir))
                    return@dir FileVisitResult.CONTINUE
            }
            dir.deleteRecursively()
            FileVisitResult.SKIP_SUBTREE
        }
        onVisitFile file@ { file, _ ->
            if (keepPaths.any { it == file })
                return@file FileVisitResult.CONTINUE

            file.deleteExisting()
            FileVisitResult.CONTINUE
        }
    }
}