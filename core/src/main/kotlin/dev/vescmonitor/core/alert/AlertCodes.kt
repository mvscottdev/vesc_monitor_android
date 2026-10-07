package dev.vescmonitor.core.alert

/**
 * Alerts stored with ride samples as one small integer: `keyIndex * 4 + severity`
 * (severity 1 info, 2 warning, 3 critical; 0 = no alert). [KEYS] is append-only so
 * stored codes keep their meaning.
 */
object AlertCodes {
    val KEYS =
        listOf(
            "fet_temp",
            "motor_temp",
            "fet_temp_rise",
            "motor_temp_rise",
            "low_battery",
            "battery_cut",
            "low_cell",
            "sag",
            "diverge",
            "duty",
            "fault",
            "link_stale",
            "slip",
        )

    fun encode(a: ActiveAlert): Int {
        val i = KEYS.indexOf(a.key)
        return if (i < 0) 0 else i * 4 + a.severity.ordinal + 1
    }

    /** Severity rank of a code (0 = none), for picking the most severe. */
    fun rank(code: Int): Int = code % 4

    /** The most severe alert for [controllerId]: its own or a vehicle-wide one. */
    fun forController(
        active: List<ActiveAlert>,
        controllerId: Int,
    ): Int =
        active
            .filter { it.controllerId == null || it.controllerId == controllerId }
            .filterNot { it.cleared }
            .maxByOrNull { it.severity.ordinal }
            ?.let(::encode) ?: 0
}
