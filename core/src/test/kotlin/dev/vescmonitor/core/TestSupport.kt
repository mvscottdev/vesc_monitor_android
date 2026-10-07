package dev.vescmonitor.core

fun hex(s: String): ByteArray =
    s
        .split(' ')
        .filter { it.isNotBlank() }
        .map { it.toInt(16).toByte() }
        .toByteArray()

fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }

fun bytes(vararg v: Int): ByteArray = ByteArray(v.size) { v[it].toByte() }
