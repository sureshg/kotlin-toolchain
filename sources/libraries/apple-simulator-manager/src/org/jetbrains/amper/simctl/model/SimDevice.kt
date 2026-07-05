/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.simctl.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.amper.serialization.paths.SerializablePath

@Serializable
internal data class SimDevices(val devices: Map<SimRuntimeId, List<SimDevice>>)

@Serializable
@JvmInline
value class SimDeviceId(val value: String)

@Serializable
data class SimDevice(
    @SerialName("udid")
    val id: SimDeviceId,
    @SerialName("deviceTypeIdentifier")
    val deviceTypeId: SimDeviceTypeId,
    val name: String,
    val isAvailable: Boolean,
    val state: String? = null,
    val dataPath: SerializablePath? = null,
    val dataPathSize: Long? = null,
    val logPath: SerializablePath? = null,
    val lastBootedAt: String? = null,
) {
    override fun toString(): String = "$name ($id)"
}
