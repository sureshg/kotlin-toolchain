/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

fun argFileContents(): String = commonDefaultJvmArgs().joinToString("\n")

private fun commonDefaultJvmArgs(): List<String> = listOf(
    "-ea",
    // Necessary for ByteBuddy loading the coroutines agent
    "-XX:+EnableDynamicAgentLoading",
    // Smaller memory footprint because each object takes less space, less GC, more memory locality
    "-XX:+UseCompactObjectHeaders",
    // Needed in JRE 24+ because of some JNA usages
    "--enable-native-access=ALL-UNNAMED",
    // Needed in JRE 24+ because of OpenTelemetry (see https://github.com/open-telemetry/opentelemetry-java/issues/7219)
    "--sun-misc-unsafe-memory-access=allow",
)
