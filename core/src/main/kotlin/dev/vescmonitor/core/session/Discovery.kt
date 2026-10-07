package dev.vescmonitor.core.session

import dev.vescmonitor.core.link.Link
import dev.vescmonitor.core.link.Matchers
import dev.vescmonitor.core.link.Reason
import dev.vescmonitor.core.protocol.CommandId
import dev.vescmonitor.core.protocol.FwVersion
import dev.vescmonitor.core.protocol.ParseException
import dev.vescmonitor.core.protocol.Requests
import dev.vescmonitor.core.protocol.SimpleReplies
import dev.vescmonitor.core.protocol.ValuesLayout
import dev.vescmonitor.core.protocol.ValuesParser

sealed interface DiscoveryResult {
    data class Found(
        val topology: Topology,
    ) : DiscoveryResult

    data class Failed(
        val reason: Reason,
    ) : DiscoveryResult
}

/** Where connection setup is, for the connect screen's steps. */
enum class SetupStep { FIRMWARE, CONTROLLERS }

/** Setup progress: [firmware]/[hardware] of the bridge-side VESC once read (null = unchanged). */
data class SetupProgress(
    val step: SetupStep,
    val firmware: String?,
    val hardware: String?,
    val found: Int,
)

/**
 * Finds every motor controller behind the bridge, once per connection:
 * local FW_VERSION → local controller ID → PING_CAN → FW_VERSION per CAN ID
 * (keep motor controllers) → probe `id + 1` if PING did not list it → confirm each
 * with one GET_VALUES. PING_CAN only lists peers, never the local node.
 * @source vedderb/bldc@4fd8279 comm/commands.c:2355-2370 (GPL-3.0)
 */
class Discovery(
    private val link: Link,
    private val onProgress: (SetupProgress) -> Unit = {},
) {
    private val controllers = ArrayList<Controller>()
    private val unsupported = ArrayList<Int>()
    private val others = ArrayList<CanNode>()

    suspend fun run(): DiscoveryResult {
        val localFw = fwVersion(null, FW_PROBE_TIMEOUT_MS, retries = 0) ?: return DiscoveryResult.Failed(Reason.VESC_NOT_ANSWERING)
        if (!localFw.isSupported) return DiscoveryResult.Failed(Reason.FIRMWARE_TOO_OLD)
        onProgress(SetupProgress(SetupStep.CONTROLLERS, localFw.label, localFw.hwName, 0))
        var localId: Int? = null
        if (localFw.isMotorController) {
            localId = controllerId(null) ?: return DiscoveryResult.Failed(Reason.VESC_NOT_ANSWERING)
            controllers += Controller(localId, isLocal = true, fw = localFw)
            onProgress(SetupProgress(SetupStep.CONTROLLERS, localFw.label, localFw.hwName, controllers.size))
        }
        val pinged = pingCan()
        for (id in pinged.distinct()) {
            if (id != localId) addCanController(id, retries = 1)
        }
        if (localId != null) {
            // Single-MCU dual hardware answers as id + 1; it may be missing from PING.
            val next = (localId + 1) and 0xFF
            if (next !in pinged && next != CAN_BROADCAST) addCanController(next, retries = 0)
        }
        if (controllers.isEmpty()) {
            val reason = if (unsupported.isNotEmpty()) Reason.FIRMWARE_TOO_OLD else Reason.NO_CONTROLLERS
            return DiscoveryResult.Failed(reason)
        }
        return DiscoveryResult.Found(Topology(localFw, controllers.toList(), unsupported.toList(), others.toList()))
    }

    private suspend fun addCanController(
        id: Int,
        retries: Int,
    ) {
        val fw = fwVersion(id, REQUEST_TIMEOUT_MS, retries) ?: return
        if (!fw.isMotorController) {
            others += CanNode.of(id, fw)
            return
        }
        if (!fw.isSupported) {
            unsupported += id
            return
        }
        if (controllerId(id) != id) return
        controllers += Controller(id, isLocal = false, fw = fw)
        onProgress(SetupProgress(SetupStep.CONTROLLERS, null, null, controllers.size))
    }

    private suspend fun fwVersion(
        canId: Int?,
        timeoutMs: Long,
        retries: Int,
    ): FwVersion? {
        val reply =
            link.requestWithRetry(Requests.frame(Requests.fwVersion(), canId), timeoutMs, retries, Matchers.command(CommandId.FW_VERSION))
                ?: return null
        return try {
            FwVersion.parse(reply.payload)
        } catch (_: ParseException) {
            null
        }
    }

    /** Controller ID from one full GET_VALUES; for a CAN node it must equal [canId]. */
    private suspend fun controllerId(canId: Int?): Int? {
        val layout = ValuesLayout.VALUES
        val reply =
            link.requestWithRetry(Requests.frame(Requests.getValues(), canId), REQUEST_TIMEOUT_MS, 1, Matchers.fullValues(layout, canId))
                ?: return null
        return ValuesParser.parseFull(layout, reply.payload).controllerId
    }

    private suspend fun pingCan(): List<Int> {
        val reply =
            link.request(Requests.frame(Requests.pingCan(), null), PING_TIMEOUT_MS, Matchers.command(CommandId.PING_CAN))
                ?: return emptyList()
        return SimpleReplies.parsePingCan(reply.payload)
    }

    companion object {
        const val FW_PROBE_TIMEOUT_MS = 2_500L
        const val REQUEST_TIMEOUT_MS = 1_000L

        /** vesc_tool waits 5 s; the firmware scan takes up to ~2.5 s. */
        const val PING_TIMEOUT_MS = 5_000L
        private const val CAN_BROADCAST = 255
    }
}
