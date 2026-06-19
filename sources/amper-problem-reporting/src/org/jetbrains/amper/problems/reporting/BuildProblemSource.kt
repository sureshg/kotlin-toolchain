/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.problems.reporting

import java.nio.file.Path

/**
 * Designates the place where the cause of the problem is located.
 */
sealed interface BuildProblemSource

/**
 * Use only when there is no way to pinpoint the cause of the problem inside the Amper files.
 */
@NonIdealDiagnostic
data object GlobalBuildProblemSource : BuildProblemSource

/**
 * Can be used to express the problem with multiple locations (e.g., conflicting declarations).
 *
 * @param sources individual file-related problem sources
 * @param groupingMessage a message to be displayed before listing the list of sources,
 *   e.g. `"See here:"` or `"Encountered in:"`
 */
class MultipleLocationsBuildProblemSource(
    val sources: List<FileBuildProblemSource>,
    val groupingMessage: String,
) : BuildProblemSource {
    constructor(
        vararg sources: FileBuildProblemSource,
        groupingMessage: String,
    ): this(sources.toList(), groupingMessage)
}

interface FileBuildProblemSource : BuildProblemSource {
    /**
     * Path to the file containing a problem.
     */
    val file: Path
}

interface FileWithRangesBuildProblemSource : FileBuildProblemSource {

    /**
     * Range of problematic code expressed in terms of character offsets inside the file.
     */
    val offsetRange: IntRange
}
