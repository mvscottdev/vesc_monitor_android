package dev.vescmonitor.core.session

import dev.vescmonitor.core.protocol.FwVersion

/** One motor controller on the link. The local one is wired to the bridge and polled directly. */
data class Controller(
    val canId: Int,
    val isLocal: Boolean,
    val fw: FwVersion,
)

/** Controllers found on a link. Kept in memory for the session only. */
data class Topology(
    val bridgeNode: FwVersion,
    val controllers: List<Controller>,
    /** CAN IDs of motor controllers skipped because their firmware is too old. */
    val unsupportedIds: List<Int> = emptyList(),
    /** Other CAN nodes (BMS, custom modules) that answered; listed, never polled. */
    val otherNodes: List<CanNode> = emptyList(),
)

/** A CAN node that is not a motor controller. */
data class CanNode(
    val canId: Int,
    val kind: String,
    val hwName: String,
) {
    companion object {
        fun of(
            canId: Int,
            fw: FwVersion,
        ): CanNode =
            CanNode(
                canId,
                when (fw.hwType) {
                    FwVersion.HW_TYPE_BMS -> "bms"
                    FwVersion.HW_TYPE_CUSTOM_MODULE -> "module"
                    else -> "other"
                },
                fw.hwName,
            )
    }
}
