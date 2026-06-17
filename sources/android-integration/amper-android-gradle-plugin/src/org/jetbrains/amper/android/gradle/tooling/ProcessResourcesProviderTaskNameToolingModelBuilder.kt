/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.android.gradle.tooling

import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.jetbrains.amper.android.ProcessResourcesProviderData
import org.jetbrains.amper.android.ProjectPath
import org.jetbrains.amper.android.TaskName
import org.jetbrains.amper.android.VariantName
import org.jetbrains.amper.android.gradle.projectPathToModule
import org.jetbrains.amper.android.gradle.request

data class ProcessResourcesProviderDataImpl(override val data: Map<ProjectPath, Map<VariantName, TaskName>>) :
    ProcessResourcesProviderData

class ProcessResourcesProviderTaskNameToolingModelBuilder : ToolingModelBuilder {

    override fun canBuild(modelName: String): Boolean = modelName == ProcessResourcesProviderData::class.java.name

    override fun buildAll(modelName: String, project: Project): ProcessResourcesProviderData {
        val projectPathToModule = project.gradle.projectPathToModule
        val request = project.gradle.request
        val requestedModulePaths = request?.modules?.map { it.modulePath }?.toSet() ?: emptySet()
        val buildTypesChosen = request?.buildTypes?.map { it.value } ?: emptyList()
        val stack = ArrayDeque<Project>()
        stack.add(project)
        val alreadyTraversed = mutableSetOf<Project>()

        return ProcessResourcesProviderDataImpl(
            buildMap {
                while (stack.isNotEmpty()) {
                    val p = stack.removeFirst()
                    for (subproject in p.subprojects) {
                        if (subproject !in alreadyTraversed) {
                            stack.add(subproject)
                            alreadyTraversed.add(subproject)
                        }
                    }
                    if (p.path !in requestedModulePaths) {
                        continue
                    }
                    if (!projectPathToModule.containsKey(p.path)) {
                        continue
                    }

                    // AGP's legacy variant API (AppExtension.applicationVariants) is no longer usable in AGP 9+,
                    // so we rely on AGP's deterministic task naming instead. The Prepare phase needs the compile-time
                    // R class jar (reported in the model under .../generate<Variant>RFile/R.jar), produced by the
                    // "generate<Variant>RFile" task, which transitively runs "process<Variant>Resources". The
                    // delegated build has no product flavors, so the variant name equals the build type (matching
                    // how the runner filters variants by build type).
                    put(p.path, buildMap {
                        for (buildType in buildTypesChosen) {
                            val capitalized = buildType.replaceFirstChar { it.uppercaseChar() }
                            val taskName = listOf("generate${capitalized}RFile", "process${capitalized}Resources")
                                .firstOrNull { it in p.tasks.names }
                            if (taskName != null) {
                                put(buildType, taskName)
                            }
                        }
                    })
                }
            }.filterValues { it.isNotEmpty() })
    }
}
