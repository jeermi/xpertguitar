package fr.xpert.guitar.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Process
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Fournit des sons à jouer à une position précise (en frames audio).
 * Appelé depuis le thread audio : doit rester rapide et ne jamais bloquer.
 *
 * @param writeFrame première frame du tampon en cours d'écriture
 * @param untilFrame planifier au moins jusqu'à cette frame
 * @param sink (frame, son, gain, corde) : corde 0..5 (6e à 1re) ou -1. Un son joué sur une corde
 *             arrête le son précédent de la même corde, comme sur une vraie guitare.
 */
fun interface Scheduler {
    fun fill(writeFrame: Long, untilFrame: Long, sink: (Long, Sound, Float, Int) -> Unit)
}

/**
 * Moteur audio basse latence basé sur AudioTrack (mode PERFORMANCE_MODE_LOW_LATENCY).
 * L'horloge de référence est la position audio (frames), convertie en temps
 * système grâce à AudioTrack.getTimestamp() : c'est ce qui permet de juger
 * un coup « en avance », « en retard » ou « pile ».
 */
class AudioEngine(context: Context) {
    val sampleRate: Int
    private val burst: Int
    val sounds: GuitarSounds

    @Volatile var scheduler: Scheduler? = null
    @Volatile var writtenFrames = 0L
        private set

    private class Immediate(val delayFrames: Long, val sound: Sound, val gain: Float, val group: Int)
    private val immediate = ConcurrentLinkedQueue<Immediate>()
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private class TsPoint(val frame: Long, val nanos: Long)
    @Volatile private var tsPoint: TsPoint? = null
    @Volatile private var startNanos = 0L
    @Volatile private var latencyFrames = 0

    init {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        sampleRate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
        burst = (am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 256)
            .coerceIn(64, 1024)
        sounds = GuitarSounds(sampleRate)
        val assets = context.assets
        Thread({ sounds.load(assets) }, "xpert-samples").start()
    }

    @Synchronized
    fun start() {
        if (running) return
        val minBytes = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT
        )
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBytes, burst * 4 * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        latencyFrames = t.bufferSizeInFrames
        writtenFrames = 0L
        tsPoint = null
        startNanos = System.nanoTime()
        track = t
        running = true
        t.play()
        thread = Thread({ renderLoop(t) }, "xpert-audio").also { it.start() }
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        thread?.join(500)
        thread = null
        track?.let {
            try {
                it.pause(); it.flush(); it.release()
            } catch (_: Exception) {
            }
        }
        track = null
        scheduler = null
    }

    /** Joue un son le plus tôt possible, éventuellement décalé de [delayFrames]. */
    fun playNow(sound: Sound, gain: Float = 1f, group: Int = -1, delayFrames: Long = 0) {
        immediate.add(Immediate(delayFrames, sound, gain, group))
    }

    /** Instant (System.nanoTime) où la frame [frame] sort du haut-parleur. */
    fun frameToNanos(frame: Long): Long {
        val p = tsPoint
        return if (p != null) {
            p.nanos + (frame - p.frame) * 1_000_000_000L / sampleRate
        } else {
            startNanos + (frame + latencyFrames) * 1_000_000_000L / sampleRate
        }
    }

    /** Frame audible à l'instant [nanos] (inverse de [frameToNanos]). */
    fun nanosToFrame(nanos: Long): Long {
        val p = tsPoint
        return if (p != null) {
            p.frame + (nanos - p.nanos) * sampleRate / 1_000_000_000L
        } else {
            (nanos - startNanos) * sampleRate / 1_000_000_000L - latencyFrames
        }
    }

    private class Pending(val frame: Long, val sound: Sound, val gain: Float, val group: Int)

    private class Voice(val sound: Sound, val gain: Float, var offset: Int, val group: Int) {
        var pos = 0
        /** Étouffement en cours : nombre d'échantillons restants avant silence (-1 = pas d'étouffement). */
        var fadeLeft = -1
        var fadeLen = 1
        var fadeFrom = -1

        /** Arrête le son en [len] échantillons à partir de la position [at] du tampon (pas de clic). */
        fun choke(at: Int, len: Int) {
            if (fadeLeft < 0) { fadeLeft = len; fadeLen = len; fadeFrom = at }
        }

        /** Mixe dans le tampon ; renvoie true quand le son est terminé. */
        fun mixInto(buf: FloatArray): Boolean {
            var i = offset
            offset = 0
            val d = sound.data
            while (i < buf.size && pos < d.size) {
                var g = gain
                if (fadeLeft >= 0 && i >= fadeFrom) {
                    if (fadeLeft == 0) return true
                    g *= fadeLeft.toFloat() / fadeLen
                    fadeLeft--
                }
                buf[i] += d[pos] * g
                i++; pos++
            }
            fadeFrom = 0
            return pos >= d.size || fadeLeft == 0
        }

        companion object {
            const val FADE = 480
        }
    }

    private fun renderLoop(t: AudioTrack) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val buf = FloatArray(burst)
        val voices = ArrayList<Voice>()
        val pending = ArrayList<Pending>()
        val ts = AudioTimestamp()
        val sink: (Long, Sound, Float, Int) -> Unit = { f, s, g, grp -> pending.add(Pending(f, s, g, grp)) }
        fun startVoice(v: Voice) {
            if (v.group >= 0) for (o in voices) if (o.group == v.group) o.choke(v.offset, Voice.FADE)
            voices.add(v)
        }
        var lastScheduler: Scheduler? = null
        var lastTsCheck = -sampleRate.toLong()
        val minAhead = sampleRate / 10L

        while (running) {
            val start = writtenFrames
            val end = start + burst

            val sch = scheduler
            if (sch !== lastScheduler) {
                pending.clear()
                lastScheduler = sch
                // Changement d'exercice ou arrêt : on étouffe la guitare en douceur.
                for (v in voices) if (v.group >= 0) v.choke(0, Voice.FADE * 4)
            }
            sch?.fill(start, end + minAhead, sink)

            while (true) {
                val p = immediate.poll() ?: break
                pending.add(Pending(start + p.delayFrames, p.sound, p.gain, p.group))
            }
            pending.sortBy { it.frame }
            val it = pending.iterator()
            while (it.hasNext()) {
                val p = it.next()
                if (p.frame < end) {
                    startVoice(Voice(p.sound, p.gain, (p.frame - start).coerceAtLeast(0L).toInt(), p.group))
                    it.remove()
                }
            }

            java.util.Arrays.fill(buf, 0f)
            val vi = voices.iterator()
            while (vi.hasNext()) {
                if (vi.next().mixInto(buf)) vi.remove()
            }
            for (i in buf.indices) {
                val x = buf[i] * 0.8f
                buf[i] = if (x > 1f) 1f else if (x < -1f) -1f else x
            }

            val n = t.write(buf, 0, burst, AudioTrack.WRITE_BLOCKING)
            if (n < 0) break
            writtenFrames = end

            if (end - lastTsCheck > sampleRate / 4) {
                lastTsCheck = end
                if (t.getTimestamp(ts)) {
                    tsPoint = TsPoint(ts.framePosition, ts.nanoTime)
                }
            }
        }
    }
}
