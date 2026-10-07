package dev.vescmonitor.core.link

import dev.vescmonitor.core.protocol.ParseException
import dev.vescmonitor.core.protocol.ValuesLayout
import dev.vescmonitor.core.protocol.ValuesParser

/**
 * Reply matchers. Replies echo the request's command ID only (forwarded replies come
 * back bare), so values replies are also matched by controller ID and echoed mask.
 */
object Matchers {
    fun command(id: Int): (ByteArray) -> Boolean = { it.isNotEmpty() && (it[0].toInt() and 0xFF) == id }

    /** Full values reply from [controllerId] (any controller when null). */
    fun fullValues(
        layout: ValuesLayout,
        controllerId: Int?,
    ): (ByteArray) -> Boolean =
        { payload ->
            accepts(payload, layout.fullCommand, controllerId) { ValuesParser.parseFull(layout, it) }
        }

    /** SELECTIVE reply whose echoed mask equals [mask], from [controllerId]. */
    fun selectiveValues(
        layout: ValuesLayout,
        mask: Int,
        controllerId: Int?,
    ): (ByteArray) -> Boolean =
        { payload ->
            ValuesParser.echoedMask(payload) == mask &&
                accepts(payload, layout.selectiveCommand, controllerId) { ValuesParser.parseSelective(layout, it) }
        }

    private inline fun accepts(
        payload: ByteArray,
        command: Int,
        controllerId: Int?,
        parse: (ByteArray) -> dev.vescmonitor.core.protocol.ValuesSample,
    ): Boolean {
        if (payload.isEmpty() || (payload[0].toInt() and 0xFF) != command) return false
        return try {
            val sample = parse(payload)
            controllerId == null || sample.controllerId == controllerId
        } catch (_: ParseException) {
            false
        }
    }
}
