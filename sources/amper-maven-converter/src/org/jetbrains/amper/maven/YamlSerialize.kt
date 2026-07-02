/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven

import org.jetbrains.amper.frontend.contexts.TestCtx
import org.jetbrains.amper.frontend.contexts.defaultContextsInheritance
import org.jetbrains.amper.frontend.contexts.plus
import org.jetbrains.amper.frontend.schema.SchemaMavenCoordinates
import org.jetbrains.amper.frontend.tree.BooleanNode
import org.jetbrains.amper.frontend.tree.EnumNode
import org.jetbrains.amper.frontend.tree.IntNode
import org.jetbrains.amper.frontend.tree.ListNode
import org.jetbrains.amper.frontend.tree.MappingNode
import org.jetbrains.amper.frontend.tree.PathNode
import org.jetbrains.amper.frontend.tree.ScalarNode
import org.jetbrains.amper.frontend.tree.StringNode
import org.jetbrains.amper.frontend.tree.TreeNode
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.tree.copy
import org.jetbrains.amper.frontend.tree.schemaValue
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.problems.reporting.CollectingProblemReporter
import org.jetbrains.amper.problems.reporting.noProblemsReported
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.pathString

typealias Key = String

val treeRefiner = TreeRefiner(contextComparator = defaultContextsInheritance + MavenContributorContextInheritance)

internal fun MappingNode.serializeToYaml(comments: YamlComments = emptyMap()): String = buildString {
    val problemReporter = CollectingProblemReporter()
    val refinedMain = context(problemReporter) {
        treeRefiner.refineTree(
            this@serializeToYaml,
            listOf(MavenContributorContext.WithAllContributors),
            withDefaults = false,
        )
    }
    val refinedTest = context(problemReporter) {
        treeRefiner.refineTree(
            this@serializeToYaml,
            listOf(MavenContributorContext.WithAllContributors, TestCtx),
            withDefaults = false,
        )
    }
    check(problemReporter.noProblemsReported) {
        // This indicates a problem in the converter code, so it's fine to just fail here
        buildString {
            appendLine("Failed to refine converted tree:")
            problemReporter.problems.forEach { appendLine(" - ${it.message}") }
        }
    }
    val test = refinedTest.filterByContext(TestCtx, refinedMain)

    val mainComments = comments.filterValues { !it.test }
    val testComments = comments.filterValues { it.test }

    append(refinedMain.serializeToYaml(0, emptyList(), mainComments))
    if (test != null) {
        appendLine()
        append(test.serializeToYaml(0, emptyList(), testComments))
    }
}

private fun TreeNode.serializeToYaml(
    indent: Int, 
    currentPath: List<String>, 
    comments: YamlComments,
    listItem: Boolean = false,
): String = buildString {
    when (this@serializeToYaml) {
        is ListNode -> append(this@serializeToYaml.serializeToYaml(indent))
        is MappingNode -> append(this@serializeToYaml.serializeToYaml(indent, currentPath, comments, listItem))
        is ScalarNode -> append(this@serializeToYaml.serializeToYaml(indent))
        else -> {}
    }
}

