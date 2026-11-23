package fr.xpert.guitar.practice

import fr.xpert.guitar.audio.AudioEngine
import fr.xpert.guitar.audio.ChordMatcher
import fr.xpert.guitar.audio.ChordStream
import fr.xpert.guitar.audio.ChordVerdict
import fr.xpert.guitar.audio.Scheduler
import fr.xpert.guitar.audio.Sound
import fr.xpert.guitar.audio.StrumDir
import fr.xpert.guitar.music.Shape
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/** Un coup de médiator attendu. */
class StrumEvent(val frame: Long, val strum: Strum, val shape: Shape) {
    @Volatile var result: Judgement? = null
    @Volatile var deltaMs: Int = 0
}

/** Un accord de la grille, du début de sa mesure jusqu'au changement suivant. */
class ChordSegment(val startFrame: Long, val endFrame: Long, val shape: Shape) {
    @Volatile var verdict: ChordVerdict? = null
    /** Jugé sans rien entendre d'exploitable (accord pas joué ou trop faible). */
    @Volatile var unheard = false
}

class BarMark(val frame: Long, val countIn: Boolean, val bpm: Int)
class BeatMark(val frame: Long, val number: Int, val countIn: Boolean)

class Feedback(val judgement: Judgement, val deltaMs: Int, val atNanos: Long, val counted: Boolean = true)

class Stats {
    var perfect = 0; var early = 0; var late = 0; var miss = 0; var extra = 0
    var chordsOk = 0; var chordsWrong = 0
    private var sumDelta = 0L
    private var nDelta = 0

    val judged get() = perfect + early + late + miss
    val chordsJudged get() = chordsOk + chordsWrong

    fun record(j: Judgement, deltaMs: Int) {
        when (j) {
            Judgement.PERFECT -> perfect++
            Judgement.EARLY -> early++
            Judgement.LATE -> late++
            Judgement.MISS -> miss++
            Judgement.EXTRA -> extra++
        }
        if (j == Judgement.PERFECT || j == Judgement.EARLY || j == Judgement.LATE) {
            sumDelta += deltaMs; nDelta++
        }
    }

    /** Précision rythmique en % : pile = 1, avance/retard = 0,5, raté = 0. */
    val accuracy: Int
        get() = if (judged == 0) 0 else ((perfect + 0.5 * (early + late)) * 100.0 / judged).toInt()

    /** Accords justes en %. */
    val chordAccuracy: Int get() = if (chordsJudged == 0) 0 else chordsOk * 100 / chordsJudged

    /** Écart moyen en ms (négatif = tendance à être en avance). */
    val meanDeltaMs: Int get() = if (nDelta == 0) 0 else (sumDelta / nDelta).toInt()
}

/**
 * Une séance de rythmique : décompte d'une mesure, puis la rythmique en boucle sur la grille
 * d'accords, avec métronome et tempo qui monte.
 *
 * Deux jugements au micro :
 *  - le placement de chaque coup de médiator (attaque entendue vs coup attendu) ;
 *  - la justesse de chaque accord : à la fin de chaque segment de la grille, le profil chroma
 *    entendu pendant le segment est comparé à l'accord attendu (ChordMatcher).
 */
