package fr.xpert.guitar.audio

import android.content.res.AssetManager
import fr.xpert.guitar.music.Shape
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Un son pré-calculé (mono, float, -1..1). */
class Sound(val name: String, val data: FloatArray)

/** Sens du coup de médiator. */
enum class StrumDir { DOWN, UP }

/** Façon de jouer un coup de médiator. */
enum class StrumKind { OPEN, PALM_MUTE }

/**
 * Sons de guitare électrique : échantillons de la banque « Black And Green Guitars »
 * de Karoryfer Samples (Gretsch Anniversary verte, jeu normal et étouffé), domaine public (CC0).
 * Une note par demi-ton de Mi2 (corde grave à vide) à Mi5 (12e case de la corde aiguë),
 * stockées en PCM 16 bits à 24 kHz dans assets/guitar et rééchantillonnées au démarrage.
 * Les clics du métronome sont synthétisés.
 */
class GuitarSounds(private val sampleRate: Int) {
    private val ord = arrayOfNulls<Sound>(128)
    private val mute = arrayOfNulls<Sound>(128)
    private val silence = Sound("silence", FloatArray(1))

    @Volatile var ready = false
        private set

    val click = render("click", 0.04) { t -> sin(2 * PI * 1000.0 * t) * 0.55 * exp(-t * 110.0) }
    val clickAccent = render("clickAccent", 0.05) { t -> sin(2 * PI * 1600.0 * t) * 0.8 * exp(-t * 90.0) }

    /** Charge les échantillons (à appeler hors du thread principal). */
    fun load(assets: AssetManager) {
        for (k in LOW..HIGH) {
            ord[k] = read(assets, "guitar/ord_$k.pcm", "ord$k")
            mute[k] = read(assets, "guitar/mute_$k.pcm", "mute$k")
        }
        ready = true
    }

    private fun read(assets: AssetManager, path: String, name: String): Sound? = try {
        val bytes = assets.open(path).use { it.readBytes() }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val src = FloatArray(bytes.size / 2) { bb.short / 32768f }
        Sound(name, resample(src, SOURCE_RATE, sampleRate))
    } catch (e: Exception) {
        null
    }

    /** Note MIDI [midi] (Mi2 = 40 … Mi5 = 76), jouée normalement ou étouffée. */
    fun note(midi: Int, kind: StrumKind = StrumKind.OPEN): Sound? {
        if (midi !in LOW..HIGH) return null
        return if (kind == StrumKind.PALM_MUTE) mute[midi] else ord[midi]
    }

    /**
     * Un coup de médiator sur une position d'accord : les cordes sonnent l'une après l'autre
     * (de la grave à l'aiguë en descendant, l'inverse en remontant), quelques ms d'écart.
     * Les cordes non jouées sont étouffées (le son précédent s'arrête).
     * En remontant, on ne gratte que les 3 ou 4 cordes aiguës, comme un vrai guitariste.
     *
     * @param sink (frame, son, gain, corde)
     */
    fun strum(
        shape: Shape, frame: Long, dir: StrumDir, kind: StrumKind = StrumKind.OPEN, gain: Float = 0.8f,
        spreadMs: Double = 9.0, sink: (Long, Sound, Float, Int) -> Unit,
    ) {
        val played = (0 until 6).filter { shape.frets[it] >= 0 }
        val strings = when {
            dir == StrumDir.UP -> played.takeLast(minOf(4, played.size)).reversed()
            kind == StrumKind.PALM_MUTE -> played.take(minOf(3, played.size))
            else -> played
        }
        for (s in 0 until 6) if (s !in strings) sink(frame, silence, 0f, s)
        val spread = (spreadMs * sampleRate / 1000).toLong()
        strings.forEachIndexed { i, s ->
            val snd = note(Notes_STANDARD[s] + shape.frets[s], kind) ?: return@forEachIndexed
            val g = gain * (if (dir == StrumDir.UP) 0.75f else 1f) * (if (i == 0) 1f else 0.9f)
            sink(frame + i * spread, snd, g, s)
        }
    }

    /** Étouffe toutes les cordes (fin d'un exemple). */
    fun muteAll(frame: Long, sink: (Long, Sound, Float, Int) -> Unit) {
        for (s in 0 until 6) sink(frame, silence, 0f, s)
    }

    private fun render(name: String, seconds: Double, gen: (Double) -> Double): Sound {
        val n = (seconds * sampleRate).toInt()
        val out = FloatArray(n)
        val fade = minOf(n / 10, sampleRate / 200)
        for (i in 0 until n) {
            var v = gen(i.toDouble() / sampleRate)
            if (i > n - fade) v *= (n - i).toDouble() / fade
            out[i] = v.toFloat()
        }
        return Sound(name, out)
    }

    companion object {
        const val LOW = 40
        const val HIGH = 76
        const val SOURCE_RATE = 24000
        private val Notes_STANDARD = intArrayOf(40, 45, 50, 55, 59, 64)

        /** Rééchantillonnage par interpolation cubique (Catmull-Rom). */
        fun resample(src: FloatArray, from: Int, to: Int): FloatArray {
            if (from == to) return src
            val n = (src.size.toLong() * to / from).toInt()
            val out = FloatArray(n)
            val step = from.toDouble() / to
            for (i in 0 until n) {
                val p = i * step
                val k = p.toInt()
                val t = (p - k).toFloat()
                val y0 = src[maxOf(0, k - 1)]
                val y1 = src[minOf(src.size - 1, k)]
                val y2 = src[minOf(src.size - 1, k + 1)]
                val y3 = src[minOf(src.size - 1, k + 2)]
                out[i] = y1 + 0.5f * t * (y2 - y0 + t * (2f * y0 - 5f * y1 + 4f * y2 - y3 + t * (3f * (y1 - y2) + y3 - y0)))
            }
            return out
        }
    }
}
