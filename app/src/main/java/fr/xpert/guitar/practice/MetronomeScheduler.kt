package fr.xpert.guitar.practice

import fr.xpert.guitar.audio.AudioEngine
import fr.xpert.guitar.audio.Scheduler
import fr.xpert.guitar.audio.Sound
import java.util.concurrent.CopyOnWriteArrayList

/** Métronome simple, avec option « tempo qui monte ». Réglages modifiables pendant la lecture. */
class MetronomeScheduler(
    private val engine: AudioEngine,
    bpm: Int,
    @Volatile var beatsPerBar: Int = 4,
    @Volatile var accent: Boolean = true,
) : Scheduler {
    @Volatile var bpm: Int = bpm
    @Volatile var rampEnabled = false
    @Volatile var rampStep = 4
    @Volatile var rampEveryBars = 4
    @Volatile var rampTarget = 120

    private var nextBeat = -1.0
    private var beatIndex = 0
    val beatMarks = CopyOnWriteArrayList<BeatMark>()

    override fun fill(writeFrame: Long, untilFrame: Long, sink: (Long, Sound, Float, Int) -> Unit) {
        val sr = engine.sampleRate
        // Premier passage, redémarrage après une pause ou relance du moteur audio : on repart proprement.
        if (nextBeat < 0 || nextBeat < writeFrame - sr / 2 || nextBeat > writeFrame + 10L * sr) {
            nextBeat = (writeFrame + sr / 10).toDouble()
            beatIndex = 0
        }
        while (nextBeat < untilFrame) {
            val beats = beatsPerBar.coerceAtLeast(1)
            val number = beatIndex % beats + 1
            val f = nextBeat.toLong()
            val first = number == 1 && accent
            sink(f, if (first) engine.sounds.clickAccent else engine.sounds.click, if (first) 0.9f else 0.6f, -1)
            beatMarks.add(BeatMark(f, number, false))
            if (beatMarks.size > 64) beatMarks.removeAt(0)
            nextBeat += sr * 60.0 / bpm.coerceIn(20, 300)
            beatIndex++
            if (rampEnabled && beatIndex % (beats * rampEveryBars.coerceAtLeast(1)) == 0 && bpm < rampTarget) {
                bpm = minOf(bpm + rampStep, rampTarget)
            }
        }
    }

    /** Dernier temps entendu à [nowNanos]. */
    fun lastBeat(nowNanos: Long): BeatMark? {
        var current: BeatMark? = null
        for (m in beatMarks) {
            if (engine.frameToNanos(m.frame) <= nowNanos) current = m else break
        }
        return current
    }
}
