package dev.vescmonitor.core.protocol

/** Parsed FW_VERSION reply. Optional trailing fields are null when absent. */
data class FwVersion(
    val major: Int,
    val minor: Int,
    val hwName: String,
    val uuid: ByteArray,
    val hwType: Int,
    val testVersion: Int?,
    val fwName: String?,
    /** Reply had bytes after the last known field. */
    val untestedFirmware: Boolean,
) {
    val isSupported: Boolean get() = major > MIN_MAJOR || (major == MIN_MAJOR && minor >= MIN_MINOR)
    val isMotorController: Boolean get() = hwType == HW_TYPE_VESC
    val label: String get() = "$major.${minor.toString().padStart(2, '0')}"

    override fun equals(other: Any?): Boolean =
        other is FwVersion && major == other.major && minor == other.minor && hwName == other.hwName &&
            uuid.contentEquals(other.uuid) && hwType == other.hwType && fwName == other.fwName

    override fun hashCode(): Int = 31 * (31 * major + minor) + hwName.hashCode()

    companion object {
        const val MIN_MAJOR = 5
        const val MIN_MINOR = 3
        const val HW_TYPE_VESC = 0
        const val HW_TYPE_BMS = 1
        const val HW_TYPE_CUSTOM_MODULE = 2
        const val UUID_LENGTH = 12

        /**
         * `major i8, minor i8, hw_name cstring, uuid 12 B, pairing_done, test_version, hw_type,
         * custom_config_num, has_phase_filters, qml_hw, qml_app, nrf_flags, fw_name cstring, hw_crc u32`.
         * Each optional field is read only while bytes remain.
         * @source vedderb/vesc_tool@dc53c65 commands.cpp:109-166 (GPL-3.0)
         */
        fun parse(payload: ByteArray): FwVersion {
            if (payload.isEmpty() || payload[0].toInt() != CommandId.FW_VERSION) throw ParseException("not FW_VERSION")
            val r = ByteReader(payload, 1)
            val major = r.i8()
            val minor = r.i8()
            val hwName = r.cString()
            val uuid = r.bytes(UUID_LENGTH)
            if (r.remaining > 0) r.u8() // pairing_done
            val testVersion = if (r.remaining > 0) r.u8() else null
            // Before 5.02 the field is absent: such a node is a motor controller.
            val hwType = if (r.remaining > 0) r.u8() else HW_TYPE_VESC
            repeat(OPTIONAL_FLAG_BYTES) { if (r.remaining > 0) r.u8() }
            val fwName = if (r.remaining > 0) r.cString() else null
            if (r.remaining >= 4) r.u32() // hw_crc
            return FwVersion(major, minor, hwName, uuid, hwType, testVersion, fwName, r.remaining > 0)
        }

        /** custom_config_num, has_phase_filters, qml_hw, qml_app, nrf_flags. */
        private const val OPTIONAL_FLAG_BYTES = 5
    }
}
