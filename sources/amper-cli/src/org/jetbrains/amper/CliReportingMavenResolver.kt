/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper

import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.frontend.dr.resolver.MavenResolver
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.problems.reporting.BuildProblem
import org.jetbrains.amper.problems.reporting.Level

/**
 * [org.jetbrains.amper.frontend.dr.resolver.MavenResolver] inheritor that throws a
 * [org.jetbrains.amper.cli.UserReadableError] if any problems are errors.
 */
class CliReportingMavenResolver(
    userCacheRoot: AmperUserCacheRoot,
    incrementalCache: IncrementalCache,
) : MavenResolver(userCacheRoot, incrementalCache) {

    override fun handleProblems(buildProblems: List<BuildProblem>, resolveSourceMoniker: String) {
        val errors = buildProblems.filter { it.level.atLeastAsSevereAs(Level.Error) }
        if (errors.isNotEmpty()) {
            userReadableError("Unable to resolve dependencies for $resolveSourceMoniker, see the errors above.")
        }
    }
}