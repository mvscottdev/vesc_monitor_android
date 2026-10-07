package dev.vescmonitor.core.protocol

/** Thrown when a request is not on the read-only allow-list. */
class CommandDenied(
    message: String,
) : IllegalArgumentException(message)

/**
 * Read-only allow-list for every outgoing request. Everything not listed is denied,
 * including commands that write configuration, drive the motor or reboot the VESC.
 * Handlers of the allowed commands were checked to be read-only in
 * vedderb/bldc@4fd8279 comm/commands.c.
 */
object CommandGuard {
    /** Command ID to the exact request payload length (command byte included). */
    private val fixedLength =
        mapOf(
            CommandId.FW_VERSION to 1,
            CommandId.GET_VALUES to 1,
            CommandId.GET_VALUES_SETUP to 1,
            CommandId.GET_VALUES_SELECTIVE to 5,
            CommandId.GET_VALUES_SETUP_SELECTIVE to 5,
            CommandId.PING_CAN to 1,
            CommandId.GET_BATTERY_CUT to 1,
        )

    /** CAN ID 255 is the broadcast address in comm_can_send_buffer. */
    private const val CAN_BROADCAST = 255

    fun isAllowedId(id: Int): Boolean = id == CommandId.FORWARD_CAN || id in fixedLength

    /** Throws [CommandDenied] unless [payload] is an allowed read-only request. */
    fun check(payload: ByteArray) {
        if (payload.isEmpty()) throw CommandDenied("empty payload")
        val id = payload[0].toInt() and 0xFF
        if (id == CommandId.FORWARD_CAN) {
            checkForward(payload)
        } else {
            checkPlain(id, payload.size)
        }
    }

    private fun checkPlain(
        id: Int,
        size: Int,
    ) {
        val expected = fixedLength[id] ?: throw CommandDenied("command $id is not allow-listed")
        if (size != expected) throw CommandDenied("command $id needs $expected bytes, got $size")
    }

    private fun checkForward(payload: ByteArray) {
        if (payload.size < 3) throw CommandDenied("FORWARD_CAN needs a CAN ID and an inner command")
        val canId = payload[1].toInt() and 0xFF
        if (canId == CAN_BROADCAST) throw CommandDenied("FORWARD_CAN to broadcast ID is denied")
        val inner = payload[2].toInt() and 0xFF
        if (inner == CommandId.FORWARD_CAN) throw CommandDenied("nested FORWARD_CAN is denied")
        checkPlain(inner, payload.size - 2)
    }
}

/**
 * A framed request that has passed [CommandGuard]. The only way to build one is
 * [of], so a transport that accepts only this type cannot send anything else.
 */
class GuardedFrame private constructor(
    val payload: ByteArray,
    val bytes: ByteArray,
) {
    val commandId: Int get() = payload[0].toInt() and 0xFF

    /** Inner command for FORWARD_CAN, else the command itself. */
    val effectiveCommandId: Int
        get() = if (commandId == CommandId.FORWARD_CAN) payload[2].toInt() and 0xFF else commandId

    companion object {
        fun of(payload: ByteArray): GuardedFrame {
            CommandGuard.check(payload)
            val copy = payload.copyOf()
            return GuardedFrame(copy, PacketCodec.encode(copy))
        }
    }
}
