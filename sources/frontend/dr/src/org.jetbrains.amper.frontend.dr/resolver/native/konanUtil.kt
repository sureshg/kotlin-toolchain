/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver.native

import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.dr.resolver.native.NativeLibraryConstants.KONAN_DISTRIBUTION_COMMONIZED_LIBS_DIR
import org.jetbrains.amper.frontend.dr.resolver.native.NativeLibraryConstants.KONAN_DISTRIBUTION_COMMON_LIBS_DIR
import org.jetbrains.amper.frontend.dr.resolver.native.NativeLibraryConstants.KONAN_DISTRIBUTION_KLIB_DIR
import org.jetbrains.amper.frontend.dr.resolver.native.NativeLibraryConstants.KONAN_DISTRIBUTION_PLATFORM_LIBS_DIR
import org.jetbrains.amper.frontend.dr.resolver.native.NativeLibraryConstants.KONAN_STDLIB_NAME
import java.io.File
import java.net.URLEncoder
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

/*
  This logic is mostly copied from Kotlin/Native source code and modified to use java.nio

  native/commonizer-api/src/org/jetbrains/kotlin/commonizer/KonanDistribution.kt
  native/commonizer-api/src/org/jetbrains/kotlin/commonizer/TargetLibrariesLayout.kt
*/

// TODO: It should also operate on Platform API, perhaps.

private const val KONAN_DATA_DIR = "KONAN_DATA_DIR"

@JvmInline
value class KonanDistribution(val path: Path)

val KonanDistribution.klibDir: Path
    get() = path / KONAN_DISTRIBUTION_KLIB_DIR

val KonanDistribution.commonLibraries: Path
    get() = klibDir / KONAN_DISTRIBUTION_COMMON_LIBS_DIR

val KonanDistribution.stdlibDir: Path
    get() = commonLibraries / KONAN_STDLIB_NAME

val KonanDistribution.platformLibsDir: Path
    get() = klibDir / KONAN_DISTRIBUTION_PLATFORM_LIBS_DIR

val KonanDistribution.commonizedLibrariesDir: Path
    get() = klibDir / KONAN_DISTRIBUTION_COMMONIZED_LIBS_DIR

private val Path.commonizedLibrariesDir: Path
    get() = KonanDistribution(this).commonizedLibrariesDir

fun KonanDistribution.platformDir(platform: String): Path =
    platformLibsDir / platform

fun Collection<Platform>.compilerPlatforms(): List<String> = map { it.nameForCompiler }

@UsedInIdePlugin
fun KonanDistribution.commonizedKlibs(compilerPlatforms: List<String>, kotlinVersion: String): List<Path> {
    val sortedPlatforms = compilerPlatforms.sorted().toSet()
    val commonizedPath = commonizedRoot(kotlinVersion).commonizedPlatformsDirectory(sortedPlatforms)
    val chosenCommonized = commonizedPath
        .takeIf { it.exists() }
        ?.listDirectoryEntries()
        ?.filter { it.isDirectory() || it.extension == "*.klib" }

    return chosenCommonized ?: emptyList()
}

fun KonanDistribution.commonizedRoot(kotlinVersion: String): Path {
    val explicitKonanDataDir = System.getenv(KONAN_DATA_DIR)
    val konanDataDir = if (explicitKonanDataDir != null) Path(explicitKonanDataDir) else path
    val encodedVersion = URLEncoder.encode(kotlinVersion, Charsets.UTF_8.name())
    // TODO: We should probably use KONAN_DATA_DIR here instead. See AMPER-5319.
    val commonizedRoot = konanDataDir.commonizedLibrariesDir / encodedVersion

    return commonizedRoot
}

private fun Path.commonizedPlatformsDirectory(platforms: Set<String>): Path {
    // native/commonizer-api/src/org/jetbrains/kotlin/commonizer/CommonizerTarget.kt
    val identityString = platforms.joinToString(
        separator = ", ",
        prefix = "(",
        postfix = ")",
    )
    return this / nameTrimmedWithHash(identityString)
}

private const val maxFileNameLength = 150

private fun nameTrimmedWithHash(fileName: String): String =
    if (fileName.length <= maxFileNameLength) {
        fileName
    }
    else {
        val hashSuffix = "[--${base64Hash(fileName)}]"
        fileName.take(maxFileNameLength - hashSuffix.length) + hashSuffix
    }

private fun base64Hash(value: String): String {
    val sha = MessageDigest.getInstance("SHA-1")
    return Base64.UrlSafe.encode(sha.digest(value.encodeToByteArray()))
}

/*
  These constants are copied from kotlin-compiler-common-for-ide library
  (org.jetbrains.kotlin.konan.library.NativeLibraryConstants.kt)
*/
private object NativeLibraryConstants {
    const val KONAN_STDLIB_NAME = "stdlib"

    const val KONAN_DISTRIBUTION_KLIB_DIR = "klib"
    const val KONAN_DISTRIBUTION_COMMON_LIBS_DIR = "common"
    const val KONAN_DISTRIBUTION_PLATFORM_LIBS_DIR = "platform"
    const val KONAN_DISTRIBUTION_COMMONIZED_LIBS_DIR = "commonized"
    const val KLIB_INTEROP_IR_PROVIDER_IDENTIFIER = "kotlin.native.cinterop"

    const val KONAN_DISTRIBUTION_SOURCES_DIR = "sources"
    const val KONAN_DISTRIBUTION_TOOLS_DIR = "tools"

    fun konanCommonLibraryPath(libraryName: String) =
        File(KONAN_DISTRIBUTION_KLIB_DIR, KONAN_DISTRIBUTION_COMMON_LIBS_DIR).resolve(libraryName)

    fun konanPlatformLibraryPath(libraryName: String, platform: String) =
        File(KONAN_DISTRIBUTION_KLIB_DIR, KONAN_DISTRIBUTION_PLATFORM_LIBS_DIR).resolve(platform).resolve(libraryName)

    // Used to provide unique names for platform libraries, according to KT-36720.
    const val KONAN_PLATFORM_LIBS_NAME_PREFIX = "org.jetbrains.kotlin.native.platform."
}