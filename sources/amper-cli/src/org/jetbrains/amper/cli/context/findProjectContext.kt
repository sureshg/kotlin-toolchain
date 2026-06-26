/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.context

import org.jetbrains.amper.cli.CliProblemReporter
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.use
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute

/**
 * Creates an [AmperProjectContext] based on the given [explicitProjectDir].
 *
 * If no [explicitProjectDir] is given (`null`), this function finds a project by starting at the current directory
 * using the semantics of [AmperProjectContext.find]. If no project is found in this case, `null` is returned.
 *
 * If an [explicitProjectDir] is passed, this function creates the [AmperProjectContext] using that directory.
 * If this fails (missing module or project file), this function fails gracefully.
 * **It does NOT return `null` in this case because explicit directories must be valid.**
 *
 * The [explicitBuildDir] parameter is just a configuration option and doesn't impact the project search.
 */
internal suspend fun findProjectContext(
    explicitProjectDir: Path?,
    explicitBuildDir: Path?,
): AmperProjectContext? = spanBuilder("Find Kotlin project context").use {
    with(CliProblemReporter) {
        val context = if (explicitProjectDir != null) {
            AmperProjectContext.create(
                rootDir = explicitProjectDir.absolute(),
                buildDir = explicitBuildDir?.absolute(),
            )
                ?: userReadableError(
                    "The given path '$explicitProjectDir' is not a valid Kotlin project root directory. " +
                            "Make sure you have a project file or a module file at the root of your Kotlin project."
                )
        } else {
            AmperProjectContext.find(
                start = Path(System.getProperty("user.dir")),
                buildDir = explicitBuildDir?.absolute(),
            )
        }
        if (wereProblemsReported()) {
            userReadableError("Aborting because there were errors in the Kotlin project file, please see above.")
        }
        context
    }
}
