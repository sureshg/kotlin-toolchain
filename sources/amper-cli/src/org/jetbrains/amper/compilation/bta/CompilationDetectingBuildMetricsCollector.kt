/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation.bta

import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.trackers.BuildMetricsCollector

/**
 * Detects whether the something was actually compiled during an incremental compilation run.
 *
 * When incremental compilation is used, modules might not need to be recompiled even if their dependencies changed.
 * That is, if dependencies only changed in implementation without changing their ABIs, the consuming module doesn't
 * need recompilation. This metrics collector listens to metric events of the compilation to distinguish when something
 * is actually built (as opposed to just a no-op up-to-date check).
 */
@OptIn(ExperimentalBuildToolsApi::class)
class CompilationDetectingBuildMetricsCollector : BuildMetricsCollector {

    /**
     * Whether the compilation operation actually compiled anything, so we can report a message to the user.
     */
    var didCompileSomething: Boolean = false
        private set

    override fun collectMetric(
        name: String,
        type: BuildMetricsCollector.ValueType,
        value: Long,
    ) {
        // This seems to be the event that distinguishes real compilations from no-op compilations.
        // It is not present at all when compiling a module that didn't change, and whose dependencies' ABI snapshots
        // didn't change. We still test for value>0 in case the compiler starts reporting the 0 count.
        if (name == "Total compiler iteration" && value > 0) {
            didCompileSomething = true
        }
    }
}
