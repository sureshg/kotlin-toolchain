/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.project.AmperFrontendProjectRoot
import org.jetbrains.amper.frontend.schema.Project
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.tree.completeTree
import org.jetbrains.amper.frontend.tree.instance
import org.jetbrains.amper.frontend.tree.reading.readTree
import org.jetbrains.amper.frontend.types.generated.*
import org.jetbrains.amper.problems.reporting.ProblemReporter

context(_: ProblemReporter, _: FrontendPathResolver, _: AmperFrontendProjectRoot)
internal fun readProject(
    projectFile: VirtualFile,
): Project {
    val projectTree = readTree(projectFile, DeclarationOfProject)
    return TreeRefiner().refineTree(projectTree, EmptyContexts)
        .completeTree()?.instance<Project>()
        ?: error("No required values must be in the project schema!")
}
