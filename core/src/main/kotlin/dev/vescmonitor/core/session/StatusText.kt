package dev.vescmonitor.core.session

import dev.vescmonitor.core.frame.TelemetryFrame
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** The ongoing notification: [title] is the big line, [text] the source line, [sub] "REC" while a ride records (the system draws its timer). */
data class StatusText(
    val title: String,
    val text: String,
    val sub: String?,
) {
    companion object {
        private const val KMH_PER_MPS = 3.6
        private const val M_PER_MI = 1609.344
        private const val KM_PER_MI = 1.609344

        /**
         * Builds the texts from the session state and the latest frame. [source] is the
         * transport name ("ble", "replay", "synthetic-N"); [hz] the slowest fresh controller's rate.
         */
        fun of(
            state: ConnectionState,
            attempt: Int,
            source: String,
            hz: Double?,
            frame: TelemetryFrame?,
            rideElapsedMs: Long?,
            mph: Boolean,
        ): StatusText {
            val sub = rideElapsedMs?.let { "REC" }
            val label = sourceLabel(source)
            return when (state) {
                ConnectionState.CONNECTED -> {
                    val d = frame?.combined?.derived
                    val parts =
                        listOfNotNull(
                            d?.speedMps?.let { speed(it, mph) },
                            d?.socPct?.let { "${it.roundToInt()} %" },
                            frame
                                ?.combined
                                ?.values
                                ?.powerW
                                ?.let(::power),
                        )
                    val title = parts.joinToString(" · ").ifEmpty { "Connected" }
                    val text =
                        listOfNotNull(
                            "$label connected",
                            hz?.takeIf { it > 0 }?.let { "${it.roundToInt()} Hz" },
                            d?.tripM?.takeIf { it > 0 }?.let { distance(it, mph) },
                        ).joinToString(" · ")
                    StatusText(title, text, sub)
                }

                ConnectionState.RECONNECTING -> {
                    val keeps = if (rideElapsedMs != null) " The ride keeps recording." else ""
                    StatusText("Reconnecting (attempt $attempt)", "Connection lost · retrying.$keeps", sub)
                }

                ConnectionState.CONNECTING -> {
                    StatusText("Connecting", label, sub)
                }

                ConnectionState.LOST -> {
                    StatusText("Connection lost", label, sub)
                }

                else -> {
                    StatusText("Not connected", label, sub)
                }
            }
        }

        fun sourceLabel(source: String): String =
            when {
                source == "ble" -> "VESC BLE"
                source == "replay" -> "Replay"
                source.startsWith("synthetic") -> "Synthetic"
                else -> source
            }

        private fun speed(
            mps: Double,
            mph: Boolean,
        ): String {
            val kmh = abs(mps) * KMH_PER_MPS
            return if (mph) "${(kmh / KM_PER_MI).roundToInt()} mph" else "${kmh.roundToInt()} km/h"
        }

        private fun distance(
            m: Double,
            mph: Boolean,
        ): String = if (mph) String.format(Locale.ROOT, "%.1f mi", m / M_PER_MI) else String.format(Locale.ROOT, "%.1f km", m / 1000)

        private fun power(w: Double): String = if (abs(w) < 1000) "${w.roundToInt()} W" else String.format(Locale.ROOT, "%.2f kW", w / 1000)
    }
}
