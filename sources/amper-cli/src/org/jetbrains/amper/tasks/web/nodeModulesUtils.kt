/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.web

import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

internal fun generateImportMap(
    nodeModulesDir: Path,
): Map<String, Path> {
    val moduleNames = collectModuleNames(nodeModulesDir = nodeModulesDir)

    val importMap = moduleNames
        .associateWith { moduleName: String ->
            resolveInNodeModulesDir(moduleName, nodeModulesDir) ?: error("Module $moduleName not found")
        }

    return importMap
}

private fun resolveInNodeModulesDir(name: String, nodeModulesDir: Path): Path? {
    return findModuleEntryPoint(nodeModulesDir.resolve(name))
}

private fun collectModuleNames(nodeModulesDir: Path): List<String> {
    if (!nodeModulesDir.isDirectory()) return emptyList()

    return nodeModulesDir.listDirectoryEntries()
        .filter { it.isDirectory() }
        .flatMap { entry ->
            if (entry.name == ".pnpm") return@flatMap emptyList()

            if (entry.name.startsWith("@")) {
                if (entry.name.startsWith("@types")) return@flatMap emptyList()

                entry.listDirectoryEntries()
                    .filter { it.isDirectory() }
                    .map { "${entry.name}/${it.name}" }
            } else {
                listOf(entry.name)
            }
        }
}

private fun findModuleEntryPoint(dir: Path): Path? {
    val packageJsonFile = dir.resolve(PACKAGE_JSON)

    if (!packageJsonFile.isRegularFile()) return null

    val packageJson = json.decodeFromString<PackageJson>(packageJsonFile.readText())

    val main = packageJson.module ?: packageJson.main

    if (main == null) return findEntryPointAsIndexOrNull(dir)

    val mainFile = dir.resolve(main)
    return findEntryPointAsFileOrNull(mainFile) ?: findEntryPointAsIndexOrNull(mainFile)
}

private val json = Json {
    ignoreUnknownKeys = true
}

private fun findEntryPointAsIndexOrNull(dir: Path): Path? =
    ["index.js", "index.mjs"].firstNotNullOfOrNull {
        findEntryPointAsFileOrNull(dir.resolve(it))
    }

private fun findEntryPointAsFileOrNull(file: Path): Path? =
    file.takeIf { file.isRegularFile() }

internal const val NODE_MODULES = "node_modules"
internal const val PACKAGE_JSON = "package.json"
internal const val VENDORS = "vendors"