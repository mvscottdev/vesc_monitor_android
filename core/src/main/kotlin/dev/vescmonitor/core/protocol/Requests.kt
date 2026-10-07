package dev.vescmonitor.core.protocol

/** Builders for the allowed requests. Each returns a guard-checked frame. */
object Requests {
    fun fwVersion(): ByteArray = byteArrayOf(CommandId.FW_VERSION.toByte())

    fun getValues(): ByteArray = byteArrayOf(CommandId.GET_VALUES.toByte())

    fun pingCan(): ByteArray = byteArrayOf(CommandId.PING_CAN.toByte())

    fun batteryCut(): ByteArray = byteArrayOf(CommandId.GET_BATTERY_CUT.toByte())

    fun getValuesSelective(mask: Int): ByteArray = withMask(CommandId.GET_VALUES_SELECTIVE, mask)

    fun getValuesSetupSelective(mask: Int): ByteArray = withMask(CommandId.GET_VALUES_SETUP_SELECTIVE, mask)

    fun forwardCan(
        canId: Int,
        inner: ByteArray,
    ): ByteArray = byteArrayOf(CommandId.FORWARD_CAN.toByte(), canId.toByte()) + inner

    /** Wraps [inner] in FORWARD_CAN when [canId] is not null, then guards and frames it. */
    fun frame(
        inner: ByteArray,
        canId: Int?,
    ): GuardedFrame = GuardedFrame.of(if (canId == null) inner else forwardCan(canId, inner))

    private fun withMask(
        id: Int,
        mask: Int,
    ): ByteArray =
        byteArrayOf(
            id.toByte(),
            (mask ushr 24).toByte(),
            (mask ushr 16).toByte(),
            (mask ushr 8).toByte(),
            mask.toByte(),
        )
}
