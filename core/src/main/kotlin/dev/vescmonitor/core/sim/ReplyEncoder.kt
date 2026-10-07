package dev.vescmonitor.core.sim

import dev.vescmonitor.core.protocol.CommandId
import dev.vescmonitor.core.protocol.FwVersion
import dev.vescmonitor.core.protocol.ValuesLayout
import dev.vescmonitor.core.protocol.Width
import java.io.ByteArrayOutputStream
import kotlin.math.roundToLong

/** Encodes replies the way the firmware does (big-endian scaled integers). */
object ReplyEncoder {
    fun fwVersion(node: SimNode): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(CommandId.FW_VERSION)
        out.write(node.major)
        out.write(node.minor)
        out.write(node.hwName.toByteArray())
        out.write(0)
        out.write(ByteArray(FwVersion.UUID_LENGTH))
        out.write(0) // pairing_done
        out.write(0) // test_version
        out.write(node.hwType)
        repeat(5) { out.write(0) } // config num, phase filters, qml hw/app, nrf flags
        if (node.major >= 6) {
            out.write(0) // empty fw_name
            be(out, 0, 4) // hw_crc
        }
        return out.toByteArray()
    }

    /** Full reply when [mask] is null, else a SELECTIVE reply echoing [mask]. */
    fun values(
        layout: ValuesLayout,
        mask: Int?,
        v: Map<String, Double>,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(if (mask == null) layout.fullCommand else layout.selectiveCommand)
        if (mask != null) be(out, mask.toLong(), 4)
        for (f in layout.fields) {
            if (mask != null && mask and (1 shl f.bit) == 0) continue
            val raw = ((v[f.name] ?: 0.0) * f.scale).roundToLong()
            repeat(f.width.count) { be(out, raw, if (f.width == Width.I16X3) 2 else f.width.bytes) }
        }
        return out.toByteArray()
    }

    fun batteryCut(
        startV: Double,
        endV: Double,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(CommandId.GET_BATTERY_CUT)
        be(out, (startV * 1e3).roundToLong(), 4)
        be(out, (endV * 1e3).roundToLong(), 4)
        return out.toByteArray()
    }

    private fun be(
        out: ByteArrayOutputStream,
        value: Long,
        bytes: Int,
    ) {
        for (i in bytes - 1 downTo 0) out.write(((value ushr (8 * i)) and 0xFF).toInt())
    }
}
