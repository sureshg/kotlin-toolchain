/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test

fun main() {
    val env = System.getenv()

    val lines = buildList {
        add("compose.reload.devToolsEnabled=" + System.getProperty("compose.reload.devToolsEnabled"))
    }

    lines.forEach { println(it) }
}
