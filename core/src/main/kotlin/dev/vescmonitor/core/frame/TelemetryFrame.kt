package dev.vescmonitor.core.frame

import dev.vescmonitor.core.battery.Chemistry

/**
 * Snapshot sent to the UI at up to 30 Hz. Contract version 1; the golden sample is
 * `contract/telemetry-frame.v1.json`. New optional fields may be added without a
 * version bump; renaming or removing one needs version 2. Numbers only (flags are 0/1),
 * SI units, null = unknown.
 */
data class TelemetryFrame(
    val seq: Long,
    val tMs: Long,
    val generation: Int,
    val combined: Combined,
    val vescs: List<VescEntry>,
) {
    val v: Int get() = VERSION

    data class Combined(
        val voltageV: Double?,
        val fresh: Int,
        val total: Int,
        val divergent: Boolean,
        val values: CombinedValues = CombinedValues.EMPTY,
        val peaks: Peaks = Peaks(),
        val fault: LatchedFault? = null,
        val derived: Derived = Derived.EMPTY,
    ) {
        /** Some controllers are stale: the combined value covers only k of N. */
        val partial: Boolean get() = fresh < total
    }

    data class VescEntry(
        val controllerId: Int,
        val local: Boolean,
        val voltageV: Double?,
        val fresh: Boolean,
        val values: ControllerValues = ControllerValues.EMPTY,
        /** Milliseconds since this controller's last sample; null before the first. */
        val ageMs: Long? = null,
        /** Speed from this controller's learned ratio; null when unknown or stale. */
        val speedMps: Double? = null,
    )

    /** Session peaks for the power bar tick (drive ≥ 0, regen ≤ 0). */
    data class Peaks(
        val powerW: Double = 0.0,
        val regenW: Double = 0.0,
    )

    /** First non-zero fault of the session, kept until the next connect. */
    data class LatchedFault(
        val code: Int,
        val controllerId: Int,
    )

    fun toMap(): Map<String, Any?> =
        mapOf(
            "v" to v,
            "seq" to seq,
            "tMs" to tMs,
            "generation" to generation,
            "combined" to combinedMap(),
            "vescs" to vescs.map(::vescMap),
        )

    private fun combinedMap(): Map<String, Any?> =
        mapOf(
            "voltageV" to combined.voltageV,
            "fresh" to combined.fresh,
            "total" to combined.total,
            "partial" to combined.partial.flag(),
            "divergent" to combined.divergent.flag(),
            "currentInA" to combined.values.currentInA,
            "currentMotorA" to combined.values.currentMotorA,
            "powerW" to combined.values.powerW,
            "duty" to combined.values.duty,
            "tempMosC" to combined.values.tempMosC,
            "tempMotorC" to combined.values.tempMotorC,
            "peakPowerW" to combined.peaks.powerW,
            "peakRegenW" to combined.peaks.regenW,
            "faultCode" to (combined.fault?.code ?: 0),
            "faultControllerId" to combined.fault?.controllerId,
        ) + derivedMap(combined.derived)

    private fun derivedMap(d: Derived): Map<String, Any?> =
        mapOf(
            "speedMps" to d.speedMps,
            "speedSource" to (if (d.speedValid) SPEED_VESC else SPEED_UNKNOWN),
            "speedWheelMissing" to d.wheelMissing.flag(),
            "slip" to d.slip.flag(),
            "maxSpeedMps" to d.maxSpeedMps,
            "tripM" to d.tripM,
            "whUsed" to d.whUsed,
            "whPerKm" to d.whPerKm,
            "socPct" to d.socPct,
            "cellV" to d.cellV,
            "cells" to d.cells,
            "chemistry" to d.chemistry?.let { CHEMISTRY_CODE.getValue(it) },
            "socConfidence" to d.socConfidence?.let { it.ordinal + 1 },
            "batteryConfirm" to d.batteryConfirm.flag(),
            "sagV" to d.sagV,
            "minVoltageV" to d.minVoltageV,
            "capacityAh" to d.capacityAh,
            "rangeKm" to d.rangeKm,
        )

    private fun vescMap(e: VescEntry): Map<String, Any?> =
        mapOf(
            "controllerId" to e.controllerId,
            "local" to e.local.flag(),
            "voltageV" to e.voltageV,
            "fresh" to e.fresh.flag(),
            "ageMs" to e.ageMs,
            "tempMosC" to e.values.tempMosC,
            "tempMotorC" to e.values.tempMotorC,
            "currentMotorA" to e.values.currentMotorA,
            "currentInA" to e.values.currentInA,
            "powerW" to e.values.powerW,
            "duty" to e.values.duty,
            "erpm" to e.values.erpm,
            "faultCode" to e.values.faultCode,
            "speedMps" to e.speedMps,
        )

    companion object {
        const val VERSION = 1

        /** `speedSource` codes. */
        const val SPEED_UNKNOWN = 0
        const val SPEED_VESC = 1

        /** `chemistry` codes; `socConfidence` is 1 low, 2 medium, 3 high. */
        val CHEMISTRY_CODE = mapOf(Chemistry.NMC to 1, Chemistry.LFP to 2)

        private fun Boolean.flag(): Int = if (this) 1 else 0
    }
}
