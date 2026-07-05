/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.simctl.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.amper.serialization.paths.SerializablePath

@Serializable
internal data class SimRuntimes(
    val runtimes: List<SimRuntime>,
)

/**
 * Example: com.apple.CoreSimulator.SimRuntime.iOS-18-1
 */
@Serializable
@JvmInline
value class SimRuntimeId(val value: String)

@Serializable
data class SimRuntime(
    @SerialName("identifier")
    val id: SimRuntimeId,
    /**
     * Example: `iOS 18.1`
     */
    val name: String,
    /**
     * Example: `18.1`
     */
    val version: String,
    /**
     * This version may contain letters, and most likely cannot be compared alphabetically.
     *
     * Example: `22B81`
     */
    @SerialName("buildversion")
    val buildVersion: String,
    /**
     * See [SimulatorPlatforms] for some known values.
     *
     * Example: `iOS`
     */
    val platform: String,
    /**
     * Example: `/Library/Developer/CoreSimulator/Volumes/iOS_22B81/Library/Developer/CoreSimulator/Profiles/Runtimes/iOS 18.1.simruntime`
     */
    val bundlePath: SerializablePath,
    /**
     * Example: `/Library/Developer/CoreSimulator/Volumes/iOS_22B81/Library/Developer/CoreSimulator/Profiles/Runtimes/iOS 18.1.simruntime/Contents/Resources/RuntimeRoot`
     */
    val runtimeRoot: SerializablePath,
    val isInternal: Boolean,
    val isAvailable: Boolean,
    val supportedDeviceTypes: List<SimDeviceTypeShort> = emptyList(),
) {
    override fun toString(): String = name
}

@Serializable
data class SimDeviceTypeShort(
    @SerialName("identifier")
    val id: SimDeviceTypeId,
    val name: String, // iPhone Xs
    val productFamily: String, // iPhone
    val bundlePath: SerializablePath, // /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneOS.platform/Library/Developer/CoreSimulator/Profiles/DeviceTypes/iPhone Xs.simdevicetype
) {
    override fun toString(): String = name
}

/**
 * Some known values used as [SimRuntime.platform].
 */
// Not an enum because we can't be sure we know all values.
object SimulatorPlatforms {
    const val IOS = "iOS"
    const val WatchOS = "watchOS"
    const val TvOS = "tvOS"
    /**
     * The platform name for VisionOS.
     */
    const val XrOS = "xrOS"
}
