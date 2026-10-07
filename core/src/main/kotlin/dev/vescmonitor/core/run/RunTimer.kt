package dev.vescmonitor.core.run

import kotlin.math.abs

/** A bracket: from standstill to a speed (km/h) or over a distance (m). */
data class Bracket(
    val label: String,
    val kmh: Double? = null,
    val meters: Double? = null,
) {
    companion object {
        val DEFAULT =
            listOf(
                Bracket("0-30", kmh = 30.0),
                Bracket("0-50", kmh = 50.0),
                Bracket("0-60", kmh = 60.0),
                Bracket("100 m", meters = 100.0),
            )

        /** For riders who read mph: the same idea at round mph speeds. */
        val IMPERIAL =
            listOf(
                Bracket("0-20 mph", kmh = 20 * KMH_PER_MPH),
                Bracket("0-30 mph", kmh = 30 * KMH_PER_MPH),
                Bracket("0-40 mph", kmh = 40 * KMH_PER_MPH),
                Bracket("100 m", meters = 100.0),
            )

        private const val KMH_PER_MPH = 1.609344
    }
}

/** Times in seconds for one bracket; null = not reached (yet). */
data class BracketTime(
    val bracket: Bracket,
    val rolloutS: Double? = null,
    val firstMotionS: Double? = null,
)

/** What the session knows at one fast sample besides speed. */
data class RunContext(
    val allFresh: Boolean = true,
    val fault: Boolean = false,
    val powerW: Double? = null,
    val voltageV: Double? = null,
    val tempMosC: Double? = null,
    val tempMotorC: Double? = null,
    val slip: Boolean = false,
)

/**
 * Acceleration run state machine (speed-test.md): IDLE → ARMED → STAGED → RUNNING →
 * DONE | ABORTED. Fed with the combined motor speed at each fast sample's own time;
 * [tick] covers the time-based rules between samples. Owned by the session thread.
 */
