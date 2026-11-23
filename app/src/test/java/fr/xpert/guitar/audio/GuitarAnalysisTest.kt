package fr.xpert.guitar.audio

import fr.xpert.guitar.music.Chords
import fr.xpert.guitar.music.Notes
import fr.xpert.guitar.music.Shape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log2
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Analyse des accords et accordeur, testés sur les vrais échantillons de guitare de l'appli
 * (assets/guitar, 24 kHz) : son clair, saturé, grattés vers le bas ou vers le haut.
 */
class GuitarAnalysisTest {
    private val sr = 48_000
    private val cache = HashMap<Int, FloatArray>()

    private fun assetDir(): File =
        listOf("src/main/assets/guitar", "app/src/main/assets/guitar").map { File(it) }.first { it.isDirectory }

    /** Note MIDI [midi] en jeu normal, rééchantillonnée à 48 kHz. */
    private fun note(midi: Int): FloatArray = cache.getOrPut(midi) {
        val bb = ByteBuffer.wrap(File(assetDir(), "ord_$midi.pcm").readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val x = FloatArray(bb.remaining() / 2) { bb.short / 32768f }
        FloatArray(x.size * 2) { i -> val k = i / 2; if (i % 2 == 0 || k + 1 >= x.size) x[k] else (x[k] + x[k + 1]) / 2 }
    }

    private fun strum(notes: List<Int>, up: Boolean = false, drive: Double = 0.0, rnd: Random = Random(1)): FloatArray {
        val out = FloatArray(sr)
        val order = if (up) notes.reversed() else notes
        order.forEachIndexed { i, m ->
            val s = note(m)
            val off = (0.03 * sr + i * 0.012 * sr).toInt()
            val g = (0.6 + 0.4 * rnd.nextDouble()).toFloat()
            for (j in 0 until minOf(s.size, out.size - off)) out[off + j] += s[j] * g
        }
        if (drive > 0) {
            val norm = tanh(drive * 0.3)
            var hp = 0.0; var px = 0.0; var l1 = 0.0; var l2 = 0.0
            val a = exp(-2 * Math.PI * 4000.0 / sr)
            for (i in out.indices) {
                val x = out[i].toDouble()
                hp = 0.995 * (hp + x - px); px = x
                val y = tanh(drive * hp) / norm * 0.3
                l1 = (1 - a) * y + a * l1; l2 = (1 - a) * l1 + a * l2
                out[i] = l2.toFloat()
            }
        }
        return out
    }

    private fun profile(sig: FloatArray): Pair<FloatArray, Int> {
        val an = ChromaAnalyzer(sr)
        val acc = ChromaAccumulator()
        val hop = 1024
        var pos = 0
        while (pos + hop <= sig.size) {
            an.push(sig.copyOfRange(pos, pos + hop))
            pos += hop
            val t = pos.toDouble() / sr
            if (t >= 0.1 + an.frameSeconds * 0.5 && t <= 0.5) acc.add(an.analyze())
        }
        return acc.profile() to acc.bass()
    }

    private fun verify(played: List<Int>, expected: Shape, drive: Double = 0.0, up: Boolean = false): ChordVerdict {
        val (p, bass) = profile(strum(played, up, drive))
        return ChordMatcher.verify(p, expected.chord, expected, bass)
    }

    @Test
    fun accordsJustesReconnus() {
        var ok = 0
        var n = 0
        for (sh in Chords.all) for (drive in listOf(0.0, 15.0)) {
            n++
            if (verify(sh.midiNotes, sh, drive).ok) ok++
        }
        assertTrue("accords justes acceptés : $ok / $n", ok >= n * 0.95)
    }

    @Test
    fun accordsDeBaseClairEtSature() {
        for (id in listOf("Em", "Am", "C", "G", "D", "E5", "F-barre")) {
            val sh = Chords.get(id)
            assertTrue("$id clair", verify(sh.midiNotes, sh).ok)
            assertTrue("$id saturé", verify(sh.midiNotes, sh, drive = 15.0).ok)
            assertTrue("$id vers le haut", verify(sh.midiNotes.takeLast(4), sh, up = true).ok || sh.playedStrings <= 3)
        }
    }

    @Test
    fun mauvaisAccordRefuse() {
        val pairs = listOf("Em" to "D", "A" to "C", "G" to "Dm", "E" to "F-barre", "D" to "Am", "C" to "E")
        for ((played, expected) in pairs) {
            val v = verify(Chords.get(played).midiNotes, Chords.get(expected))
            assertFalse("$played joué, $expected attendu", v.ok)
        }
        val v = verify(Chords.get("G").midiNotes, Chords.get("D"), drive = 15.0)
        assertFalse("G saturé joué, D attendu", v.ok)
    }

    @Test
    fun doigtDeTraversDetecte() {
        // Do majeur avec le majeur une case trop loin sur la corde de Ré (3 au lieu de 2) : Fa au lieu de Mi
        val c = Chords.get("C")
        val wrong = c.frets.indices.filter { c.frets[it] >= 0 }.map { Notes.STANDARD_TUNING[it] + c.frets[it] + if (it == 2) 1 else 0 }
        val v = verify(wrong, c)
        assertFalse(v.ok)
    }

    @Test
    fun silenceSansVerdictPositif() {
        val v = ChordMatcher.verify(profile(FloatArray(sr)).first, Chords.get("Em").chord, Chords.get("Em"))
        assertFalse(v.ok)
    }

    @Test
    fun accordeurCordesAVide() {
        val pd = PitchDetector(24_000, 2048)
        for (m in Notes.STANDARD_TUNING) {
            val s = note(m)
            val x = FloatArray(s.size / 2) { (s[2 * it] + s[2 * it + 1]) / 2 }
            val r = pd.detect(x, (0.6 * 24_000).toInt())!!
            val cents = 1200 * log2(r.first / Notes.freq(m.toDouble()))
            assertTrue("corde $m : $cents cents", abs(cents) < 15)
        }
        assertEquals(82.41, Notes.freq(40.0), 0.01)
    }
}
