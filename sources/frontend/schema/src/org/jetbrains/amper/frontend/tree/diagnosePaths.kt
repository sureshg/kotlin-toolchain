/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.tree.reading.YamlValue
import org.jetbrains.amper.frontend.tree.reading.asPreciseTrace
import org.jetbrains.amper.problems.reporting.ProblemReporter
import java.nio.file.Path
import kotlin.io.path.pathString

context(_: ProblemReporter)
internal fun diagnoseProjectRootRelativePath(
    origin: YamlValue.Scalar,
    path: Path,
    initialOffset: Int,
): Boolean {
    var currentOffset = initialOffset
    var isValid = true
    path.forEach {
        val element = it.pathString
        if (element == "." || element == "..") {
            isValid = false
            reportBundleError(
                origin.asPreciseTrace(currentOffset..<currentOffset + element.length).asBuildProblemSource(),
                diagnosticId = TreeDiagnosticId.InvalidProjectRootRelativePath,
                messageKey = "validation.types.invalid.path.project.root.relative",
            )
        }
        currentOffset += element.length + 1
    }
    return isValid
}

context(_: ProblemReporter)
internal fun diagnoseRawPath(
    pathValue: YamlValue.Scalar,
): Boolean {
    var isValid = true
    BackslashRegex.findAll(pathValue.textValue).forEach { match ->
        isValid = false
        reportBundleError(
            pathValue.asPreciseTrace(match.range).asBuildProblemSource(),
            diagnosticId = TreeDiagnosticId.InvalidPathBackslash,
            messageKey = "validation.types.invalid.path.backslash",
        )
    }
    return isValid
}

private val BackslashRegex = """\\""".toRegex()
