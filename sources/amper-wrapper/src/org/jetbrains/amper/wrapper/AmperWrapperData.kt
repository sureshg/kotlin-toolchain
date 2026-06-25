/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.wrapper

import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.system.info.OsFamily
import org.jetbrains.amper.system.info.SystemInfo
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

@UsedInIdePlugin
data class AmperWrapperData private constructor(
    val version: String,
    val sha256: String,
    val path: Path,
) {
    companion object {
        /**
         * Parses [AmperWrapperData] from the project with the directory [projectRoot].
         * The concrete wrapper script is selected based on the host platform.
         * If there is no wrapper there, returns `null`.
         */
        @UsedInIdePlugin
        fun parseFromProjectRoot(
            projectRoot: Path,
        ): AmperWrapperData? {
            val wrapperPath = wrapperTypeForCurrentPlatform()
                .let { projectRoot / it.wrapperFileName }
                .takeIf { it.isRegularFile() }
                ?: return null
            return parse(wrapperPath = wrapperPath)
        }

        /**
         * Parses [AmperWrapperData] from the wrapper file at [wrapperPath].
         */
        fun parse(
            wrapperPath: Path,
        ): AmperWrapperData {
            val type = WrapperType.entries.first { it.wrapperFileName == wrapperPath.name }
            val wrapperText = wrapperPath.readText()

            // If any error arises from here, that means the wrapper format has changed and things need to be adjusted.
            return AmperWrapperData(
                version = checkNotNull(type.versionRegex.find(wrapperText)) {
                    "Missing kotlin_cli_version in the $wrapperPath"
                }.groupValues[1],
                sha256 = checkNotNull(type.checkSumRegex.find(wrapperText)){
                    "Missing kotlin_cli_sha256 in the $wrapperPath"
                }.groupValues[1],
                path = wrapperPath,
            )
        }

        private fun wrapperTypeForCurrentPlatform() = when (SystemInfo.CurrentHost.family) {
            OsFamily.Windows -> WrapperType.KotlinBatch
            OsFamily.Linux,
            OsFamily.MacOs,
            OsFamily.FreeBSD,
            OsFamily.Solaris -> WrapperType.KotlinSh
        }

        private enum class WrapperType(
            val versionRegex: Regex,
            val checkSumRegex: Regex,
            val wrapperFileName: String,
        ) {
            KotlinBatch(
                versionRegex = "^set kotlin_cli_version=(.*)$".toRegex(RegexOption.MULTILINE),
                checkSumRegex = "^set kotlin_cli_sha256=(.*)$".toRegex(RegexOption.MULTILINE),
                wrapperFileName = "kotlin.bat"
            ),
            KotlinSh(
                versionRegex = "^kotlin_cli_version=(.*)$".toRegex(RegexOption.MULTILINE),
                checkSumRegex = "^kotlin_cli_sha256=(.*)$".toRegex(RegexOption.MULTILINE),
                wrapperFileName = "kotlin"
            ),
        }
    }
}
