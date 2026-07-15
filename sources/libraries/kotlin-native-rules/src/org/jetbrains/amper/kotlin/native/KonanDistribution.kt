/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.kotlin.native

import java.net.URLEncoder
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

/**
 * A Kotlin/Native platform as known by the compiler and commonizer.
 */
@JvmInline
value class KonanPlatform(
    /**
     * The name of this platform for the compiler. It is also used in directories in the Kotlin/Native distribution.
     */
    val nameForCompiler: String,
)

/**
 * Represents a Kotlin Native distribution and provides type-safe accessors to its directory structure.
 */
// Conventions from:
// https://github.com/JetBrains/kotlin/blob/cf4e556a02d9c1cb67d19c2422fdae02c743c499/native/commonizer-api/src/org/jetbrains/kotlin/commonizer/KonanDistribution.kt
// https://github.com/JetBrains/kotlin/blob/cf4e556a02d9c1cb67d19c2422fdae02c743c499/native/utils/src/org/jetbrains/kotlin/konan/library/NativeLibraryConstants.kt#L12
data class KonanDistribution(val homeDir: Path, val kotlinVersion: String) {

    /**
     * The directory containing the JARs of the Kotlin Native compiler.
     */
    val konanLibDir: Path
        get() = homeDir / "konan" / "lib"

    private val klibDir: Path
        get() = homeDir / "klib"

    /**
     * The directory containing the versioned, commonized native distribution.
     */
    val commonizedRoot: Path by lazy {
        klibDir / "commonized" / URLEncoder.encode(kotlinVersion, Charsets.UTF_8.name())
    }

    val platformLibsDir: Path
        get() = klibDir / "platform"

    val stdlibDir: Path
        get() = klibDir / "common" / "stdlib"

    val commonizerCache: NativeDistributionCommonizerCache by lazy {
        NativeDistributionCommonizerCache(commonizedRoot)
    }

    fun platformLibs(platform: KonanPlatform): List<Path> = (platformLibsDir / platform.nameForCompiler).listLibraries()

    fun commonizedKlibs(target: CommonizerTarget): List<Path> = (commonizedRoot / target.dirName).listLibraries()
}

private fun Path.listLibraries(): List<Path> {
    if (!exists()) {
        return []
    }
    return listDirectoryEntries().filter { it.isDirectory() || it.extension == "klib" }
}

/**
 * Returns the libraries that should be used as `-dependency-libraries` when commonizing klibs for Cinterop.
 */
fun KonanDistribution.dependencyLibrariesForCommonization(target: CommonizerTarget): List<Path> = buildList {
    // Commonized platform libs
    addAll((commonizedRoot / target.dirName).listLibraries())
    // Leaf ("un-commonized") platform libs are required as well.
    target.platforms.forEach { platform ->
        addAll(platformLibs(platform))
    }
}

/**
 * Returns the libraries that should be used as `-library` when compiling metadata for a shared native fragment.
 */
fun KonanDistribution.librariesForMetadataCompilation(target: CommonizerTarget): List<Path> = buildList {
    // Starting with 2.2.20,
    // the kotlin-stdlib metadata JSON descriptor doesn't map common sourceSet to nativeApiElements variant.
    // This way dependency on kotlin-stdlib is not resolved if at least one target platform is native.
    // Fortunately, the commonMain metadata of kotlin-stdlib is shipped as a prebuilt klib with K/Native compiler.
    // todo (AB) common sourceSet of kotlin-stdlib is still resolved for Native+non-native set of platforms
    //  (check that this is expected, maybe it shouldn't and kotlin-stdlib metadata should be taken from platform commonizer output in this case)
    add(stdlibDir)
    addAll(commonizedKlibs(target))
}
