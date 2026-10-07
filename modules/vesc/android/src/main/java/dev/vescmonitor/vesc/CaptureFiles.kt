package dev.vescmonitor.vesc

import android.content.Context
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** JSONL captures in app storage (`files/captures`). */
internal class CaptureFiles(
    context: Context,
) {
    private val dir = File(context.filesDir, "captures")
    private var writer: BufferedWriter? = null
    private var current: File? = null

    fun list(): List<String> =
        dir
            .listFiles { f -> f.name.endsWith(".jsonl") }
            ?.sortedByDescending { it.lastModified() }
            ?.map { it.absolutePath }
            .orEmpty()

    /** Opens a new capture file and returns a line sink for it. */
    fun open(): (String) -> Unit {
        close()
        dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val f = File(dir, "capture-$stamp.jsonl")
        val w = f.bufferedWriter()
        writer = w
        current = f
        return { line ->
            w.write(line)
            w.newLine()
        }
    }

    /** Flushes and closes the open capture; returns its path. */
    fun close(): String? {
        val path = current?.absolutePath
        writer?.close()
        writer = null
        current = null
        return path
    }

    val recordingPath: String? get() = current?.absolutePath

    /** Reads a capture, only from our own capture folder. */
    fun read(path: String): String {
        val f = File(path).canonicalFile
        require(f.parentFile == dir.canonicalFile) { "not a capture file" }
        return f.readText()
    }
}
