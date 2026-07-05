/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package iosUtils

import kotlinx.coroutines.delay
import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.amper.processes.ProcessLeak
import org.jetbrains.amper.processes.startLongLivedProcess
import org.jetbrains.amper.simctl.SimCtl
import org.jetbrains.amper.simctl.model.SimDevice
import org.jetbrains.amper.simctl.model.SimDeviceId
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.seconds

/**
 * Manages the iOS emulator lifecycle and provides helper methods
 */
internal object SimulatorManager {
    private val simulatorPath = Path("/Applications/Xcode.app/Contents/Developer/Applications/Simulator.app")
    /**
     * Launches the iOS Simulator with the specified device.
     */
    @ProcessLeak
    suspend fun launchSimulator(deviceName: String = "iPhone 16"): SimDeviceId {
        if (!simulatorPath.exists()) {
            error("Simulator app not found at $simulatorPath")
        }

        // The iOS Simulator that is launched must be the latest version available in the system because the Kotlin
        // Toolchain builds the app file targeting the highest available iOS version by default.
        val deviceId = getDeviceWithLatestIOS(deviceName)
        SimCtl.bootSimulator(deviceId, failIfAlreadyBooted = false)

        startLongLivedProcess(command = listOf("open", simulatorPath.pathString))

        repeat(3) { attempt ->
            delay(5.seconds)

            if (SimCtl.isSimulatorRunning(deviceId)) {
                println("Simulator is now running.")
                return deviceId
            }
            println("Attempt ${attempt + 1}: Simulator not yet initialized, retrying...")
        }

        error("Simulator failed to start or initialize after multiple attempts.")
    }

    private suspend fun getDeviceWithLatestIOS(deviceName: String): SimDeviceId {
        val devicesForLatestIOS = getDevicesForLatestIos()
        val device = devicesForLatestIOS.find { it.name == deviceName }
            ?: error("Device $deviceName not found for latest iOS. Available devices for this runtime:\n" +
                    devicesForLatestIOS.joinToString { "  - ${it.name} (${it.id})" })
        return device.id
    }

    private suspend fun getDevicesForLatestIos(): List<SimDevice> {
        val latestIOSRuntime = SimCtl.listRuntimes()
            .filter { it.platform == "iOS" }
            .maxByOrNull { ComparableVersion(it.version) }
            ?: error("No iOS runtime installed. Run 'xcodebuild -downloadPlatform iOS' to install runtimes.")
        println("Latest installed iOS runtime: ${latestIOSRuntime.version}")

        return SimCtl.listDevices()[latestIOSRuntime.id]
            ?: error("No device for the latest iOS (${latestIOSRuntime.name}) installed. " +
                    "Check out 'xcrun simctl create help' to create some simulators.")
    }
}
