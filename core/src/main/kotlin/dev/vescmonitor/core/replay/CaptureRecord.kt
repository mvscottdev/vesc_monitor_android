package dev.vescmonitor.core.replay

import java.util.Base64

/**
 * One line of a JSONL capture. `t` is ms since the capture started.
 * `{"type":"meta",…}`, `{"type":"session-state","state":…}`,
 * `{"type":"ble-chunk","direction":"tx"|"rx","base64":…}`.
 */
sealed interface CaptureRecord {
    val t: Long

    data class Meta(
        override val t: Long,
        val fields: Map<String, String>,
    ) : CaptureRecord

    data class SessionState(
        override val t: Long,
        val state: String,
    ) : CaptureRecord

    class BleChunk(
        override val t: Long,
        val direction: Direction,
        val bytes: ByteArray,
    ) : CaptureRecord

    enum class Direction(
        val wire: String,
    ) {
        TX("tx"),
        RX("rx"),
    }

    companion object {
        const val FORMAT_VERSION = 1

        fun encode(r: CaptureRecord): String =
            when (r) {
                is Meta -> {
                    FlatJson.write(mapOf("type" to "meta", "t" to r.t) + r.fields)
                }

                is SessionState -> {
                    FlatJson.write(mapOf("type" to "session-state", "t" to r.t, "state" to r.state))
                }

                is BleChunk -> {
                    FlatJson.write(
                        mapOf(
                            "type" to "ble-chunk",
                            "t" to r.t,
                            "direction" to r.direction.wire,
                            "base64" to Base64.getEncoder().encodeToString(r.bytes),
                        ),
                    )
                }
            }

        /** Parses one line; unknown record types return null so newer captures still load. */
        fun decode(line: String): CaptureRecord? {
            val m = FlatJson.parse(line)
            val t = (m["t"] as? Double)?.toLong() ?: 0L
            return when (m["type"]) {
                "meta" -> {
                    Meta(t, m.filterKeys { it != "type" && it != "t" }.mapValues { it.value?.toString() ?: "" })
                }

                "session-state" -> {
                    SessionState(t, m["state"] as? String ?: "")
                }

                "ble-chunk" -> {
                    val dir = if (m["direction"] == "tx") Direction.TX else Direction.RX
                    BleChunk(t, dir, Base64.getDecoder().decode(m["base64"] as? String ?: ""))
                }

                else -> {
                    null
                }
            }
        }

        fun decodeAll(text: String): List<CaptureRecord> =
            text
                .lineSequence()
                .filter { it.isNotBlank() }
                .mapNotNull { decode(it) }
                .toList()
    }
}