class PracticeSession(
    private val engine: AudioEngine,
    val exercise: Exercise,
    startBpm: Int,
    private val rampEnabled: Boolean,
    /** Mode « écouter » : l'appli joue la rythmique elle-même, sans jugement. */
    val demo: Boolean,
    private val metronome: Boolean,
    private val calibrationMs: Int,
    private val chords: ChordStream?,
    private val countInBars: Int = 1,
) : Scheduler {
    private val sr = engine.sampleRate
    private val rhythm = exercise.rhythm ?: error("Exercice sans rythmique")
    private val sounds = engine.sounds
    private val aheadFrames = (sr * 3.0).toLong()

    private var bpm = startBpm
    private var nextBarFrame = -1.0
    private var barIndex = 0
    private var segStart = -1L
    private var segShape: Shape? = null

    val strums = CopyOnWriteArrayList<StrumEvent>()
    val segments = CopyOnWriteArrayList<ChordSegment>()
    val barMarks = CopyOnWriteArrayList<BarMark>()
    val beatMarks = CopyOnWriteArrayList<BeatMark>()
    val stats = Stats()
    var maxBpmPlayed = startBpm
        private set
    val startedAtMillis = System.currentTimeMillis()
    @Volatile var lastChordVerdict: ChordSegment? = null
        private set

    override fun fill(writeFrame: Long, untilFrame: Long, sink: (Long, Sound, Float, Int) -> Unit) {
        if (nextBarFrame < 0) {
            nextBarFrame = (writeFrame + sr / 4).toDouble()
            barIndex = -countInBars
        }
        val target = maxOf(untilFrame, writeFrame + aheadFrames)
        while (nextBarFrame < target) generateBar(sink)
    }

    private fun generateBar(sink: (Long, Sound, Float, Int) -> Unit) {
        val framesPerBeat = sr * 60.0 / bpm
        val stepFrames = framesPerBeat / rhythm.stepsPerBeat
        val barStart = nextBarFrame
        val countIn = barIndex < 0
        barMarks.add(BarMark(barStart.toLong(), countIn, bpm))
        for (b in 0 until rhythm.beatsPerBar) {
            val f = (barStart + b * framesPerBeat).toLong()
            if (metronome || countIn) {
                sink(f, if (b == 0) sounds.clickAccent else sounds.click, if (countIn) 0.9f else 0.55f, -1)
            }
            beatMarks.add(BeatMark(f, b + 1, countIn))
        }
        if (!countIn) {
            val slot = exercise.slotAt(barIndex)
            if (exercise.changesAt(barIndex) || segShape == null) {
                closeSegment(barStart.toLong())
                segStart = barStart.toLong()
                segShape = slot.shape
            }
            for (s in rhythm.strums) {
                val f = (barStart + s.step * stepFrames).toLong()
                strums.add(StrumEvent(f, s, slot.shape))
                if (demo) sounds.strum(slot.shape, f, s.dir, s.kind, rhythm.gainOf(s), sink = sink)
            }
        }
        nextBarFrame += rhythm.beatsPerBar * framesPerBeat
        barIndex++
        if (rampEnabled && barIndex > 0 && barIndex % exercise.rampEveryBars == 0 && bpm < exercise.targetBpm) {
            bpm = minOf(bpm + exercise.rampStep, exercise.targetBpm)
        }
    }

    private fun closeSegment(end: Long) {
        val sh = segShape ?: return
        if (segStart >= 0) segments.add(ChordSegment(segStart, end, sh))
    }

    fun frameNanos(frame: Long): Long = engine.frameToNanos(frame)

    /** Tempo de la mesure en train de sonner. */
    fun currentBpm(nowNanos: Long): Int {
        var b = barMarks.firstOrNull()?.bpm ?: bpm
        for (m in barMarks) {
            if (engine.frameToNanos(m.frame) <= nowNanos) b = m.bpm else break
        }
        return b
    }

    /** Numéro du temps du décompte en cours, ou null si le décompte est fini. */
    fun countInBeat(nowNanos: Long): Int? {
        var current: BeatMark? = null
        for (m in beatMarks) {
            if (engine.frameToNanos(m.frame) <= nowNanos) current = m else break
        }
        return if (current == null) 0 else if (current.countIn) current.number else null
    }

    /** Accord attendu à l'instant [nowNanos] et le suivant (pour l'affichage). */
    fun chordAt(nowNanos: Long): Pair<Shape?, Shape?> {
        var cur: StrumEvent? = null
        var next: StrumEvent? = null
        for (s in strums) {
            if (engine.frameToNanos(s.frame) <= nowNanos + 80_000_000L) cur = s
            else if (cur == null || s.shape != cur.shape) { next = s; break }
        }
        if (cur == null) cur = strums.firstOrNull()
        return cur?.shape to (next?.shape ?: cur?.shape)
    }

    /** Une attaque entendue au micro à [onsetNanos] : valide le coup attendu le plus proche. */
    fun onOnset(onsetNanos: Long): Feedback {
        val adjusted = onsetNanos - calibrationMs * 1_000_000L
        var best: StrumEvent? = null
        var bestDelta = Long.MAX_VALUE
        for (n in strums) {
            if (n.result != null) continue
            val d = adjusted - engine.frameToNanos(n.frame)
            if (abs(d) < abs(bestDelta)) {
                best = n; bestDelta = d
            }
        }
        val window = windowNanos()
        if (best == null || abs(bestDelta) > window) {
            stats.record(Judgement.EXTRA, 0)
            return Feedback(Judgement.EXTRA, 0, onsetNanos)
        }
        val ms = (bestDelta / 1_000_000L).toInt()
        val j = when {
            abs(ms) <= PERFECT_MS -> Judgement.PERFECT
            ms < 0 -> Judgement.EARLY
            else -> Judgement.LATE
        }
        best.deltaMs = ms
        best.result = j
        stats.record(j, ms)
        return Feedback(j, ms, onsetNanos)
    }

    /** Fenêtre de jugement : 160 ms, réduite à 45 % de l'écart entre deux coups pour les rythmes rapides. */
    private fun windowNanos(): Long {
        val stepNanos = 60_000_000_000L / bpm / rhythm.stepsPerBeat
        return minOf(WINDOW_NANOS, stepNanos * 45 / 100)
    }

    /** À appeler à chaque image : marque les coups ratés, juge les accords terminés, fait le ménage. */
    fun tick(nowNanos: Long) {
        val calib = calibrationMs * 1_000_000L
        val adjustedNow = nowNanos - calib
        val window = windowNanos()
        for (n in strums) {
            val t = engine.frameToNanos(n.frame)
            if (!demo && n.result == null && adjustedNow - t > window) {
                n.result = Judgement.MISS
                stats.record(Judgement.MISS, 0)
            }
            if (nowNanos - t > 4_000_000_000L) strums.remove(n)
        }
        if (!demo && chords != null) {
            for (seg in segments) {
                if (seg.verdict != null || seg.unheard) continue
                val start = engine.frameToNanos(seg.startFrame) + calib
                val end = engine.frameToNanos(seg.endFrame) + calib
                if (nowNanos < end + 120_000_000L) continue
                val acc = chords.collect(start + CHORD_SKIP_NANOS, end, MIN_CHORD_LEVEL)
                if (acc.frames == 0) {
                    seg.unheard = true
                    stats.chordsWrong++
                } else {
                    val v = ChordMatcher.verify(acc.profile(), seg.shape.chord, seg.shape, acc.bass())
                    seg.verdict = v
                    if (v.ok) stats.chordsOk++ else stats.chordsWrong++
                }
                lastChordVerdict = seg
            }
            segments.removeAll { nowNanos - engine.frameToNanos(it.endFrame) > 10_000_000_000L }
        }
        if (!demo) {
            val playing = currentBpm(nowNanos)
            if (countInBeat(nowNanos) == null && playing > maxBpmPlayed) maxBpmPlayed = playing
        }
        barMarks.removeAll { nowNanos - engine.frameToNanos(it.frame) > 8_000_000_000L }
        beatMarks.removeAll { nowNanos - engine.frameToNanos(it.frame) > 8_000_000_000L }
    }

    companion object {
        const val PERFECT_MS = 35
        const val WINDOW_NANOS = 160_000_000L
        /** On ignore le tout début de l'accord (attaque du médiator, accord précédent qui s'éteint). */
        const val CHORD_SKIP_NANOS = 60_000_000L
        const val MIN_CHORD_LEVEL = 0.0015f

        fun isUp(s: StrumEvent) = s.strum.dir == StrumDir.UP
    }
}
