package dev.vescmonitor.core.alert

import dev.vescmonitor.core.battery.Chemistry
import dev.vescmonitor.core.frame.TelemetryFrame
import dev.vescmonitor.core.protocol.BatteryCut
import kotlin.math.abs

/**
 * Evaluates the default catalog on each frame, for the vehicle and for every controller.
 * An alert whose input is missing (no sensor, no detected pack, default battery cut,
 * a single controller for divergence) is not evaluated. Owned by the session thread.
 */
class AlertEngine {
    private val states = HashMap<Pair<String, Int?>, RuleState>()
    private val rates = HashMap<Pair<String, Int>, RateWindow>()
    private val faults = HashMap<Int, ActiveAlert>()
    private val slips = ArrayDeque<Long>()
    private var lastSlip = false
    private var cutRule: AlertRule? = null

    /** Alert keys the rider switched off; they are not evaluated and never fire. */
    var disabled: Set<String> = emptySet()

    /** The rider's thresholds per alert key (one per level); invalid entries are ignored. */
    var thresholds: Map<String, List<Double>> = emptyMap()
        set(value) {
            val changed = (field.keys + value.keys).filter { field[it] != value[it] }.toSet()
            field = value
            states.keys.removeAll { it.first in changed }
        }

    /** The rider's hysteresis and repeat time per alert key, as [hysteresis, cooldown s]. */
    var tuning: Map<String, List<Double>> = emptyMap()
        set(value) {
            val changed = (field.keys + value.keys).filter { field[it] != value[it] }.toSet()
            field = value
            states.keys.removeAll { it.first in changed }
        }

    /** Active alerts after the last evaluation, most severe first. */
    var active: List<ActiveAlert> = emptyList()
        private set

    /** Returns true when the active list changed (a fire, clear, step or new notify). */
    fun evaluate(
        frame: TelemetryFrame,
        batteryCut: BatteryCut?,
        nowMs: Long,
    ): Boolean {
        val out = ArrayList<ActiveAlert>()
        val c = frame.combined
        val d = c.derived
        for (e in frame.vescs) {
            val id = e.controllerId
            out += eval(AlertCatalog.LINK_STALE, id, e.ageMs?.toDouble(), nowMs)
            val fresh = e.fresh
            val fet = e.values.tempMosC.takeIf { fresh }
            val motor = e.values.tempMotorC.takeIf { fresh }
            out += eval(AlertCatalog.FET_TEMP, id, fet, nowMs)
            out += eval(AlertCatalog.MOTOR_TEMP, id, motor, nowMs)
            out += eval(AlertCatalog.FET_TEMP_RISE, id, rate("fet", id, fet, AlertCatalog.FET_RISE_WINDOW_MS, nowMs), nowMs)
            out += eval(AlertCatalog.MOTOR_TEMP_RISE, id, rate("motor", id, motor, AlertCatalog.MOTOR_RISE_WINDOW_MS, nowMs), nowMs)
            out +=
                eval(
                    AlertCatalog.DUTY,
                    id,
                    e.values.duty
                        ?.let(::abs)
                        ?.takeIf { fresh },
                    nowMs,
                )
            if ("fault" !in disabled) fault(e, nowMs)?.let { out += it }
        }
        val all = frame.vescs.size == c.total && c.fresh == c.total
        out += eval(AlertCatalog.LOW_BATTERY, null, d.socPct.takeIf { d.cells != null && c.fresh > 0 }, nowMs)
        val lowCell = if (d.chemistry == Chemistry.LFP) AlertCatalog.LOW_CELL_LFP else AlertCatalog.LOW_CELL_NMC
        out += eval(lowCell, null, d.cellV.takeIf { all }, nowMs)
        val sagPct = d.sagV?.let { s -> d.restRefV?.takeIf { it > 0 }?.let { s / it * 100 } }
        out += eval(AlertCatalog.SAG, null, sagPct.takeIf { all }, nowMs)
        out += eval(cutRule(batteryCut), null, c.voltageV.takeIf { all && cutRule != null }, nowMs)
        val volts = frame.vescs.filter { it.fresh }.mapNotNull { it.voltageV }
        out += eval(AlertCatalog.DIVERGE, null, if (volts.size >= 2) volts.max() - volts.min() else null, nowMs)
        out += eval(AlertCatalog.SLIP, null, slipCount(d.slip, nowMs), nowMs)

        val next = out.sortedWith(compareByDescending<ActiveAlert> { it.severity }.thenBy { it.sinceMs })
        val changed = next.any { it.notify } || next.map { it.copy(value = 0.0) } != active.map { it.copy(value = 0.0, notify = false) }
        active = next
        return changed
    }

