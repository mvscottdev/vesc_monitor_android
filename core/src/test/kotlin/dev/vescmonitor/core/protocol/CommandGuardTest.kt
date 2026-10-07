package dev.vescmonitor.core.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class CommandGuardTest {
    private val allowed = setOf(0, 4, 34, 47, 50, 51, 62, 115)
    private val masked = setOf(50, 51)

    private fun validRequest(id: Int): ByteArray =
        when (id) {
            in masked -> byteArrayOf(id.toByte(), 0, 0, 0, 1)
            CommandId.FORWARD_CAN -> byteArrayOf(id.toByte(), 1, CommandId.GET_VALUES.toByte())
            else -> byteArrayOf(id.toByte())
        }

    private fun allowedAsPlain(p: ByteArray): Boolean =
        try {
            CommandGuard.check(p)
            true
        } catch (_: CommandDenied) {
            false
        }

    @Test
    fun `every command ID 0-255 is checked against the allow-list`() {
        for (id in 0..255) {
            val ok = allowedAsPlain(validRequest(id))
            assertEquals(id in allowed, ok, "command $id")
            assertEquals(id in allowed, CommandGuard.isAllowedId(id), "isAllowedId $id")
        }
    }

    @Test
    fun `every inner command ID 0-255 is checked through FORWARD_CAN`() {
        for (id in 0..255) {
            val inner = validRequest(id)
            val ok = allowedAsPlain(Requests.forwardCan(5, inner))
            assertEquals(id in allowed && id != CommandId.FORWARD_CAN, ok, "forwarded $id")
        }
    }

    @Test
    fun `write commands are denied with any payload length`() {
        for (id in listOf(5, 6, 7, 8, 9, 13, 16, 29, 30, 20, 86, 110, 14, 17)) {
            for (len in 1..8) {
                val p = ByteArray(len).also { it[0] = id.toByte() }
                assertFalse(allowedAsPlain(p), "command $id len $len")
            }
        }
    }

    @Test
    fun `payload length must match the command`() {
        assertThrows<CommandDenied> { CommandGuard.check(byteArrayOf(0, 0)) }
        assertThrows<CommandDenied> { CommandGuard.check(byteArrayOf(50, 0, 0, 1)) }
        assertThrows<CommandDenied> { CommandGuard.check(byteArrayOf(50, 0, 0, 0, 1, 0)) }
        assertThrows<CommandDenied> { CommandGuard.check(byteArrayOf(34, 1, 50, 0)) }
        assertThrows<CommandDenied> { CommandGuard.check(ByteArray(0)) }
    }

    @Test
    fun `forward rules`() {
        assertDoesNotThrow { CommandGuard.check(byteArrayOf(34, 254.toByte(), 0)) }
        assertThrows<CommandDenied> { CommandGuard.check(byteArrayOf(34, 255.toByte(), 0)) }
        assertThrows<CommandDenied> { CommandGuard.check(byteArrayOf(34, 1, 34, 2, 4)) }
        assertThrows<CommandDenied> { CommandGuard.check(byteArrayOf(34, 1)) }
    }

    @Test
    fun `a guarded frame can only be built from an allowed request`() {
        assertThrows<CommandDenied> { GuardedFrame.of(byteArrayOf(6, 0, 0, 0, 0)) }
        assertEquals(CommandId.GET_VALUES, GuardedFrame.of(byteArrayOf(34, 1, 4)).effectiveCommandId)
    }
}
