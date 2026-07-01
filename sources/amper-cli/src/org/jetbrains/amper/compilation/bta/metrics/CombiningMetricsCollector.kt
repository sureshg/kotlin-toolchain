/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation.bta.metrics

import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.trackers.BuildMetricsCollector

@OptIn(ExperimentalBuildToolsApi::class)
class CombiningMetricsCollector(
    private val delegates: List<BuildMetricsCollector>,
) : BuildMetricsCollector {

    override fun collectMetric(name: String, type: BuildMetricsCollector.ValueType, value: Long) {
        delegates.forEach {
            it.collectMetric(name, type, value)
        }
    }
}
