package dev.vescmonitor.core.alert

import dev.vescmonitor.core.alert.Severity.CRITICAL
import dev.vescmonitor.core.alert.Severity.INFO
import dev.vescmonitor.core.alert.Severity.WARNING

/**
 * Default alert rules (alerts.md). Limits that live in the VESC config use bldc's
 * factory defaults, because the app does not read mcconf yet.
 */
object AlertCatalog {
    /** bldc defaults: temperature derating start / end (degC) and the acceleration derating factor. */
    const val TEMP_START_C = 85.0
    const val TEMP_END_C = 100.0
    const val ACCEL_DEC = 0.15

    /** bldc default max duty. */
    const val MAX_DUTY = 0.95

    private val tempApproach = TEMP_START_C + ACCEL_DEC * (25 - TEMP_START_C)
    private val tempCritical = TEMP_START_C + 0.5 * (TEMP_END_C - TEMP_START_C)

    val FET_TEMP =
        AlertRule(
            "fet_temp",
            listOf(Level(tempApproach, INFO, 2_000), Level(TEMP_START_C, WARNING, 1_000), Level(tempCritical, CRITICAL, 500)),
            hysteresis = 3.0,
            cooldownMs = 60_000,
        )
    val MOTOR_TEMP =
        AlertRule(
            "motor_temp",
            listOf(Level(tempApproach, INFO, 2_000), Level(TEMP_START_C, WARNING, 2_000), Level(tempCritical, CRITICAL, 2_000)),
            hysteresis = 3.0,
            cooldownMs = 60_000,
        )

    /** degC per second over the rate window. */
    val FET_TEMP_RISE = AlertRule("fet_temp_rise", listOf(Level(0.7, WARNING, 0)), 0.2, 120_000)
    val MOTOR_TEMP_RISE = AlertRule("motor_temp_rise", listOf(Level(0.3, WARNING, 0)), 0.1, 120_000)
    const val FET_RISE_WINDOW_MS = 5_000L
    const val MOTOR_RISE_WINDOW_MS = 20_000L

    val LOW_BATTERY =
        AlertRule("low_battery", listOf(Level(20.0, WARNING, 5_000), Level(10.0, CRITICAL, 5_000)), 3.0, 300_000, above = false)

    fun batteryCut(
        startV: Double,
        endV: Double,
    ) = AlertRule(
        "battery_cut",
        listOf(Level(startV + 1.0, WARNING, 1_000), Level((startV + endV) / 2, CRITICAL, 1_000)),
        0.5,
        60_000,
        above = false,
    )

    /** V/cell: NMC/NCA and LFP. */
    val LOW_CELL_NMC =
        AlertRule("low_cell", listOf(Level(3.0, WARNING, 1_000), Level(2.8, CRITICAL, 1_000)), 0.05, 60_000, above = false)
    val LOW_CELL_LFP =
        AlertRule("low_cell", listOf(Level(2.7, WARNING, 1_000), Level(2.5, CRITICAL, 1_000)), 0.05, 60_000, above = false)

    /** Percent of the rest reference. */
    val SAG = AlertRule("sag", listOf(Level(20.0, WARNING, 1_000), Level(30.0, CRITICAL, 1_000)), 3.0, 60_000)

    val DIVERGE = AlertRule("diverge", listOf(Level(1.5, WARNING, 5_000)), 0.5, 300_000)

    val DUTY =
        AlertRule("duty", listOf(Level(MAX_DUTY - 0.05, WARNING, 1_000), Level(MAX_DUTY - 0.01, CRITICAL, 1_000)), 0.03, 20_000)

    /** Milliseconds since the controller's last sample. */
    val LINK_STALE = AlertRule("link_stale", listOf(Level(1_000.0, WARNING, 0), Level(3_000.0, CRITICAL, 0)), 0.0, 30_000)

    /** Slip events in the last minute. */
    val SLIP = AlertRule("slip", listOf(Level(3.0, INFO, 0)), 0.0, 60_000)
    const val SLIP_WINDOW_MS = 60_000L

    /** Rules whose thresholds the rider may change (derived and per-chemistry rules are not). */
    val EDITABLE: List<AlertRule> by lazy {
        listOf(FET_TEMP, MOTOR_TEMP, FET_TEMP_RISE, MOTOR_TEMP_RISE, LOW_BATTERY, SAG, DIVERGE, DUTY, LINK_STALE, SLIP)
    }

    /** Fault severity by code group (alerts.md); unknown codes are critical. */
    fun faultSeverity(code: Int): Severity =
        when (code) {
            10, 29, 31, 32, 33 -> WARNING

            // Encoder and resolver faults: critical only if the motor stops; without that
            // context they are shown as warnings.
            11, 12, 13, 20, 21, 22, 25, 26, 28, 30 -> WARNING

            else -> CRITICAL
        }
}
