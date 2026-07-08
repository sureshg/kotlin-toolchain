/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.system.info.SystemInfo
import org.jetbrains.amper.util.runnablePlatforms

/**
 * The leaf [org.jetbrains.amper.frontend.Platform]s that can be "run" from the current host.
 *
 * That is, code compiled to the returned [org.jetbrains.amper.frontend.Platform]s can be run in any of the following ways:
 * * on this host directly
 * * in an emulator that runs on this host
 * * on a suitable physical device connected to this host
 *
 * Note: the actual presence of connected physical devices is not checked here.
 * Platforms that correspond to connected devices are returned as part of the set based on whether it would be
 * _possible_ to run them, should a suitable device be connected.
 */
val runnablePlatforms: Set<Platform> by lazy { SystemInfo.CurrentHost.runnablePlatforms() }

fun formatModules(modules: Collection<AmperModule>) =
    modules.map { it.userReadableName }.sorted().joinToString(" ")

fun formatPlatforms(platforms: Collection<Platform>) =
    platforms.map { it.pretty }.sorted().joinToString(" ")

fun formatModulePlatforms(moduleToRun: AmperModule): String {
    return formatPlatforms(moduleToRun.leafPlatforms)
}

fun Model.getModuleByName(moduleName: String) = getModuleByNameOrNull(moduleName) ?: userReadableError(
    "Unable to resolve module by name '$moduleName'.\n\n" +
            "Available modules: ${formatModules(modules)}"
)

fun Model.resolveModuleToRun(
    moduleName: String?,
    platform: Platform?,
    isComposeHotReload: Boolean,
    hasDeviceId: Boolean,
): AmperModule {
    return when {
        moduleName != null -> getModuleByName(moduleName)
        // The special case for single module projects is only here to have different error messages
        modules.size == 1 -> modules.single()
        else -> findSingleRunnableAppModule(
            platform = platform,
            isComposeHotReload = isComposeHotReload,
            hasDeviceId = hasDeviceId,
        )
    }
}

fun Model.findSingleRunnableAppModule(
    platform: Platform?,
    isComposeHotReload: Boolean,
    hasDeviceId: Boolean,
): AmperModule {
    val appModules = modules
        .filter { it.type.isApplication() }
        .ifEmpty {
            userReadableError("There are no application modules in the project, nothing to run")
        }
    val appModulesMatchingCommand = appModules
        .filter { !isComposeHotReload || it.type == ProductType.JVM_APP }
        .ifEmpty {
            userReadableError(
                "There are no JVM application modules in the project, and only those support Compose Hot Reload.\n\n" +
                        "Available application modules and their platforms:\n" +
                        appModules.joinToString("\n") { "  ${it.userReadableName}: ${formatPlatforms(it.leafPlatforms)}" },
            )
        }
        .filter { !hasDeviceId || it.supportsDeviceIdSelection() }
        .ifEmpty {
            userReadableError(
                "There are no Android or iOS application modules in the project, and only those support " +
                        "selecting a device or emulator explicitly. Please remove the '--device-id' option.\n\n" +
                        "Available application modules and their type:\n" +
                        appModules.joinToString("\n") { "  ${it.userReadableName}: ${it.type.value}" },
            )
        }
        // we check platforms after so that the error messages make sense (they are easier to write this way)
        .filter { platform == null || platform in it.leafPlatforms }
        .ifEmpty {
            userReadableError {
                // Note that platform can't be null here (otherwise we would not have filtered out the last modules)
                append("There are no application modules in the project that support the '${platform?.pretty}' platform")
                // The double "and" might be awkward if both --device-id and --compose-hot-reload-mode are passed,
                // but it's technically correct, and will realistically never happen, so let's not complicate.
                if (hasDeviceId) {
                    append(" and device selection with --device-id")
                }
                if (isComposeHotReload) {
                    append(" and Compose Hot Reload")
                }
                appendLine(".")
                appendLine()
                appendLine("Available application modules and their platforms:")
                appModules.sortedBy { it.userReadableName }.forEach { module ->
                    appendLine("  ${module.userReadableName}: ${formatPlatforms(module.leafPlatforms)}")
                }
            }
        }

    // We don't check this earlier because if there are no modules with the given platform at all,
    // it's a more important error because the command doesn't work for this project on any host.
    if (platform != null && platform !in runnablePlatforms) {
        userReadableError("Code compiled for the '${platform.pretty}' platform cannot be run from the current host")
    }
    val runnableCandidatesIgnoringDeviceSelection = appModulesMatchingCommand
        .filter { it.canBeRunFromCurrentHost() }
        .ifEmpty {
            userReadableError(
                "There are no application modules in the project that can be run from the current host.\n\n" +
                        "Runnable platforms on this host: ${formatPlatforms(runnablePlatforms)}",
            )
        }
    val runnableCandidates = runnableCandidatesIgnoringDeviceSelection
        .filter { hasDeviceId || !it.requiresPhysicalDeviceToRunFromCurrentHost() }
        .ifEmpty {
            if (runnableCandidatesIgnoringDeviceSelection.size > 1) {
                userReadableError(
                    "All runnable application modules in the project require selecting a physical device with " +
                            "'--device-id'."
                )
            } else {
                userReadableError(
                    "The only runnable application module " +
                            "'${runnableCandidatesIgnoringDeviceSelection.single().userReadableName}' requires " +
                            "selecting a physical device with '--device-id'."
                )
            }
        }
    if (runnableCandidates.size > 1) {
        val canBeSelectedUsingPlatform = runnableCandidates
            .flatMap { it.leafPlatforms intersect runnablePlatforms }
            .let { allPlatformEntries ->
                // Check if there are no such two app modules that share a leaf platform
                allPlatformEntries.distinct().size == allPlatformEntries.size
            }

        userReadableError {
            append("There are several matching application modules in the project. Please specify one with the ")
            if (canBeSelectedUsingPlatform && platform == null) {
                append("'--platform' or '--module'")
            } else {
                append("'--module'")
            }
            appendLine(" option.")
            appendLine()
            append("Runnable application modules")
            if (platform != null) {
                append(" supporting the '${platform.pretty}' platform")
            }
            appendLine(":")
            runnableCandidates.sortedBy { it.userReadableName }.forEach { module ->
                append("  ${module.userReadableName}")
                if (canBeSelectedUsingPlatform) {
                    append(": ${formatPlatforms(module.leafPlatforms)}")
                }
                appendLine()
            }
        }
    }
    return runnableCandidates.single()
}

fun AmperModule.canBeRunFromCurrentHost(): Boolean = leafPlatforms.any { it in runnablePlatforms }

fun AmperModule.supportsDeviceIdSelection(): Boolean = leafPlatforms.any { it.supportsDeviceSelection }

fun AmperModule.requiresPhysicalDeviceToRunFromCurrentHost(): Boolean =
    (leafPlatforms intersect runnablePlatforms).all { it.requiresPhysicalDeviceSelection }

val Platform.supportsDeviceSelection: Boolean
    get() = this == Platform.ANDROID || isDescendantOf(Platform.IOS)

val Platform.requiresPhysicalDeviceSelection: Boolean
    get() = isAppleDevice