private fun MappingNode.serializeToYaml(
    indent: Int, 
    currentPath: List<String>, 
    comments: YamlComments,
    listItem: Boolean = false,
): String = buildString {
    val isFromKeyAndTheRestNestedProperties = children.filter { it.propertyDeclaration?.isFromKeyAndTheRestNested == true }
    val isCollapsibleFromKey = isFromKeyAndTheRestNestedProperties.isNotEmpty()
    
    val coordinatesPresent = declaration?.isExternalDependencyNotation == true && children.any { it.key in SchemaMavenCoordinates.properties }
    
    if (isCollapsibleFromKey || coordinatesPresent) {
        
        // We treat [ExternalDependencyNotation] as if all coordinates parts are marked with [FromKeyAndTheRestIsNested].
        val theRest = if (declaration?.isExternalDependencyNotation != true)
            children.filter { it !in isFromKeyAndTheRestNestedProperties }
        else 
            children.filter { it.key !in SchemaMavenCoordinates.properties }

        val declarationInstance = declaration?.createInstance()
        val collapsiblePropertyValue = if (declarationInstance is SchemaMavenCoordinates) {
            SchemaMavenCoordinates.properties
                .mapNotNull { property -> children.find { it.key == property }?.value }
                .filterIsInstance<StringNode>()
                .joinToString(separator = ":") { it.value }
        } else if (isFromKeyAndTheRestNestedProperties.size == 1) {
            isFromKeyAndTheRestNestedProperties.single().value
        } else error("Must not reach here")

        require(collapsiblePropertyValue is ScalarNode || collapsiblePropertyValue is String) {
            "Only scalar values can be collapsible"
        }
        
        append(" ")
        when (collapsiblePropertyValue) {
            is String -> append(collapsiblePropertyValue)
            is BooleanNode -> append(collapsiblePropertyValue.value)
            is EnumNode -> append(collapsiblePropertyValue.schemaValue)
            is IntNode -> append(collapsiblePropertyValue.value)
            is PathNode -> append(collapsiblePropertyValue.value.invariantSeparatorsPathString)
            is StringNode -> append(collapsiblePropertyValue.value)
        }

        if (theRest.isEmpty()) {
            appendLine()
            return@buildString
        } else {
            append(":")
            append(this@serializeToYaml.copy(theRest, declaration, trace, contexts).serializeToYaml(indent + 1, currentPath, comments))
            return@buildString
        }
    }

    for (child in children) {
        val childPath = currentPath + child.key
        val comment = comments[childPath]

        if (child.propertyDeclaration?.hasShorthand == true && children.size == 1) {
            when (child.propertyDeclaration?.type) {
                is SchemaType.BooleanType -> appendLine(" ${child.key}")
                else -> append(child.value.serializeToYaml(indent, childPath, comments))
            }
        } else {
            // If this is a nested mapping, we need to add a line break before the key.
            // If this is a list item, we need to add a space before the key.
            val isFirstChild = child == children.first()
            if (isFirstChild) {
                if (indent > 0 && !listItem) appendLine()
                else if (listItem) append(" ")
            }

            require(child.contexts.size == 1) {
                "After context selection there must be only one context"
            }

            comment?.beforeKeyComment?.let { beforeComment ->
                beforeComment.lines().forEach { line ->
                    append("  ".repeat(indent))
                    append("# ")
                    appendLine(line)
                }
            }
            
            val keyString = if (child.contexts.singleOrNull() is TestCtx && indent == 0) "test-${child.key}" else child.key
            val keyIndent = if (listItem && isFirstChild) 0 else indent
            append(keyString.serializeToYaml(keyIndent))

            append(child.value.serializeToYaml(indent + 1, childPath, comments))

            comment?.afterValueComment?.let { afterComment ->
                afterComment.lines().forEach { line ->
                    append("  ".repeat(indent + 1))
                    append("# ")
                    appendLine(line)
                }
            }

            if (indent == 0 && child != children.last()) {
                appendLine()
            }
        }
    }
}

private fun ListNode.serializeToYaml(indent: Int): String = buildString {
    appendLine()
    for (item in children) {
        append("  ".repeat(indent))
        append("-")
        append(item.serializeToYaml(indent + 1, emptyList(), emptyMap(), listItem = true))
    }
}

private fun ScalarNode.serializeToYaml(indent: Int): String = buildString {
    if (this@serializeToYaml is StringNode && value.contains('\n')) {
        appendLine(" |-")
        val indentStr = "  ".repeat(indent)
        for (line in value.lines()) {
            append(indentStr)
            appendLine(line)
        }
        return@buildString
    }
    append(" ")
    when (this@serializeToYaml) {
        is BooleanNode -> append(value)
        is EnumNode -> append(schemaValue)
        is IntNode -> append(value)
        is PathNode -> append(value.pathString)
        is StringNode -> append(YamlQuoting.quote(value))
    }
    appendLine()
}

private fun Key.serializeToYaml(indent: Int): String = buildString {
    append("  ".repeat(indent))
    append(this@serializeToYaml)
    append(":")
}
