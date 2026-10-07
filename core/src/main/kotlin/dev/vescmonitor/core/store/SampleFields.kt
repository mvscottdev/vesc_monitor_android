package dev.vescmonitor.core.store

import dev.vescmonitor.core.session.LiveValues
import kotlin.math.roundToLong

/**
 * Stored sample fields with stable IDs, integer-scaled exactly as on the wire
 * (bldc comm/commands.c GET_VALUES scales), so a stored value equals the reply value.
 * IDs are never reused; a new field gets a new ID.
 */
enum class SampleField(
    val id: Int,
    val scale: Double,
) {
    T_MS(0, 1.0),
    V_IN(1, 10.0),
    CURRENT_MOTOR(2, 100.0),
    CURRENT_IN(3, 100.0),
    ERPM(4, 1.0),
    DUTY(5, 1000.0),
    TEMP_FET(6, 10.0),
    TEMP_MOTOR(7, 10.0),
    FAULT(8, 1.0),

    /** The app's speed for this controller (m/s); missing while its ratio is not trusted. */
    SPEED(9, 100.0),

    /** The most severe alert active for this controller or the vehicle: [AlertCodes] code, 0 = none. */
    ALERT(10, 1.0),
    ;

    companion object {
        /** Stored for a value the controller did not report (e.g. a missing temperature sensor). */
        const val MISSING = Int.MIN_VALUE.toLong()

        fun scaled(
            value: Double,
            scale: Double,
        ): Long = if (value.isNaN()) MISSING else (value * scale).roundToLong()

        fun unscaled(
            raw: Long,
            scale: Double,
        ): Double = if (raw == MISSING) Double.NaN else raw / scale
    }
}

/** Counters stored once per chunk (last value of the second), scaled like the wire. */
enum class KeyframeField(
    val id: Int,
    val scale: Double,
) {
    AMP_HOURS(0, 1e4),
    AMP_HOURS_CHARGED(1, 1e4),
    WATT_HOURS(2, 1e4),
    WATT_HOURS_CHARGED(3, 1e4),
    TACHOMETER_ABS(4, 1.0),
}

/** One sample row as scaled integers, in [SampleField] order. */
fun LiveValues.toRow(
    tMs: Long,
    speedMps: Double = Double.NaN,
    alertCode: Int = 0,
): LongArray =
    longArrayOf(
        tMs,
        SampleField.scaled(voltageV, SampleField.V_IN.scale),
        SampleField.scaled(currentMotorA, SampleField.CURRENT_MOTOR.scale),
        SampleField.scaled(currentInA, SampleField.CURRENT_IN.scale),
        SampleField.scaled(erpm, SampleField.ERPM.scale),
        SampleField.scaled(duty, SampleField.DUTY.scale),
        SampleField.scaled(tempMosC, SampleField.TEMP_FET.scale),
        SampleField.scaled(tempMotorC, SampleField.TEMP_MOTOR.scale),
        faultCode.toLong(),
        SampleField.scaled(speedMps, SampleField.SPEED.scale),
        alertCode.toLong(),
    )

/** Counter values for the keyframe; unknown counters are left out. */
fun LiveValues.keyframe(): Map<Int, Long> =
    listOf(
        KeyframeField.AMP_HOURS to ampHours,
        KeyframeField.AMP_HOURS_CHARGED to ampHoursCharged,
        KeyframeField.WATT_HOURS to wattHours,
        KeyframeField.WATT_HOURS_CHARGED to wattHoursCharged,
        KeyframeField.TACHOMETER_ABS to tachometerAbs,
    ).filter { !it.second.isNaN() }
        .associate { (f, v) -> f.id to (v * f.scale).roundToLong() }
