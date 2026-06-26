/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.context

import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isDirectory

data class AmperProjectRoot(val path: Path) {
    init {
        require(path.isDirectory()) {
            "Project root is not a directory: $path"
        }
        require(path.isAbsolute) {
            "Project root is not an absolute path: $path"
        }
    }
}

data class AmperBuildOutputRoot(val path: Path) {
    init {
        require(path.isDirectory()) {
            "Build output root is not a directory: $path"
        }
        require(path.isAbsolute) {
            "Build output root is not an absolute path: $path"
        }
    }
}

data class AmperProjectLogsRoot(val path: Path) {
    init {
        require(path.isDirectory()) {
            "Logs root is not a directory: $path"
        }
        require(path.isAbsolute) {
            "Logs root is not an absolute path: $path"
        }
    }
}

data class AmperBuildLogsRoot(val path: Path) {
    init {
        require(path.isDirectory()) {
            "Build logs root is not a directory: $path"
        }
        require(path.isAbsolute) {
            "Build logs root is not an absolute path: $path"
        }
    }

    val telemetryPath = path / "telemetry"
}

data class AmperProjectTempRoot(val path: Path) {
    init {
        require(path.isDirectory()) {
            "Temp root is not a directory: $path"
        }
        require(path.isAbsolute) {
            "Temp root is not an absolute path: $path"
        }
    }
}

data class AndroidHomeRoot(val path: Path) {
    init {
        require(path.isDirectory()) {
            "Android home is not a directory: $path"
        }
        require(path.isAbsolute) {
            "Android home is not an absolute path: $path"
        }
    }
}
