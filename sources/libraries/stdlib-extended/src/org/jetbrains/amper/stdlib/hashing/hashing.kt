/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.stdlib.hashing

import java.security.MessageDigest

/**
 * Computes the hash of these bytes using the given [algorithm].
 *
 * The given [algorithm] must be a standard algorithm name as described in the
 * [Java Security Standard Algorithm Names Specification](https://docs.oracle.com/en/java/javase/25/docs/specs/security/standard-names.html),
 * in the _MessageDigest Algorithms_ section.
 */
fun ByteArray.hash(algorithm: String): ByteArray = MessageDigest.getInstance(algorithm).digest(this)

/**
 * Returns the SHA-256 hash of these bytes.
 */
fun ByteArray.sha256(): ByteArray = hash("SHA-256")

/**
 * Returns the SHA-256 hash of the UTF-8 representation of this string.
 */
fun String.sha256(): ByteArray = encodeToByteArray().sha256()

/**
 * Returns the SHA-256 hash of these bytes, as a hexadecimal string.
 */
fun ByteArray.sha256String(): String = sha256().toHexString()

/**
 * Returns the SHA-256 hash of the UTF-8 representation of this string, as a hexadecimal string.
 */
fun String.sha256String(): String = sha256().toHexString()
