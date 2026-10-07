package dev.vescmonitor.core.protocol

/** Small replies with fixed layouts. */
object SimpleReplies {
    /** `[62, id…]`: bare CAN IDs; the local node is never in its own list. */
    fun parsePingCan(payload: ByteArray): List<Int> {
        if (payload.isEmpty() || (payload[0].toInt() and 0xFF) != CommandId.PING_CAN) throw ParseException("not PING_CAN")
        return (1 until payload.size).map { payload[it].toInt() and 0xFF }
    }

    /**
     * `[115, cut_start i32 /1e3, cut_end i32 /1e3]` in volts.
     * @source vedderb/bldc@4fd8279 comm/commands.c:1265 (GPL-3.0)
     */
    fun parseBatteryCut(payload: ByteArray): BatteryCut {
        if (payload.isEmpty() || (payload[0].toInt() and 0xFF) != CommandId.GET_BATTERY_CUT) {
            throw ParseException("not GET_BATTERY_CUT")
        }
        val r = ByteReader(payload, 1)
        return BatteryCut(r.i32() / 1e3, r.i32() / 1e3)
    }
}

data class BatteryCut(
    val startV: Double,
    val endV: Double,
)
