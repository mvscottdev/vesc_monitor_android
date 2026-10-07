package dev.vescmonitor.core.alert

enum class Severity { INFO, WARNING, CRITICAL }

/** One threshold of a rule; [dwellMs] is how long the condition must hold before it fires. */
data class Level(
    val threshold: Double,
    val severity: Severity,
    val dwellMs: Long,
)

/**
 * A threshold alert (alerts.md engine): fires a level when its condition held for the
 * level's dwell, clears or steps down once the value is past threshold ∓ hysteresis.
 * Escalation is immediate after its dwell; [cooldownMs] only suppresses repeated
 * notifications of the same or a lower level.
 */
data class AlertRule(
    val key: String,
    /** Ascending severity. */
    val levels: List<Level>,
    val hysteresis: Double,
    val cooldownMs: Long,
    /** true: fires when the value rises to the threshold; false: when it falls to it. */
    val above: Boolean = true,
)

/**
 * The rule with the rider's thresholds, one per level; null when the count differs or
 * the order no longer escalates in the rule's direction (rising for [AlertRule.above]).
 */
fun AlertRule.withThresholds(ts: List<Double>): AlertRule? {
    if (ts.size != levels.size || ts.any { it.isNaN() || it.isInfinite() }) return null
    val ordered = ts.zipWithNext().all { (a, b) -> if (above) b > a else b < a }
    if (!ordered) return null
    return copy(levels = levels.mapIndexed { i, l -> l.copy(threshold = ts[i]) })
}

/** Longest repeat time the rider can set. */
const val MAX_COOLDOWN_MS = 3_600_000L

/**
 * The rule with the rider's hysteresis (rule units) and repeat time; null when either is
 * negative or not finite, the hysteresis is not smaller than the first threshold's
 * magnitude, or the repeat time is above [MAX_COOLDOWN_MS].
 */
fun AlertRule.withTuning(
    hysteresis: Double,
    cooldownMs: Long,
): AlertRule? {
    if (hysteresis.isNaN() || hysteresis.isInfinite() || hysteresis < 0) return null
    if (hysteresis >= kotlin.math.abs(levels.first().threshold)) return null
    if (cooldownMs !in 0..MAX_COOLDOWN_MS) return null
    return copy(hysteresis = hysteresis, cooldownMs = cooldownMs)
}

/** One firing alert. [notify] is true on the evaluation where it fired or escalated outside its cooldown. */
data class ActiveAlert(
    val key: String,
    val controllerId: Int?,
    val severity: Severity,
    val value: Double,
    val sinceMs: Long,
    val notify: Boolean = false,
    /** A latched fault whose code has returned to 0. */
    val cleared: Boolean = false,
) {
    /** Event map for the UI: severity as "info" / "warning" / "critical", flags 0/1. */
    fun toMap(): Map<String, Any?> =
        mapOf(
            "key" to key,
            "controllerId" to controllerId,
            "severity" to severity.name.lowercase(),
            "value" to value,
            "sinceMs" to sinceMs,
            "notify" to if (notify) 1 else 0,
            "cleared" to if (cleared) 1 else 0,
        )
}

/** Runtime state of one rule for one subject (the vehicle or one controller). */
class RuleState(
    private val rule: AlertRule,
    private val controllerId: Int?,
) {
    private var active = -1
    private var activeSince = 0L
    private var pending = -1
    private var pendingSince = 0L
    private var lastNotifiedMs = Long.MIN_VALUE
    private var lastNotifiedLevel = -1

    /** Evaluates [value] (null = input unavailable: the alert clears and stays quiet). */
    fun update(
        value: Double?,
        nowMs: Long,
    ): ActiveAlert? {
        if (value == null || value.isNaN()) {
            active = -1
            pending = -1
            return null
        }
        var held = -1
        rule.levels.forEachIndexed { i, l ->
            val t = if (i <= active) l.threshold - sign() * rule.hysteresis else l.threshold
            if (crosses(value, t)) held = i
        }
        var notify = false
        if (held < active) {
            active = held
            pending = -1
            if (held >= 0) activeSince = nowMs
        } else if (held > active) {
            if (pending != held) {
                pending = held
                pendingSince = nowMs
            }
            if (nowMs - pendingSince >= rule.levels[held].dwellMs) {
                active = held
                activeSince = nowMs
                pending = -1
                val escalation = held > lastNotifiedLevel || nowMs - lastNotifiedMs >= rule.cooldownMs
                if (escalation || lastNotifiedMs == Long.MIN_VALUE) {
                    notify = true
                    lastNotifiedMs = nowMs
                    lastNotifiedLevel = held
                }
            }
        } else {
            pending = -1
        }
        if (active < 0) {
            // The notified level ages out of the cooldown from here on.
            if (lastNotifiedLevel >= 0 && nowMs - lastNotifiedMs >= rule.cooldownMs) lastNotifiedLevel = -1
            return null
        }
        return ActiveAlert(rule.key, controllerId, rule.levels[active].severity, value, activeSince, notify)
    }

    private fun sign(): Double = if (rule.above) 1.0 else -1.0

    private fun crosses(
        v: Double,
        t: Double,
    ): Boolean = if (rule.above) v >= t else v <= t
}
