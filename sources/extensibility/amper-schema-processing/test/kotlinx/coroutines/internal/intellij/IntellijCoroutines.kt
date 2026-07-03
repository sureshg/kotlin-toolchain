/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("unused") // used by Analysis API

package kotlinx.coroutines.internal.intellij

import kotlin.coroutines.CoroutineContext

/*
This class is here to fill in the gap we created by excluding the IntelliJ coroutines fork dependency
(com.intellij.platform:kotlinx-coroutines-core-jvm). The only function used by the Analysis API for what we do is
IntellijCoroutines.currentThreadCoroutineContext(), hence this stub.

IMPORTANT: Setting the 'ide.can.use.coroutines.fork' system property has no effect at the moment, because the IJ
platform classes that use coroutines are embedded in the compiler in an older version that didn't know about this
property. See these issues to track the bump of the platform in the compiler:
https://youtrack.jetbrains.com/projects/KT/issues/KT-81457/Update-IntelliJ-SDK-dependency-to-253.
https://youtrack.jetbrains.com/issue/KT-82657/Update-IntelliJ-SDK-dependency-to-261.9214
*/
object IntellijCoroutines {

    fun currentThreadCoroutineContext(): CoroutineContext? = null
}
