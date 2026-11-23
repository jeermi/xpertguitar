package fr.xpert.guitar.audio

/**
 * Détection de hauteur d'une note isolée (accordeur), algorithme YIN
 * (de Cheveigné & Kawahara, 2002) : différence cumulée normalisée, premier creux
 * sous le seuil, interpolation parabolique. Pur Kotlin.
 *
 * @param rate fréquence d'échantillonnage du signal fourni à [detect]
 */
class PitchDetector(val rate: Int, val window: Int = 2048, minHz: Double = 60.0, maxHz: Double = 1100.0) {
    private val tauMin = (rate / maxHz).toInt().coerceAtLeast(2)
    private val tauMax = (rate / minHz).toInt().coerceAtMost(window - 2)
    private val d = DoubleArray(tauMax + 2)
    var threshold = 0.12

    /** Hauteur en Hz et clarté 0..1 (1 = son parfaitement périodique), ou null si pas de note nette. */
    fun detect(x: FloatArray, offset: Int = 0): Pair<Double, Double>? {
        val n = window - tauMax
        var energy = 0.0
        for (i in 0 until window) energy += x[offset + i] * x[offset + i]
        if (energy / window < 1e-8) return null
        for (tau in 1..tauMax + 1) {
            var s = 0.0
            for (i in 0 until n) {
                val v = x[offset + i] - x[offset + i + tau]
                s += v * v
            }
            d[tau] = s
        }
        d[0] = 1.0
        var run = 0.0
        for (tau in 1..tauMax + 1) {
            run += d[tau]
            d[tau] = if (run > 0) d[tau] * tau / run else 1.0
        }
        var tau = tauMin
        var found = -1
        while (tau <= tauMax) {
            if (d[tau] < threshold) {
                while (tau + 1 <= tauMax && d[tau + 1] < d[tau]) tau++
                found = tau
                break
            }
            tau++
        }
        if (found < 0) {
            // Pas de creux sous le seuil : on prend le minimum global s'il reste assez net.
            var best = tauMin
            for (t in tauMin..tauMax) if (d[t] < d[best]) best = t
            if (d[best] > 0.3) return null
            found = best
        }
        val a = d[found - 1]
        val b = d[found]
        val c = d[found + 1]
        val den = a - 2 * b + c
        val shift = if (den > 0) (0.5 * (a - c) / den).coerceIn(-1.0, 1.0) else 0.0
        val period = found + shift
        return Pair(rate / period, (1.0 - b).coerceIn(0.0, 1.0))
    }
}
