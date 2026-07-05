/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.simctl.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SimDeviceTypes(
    val devicetypes: List<SimDeviceType>,
)

/**
 * Examples: `com.apple.CoreSimulator.SimDeviceType.iPhone-XS`, `com.apple.CoreSimulator.SimDeviceType.iPhone-6s`
 */
@Serializable
@JvmInline
value class SimDeviceTypeId(val value: String)

@Serializable
data class SimDeviceType(
    @SerialName("identifier")
    val id: SimDeviceTypeId,
    /**
     * Example: `iPhone 6s`
     */
    val name: String,
    /**
     * See [ProductFamilies] for some known values.
     *
     * Example: `iPhone`
     */
    val productFamily: String,
    /**
     * Example: `/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneOS.platform/Library/Developer/CoreSimulator/Profiles/DeviceTypes/iPhone 6s.simdevicetype`
     */
    val bundlePath: String,
    /**
     * Example: `iPhone8,1`
     */
    val modelIdentifier: String,
    /**
     * Example: `1048575`
     */
    val maxRuntimeVersion: Long,
    /**
     * Example: `15.255.255`
     */
    val maxRuntimeVersionString: String,
    /**
     * Example: `589824`
     */
    val minRuntimeVersion: Long,
    /**
     * Example: `9.0.0`
     */
    val minRuntimeVersionString: String,
) {
    override fun toString(): String = name
}

/**
 * Some known values used as [SimDeviceType.productFamily].
 */
// Not an enum because we can't be sure we know all values.
object ProductFamilies {
    const val AppleTV = "Apple TV"
    const val AppleVision = "Apple Vision"
    const val AppleWatch = "Apple Watch"
    const val IPad = "iPad"
    const val IPhone = "iPhone"
}
