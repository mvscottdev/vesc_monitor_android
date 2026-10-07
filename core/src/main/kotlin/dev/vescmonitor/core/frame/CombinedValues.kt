package dev.vescmonitor.core.frame

import dev.vescmonitor.core.session.LiveValues
import kotlin.math.abs

/** One controller's values in the frame; null = unknown. */
data class ControllerValues(
    val tempMosC: Double? = null,
    val tempMotorC: Double? = null,
    val currentMotorA: Double? = null,
    val currentInA: Double? = null,
    val powerW: Double? = null,
    val duty: Double? = null,
    val erpm: Double? = null,
    val faultCode: Int = 0,
) {
    companion object {
        val EMPTY = ControllerValues()

        fun of(v: LiveValues): ControllerValues =
            ControllerValues(
                tempMosC = v.tempMosC.known(),
                tempMotorC = v.tempMotorC.known(),
                currentMotorA = v.currentMotorA.known(),
                currentInA = v.currentInA.known(),
                powerW = v.powerW.known(),
                duty = v.duty.known(),
                erpm = v.erpm.known(),
                faultCode = v.faultCode,
            )

        private fun Double.known(): Double? = takeUnless { isNaN() }
    }
}

/**
 * Values combined over the fresh controllers (rules in vesc-topology.md): currents and
 * power sum, duty is the largest magnitude (sign kept), temperatures the hottest
 * present sensor. Null when no fresh controller knows the value.
 */
data class CombinedValues(
    val currentInA: Double? = null,
    val currentMotorA: Double? = null,
    val powerW: Double? = null,
    val duty: Double? = null,
    val tempMosC: Double? = null,
    val tempMotorC: Double? = null,
) {
    companion object {
        val EMPTY = CombinedValues()

        fun of(fresh: List<ControllerValues>): CombinedValues =
            CombinedValues(
                currentInA = fresh.sumOrNull { it.currentInA },
                currentMotorA = fresh.sumOrNull { it.currentMotorA },
                powerW = fresh.sumOrNull { it.powerW },
                duty = fresh.mapNotNull { it.duty }.maxByOrNull { abs(it) },
                tempMosC = fresh.mapNotNull { it.tempMosC }.maxOrNull(),
                tempMotorC = fresh.mapNotNull { it.tempMotorC }.maxOrNull(),
            )

        private inline fun List<ControllerValues>.sumOrNull(pick: (ControllerValues) -> Double?): Double? {
            val known = mapNotNull(pick)
            return if (known.isEmpty()) null else known.sum()
        }
    }
}
