/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import kotlin.assert
import kotlin.test.assertEquals
import kotlin.test.Test

class MultilineTest {

    @Test
    fun multline_comparison() {
        val prefixValue = "Hello:"
        val str = """
            This
             Is
              A
               Long
              Multiple
             Line
            String
        """.trimIndent()
        assert((prefixValue + str.substring(0, 8)).length == 0)
    }
}
