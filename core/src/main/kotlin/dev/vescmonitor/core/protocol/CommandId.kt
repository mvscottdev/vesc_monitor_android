package dev.vescmonitor.core.protocol

/**
 * COMM_PACKET_ID values used by this app. The enum is append-only in firmware.
 * @source vedderb/bldc@4fd8279 datatypes.h:964-1153 (GPL-3.0)
 */
object CommandId {
    const val FW_VERSION = 0
    const val GET_VALUES = 4
    const val FORWARD_CAN = 34
    const val GET_VALUES_SETUP = 47
    const val GET_VALUES_SELECTIVE = 50
    const val GET_VALUES_SETUP_SELECTIVE = 51
    const val PING_CAN = 62
    const val GET_BATTERY_CUT = 115
}
