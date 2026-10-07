package dev.vescmonitor.core.battery

import kotlin.math.exp
import kotlin.math.ln

/**
 * Guesses chemistry and series cell count from voltages (t4-algorithms findings §7.3):
 * log-likelihood per candidate {NMC, LFP} x 6..30 S, softmax, confidence from the top
 * posterior. Without mcconf only GET_BATTERY_CUT adds VESC evidence; bldc's defaults
 * (10 V / 8 V) mean "never set" and count as no evidence.
 */
object BatteryDetect {
    enum class Confidence { LOW, MEDIUM, HIGH }

    data class Candidate(
        val chemistry: Chemistry,
        val cells: Int,
        val p: Double,
    )

    data class Result(
        val best: Candidate,
        val alternatives: List<Candidate>,
        val confidence: Confidence,
        /** Only the chemistry prior separates the top candidates: ask the rider to confirm. */
        val confirmChemistry: Boolean,
    )

    /**
     * Voltages in V. Config fields are optional VESC evidence; values equal to bldc's
     * defaults (3 cells, 10 V / 8 V cut, 57 V max) mean "never set" and are ignored.
     */
    data class Evidence(
        val vRest: Double,
        val vMaxSeen: Double? = null,
        val vMinLoaded: Double? = null,
        val cfgCells: Int? = null,
        val cfgChemistry: Chemistry? = null,
        val cutStartV: Double? = null,
        val cutEndV: Double? = null,
        val maxVin: Double? = null,
    )

    private val COMMON_S = setOf(10, 12, 13, 14, 16, 20, 22, 24)
    private const val SLACK = 0.03

    /** The other chemistry is still this likely and only the prior separates them. */
    private const val CONFIRM_P = 0.05
    private const val DEFAULT_CUT_START = 10.0
    private const val DEFAULT_CUT_END = 8.0
    private const val DEFAULT_CELLS = 3
    private const val DEFAULT_MAX_VIN = 57.0

    fun detect(e: Evidence): Result? {
        val scored = ArrayList<Pair<Candidate, Double>>()
        for (chem in Chemistry.entries) {
            for (s in 6..30) score(e, chem, s)?.let { scored += Candidate(chem, s, 0.0) to it }
        }
        if (scored.isEmpty()) return null
        val top = scored.maxOf { it.second }
        val weights = scored.map { it.first to exp(it.second - top) }
        val sum = weights.sumOf { it.second }
        val ranked = weights.map { (c, w) -> c.copy(p = w / sum) }.sortedByDescending { it.p }
        val best = ranked.first()
        val otherChem = ranked.firstOrNull { it.chemistry != best.chemistry }?.p ?: 0.0
        val confirm = otherChem >= CONFIRM_P && !hasChemistryEvidence(e)
        var confidence =
            when {
                best.p >= 0.80 -> Confidence.HIGH
                best.p >= 0.55 -> Confidence.MEDIUM
                else -> Confidence.LOW
            }
        if (confirm && confidence == Confidence.HIGH) confidence = Confidence.MEDIUM
        return Result(best, ranked.drop(1).filter { it.p >= 0.10 }, confidence, confirm)
    }

    private fun score(
        e: Evidence,
        chem: Chemistry,
        s: Int,
    ): Double? {
        val cell = e.vRest / s
        if (cell < chem.restMinV - SLACK || cell > chem.restMaxV + SLACK) return null
        if (e.vMaxSeen != null && e.vMaxSeen / s > chem.maxSeenV) return null
        if (e.vMinLoaded != null && e.vMinLoaded / s < chem.minLoadedV) return null
        var ll = ln(if (chem == Chemistry.NMC) 0.85 else 0.15)
        if (s !in COMMON_S) ll -= 1.6
        ll += ln(density(chem, cell))
        e.vMaxSeen?.let { ll += fullSignature(chem, it / s) }
        val cellsSet = e.cfgCells != null && e.cfgCells != DEFAULT_CELLS
        if (cellsSet) ll += if (e.cfgCells == s) 3.0 else -1.0
        // A Li-ion type with every other field at default is just the default.
        if (e.cfgChemistry != null && (cellsSet || e.cfgChemistry != Chemistry.NMC)) {
            ll += if (e.cfgChemistry == chem) 1.0 else -1.0
        }
        e.maxVin?.takeIf { it != DEFAULT_MAX_VIN }?.let { if (s * chem.fullCellV > it + 1) ll -= 1.0 }
        e.cutEndV?.takeIf { it != DEFAULT_CUT_END && e.cutStartV != DEFAULT_CUT_START }?.let {
            ll += band(it / s, if (chem == Chemistry.NMC) 2.7..3.3 else 2.3..2.9)
        }
        e.cutStartV?.takeIf { it != DEFAULT_CUT_START && e.cutEndV != DEFAULT_CUT_END }?.let {
            ll += band(it / s, if (chem == Chemistry.NMC) 3.0..3.6 else 2.6..3.1)
        }
        return ll
    }

    private fun hasChemistryEvidence(e: Evidence): Boolean {
        val cellsSet = e.cfgCells != null && e.cfgCells != DEFAULT_CELLS
        val typeSet = e.cfgChemistry != null && (cellsSet || e.cfgChemistry != Chemistry.NMC)
        val cutSet = e.cutEndV != null && e.cutStartV != null && (e.cutEndV != DEFAULT_CUT_END || e.cutStartV != DEFAULT_CUT_START)
        return typeSet || cutSet
    }

    /** Where rest voltages usually sit: riders start at full or mid charge; LFP on its plateau. */
    private fun density(
        chem: Chemistry,
        v: Double,
    ): Double =
        when (chem) {
            Chemistry.NMC -> {
                when (v) {
                    in 4.15..4.22 -> 2.0
                    in 3.5..4.15 -> 1.0
                    in 3.3..3.5 -> 0.5
                    else -> 0.15
                }
            }

            Chemistry.LFP -> {
                when {
                    v in 3.2..3.4 -> 3.75
                    v > 3.4 -> 0.4
                    else -> 0.375
                }
            }
        }

    private fun fullSignature(
        chem: Chemistry,
        maxCell: Double,
    ): Double =
        when (chem) {
            Chemistry.NMC -> {
                if (maxCell in 4.15..4.25) {
                    2.0
                } else if (maxCell in 4.08..4.15) {
                    1.0
                } else {
                    0.0
                }
            }

            Chemistry.LFP -> {
                if (maxCell in 3.55..3.66) 2.0 else 0.0
            }
        }

    private fun band(
        x: Double,
        range: ClosedFloatingPointRange<Double>,
    ): Double = if (x in range) 1.5 else -1.5
}