class RunTimer(
    private var brackets: List<Bracket> = Bracket.DEFAULT,
) {
    enum class State { IDLE, ARMED, STAGED, RUNNING, DONE, ABORTED }

    enum class Abort { FALSE_START, ROLLING_START, LIFT_OFF, LINK, FAULT, TIMEOUT, ARM_TIMEOUT, CANCELLED }

    var state = State.IDLE
        private set
    var abort: Abort? = null
        private set
    var times: List<BracketTime> = brackets.map { BracketTime(it) }
        private set

    /** Start of the run (rollout t0 once known, else the launch trigger), ms. */
    var startMs: Long? = null
        private set
    var lastSampleMs: Long? = null
        private set
    var peakMps = 0.0
        private set
    var peakPowerW = 0.0
        private set
    var minVoltageV: Double? = null
        private set
    var maxTempMosC: Double? = null
        private set
    var maxTempMotorC: Double? = null
        private set
    var slipBelow20 = false
        private set

    /** First-motion start was used because low-speed samples were too sparse for rollout. */
    var startEstimated = false
        private set
    val sampleRateHz: Double
        get() {
            val s = samples
            if (s.size < 2) return 0.0
            return (s.size - 1) / ((s.last().tMs - s.first().tMs) / 1000.0).coerceAtLeast(1e-3)
        }
    val jitterP95Ms: Double get() = RunMath.jitterP95Ms(samples)

    private val samples = ArrayList<RunSample>()
    private var armedMs = 0L
    private var stillSinceMs: Long? = null
    private var lastStillMs = 0L
    private var risingCount = 0
    private var triggerSeen = false
    private var belowSinceMs: Long? = null
    private var liftSinceMs: Long? = null
    private var flatSinceMs: Long? = null
    private var runStartMs = 0L

    /** Increments on every arm, so a finished run is reported once. */
    var runNumber = 0
        private set

    /** The speed curve of the run (from the last standstill sample), m/s. */
    fun curve(): List<RunSample> = samples.toList()

    /** Arms a run; [set] replaces the brackets from this run on. */
    fun arm(
        nowMs: Long,
        set: List<Bracket>? = null,
    ) {
        if (set != null) brackets = set
        reset()
        runNumber++
        state = State.ARMED
        armedMs = nowMs
    }

    fun cancel() {
        if (state in setOf(State.ARMED, State.STAGED, State.RUNNING)) finishAbort(Abort.CANCELLED) else reset()
    }

    fun onSample(
        tMs: Long,
        speedMps: Double,
        ctx: RunContext,
    ) {
        if (state == State.IDLE || state == State.DONE || state == State.ABORTED) return
        val v = abs(speedMps)
        val prev = lastSampleMs
        lastSampleMs = tMs
        when (state) {
            State.ARMED, State.STAGED -> staged(tMs, v, ctx)
            State.RUNNING -> running(tMs, v, ctx, prev)
            else -> Unit
        }
    }

    /** Time-based rules without a sample: link loss, timeouts. */
    fun tick(nowMs: Long) {
        when (state) {
            State.ARMED, State.STAGED -> {
                if (nowMs - armedMs > ARM_TIMEOUT_MS) finishAbort(Abort.ARM_TIMEOUT)
            }

            State.RUNNING -> {
                val last = lastSampleMs ?: return
                if (nowMs - last > NO_SAMPLE_MS) finishAbort(Abort.LINK)
                if (nowMs - runStartMs > timeoutMs()) finish(timeout = true)
            }

            else -> {
                Unit
            }
        }
    }

    private fun staged(
        tMs: Long,
        v: Double,
        ctx: RunContext,
    ) {
        if (ctx.fault) return finishAbort(Abort.FAULT)
        if (!ctx.allFresh) {
            stillSinceMs = null
            return
        }
        if (v <= STILL_MPS) {
            val since = stillSinceMs ?: tMs.also { stillSinceMs = it }
            lastStillMs = tMs
            if (state == State.ARMED && tMs - since >= STILL_MS) state = State.STAGED
            samples.clear()
            samples += RunSample(tMs, v)
            risingCount = 0
            triggerSeen = false
            return
        }
        stillSinceMs = null
        if (state != State.STAGED) return
        samples += RunSample(tMs, v)
        val before = samples.getOrNull(samples.size - 2)?.v ?: 0.0
        if (v > TRIGGER_MPS) {
            if (!triggerSeen) {
                triggerSeen = true
                if (v > ROLLING_MPS) return finishAbort(Abort.ROLLING_START)
            }
            risingCount = if (v > before) risingCount + 1 else 0
            if (risingCount >= 2) {
                state = State.RUNNING
                runStartMs = tMs
                startMs = tMs
            }
        } else {
            risingCount = 0
        }
    }

    private fun running(
        tMs: Long,
        v: Double,
        ctx: RunContext,
        prevMs: Long?,
    ) {
        samples += RunSample(tMs, v)
        peakMps = maxOf(peakMps, v)
        ctx.powerW?.let { peakPowerW = maxOf(peakPowerW, it) }
        ctx.voltageV?.let { x -> minVoltageV = minVoltageV?.let { minOf(it, x) } ?: x }
        ctx.tempMosC?.let { x -> maxTempMosC = maxTempMosC?.let { maxOf(it, x) } ?: x }
        ctx.tempMotorC?.let { x -> maxTempMotorC = maxTempMotorC?.let { maxOf(it, x) } ?: x }
        if (ctx.slip && v < SLIP_FLAG_MPS) slipBelow20 = true
        if (ctx.fault) return finishAbort(Abort.FAULT)
        if (!ctx.allFresh || (prevMs != null && tMs - prevMs > NO_SAMPLE_MS)) return finishAbort(Abort.LINK)

        updateTimes()
        val allDone = times.all { it.rolloutS != null }
        if (allDone) return finish(timeout = false)
        // (a) false start: back to a stop before 10 km/h.
        if (peakMps < LIFT_PEAK_MPS && v <= STILL_MPS) {
            val since = belowSinceMs ?: tMs.also { belowSinceMs = it }
            if (tMs - since >= STILL_MS) return finishAbort(Abort.FALSE_START)
        } else {
            belowSinceMs = null
        }
        // (c) lift-off or brake after 10 km/h.
        if (peakMps > LIFT_PEAK_MPS && v < peakMps - maxOf(LIFT_DROP_MPS, LIFT_DROP_FRACTION * peakMps)) {
            val since = liftSinceMs ?: tMs.also { liftSinceMs = it }
            if (tMs - since >= LIFT_MS) return finishAbort(Abort.LIFT_OFF)
        } else {
            liftSinceMs = null
        }
        // Top speed: acceleration flat for 2 s.
        val recent = samples.filter { tMs - it.tMs <= ACCEL_WINDOW_MS }
        val a = RunMath.fit(recent)?.slope
        if (peakMps > LIFT_PEAK_MPS && a != null && a <= FLAT_ACCEL) {
            val since = flatSinceMs ?: tMs.also { flatSinceMs = it }
            if (tMs - since >= FLAT_MS) return finish(timeout = false)
        } else {
            flatSinceMs = null
        }
        if (tMs - runStartMs > timeoutMs()) finish(timeout = true)
    }

    private fun updateTimes() {
        val lastStillS = lastStillMs / 1000.0
        val rolloutT0 = RunMath.distanceCrossing(samples, lastStillS, RunMath.ROLLOUT_M)
        val motionT0 = RunMath.firstMotion(samples, lastStillS)
        startEstimated = rolloutT0 == null && motionT0 != null
        val headline = rolloutT0 ?: motionT0
        headline?.let { startMs = (it * 1000).toLong() }
        times =
            brackets.map { b ->
                fun elapsed(t0: Double?): Double? {
                    t0 ?: return null
                    val end =
                        when {
                            b.kmh != null -> RunMath.crossing(samples, b.kmh / 3.6)
                            b.meters != null -> RunMath.distanceCrossing(samples, t0, b.meters)
                            else -> null
                        }
                    return end?.let { it - t0 }?.takeIf { it > 0 }
                }
                BracketTime(b, elapsed(rolloutT0 ?: motionT0), elapsed(motionT0))
            }
    }

    private fun timeoutMs(): Long = if (brackets.any { (it.kmh ?: 0.0) > 60.0 }) 40_000L else 25_000L

    private fun finish(timeout: Boolean) {
        updateTimes()
        state = if (timeout && times.none { it.rolloutS != null }) State.ABORTED else State.DONE
        if (state == State.ABORTED) abort = Abort.TIMEOUT
    }

    private fun finishAbort(reason: Abort) {
        state = State.ABORTED
        abort = reason
    }

    private fun reset() {
        state = State.IDLE
        abort = null
        times = brackets.map { BracketTime(it) }
        startMs = null
        lastSampleMs = null
        peakMps = 0.0
        peakPowerW = 0.0
        minVoltageV = null
        maxTempMosC = null
        maxTempMotorC = null
        slipBelow20 = false
        startEstimated = false
        samples.clear()
        stillSinceMs = null
        risingCount = 0
        triggerSeen = false
        belowSinceMs = null
        liftSinceMs = null
        flatSinceMs = null
    }

    companion object {
        const val STILL_MPS = 0.5 / 3.6
        const val STILL_MS = 1_000L
        const val TRIGGER_MPS = 1.5 / 3.6
        const val ROLLING_MPS = 5.0 / 3.6
        const val LIFT_PEAK_MPS = 10.0 / 3.6
        const val LIFT_DROP_MPS = 3.0 / 3.6
        const val LIFT_DROP_FRACTION = 0.05
        const val LIFT_MS = 300L
        const val SLIP_FLAG_MPS = 20.0 / 3.6
        const val FLAT_ACCEL = 0.1
        const val FLAT_MS = 2_000L
        const val ACCEL_WINDOW_MS = 1_000L
        const val NO_SAMPLE_MS = 500L
        const val ARM_TIMEOUT_MS = 120_000L
    }
}
