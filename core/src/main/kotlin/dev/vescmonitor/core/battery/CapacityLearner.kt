package dev.vescmonitor.core.battery

/**
 * Learns pack capacity (Ah) from the controllers' Ah counters against the change in
 * resting SoC (t4-algorithms findings §7.6): `capAh = ΣAh / Σ(ΔSoC / 100)` over intervals
 * between settled rests that span at least [MIN_INTERVAL_SOC] points of discharge. The
 * sums persist across sessions ([state]); anchors are per session because the counters are.
 */
class CapacityLearner(
    state: State = State(),
) {
    data class State(
        val sumAh: Double = 0.0,
        val sumSoc: Double = 0.0,
        val intervals: Int = 0,
    )

    var state: State = state
        private set

    private var anchorSoc = Double.NaN
    private var anchorAh = Double.NaN

    /** Learned capacity, null while still learning. */
    val capacityAh: Double?
        get() =
            state
                .takeIf { it.intervals >= MIN_INTERVALS && it.sumSoc >= MIN_INTERVAL_SOC }
                ?.let { it.sumAh / (it.sumSoc / 100) }

    /**
     * A settled rest at absolute [socPct], with the session's net Ah ([ahNet], discharge
     * positive). True when the learned sums changed.
     */
    fun onRest(
        socPct: Double,
        ahNet: Double,
    ): Boolean {
        if (socPct.isNaN() || ahNet.isNaN()) return false
        if (anchorSoc.isNaN()) return anchor(socPct, ahNet)
        val dSoc = anchorSoc - socPct
        val dAh = ahNet - anchorAh
        // Charged, or counters that disagree with the voltage: start a new interval.
        if (dSoc < -CHARGE_SOC || dAh < 0) return anchor(socPct, ahNet)
        if (dSoc < MIN_INTERVAL_SOC) return false
        val ratio = dAh / (dSoc / 100)
        anchor(socPct, ahNet)
        if (ratio !in PLAUSIBLE_AH) return false
        state = State(state.sumAh + dAh, state.sumSoc + dSoc, state.intervals + 1)
        return true
    }

    private fun anchor(
        soc: Double,
        ah: Double,
    ): Boolean {
        anchorSoc = soc
        anchorAh = ah
        return false
    }

    companion object {
        const val MIN_INTERVAL_SOC = 30.0
        const val MIN_INTERVALS = 2
        const val CHARGE_SOC = 2.0
        private val PLAUSIBLE_AH = 0.5..500.0
    }
}
