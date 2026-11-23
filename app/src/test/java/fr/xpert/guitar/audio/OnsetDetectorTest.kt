package fr.xpert.guitar.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class OnsetDetectorTest {
    private val sr = 48_000

    /** Signal simulé : bruit de fond + coups (bruit + ton amortis) aux frames données. */
    private fun signal(seconds: Double, hits: List<Pair<Int, Float>>, noise: Float, rnd: Random = Random(1)): FloatArray {
        val out = FloatArray((seconds * sr).toInt()) { (rnd.nextFloat() * 2 - 1) * noise }
        for ((at, amp) in hits) {
            for (i in 0 until (sr * 0.3).toInt()) {
                val k = at + i
                if (k >= out.size) break
                val t = i.toDouble() / sr
                val v = ((rnd.nextFloat() * 2 - 1) * 0.6 + sin(2 * Math.PI * 180 * t)) * exp(-t * 18)
                out[k] += (v * amp).toFloat()
            }
        }
        return out
    }

    private fun detect(sig: FloatArray, threshold: Float): List<Pair<Long, Float>> {
        val d = OnsetDetector(sr).apply { this.threshold = threshold }
        val found = ArrayList<Pair<Long, Float>>()
        var pos = 0
        while (pos < sig.size) {
            val n = minOf(256, sig.size - pos)
            d.process(sig.copyOfRange(pos, pos + n), n, pos.toLong()) { f, l -> found.add(f to l) }
            pos += n
        }
        return found
    }

    @Test
    fun detecteLesCrochesA100BpmAvecPrecision() {
        val step = sr * 60 / 100 / 2 // croches à 100 BPM = 300 ms
        val at = (0 until 16).map { sr / 2 + it * step }
        val sig = signal(6.0, at.map { it to 0.7f }, noise = 0.01f)
        val found = detect(sig, threshold = 0.08f)
        assertEquals(at.size, found.size)
        for ((expected, got) in at.zip(found.map { it.first })) {
            assertTrue("écart ${got - expected} frames", abs(got - expected) <= sr / 1000) // ≤ 1 ms
        }
    }

    @Test
    fun ignoreLeBruitEtLesCoupsTropFaibles() {
        val sig = signal(3.0, listOf(sr / 2 to 0.04f), noise = 0.02f)
        assertTrue(detect(sig, threshold = 0.1f).isEmpty())
    }

    @Test
    fun doublesCrochesRapidesSeparees() {
        val step = sr * 60 / 120 / 4 // doubles croches à 120 BPM = 125 ms
        val at = (0 until 8).map { sr / 2 + it * step }
        val found = detect(signal(3.0, at.map { it to 0.6f }, noise = 0.005f), threshold = 0.06f)
        assertEquals(at.size, found.size)
    }

    @Test
    fun coupFaibleJusteApresUnCoupFort() {
        val a = sr / 2
        val b = a + sr / 8 // 125 ms plus tard, 3,5 fois moins fort (charleston après caisse claire)
        val found = detect(signal(2.0, listOf(a to 0.7f, b to 0.2f), noise = 0.005f), threshold = 0.04f)
        assertEquals(2, found.size)
    }

    @Test
    fun cymbaleQuiResonneNeRedeclenchePas() {
        val rnd = Random(3)
        val sig = FloatArray(sr * 3) { (rnd.nextFloat() * 2 - 1) * 0.005f }
        val at = sr / 2
        for (i in 0 until sr * 2) {
            val t = i.toDouble() / sr
            sig[at + i] += ((rnd.nextFloat() * 2 - 1) * 0.6 * exp(-t * 1.5)).toFloat()
        }
        assertEquals(1, detect(sig, threshold = 0.04f).size)
    }
}
