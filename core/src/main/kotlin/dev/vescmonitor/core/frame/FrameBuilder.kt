package dev.vescmonitor.core.frame

import dev.vescmonitor.core.battery.BatteryDetect
import dev.vescmonitor.core.battery.CapacityLearner
import dev.vescmonitor.core.session.ControllerLive

/** Builds telemetry frames from the latest controller values; keeps session peaks and the latched fault. */
class FrameBuilder(
    private val generation: Int,
) {
    private var seq = 0L
    private var peaks = TelemetryFrame.Peaks()
    private var fault: TelemetryFrame.LatchedFault? = null
    private val derivations = Derivations()

    /** See [Derivations.confirmedPack]. */
    var confirmedPack: BatteryDetect.Candidate?
        get() = derivations.confirmedPack
        set(value) {
            derivations.confirmedPack = value
        }

    fun batteryInfo() = derivations.batteryInfo()

    /** See [Derivations.capacityState]. */
    var capacityState: CapacityLearner.State
        get() = derivations.capacityState
        set(value) {
            derivations.capacityState = value
        }

    /** See [Derivations.emptyCellOverrideV]. */
    var emptyCellOverrideV: Double?
        get() = derivations.emptyCellOverrideV
        set(value) {
            derivations.emptyCellOverrideV = value
        }

    /** See [Derivations.capacityOverrideAh]. */
    var capacityOverrideAh: Double?
        get() = derivations.capacityOverrideAh
        set(value) {
            derivations.capacityOverrideAh = value
        }

    /** See [Derivations.rangePrior]. */
    var rangePrior: Double?
        get() = derivations.rangePrior
        set(value) {
            derivations.rangePrior = value
        }

    val rideWhPerKm: Double? get() = derivations.rideWhPerKm

    fun build(
        controllers: List<ControllerLive>,
        nowMs: Long,
    ): TelemetryFrame {
        val entries = controllers.map { entry(it, nowMs) }
        val fresh = entries.filter { it.fresh }
        val freshControllers = controllers.filterIndexed { i, _ -> entries[i].fresh }
        val freshVoltages = fresh.mapNotNull { it.voltageV }
        val voltage = CombinedVoltage.of(freshVoltages)
        val values = CombinedValues.of(fresh.map { it.values })
        values.powerW?.let { p -> peaks = TelemetryFrame.Peaks(maxOf(peaks.powerW, p), minOf(peaks.regenW, p)) }
        if (fault == null) {
            fresh.firstOrNull { it.values.faultCode != 0 }?.let {
                fault =
                    TelemetryFrame.LatchedFault(
                        it.values.faultCode,
                        it.controllerId,
                    )
            }
        }
        val derived = derivations.update(controllers, freshControllers, voltage.voltageV, values.currentInA, nowMs)
        return TelemetryFrame(
            seq = ++seq,
            tMs = nowMs,
            generation = generation,
            combined =
                TelemetryFrame.Combined(voltage.voltageV, fresh.size, entries.size, voltage.divergent, values, peaks, fault, derived),
            vescs = entries,
        )
    }

    private fun entry(
        c: ControllerLive,
        nowMs: Long,
    ): TelemetryFrame.VescEntry {
        val fresh = c.isFresh(nowMs)
        return TelemetryFrame.VescEntry(
            controllerId = c.controller.canId,
            local = c.controller.isLocal,
            voltageV = c.voltageV.takeUnless(Double::isNaN),
            fresh = fresh,
            values = ControllerValues.of(c.values),
            ageMs = if (c.lastSampleMs == ControllerLive.NEVER) null else nowMs - c.lastSampleMs,
            speedMps = c.speedMps.takeUnless { it.isNaN() || !fresh },
        )
    }
}
