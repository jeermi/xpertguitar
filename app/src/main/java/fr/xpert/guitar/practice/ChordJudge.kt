package fr.xpert.guitar.practice

import fr.xpert.guitar.audio.ChordMatcher
import fr.xpert.guitar.audio.ChordStream
import fr.xpert.guitar.audio.ChordVerdict
import fr.xpert.guitar.audio.MicListener
import fr.xpert.guitar.music.Shape

/** Un accord gratté et jugé, sans tempo. */
class StrumVerdict(val shape: Shape, val verdict: ChordVerdict, val atNanos: Long)

/**
 * Juge les accords grattés librement (sans métronome) : à chaque attaque entendue,
 * on laisse sonner ~0,45 s puis on compare le profil chroma à l'accord attendu.
 * Un nouveau coup de médiator avant la fin de l'analyse raccourcit la fenêtre.
 */
class ChordJudge(private val mic: MicListener, private val stream: ChordStream) {
    private var pendingAt = -1L

    fun reset() {
        pendingAt = -1L
        mic.onsets.clear()
        stream.clear()
    }

    /** À appeler à chaque image. Renvoie un verdict quand un accord vient d'être jugé. */
    fun tick(nowNanos: Long, expected: Shape): StrumVerdict? {
        var result: StrumVerdict? = null
        while (true) {
            val o = mic.onsets.poll() ?: break
            if (pendingAt >= 0 && o.nanos - pendingAt > MIN_NANOS) {
                result = judge(pendingAt, o.nanos - 10_000_000L, expected) ?: result
            }
            pendingAt = o.nanos
        }
        if (pendingAt >= 0 && nowNanos - pendingAt >= WAIT_NANOS) {
            result = judge(pendingAt, pendingAt + WAIT_NANOS, expected) ?: result
            pendingAt = -1L
        }
        return result
    }

    private fun judge(from: Long, to: Long, expected: Shape): StrumVerdict? {
        val acc = stream.collect(from + SKIP_NANOS, to, PracticeSession.MIN_CHORD_LEVEL)
        if (acc.frames == 0) return null
        val v = ChordMatcher.verify(acc.profile(), expected.chord, expected, acc.bass())
        return StrumVerdict(expected, v, to)
    }

    companion object {
        const val WAIT_NANOS = 460_000_000L
        const val SKIP_NANOS = 50_000_000L
        const val MIN_NANOS = 220_000_000L
    }
}
