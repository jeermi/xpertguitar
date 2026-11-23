package fr.xpert.guitar.audio

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Détecteur d'attaques (coups de médiator) sur le signal du micro. Repris de XPert Drum.
 *
 * Le signal passe d'abord par une pré-accentuation (x[n] - 0,95·x[n-1]) qui fait ressortir
 * les transitoires (le médiator qui attaque les cordes) et atténue les notes qui résonnent.
 * Il est ensuite découpé en petites trames (~2,7 ms). Une attaque est une trame dont
 * l'énergie dépasse le seuil absolu [threshold] (réglé par le « Réglage du micro ») ET
 * vaut au moins [ratio] fois l'énergie moyenne des ~13 ms qui précèdent : une note
 * ou un accord qui résonne décroît, il ne redéclenche donc pas. L'instant est ensuite
 * affiné à l'échantillon près (premier échantillon qui dépasse la moitié de la crête).
 * Chaque attaque est émise ~11 ms plus tard avec son niveau (énergie de trame maximale).
 *
 * Pur Kotlin, sans Android : testable sur JVM.
 */
class OnsetDetector(sampleRate: Int, refractorySeconds: Double = 0.09) {
    @Volatile var threshold: Float = 0.03f
    @Volatile var ratio: Float = 1.6f

    private val frameSize = maxOf(64, sampleRate / 375) // 128 à 48 kHz
    private val historyLen = 6
    // Un coup de médiator fait sonner les cordes l'une après l'autre (10 à 40 ms) : une seule attaque.
    private val refractoryFrames = (sampleRate * refractorySeconds / frameSize).toInt()
    private val peakFrames = 4

    private val cur = FloatArray(frameSize)
    private val prev = FloatArray(frameSize)
    private var lastX = 0f
    private var fill = 0
    private var frameStart = 0L
    private val history = FloatArray(historyLen)
    private var histPos = 0
    private var framesSinceOnset = Int.MAX_VALUE

    private var pendingFrame = -1L
    private var pendingPeak = 0f
    private var pendingLeft = 0

    @Volatile private var peakSinceRead = 0f

    /** Énergie de trame maximale depuis le dernier appel (vumètres, mesures du réglage). Même échelle que [threshold]. */
    fun takePeak(): Float {
        val p = peakSinceRead
        peakSinceRead = 0f
        return p
    }

    fun reset() {
        fill = 0; lastX = 0f; history.fill(0f); framesSinceOnset = Int.MAX_VALUE; pendingFrame = -1L
    }

    /**
     * @param startFrame position (frames depuis le début de l'enregistrement) de buf[0]
     * @param emit (frame de l'attaque, niveau crête 0..1)
     */
    fun process(buf: FloatArray, n: Int, startFrame: Long, emit: (Long, Float) -> Unit) {
        var peak = peakSinceRead
        for (i in 0 until n) {
            if (fill == 0) frameStart = startFrame + i
            val x = buf[i] - 0.95f * lastX
            lastX = buf[i]
            cur[fill++] = x
            if (fill == frameSize) {
                val rms = analyzeFrame(emit)
                if (rms > peak) peak = rms
                System.arraycopy(cur, 0, prev, 0, frameSize)
                fill = 0
            }
        }
        peakSinceRead = peak
    }

    private fun analyzeFrame(emit: (Long, Float) -> Unit): Float {
        var sum = 0f
        var framePeak = 0f
        for (v in cur) {
            sum += v * v
            val a = abs(v)
            if (a > framePeak) framePeak = a
        }
        val rms = sqrt(sum / frameSize)
        // Moyenne des trames précédentes, sans la toute dernière : une attaque à cheval
        // sur deux trames est ainsi comparée au niveau d'avant le coup.
        val last = (histPos - 1 + historyLen) % historyLen
        var mean = 0f
        for (k in 0 until historyLen) if (k != last) mean += history[k]
        mean /= (historyLen - 1)

        if (pendingFrame >= 0) {
            if (rms > pendingPeak) pendingPeak = rms
            if (--pendingLeft <= 0) {
                emit(pendingFrame, pendingPeak)
                pendingFrame = -1L
            }
        } else if (framesSinceOnset > refractoryFrames && rms > threshold && rms > ratio * mean) {
            pendingFrame = refineStart(framePeak)
            pendingPeak = rms
            pendingLeft = peakFrames
            framesSinceOnset = 0
        }
        if (framesSinceOnset < Int.MAX_VALUE) framesSinceOnset++
        history[histPos] = rms
        histPos = (histPos + 1) % historyLen
        return rms
    }

    /** Premier échantillon (trame précédente ou courante) au-dessus de la moitié de la crête. */
    private fun refineStart(framePeak: Float): Long {
        val level = framePeak * 0.5f
        for (i in 0 until frameSize) if (abs(prev[i]) > level) return frameStart - frameSize + i
        for (i in 0 until frameSize) if (abs(cur[i]) > level) return frameStart + i
        return frameStart
    }
}
