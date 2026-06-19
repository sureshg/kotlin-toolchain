/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading

import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.asTraceable
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.tree.ReferenceNode
import org.jetbrains.amper.frontend.tree.ResolvableNode
import org.jetbrains.amper.frontend.tree.StringInterpolationNode
import org.jetbrains.amper.frontend.tree.TreeDiagnosticId
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.render
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.amper.stdlib.regex.getValue

context(contexts: Contexts, _: ParsingConfig, _: ProblemReporter)
internal fun parseReferenceOrInterpolation(
    scalar: YamlValue.Scalar,
    type: SchemaType,
): ResolvableNode? {
    val parts = mutableListOf<StringInterpolationNode.Part>()
    splitIntoParts(
        scalar.textValue,
        onMatch = { match ->
            val reference by match.groups
            val closingBrace by match
            if (closingBrace == null) {
                reportBundleError(
                    scalar.asPreciseTrace(match.range).asBuildProblemSource(),
                    TreeDiagnosticId.ReferenceMissesClosingBrace,
                    "validation.reference.missing.closing.brace",
                )
                return null
            }
            val referenceText = reference?.value
            if (referenceText.isNullOrBlank()) {
                reportBundleError(
                    scalar.asPreciseTrace(reference?.range ?: match.range).asBuildProblemSource(),
                    TreeDiagnosticId.ReferenceIsEmpty,
                    "validation.reference.empty",
                )
                return null
            }
            if ('$' in referenceText || '{' in referenceText) {
                val index = referenceText.indexOf('$').takeIf { it > 0 }
                    ?: referenceText.indexOf('{').takeIf { it > 0 }
                    ?: 0
                val rangeInTextValue = (match.range.first + index)..match.range.last
                reportBundleError(
                    scalar.asPreciseTrace(rangeInTextValue).asBuildProblemSource(),
                    TreeDiagnosticId.NestedReferencesAreNotSupported, "validation.reference.nested",
                )
                return null
            }

            val referencePath = run {
                var offset = reference!!.range.first
                referenceText.split('.').map {
                    val range = offset..<offset + it.length
                    offset += it.length + 1  // +1 is for the `.`
                    TraceableString(it, scalar.asPreciseTrace(range))
                }
            }

            for (pathElement in referencePath) {
                if (pathElement.value.isBlank()) {
                    reportBundleError(
                        pathElement.asBuildProblemSource(),
                        TreeDiagnosticId.ReferenceSegmentIsEmpty, "validation.reference.empty.element"
                    )
                }
            }
            parts.add(StringInterpolationNode.Part.Reference(referencePath, scalar.asPreciseTrace(match.range)))
        },
        onText = { text, range ->
            parts.add(StringInterpolationNode.Part.Text(text.asTraceable(scalar.asPreciseTrace(range))))
        },
    )
    require(parts.isNotEmpty())

    return if (parts.size > 1) {
        if (type !is SchemaType.StringInterpolatableType || (type is SchemaType.StringType && type.semantics != null)) {
            reportParsing(
                // Maybe use MultipleLocationsBuildProblemSource with all the parts' sources?
                scalar, TreeDiagnosticId.TypeDoesNotSupportInterpolation,
                "validation.types.unsupported.interpolation", type.render(includeSyntax = false),
            )
            return null
        }
        StringInterpolationNode(
            parts = parts,
            trace = scalar.asTrace(),
            contexts = contexts,
            expectedType = type,
        )
    } else {
        val reference = checkNotNull(parts.first() as StringInterpolationNode.Part.Reference) {
            "Should not be called unless 'containsReferenceSyntax' is true"
        }
        ReferenceNode(
            referencedPath = reference.referencePath,
            trace = scalar.asTrace(),
            contexts = contexts,
            expectedType = type,
        )
    }
}

private inline fun splitIntoParts(
    input: String,
    onMatch: (MatchResult) -> Unit,
    onText: (String, IntRange) -> Unit,
) {
    var position = 0
    while (position < input.length) {
        val match = ReferenceSyntax.find(input, position)
        if (match != null) {
            if (match.range.first > position) {
                onText(input.substring(position, match.range.first), position..<match.range.first)
            }
            onMatch(match)
            position = match.range.last + 1
        } else {
            onText(input.substring(position), 0..<position)
            break
        }
    }
}

internal fun containsReferenceSyntax(scalar: YamlValue.Scalar): Boolean {
    return ReferenceSyntax.containsMatchIn(scalar.textValue)
}

// The closing brace is optional here, so when this matches, it doesn't mean that the reference is valid.
private val ReferenceSyntax =
    """\$\{(?<reference>[^}\n]*)(?<closingBrace>})?""".toRegex()
