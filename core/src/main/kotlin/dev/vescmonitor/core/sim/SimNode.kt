package dev.vescmonitor.core.sim

import dev.vescmonitor.core.protocol.FwVersion
import kotlin.math.PI
import kotlin.math.sin

/** A synthetic CAN node for tests and the synthetic transport. */
class SimNode(
    val canId: Int,
    val hwType: Int = FwVersion.HW_TYPE_VESC,
    val major: Int = 6,
    val minor: Int = 5,
    val hwName: String = "SIM",
    /** Pack voltage over time; defaults to a slow sine so values visibly change. */
    val voltageV: (Long) -> Double = sineVoltage(48.0, 2.0, 10_000L, 0.0),
    /** Moving currents, duty and temperatures; null keeps them at rest (0 A, 35-40 degC). */
    val ride: SimRide? = null,
) {
    /** False makes the node stop answering, like a controller that lost power. */
    @Volatile
    var online = true

    /** Field values by layout field name, at time [nowMs]. Missing names encode as 0. */
    fun values(nowMs: Long): Map<String, Double> {
        val rest =
            mapOf(
                "tempMosC" to 35.0,
                "tempMotorC" to 40.0,
                "voltageV" to voltageV(nowMs),
                "controllerId" to canId.toDouble(),
                "status" to 0.0,
                "faultCode" to 0.0,
                "tempMos123C" to 35.0,
            )
        val r = ride ?: return rest
        val p = r.at(nowMs)
        return rest +
            mapOf(
                "tempMosC" to r.tempMosC(nowMs),
                "tempMotorC" to r.tempMotorC(nowMs),
                "tempMos123C" to r.tempMosC(nowMs),
                "voltageV" to voltageV(nowMs) - p.currentInA * SimRide.SAG_OHM,
                "duty" to p.duty,
                "erpm" to p.erpm,
                "currentMotorA" to p.currentMotorA,
                "currentInA" to p.currentInA,
                "idA" to 0.0,
                "iqA" to p.currentMotorA,
                "speedMps" to r.speedMps(nowMs),
                "distanceAbsM" to r.distanceM(nowMs),
                "batteryLevel" to 0.6,
            )
    }

    companion object {
        fun sineVoltage(
            meanV: Double,
            amplitudeV: Double,
            periodMs: Long,
            phase: Double,
        ): (Long) -> Double = { t -> meanV + amplitudeV * sin(2 * PI * t / periodMs + phase) }
    }
}