    private fun eval(
        rule: AlertRule,
        controllerId: Int?,
        value: Double?,
        nowMs: Long,
    ): List<ActiveAlert> {
        val s =
            states.getOrPut(rule.key to controllerId) {
                val r = thresholds[rule.key]?.let { rule.withThresholds(it) } ?: rule
                RuleState(tuning[rule.key]?.let { tuned(r, it) } ?: r, controllerId)
            }
        return listOfNotNull(s.update(value.takeUnless { rule.key in disabled }, nowMs))
    }

    private fun tuned(
        r: AlertRule,
        t: List<Double>,
    ): AlertRule? = if (t.size == 2) r.withTuning(t[0], (t[1] * 1000).toLong()) else null

    /** Only a battery cut that is not bldc's default is evidence. */
    private fun cutRule(cut: BatteryCut?): AlertRule {
        val usable = cut != null && !(cut.startV == DEFAULT_CUT_START && cut.endV == DEFAULT_CUT_END) && cut.startV > cut.endV
        val rule = if (usable) AlertCatalog.batteryCut(cut!!.startV, cut.endV) else null
        if (rule != cutRule) {
            cutRule = rule
            states.remove("battery_cut" to null)
        }
        return rule ?: AlertCatalog.batteryCut(DEFAULT_CUT_START, DEFAULT_CUT_END)
    }

    private fun rate(
        name: String,
        id: Int,
        value: Double?,
        windowMs: Long,
        nowMs: Long,
    ): Double? {
        val w = rates.getOrPut(name to id) { RateWindow(windowMs) }
        if (value == null) {
            w.clear()
            return null
        }
        return w.add(nowMs, value)
    }

    /** Faults latch: kept, marked cleared, after the code returns to 0. */
    private fun fault(
        e: TelemetryFrame.VescEntry,
        nowMs: Long,
    ): ActiveAlert? {
        val code = e.values.faultCode
        val prev = faults[e.controllerId]
        if (code != 0 && e.fresh) {
            val stopped = abs(e.values.erpm ?: 0.0) < STOPPED_ERPM
            val sev =
                AlertCatalog.faultSeverity(code).let {
                    if (it == Severity.WARNING && stopped &&
                        code in ENCODER
                    ) {
                        Severity.CRITICAL
                    } else {
                        it
                    }
                }
            if (prev == null || prev.value != code.toDouble() || prev.cleared) {
                val a = ActiveAlert("fault", e.controllerId, sev, code.toDouble(), nowMs, notify = true)
                faults[e.controllerId] = a.copy(notify = false)
                return a
            }
            return prev
        }
        if (prev != null && !prev.cleared) faults[e.controllerId] = prev.copy(cleared = true)
        return faults[e.controllerId]
    }

    private fun slipCount(
        slip: Boolean,
        nowMs: Long,
    ): Double {
        if (slip && !lastSlip) slips.addLast(nowMs)
        lastSlip = slip
        while (slips.isNotEmpty() && nowMs - slips.first() > AlertCatalog.SLIP_WINDOW_MS) slips.removeFirst()
        return slips.size.toDouble()
    }

    /** Clears latched faults (the rider dismissed them). */
    fun dismissFaults() {
        faults.clear()
    }

    companion object {
        const val DEFAULT_CUT_START = 10.0
        const val DEFAULT_CUT_END = 8.0
        const val STOPPED_ERPM = 200.0
        private val ENCODER = setOf(11, 12, 13, 20, 21, 22, 25, 26, 28, 30)
    }
}

/** Rate of change (units per second) over a time window; null until the window is mostly filled. */
class RateWindow(
    private val windowMs: Long,
) {
    private val samples = ArrayDeque<Pair<Long, Double>>()

    fun add(
        nowMs: Long,
        v: Double,
    ): Double? {
        samples.addLast(nowMs to v)
        while (samples.size > 1 && nowMs - samples.first().first > windowMs) samples.removeFirst()
        val (t0, v0) = samples.first()
        val span = nowMs - t0
        return if (span < windowMs * 8 / 10) null else (v - v0) / (span / 1000.0)
    }

    fun clear() = samples.clear()
}
