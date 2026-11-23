package fr.xpert.guitar.audio

import kotlin.math.sqrt

/** Une mesure de l'accordeur : fréquence (Hz), netteté 0..1 et niveau. */
class PitchReading(val hz: Double, val clarity: Double, val level: Float, val atNanos: Long)

/** Accordeur branché sur le micro : sous-échantillonne par 2 et lance YIN toutes les ~20 ms. */
class TunerStream(sampleRate: Int) : MicConsumer {
    private val rate = sampleRate / 2
    private val window = 2048
    private val detector = PitchDetector(rate, window, minHz = 60.0, maxHz = 1000.0)
    private val ring = FloatArray(window)
    private val lin = FloatArray(window)
    private var pos = 0
    private var odd = false
    private var prev = 0f
    private var since = 0

    @Volatile var latest: PitchReading? = null
        private set

    override fun process(buf: FloatArray, n: Int, startFrame: Long, toNanos: (Long) -> Long) {
        for (i in 0 until n) {
            if (odd) {
                ring[pos] = (prev + buf[i]) * 0.5f
                pos = (pos + 1) % window
                since++
            } else {
                prev = buf[i]
            }
            odd = !odd
        }
        if (since >= window / 4) {
            since = 0
            var sum = 0.0
            for (i in 0 until window) {
                val v = ring[(pos + i) % window]
                lin[i] = v
                sum += v * v
            }
            val level = sqrt(sum / window).toFloat()
            val r = if (level > 0.002f) detector.detect(lin) else null
            latest = if (r != null) PitchReading(r.first, r.second, level, toNanos(startFrame + n)) else null
        }
    }
}
