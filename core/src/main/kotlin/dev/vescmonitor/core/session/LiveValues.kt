package dev.vescmonitor.core.session

import dev.vescmonitor.core.protocol.ValuesLayout
import dev.vescmonitor.core.protocol.ValuesSample

/**
 * Latest fast values of one controller in SI units. A field the last reply did not
 * carry keeps its previous value; a field never seen is NaN (shown as unknown, never 0).
 */
class LiveValues {
    var voltageV = Double.NaN
        private set
    var tempMosC = Double.NaN
        private set
    var tempMotorC = Double.NaN
        private set
    var currentMotorA = Double.NaN
        private set
    var currentInA = Double.NaN
        private set
    var duty = Double.NaN
        private set
    var erpm = Double.NaN
        private set
    var faultCode = 0
        private set

    // Slow-mask counters: RAM counters since the controller booted (reset on reboot).
    var ampHours = Double.NaN
        private set
    var ampHoursCharged = Double.NaN
        private set
    var wattHours = Double.NaN
        private set
    var wattHoursCharged = Double.NaN
        private set
    var tachometerAbs = Double.NaN
        private set

    // SETUP values (~1 Hz): the VESC's own speed from its configured wheel, gear and poles.
    var vescSpeedMps = Double.NaN
        private set
    var vescSpeedErpm = Double.NaN
        private set
    var batteryLevel = Double.NaN
        private set
    var distanceAbsM = Double.NaN
        private set

    /** Battery-side power from this controller's own voltage and input current. */
    val powerW: Double get() = voltageV * currentInA

    fun apply(sample: ValuesSample) {
        if (sample.layout === ValuesLayout.SETUP) {
            applySetup(sample)
            return
        }
        sample.opt("voltageV")?.let { voltageV = it }
        sample.opt("tempMosC")?.let { tempMosC = plausibleTemp(it) }
        sample.opt("tempMotorC")?.let { tempMotorC = plausibleTemp(it) }
        sample.opt("currentMotorA")?.let { currentMotorA = it }
        sample.opt("currentInA")?.let { currentInA = it }
        sample.opt("duty")?.let { duty = it }
        sample.opt("erpm")?.let { erpm = it }
        sample.opt("faultCode")?.let { faultCode = it.toInt() }
        sample.opt("ampHours")?.let { ampHours = it }
        sample.opt("ampHoursCharged")?.let { ampHoursCharged = it }
        sample.opt("wattHours")?.let { wattHours = it }
        sample.opt("wattHoursCharged")?.let { wattHoursCharged = it }
        sample.opt("tachometerAbs")?.let { tachometerAbs = it }
    }

    private fun applySetup(sample: ValuesSample) {
        val speed = sample.opt("speedMps")
        val erpm = sample.opt("erpm")
        if (speed != null && erpm != null) {
            vescSpeedMps = speed
            vescSpeedErpm = erpm
        }
        sample.opt("batteryLevel")?.let { batteryLevel = it }
        sample.opt("distanceAbsM")?.let { distanceAbsM = it }
    }

    companion object {
        /**
         * bldc reports -100 degC for a missing or broken sensor (mc_interface.c, NTC
         * readout); anything outside the plausible range is treated as absent.
         */
        const val TEMP_MIN_C = -40.0
        const val TEMP_MAX_C = 200.0

        fun plausibleTemp(c: Double): Double = if (c in TEMP_MIN_C..TEMP_MAX_C) c else Double.NaN

        private fun ValuesSample.opt(name: String): Double? {
            val f = layout.fieldOrNull(name) ?: return null
            return if (has(f.bit)) values[f.slot] else null
        }
    }
}
