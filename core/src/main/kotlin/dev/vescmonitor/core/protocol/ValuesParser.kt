package dev.vescmonitor.core.protocol

/** One parsed values reply. Absent fields are NaN; [mask] says which bits were read. */
class ValuesSample(
    val layout: ValuesLayout,
    val mask: Int,
    val values: DoubleArray,
    /** Reply was longer than the known layout (newer firmware); the known prefix was parsed. */
    val untestedFirmware: Boolean,
) {
    operator fun get(name: String): Double = values[layout.field(name).slot]

    fun has(bit: Int): Boolean = mask and (1 shl bit) != 0

    val controllerId: Int?
        get() = if (has(ValuesLayout.BIT_CONTROLLER_ID)) values[layout.field(ValuesLayout.BIT_CONTROLLER_ID)!!.slot].toInt() else null
}

/**
 * Parses full and SELECTIVE values replies by walking the mask in bit order with a
 * remaining-bytes guard per field, as vesc_tool does.
 * @source vedderb/vesc_tool@dc53c65 commands.cpp:189-282 (GPL-3.0)
 */
object ValuesParser {
    /** Full reply `[cmd, fields…]`. A field cut short rejects the whole reply. */
    fun parseFull(
        layout: ValuesLayout,
        payload: ByteArray,
    ): ValuesSample {
        expectCommand(payload, layout.fullCommand)
        val reader = ByteReader(payload, 1)
        val values = DoubleArray(layout.slotCount) { Double.NaN }
        var mask = 0
        for (f in layout.fields) {
            read(reader, f, values)
            mask = mask or (1 shl f.bit)
        }
        return ValuesSample(layout, mask, values, reader.remaining > 0)
    }

    /** SELECTIVE reply `[cmd, mask u32, fields for set bits…]`. */
    fun parseSelective(
        layout: ValuesLayout,
        payload: ByteArray,
    ): ValuesSample {
        expectCommand(payload, layout.selectiveCommand)
        val reader = ByteReader(payload, 1)
        val mask = reader.i32()
        val values = DoubleArray(layout.slotCount) { Double.NaN }
        for (bit in 0 until 32) {
            if (mask and (1 shl bit) == 0) continue
            val f = layout.field(bit) ?: throw ParseException("unknown mask bit $bit")
            read(reader, f, values)
        }
        return ValuesSample(layout, mask, values, reader.remaining > 0)
    }

    /** Echoed mask of a SELECTIVE reply, or null if too short. */
    fun echoedMask(payload: ByteArray): Int? = if (payload.size < 5) null else ByteReader(payload, 1).i32()

    private fun expectCommand(
        payload: ByteArray,
        id: Int,
    ) {
        if (payload.isEmpty() || (payload[0].toInt() and 0xFF) != id) throw ParseException("expected command $id")
    }

    private fun read(
        reader: ByteReader,
        f: FieldDef,
        out: DoubleArray,
    ) {
        when (f.width) {
            Width.U8 -> out[f.slot] = reader.u8() / f.scale
            Width.I16 -> out[f.slot] = reader.i16() / f.scale
            Width.I32 -> out[f.slot] = reader.i32() / f.scale
            Width.U32 -> out[f.slot] = reader.u32() / f.scale
            Width.I16X3 -> repeat(3) { out[f.slot + it] = reader.i16() / f.scale }
        }
    }
}
