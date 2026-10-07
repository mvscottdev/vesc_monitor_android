package dev.vescmonitor.core.sim

import dev.vescmonitor.core.protocol.CommandId
import dev.vescmonitor.core.protocol.ValuesLayout

/**
 * Answers allowed requests like a VESC behind a bridge: [local] is wired to the bridge,
 * [peers] sit on CAN and are reached through FORWARD_CAN. Absent IDs never answer.
 */
class FakeVesc(
    val local: SimNode,
    val peers: List<SimNode> = emptyList(),
    /** False simulates a CAN mode where PING_CAN lists nothing. */
    val pingListsPeers: Boolean = true,
    val batteryCutV: Pair<Double, Double> = 40.0 to 36.0,
) {
    /** Requests seen per effective command ID (FORWARD_CAN counted by its inner command). */
    val requestsByCommand = IntArray(256)

    /** SELECTIVE masks seen, in order. */
    val selectiveMasks = ArrayList<Int>()

    /** Reply payload for a request payload, or null when the node would stay silent. */
    fun respond(
        request: ByteArray,
        nowMs: Long,
    ): ByteArray? {
        val id = request[0].toInt() and 0xFF
        val effective = if (id == CommandId.FORWARD_CAN) request[2].toInt() and 0xFF else id
        requestsByCommand[effective]++
        if (effective == CommandId.GET_VALUES_SELECTIVE) selectiveMasks += mask(request.copyOfRange(request.size - 5, request.size))
        if (id == CommandId.FORWARD_CAN) {
            val target = peers.firstOrNull { it.canId == (request[1].toInt() and 0xFF) } ?: return null
            return answer(target, request.copyOfRange(2, request.size), nowMs)
        }
        if (id == CommandId.PING_CAN) {
            val ids = if (pingListsPeers) peers.map { it.canId.toByte() } else emptyList()
            return byteArrayOf(CommandId.PING_CAN.toByte()) + ids.toByteArray()
        }
        return answer(local, request, nowMs)
    }

    private fun answer(
        node: SimNode,
        request: ByteArray,
        nowMs: Long,
    ): ByteArray? {
        if (!node.online) return null
        val motor = node.hwType == 0
        return when (request[0].toInt() and 0xFF) {
            CommandId.FW_VERSION -> {
                ReplyEncoder.fwVersion(node)
            }

            CommandId.GET_VALUES -> {
                if (motor) ReplyEncoder.values(ValuesLayout.VALUES, null, node.values(nowMs)) else null
            }

            CommandId.GET_VALUES_SELECTIVE -> {
                if (motor) {
                    ReplyEncoder.values(
                        ValuesLayout.VALUES,
                        mask(request),
                        node.values(nowMs),
                    )
                } else {
                    null
                }
            }

            CommandId.GET_VALUES_SETUP_SELECTIVE -> {
                if (motor) ReplyEncoder.values(ValuesLayout.SETUP, mask(request), node.values(nowMs)) else null
            }

            CommandId.GET_BATTERY_CUT -> {
                if (motor) ReplyEncoder.batteryCut(batteryCutV.first, batteryCutV.second) else null
            }

            else -> {
                null
            }
        }
    }

    private fun mask(r: ByteArray): Int =
        ((r[1].toInt() and 0xFF) shl 24) or ((r[2].toInt() and 0xFF) shl 16) or ((r[3].toInt() and 0xFF) shl 8) or
            (r[4].toInt() and 0xFF)

    companion object {
        /** A synthetic vehicle with [count] controllers (1 or 2), IDs chosen to not collide with probes. */
        fun synthetic(count: Int): FakeVesc {
            val local = SimNode(10, voltageV = SimNode.sineVoltage(48.0, 2.0, 10_000L, 0.0), ride = SimRide())
            val peers =
                if (count >= 2) {
                    listOf(SimNode(20, voltageV = SimNode.sineVoltage(48.2, 2.0, 7_000L, 1.0), ride = SimRide(gain = 1.08, offsetMs = 150)))
                } else {
                    emptyList()
                }
            return FakeVesc(local, peers)
        }
    }
}
