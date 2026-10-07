package dev.vescmonitor.core.battery

/** Cell chemistry. LTO is manual only (not auto-detected). */
enum class Chemistry(
    /** Default 0 % point (V/cell); editable later. */
    val emptyCellV: Double,
    /** Rest voltage band that can occur at all (V/cell). */
    val restMinV: Double,
    val restMaxV: Double,
    /** Highest plausible cell voltage ever seen (V/cell). */
    val maxSeenV: Double,
    /** Lowest plausible loaded cell voltage (V/cell). */
    val minLoadedV: Double,
    /** Full-charge cell voltage (V/cell). */
    val fullCellV: Double,
) {
    NMC(3.0, 3.00, 4.22, 4.30, 2.40, 4.20),
    LFP(2.8, 2.50, 3.65, 3.75, 2.00, 3.65),
}

/**
 * Per-cell resting open-circuit voltage tables, linear interpolation, clamped outside.
 * Sources and confidence: t4-algorithms findings §6.1 (NMC/NCA composite curve within
 * 7 points of bldc's Samsung 30Q polynomial; LFP from a public chart, coarse on its plateau).
 */
object Ocv {
    private val SOC =
        doubleArrayOf(
            0.0,
            2.0,
            5.0,
            10.0,
            15.0,
            20.0,
            25.0,
            30.0,
            35.0,
            40.0,
            45.0,
            50.0,
            55.0,
            60.0,
            65.0,
            70.0,
            75.0,
            80.0,
            85.0,
            90.0,
            95.0,
            100.0,
        )
    private val NMC_V =
        doubleArrayOf(
            3.00,
            3.18,
            3.30,
            3.40,
            3.47,
            3.53,
            3.58,
            3.62,
            3.65,
            3.68,
            3.71,
            3.74,
            3.77,
            3.81,
            3.85,
            3.89,
            3.93,
            3.98,
            4.03,
            4.08,
            4.14,
            4.20,
        )
    private val LFP_SOC = doubleArrayOf(0.0, 5.0, 10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 95.0, 100.0)
    private val LFP_V = doubleArrayOf(2.50, 2.90, 3.00, 3.20, 3.22, 3.25, 3.26, 3.27, 3.30, 3.32, 3.35, 3.38, 3.40)

    /** Absolute SoC in % for a resting cell voltage. */
    fun socOf(
        chem: Chemistry,
        cellV: Double,
    ): Double =
        when (chem) {
            Chemistry.NMC -> interp(NMC_V, SOC, cellV)
            Chemistry.LFP -> interp(LFP_V, LFP_SOC, cellV)
        }

    /** Resting cell voltage at an absolute SoC in %. */
    fun voltageAt(
        chem: Chemistry,
        socPct: Double,
    ): Double =
        when (chem) {
            Chemistry.NMC -> interp(SOC, NMC_V, socPct)
            Chemistry.LFP -> interp(LFP_SOC, LFP_V, socPct)
        }

    /**
     * Energy per Ah of capacity per cell (Wh) stored between 0 % and [socPct] absolute:
     * the table integral, not a linear average.
     */
    fun whPerAhCell(
        chem: Chemistry,
        socPct: Double,
    ): Double {
        val to = socPct.coerceIn(0.0, 100.0)
        val steps = (to / INTEGRAL_STEP).toInt().coerceAtLeast(1)
        val h = to / steps
        var sum = 0.0
        for (k in 0 until steps) sum += (voltageAt(chem, k * h) + voltageAt(chem, (k + 1) * h)) / 2 * h
        return sum / 100
    }

    private const val INTEGRAL_STEP = 0.5

    /** SoC shown to the rider: 0 % at the chemistry's empty point, 100 % at full. */
    fun displaySoc(
        chem: Chemistry,
        cellV: Double,
        emptyCellV: Double = chem.emptyCellV,
    ): Double {
        val empty = socOf(chem, emptyCellV)
        return ((socOf(chem, cellV) - empty) / (100 - empty) * 100).coerceIn(0.0, 100.0)
    }

    private fun interp(
        xs: DoubleArray,
        ys: DoubleArray,
        x: Double,
    ): Double {
        if (x <= xs.first()) return ys.first()
        if (x >= xs.last()) return ys.last()
        var i = 1
        while (xs[i] < x) i++
        val f = (x - xs[i - 1]) / (xs[i] - xs[i - 1])
        return ys[i - 1] + f * (ys[i] - ys[i - 1])
    }
}
