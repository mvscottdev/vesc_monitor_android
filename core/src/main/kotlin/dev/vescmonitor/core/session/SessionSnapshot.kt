package dev.vescmonitor.core.session

import dev.vescmonitor.core.link.LinkCounters
import dev.vescmonitor.core.link.Reason

enum class ConnectionState {
    IDLE,
    SCANNING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    LOST,
}

/** Low-rate session state for the UI (connection, topology, bench numbers). */
data class SessionSnapshot(
    val generation: Int,
    val state: ConnectionState,
    val reason: Reason? = null,
    val mtu: Int = 0,
    val firmware: String? = null,
    val hardware: String? = null,
    val controllers: List<ControllerLive> = emptyList(),
    val counters: LinkCounters? = null,
    val config: PollConfig = PollConfig(),
    /** Reconnect attempts since the link was lost (0 while connected). */
    val attempt: Int = 0,
    /** Setup step while connecting (null otherwise) and the motor controllers found so far. */
    val setupStep: SetupStep? = null,
    val setupFound: Int = 0,
    /** CAN nodes that are not motor controllers (listed, never polled). */
    val otherNodes: List<CanNode> = emptyList(),
) {
    fun toMap(nowMs: Long): Map<String, Any?> =
        mapOf(
            "generation" to generation,
            "state" to state.name.lowercase(),
            "reason" to reason?.message,
            "reasonCode" to reason?.name?.lowercase(),
            "attempt" to attempt,
            "setupStep" to setupStep?.name?.lowercase(),
            "setupFound" to setupFound,
            "otherNodes" to otherNodes.map { mapOf("canId" to it.canId, "kind" to it.kind, "hardware" to it.hwName) },
            "mtu" to mtu,
            "firmware" to firmware,
            "hardware" to hardware,
            "selective" to config.selective,
            "depth" to config.depth,
            "forwardLocal" to config.forwardLocal,
            "crcErrors" to (counters?.crcErrors ?: 0L),
            "unmatched" to (counters?.unmatched ?: 0L),
            "notifications" to (counters?.notifications ?: 0L),
            "controllers" to controllers.map { controllerMap(it, nowMs) },
        )

    private fun controllerMap(
        c: ControllerLive,
        nowMs: Long,
    ): Map<String, Any?> =
        mapOf(
            "controllerId" to c.controller.canId,
            "local" to c.controller.isLocal,
            "firmware" to c.controller.fw.label,
            "hardware" to c.controller.fw.hwName,
            "fresh" to c.isFresh(nowMs),
            "untestedFirmware" to c.untestedFirmware,
            "hz" to c.measuredHz,
            "requests" to c.bench.requests,
            "timeouts" to c.bench.timeouts,
            "writeMs" to c.bench.writeMs,
            "rttMs" to c.bench.rttMs,
            "notificationsPerReply" to c.bench.notificationsPerReply,
            "notificationSpanMs" to c.bench.notificationSpanMs,
            "batteryCutStartV" to c.batteryCut?.startV,
            "batteryCutEndV" to c.batteryCut?.endV,
        )
}
