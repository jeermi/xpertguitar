package fr.xpert.guitar.practice

import fr.xpert.guitar.audio.AudioEngine
import fr.xpert.guitar.audio.Scheduler
import fr.xpert.guitar.audio.Sound
import fr.xpert.guitar.audio.StrumDir
import fr.xpert.guitar.audio.StrumKind
import fr.xpert.guitar.music.Shape

/**
 * Joue un exemple puis se tait : un accord (grattage lent, puis arpège corde par corde)
 * ou une rythmique sur une grille d'accords, avec ou sans clic.
 */
class StrumPlayer private constructor(
    private val engine: AudioEngine,
    private val plan: (Long, Double, (Long, Sound, Float, Int) -> Unit) -> Unit,
    val durationMs: Long,
) : Scheduler {
    private var scheduled = false

    override fun fill(writeFrame: Long, untilFrame: Long, sink: (Long, Sound, Float, Int) -> Unit) {
        if (scheduled) return
        scheduled = true
        plan(writeFrame + engine.sampleRate / 10, engine.sampleRate.toDouble(), sink)
    }

    companion object {
        /** Un accord gratté, puis ses cordes une à une (on entend chaque note), puis gratté à nouveau. */
        fun chord(engine: AudioEngine, shape: Shape, arpeggio: Boolean = true): StrumPlayer {
            val played = (0 until 6).filter { shape.frets[it] >= 0 }
            val arpMs = if (arpeggio) played.size * 350L + 400L else 0L
            return StrumPlayer(engine, { start, sr, sink ->
                val s = engine.sounds
                s.strum(shape, start, StrumDir.DOWN, gain = 0.85f, spreadMs = 25.0, sink = sink)
                if (arpeggio) {
                    val a = start + (1.6 * sr).toLong()
                    played.forEachIndexed { i, str ->
                        val snd = s.note(fr.xpert.guitar.music.Notes.STANDARD_TUNING[str] + shape.frets[str]) ?: return@forEachIndexed
                        sink(a + (i * 0.35 * sr).toLong(), snd, 0.8f, str)
                    }
                    s.strum(shape, a + (arpMs / 1000.0 * sr).toLong(), StrumDir.DOWN, gain = 0.85f, spreadMs = 14.0, sink = sink)
                }
            }, 1600L + arpMs + 2200L)
        }

        /** Une rythmique sur une grille, [bars] mesures, avec clic si [click]. */
        fun rhythm(engine: AudioEngine, rhythm: Rhythm, slots: List<Slot>, bpm: Int, bars: Int, click: Boolean): StrumPlayer {
            val total = slots.sumOf { it.bars }
            return StrumPlayer(engine, { start, sr, sink ->
                val s = engine.sounds
                val beat = sr * 60.0 / bpm
                val step = beat / rhythm.stepsPerBeat
                for (b in 0 until bars) {
                    var k = b % total
                    var shape = slots.last().shape
                    for (sl in slots) { if (k < sl.bars) { shape = sl.shape; break }; k -= sl.bars }
                    val barStart = start + b * rhythm.beatsPerBar * beat
                    if (click) for (i in 0 until rhythm.beatsPerBar) {
                        sink((barStart + i * beat).toLong(), if (i == 0) s.clickAccent else s.click, if (i == 0) 0.6f else 0.4f, -1)
                    }
                    for (st in rhythm.strums) {
                        s.strum(shape, (barStart + st.step * step).toLong(), st.dir, st.kind, rhythm.gainOf(st), sink = sink)
                    }
                }
                s.muteAll(start + (bars * rhythm.beatsPerBar * beat).toLong(), sink)
            }, bars * rhythm.beatsPerBar * 60_000L / bpm + 400L)
        }

        /** Une seule note (accordeur : la corde à vide de référence). */
        fun note(engine: AudioEngine, midi: Int, string: Int): StrumPlayer =
            StrumPlayer(engine, { start, _, sink ->
                engine.sounds.note(midi, StrumKind.OPEN)?.let { sink(start, it, 0.9f, string) }
            }, 2800L)
    }
}
