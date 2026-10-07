package dev.vescmonitor.core.protocol

/** Wire type of one telemetry field. Values are big-endian scaled integers. */
enum class Width(
    val bytes: Int,
    val count: Int = 1,
) {
    U8(1),
    I16(2),
    I32(4),
    U32(4),
    I16X3(6, 3),
}

/** One field of a GET_VALUES style reply; [bit] is its SELECTIVE mask bit. */
class FieldDef(
    val bit: Int,
    val name: String,
    val width: Width,
    val scale: Double,
) {
    /** First slot in the sample array; wide fields use [Width.count] slots. */
    var slot: Int = 0
        internal set
}

/**
 * Table-driven reply layout, walked in bit order (full reply = every bit).
 * Layouts are for firmware 5.03 and newer; longer replies are prefix-parsed.
 */
class ValuesLayout(
    val fullCommand: Int,
    val selectiveCommand: Int,
    val fields: List<FieldDef>,
) {
    val slotCount: Int
    private val byBit = arrayOfNulls<FieldDef>(32)

    init {
        var slot = 0
        for (f in fields) {
            f.slot = slot
            slot += f.width.count
            byBit[f.bit] = f
        }
        slotCount = slot
    }

    /** Payload length of the full reply, command byte included. */
    val fullPayloadLength: Int = 1 + fields.sumOf { it.width.bytes }

    fun field(bit: Int): FieldDef? = byBit.getOrNull(bit)

    fun field(name: String): FieldDef = fields.first { it.name == name }

    fun fieldOrNull(name: String): FieldDef? = fields.firstOrNull { it.name == name }

    companion object {
        /** @source vedderb/bldc@4fd8279 comm/commands.c:384-483 (GPL-3.0) */
        val VALUES =
            ValuesLayout(
                CommandId.GET_VALUES,
                CommandId.GET_VALUES_SELECTIVE,
                listOf(
                    FieldDef(0, "tempMosC", Width.I16, 10.0),
                    FieldDef(1, "tempMotorC", Width.I16, 10.0),
                    FieldDef(2, "currentMotorA", Width.I32, 100.0),
                    FieldDef(3, "currentInA", Width.I32, 100.0),
                    FieldDef(4, "idA", Width.I32, 100.0),
                    FieldDef(5, "iqA", Width.I32, 100.0),
                    FieldDef(6, "duty", Width.I16, 1000.0),
                    FieldDef(7, "erpm", Width.I32, 1.0),
                    FieldDef(8, "voltageV", Width.I16, 10.0),
                    FieldDef(9, "ampHours", Width.I32, 1e4),
                    FieldDef(10, "ampHoursCharged", Width.I32, 1e4),
                    FieldDef(11, "wattHours", Width.I32, 1e4),
                    FieldDef(12, "wattHoursCharged", Width.I32, 1e4),
                    FieldDef(13, "tachometer", Width.I32, 1.0),
                    FieldDef(14, "tachometerAbs", Width.I32, 1.0),
                    FieldDef(15, "faultCode", Width.U8, 1.0),
                    FieldDef(16, "pidPos", Width.I32, 1e6),
                    FieldDef(17, "controllerId", Width.U8, 1.0),
                    FieldDef(18, "tempMos123C", Width.I16X3, 10.0),
                    FieldDef(19, "vdV", Width.I32, 1000.0),
                    FieldDef(20, "vqV", Width.I32, 1000.0),
                    FieldDef(21, "status", Width.U8, 1.0),
                ),
            )

        /** @source vedderb/bldc@4fd8279 comm/commands.c:797-890 (GPL-3.0) */
        val SETUP =
            ValuesLayout(
                CommandId.GET_VALUES_SETUP,
                CommandId.GET_VALUES_SETUP_SELECTIVE,
                listOf(
                    FieldDef(0, "tempMosC", Width.I16, 10.0),
                    FieldDef(1, "tempMotorC", Width.I16, 10.0),
                    FieldDef(2, "currentMotorTotalA", Width.I32, 100.0),
                    FieldDef(3, "currentInTotalA", Width.I32, 100.0),
                    FieldDef(4, "duty", Width.I16, 1000.0),
                    FieldDef(5, "erpm", Width.I32, 1.0),
                    FieldDef(6, "speedMps", Width.I32, 1000.0),
                    FieldDef(7, "voltageV", Width.I16, 10.0),
                    FieldDef(8, "batteryLevel", Width.I16, 1000.0),
                    FieldDef(9, "ampHoursTotal", Width.I32, 1e4),
                    FieldDef(10, "ampHoursChargedTotal", Width.I32, 1e4),
                    FieldDef(11, "wattHoursTotal", Width.I32, 1e4),
                    FieldDef(12, "wattHoursChargedTotal", Width.I32, 1e4),
                    FieldDef(13, "distanceM", Width.I32, 1000.0),
                    FieldDef(14, "distanceAbsM", Width.I32, 1000.0),
                    FieldDef(15, "pidPos", Width.I32, 1e6),
                    FieldDef(16, "faultCode", Width.U8, 1.0),
                    FieldDef(17, "controllerId", Width.U8, 1.0),
                    FieldDef(18, "numVescs", Width.U8, 1.0),
                    FieldDef(19, "wattHoursLeft", Width.I32, 1000.0),
                    FieldDef(20, "odometerM", Width.U32, 1.0),
                    FieldDef(21, "uptimeMs", Width.U32, 1.0),
                ),
            )

        /** Fast mask: temps, currents, duty, ERPM, v_in, fault, controller ID. */
        const val MASK_FAST = 0x000281CF

        /** Slow (~1 Hz) mask: Ah/Wh/tachometer counters plus controller ID. */
        const val MASK_SLOW = 0x00027E00

        /** SETUP mask (~1 Hz): ERPM, the VESC's own speed, battery level, absolute distance, controller ID. */
        const val MASK_SETUP = 0x00024160
        const val BIT_CONTROLLER_ID = 17
    }
}
