/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation.bta

import io.opentelemetry.api.OpenTelemetry
import org.jetbrains.amper.telemetry.useWithoutCoroutines
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.trackers.BuildMetricsCollector
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant
import kotlin.time.toJavaInstant

@OptIn(ExperimentalBuildToolsApi::class)
class OpenTelemetryBuildMetricsCollector(
    openTelemetry: OpenTelemetry,
    private val clock: Clock = Clock.System,
) : BuildMetricsCollector {

    private val tracer = openTelemetry.getTracer("kotlin-compiler")

    private val seenChildSpanCandidatesByParent = ConcurrentHashMap<MetricPath, MutableList<PendingSpan>>()
    private val rootSpans = mutableListOf<PendingSpan>()

    private data class MetricPath(val elements: List<String>) {
        val parent get() = if (elements.size < 2) null else MetricPath(elements.dropLast(1))
    }

    private data class PendingSpan(
        val name: String,
        val start: Instant,
        val end: Instant,
        val children: List<PendingSpan>,
    )

    override fun collectMetric(
        name: String,
        type: BuildMetricsCollector.ValueType,
        value: Long,
    ) {
        val end = clock.now()
        val duration = when (type) {
            BuildMetricsCollector.ValueType.BYTES,
            BuildMetricsCollector.ValueType.NUMBER,
            BuildMetricsCollector.ValueType.ATTRIBUTE -> return
            BuildMetricsCollector.ValueType.TIME,
            BuildMetricsCollector.ValueType.MILLISECONDS -> value.milliseconds
            BuildMetricsCollector.ValueType.NANOSECONDS -> value.nanoseconds
        }

        val path = MetricPath(name.split(Regex("\\s*->\\s*")))
        val parentPath = path.parent

        val children = seenChildSpanCandidatesByParent.remove(path) ?: []
        val pendingSpan = PendingSpan(
            name = path.elements.last(),
            start = end - duration,
            end = end,
            children = children,
        )
        if (parentPath == null) {
            rootSpans.add(pendingSpan)
        } else {
            seenChildSpanCandidatesByParent
                .computeIfAbsent(parentPath) { mutableListOf() }
                .add(pendingSpan)
        }
    }

    fun reportSpans() {
        rootSpans.forEach {
            reportSpan(it)
        }
    }

    private fun reportSpan(pendingSpan: PendingSpan) {
        tracer.spanBuilder(pendingSpan.name)
            .setStartTimestamp(pendingSpan.start.toJavaInstant())
            .useWithoutCoroutines { span ->
                pendingSpan.children.forEach { reportSpan(it) }
                span.end(pendingSpan.end.toJavaInstant())
            }
    }
}
