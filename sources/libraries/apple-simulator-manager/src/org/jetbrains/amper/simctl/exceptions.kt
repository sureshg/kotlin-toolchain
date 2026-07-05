/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.simctl

import org.jetbrains.amper.simctl.model.SimDeviceId
import java.nio.file.Path

open class SimCtlException(override val message: String) : Exception(message)

class SimulatorAlreadyBootedException(
    val deviceId: SimDeviceId,
) : SimCtlException("The device with id '$deviceId' is already in 'Booted' state")

class SimulatorBootException(
    val deviceId: SimDeviceId,
    stderr: String,
) : SimCtlException("Failed to boot device with ID '$deviceId'.\n$stderr")

class AppInstallationException(
    val appFile: Path,
    val device: TargetDevice,
    stderr: String,
) : SimCtlException("Failed to install app from $appFile on ${device.monikerForExceptionMessage}.\n$stderr")

class AppUninstallationException(
    val appBundleId: String,
    val device: TargetDevice,
    stderr: String,
) : SimCtlException("Failed to uninstall app with ID '$appBundleId' on ${device.monikerForExceptionMessage}.\n$stderr")

class AppLaunchException(
    val appBundleId: String,
    val device: TargetDevice,
    /**
     * The mixed stdout/stderr output.
     */
    output: String,
) : SimCtlException("Failed to launch app with ID '$appBundleId' on ${device.monikerForExceptionMessage}. The output contains errors:\n$output")

private val TargetDevice.monikerForExceptionMessage: String get() = when (this) {
    TargetDevice.AnyBootedDevice -> "some booted device"
    is TargetDevice.SpecificDevice -> "device with ID '$id'"
}