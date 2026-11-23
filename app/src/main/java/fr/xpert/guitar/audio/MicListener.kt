package fr.xpert.guitar.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import android.os.Process
import java.util.concurrent.ConcurrentLinkedQueue

/** Une attaque entendue au micro (coup de médiator) : instant (horloge System.nanoTime) et niveau crête 0..1. */
class Onset(val nanos: Long, val level: Float)

/** Traitement supplémentaire du signal micro, appelé sur le thread d'enregistrement. */
fun interface MicConsumer {
    /** @param startFrame position de buf[0] ; [toNanos] convertit une position en instant System.nanoTime. */
    fun process(buf: FloatArray, n: Int, startFrame: Long, toNanos: (Long) -> Long)
}

/**
 * Écoute le micro : détecte les coups de médiator (attaques) et alimente les analyses
 * branchées dans [consumers] (accords, accordeur).
 * Les attaques sont horodatées sur la même horloge que la sortie audio
 * (AudioRecord.getTimestamp en base MONOTONIC), ce qui permet de les comparer
 * aux notes attendues.
 */
class MicListener(context: Context, val sampleRate: Int) {
    val detector = OnsetDetector(sampleRate)
    val onsets = ConcurrentLinkedQueue<Onset>()
    val consumers = java.util.concurrent.CopyOnWriteArrayList<MicConsumer>()

    private val unprocessed: Boolean = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        .getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"

    private class TsPoint(val frame: Long, val nanos: Long)
    @Volatile private var tsPoint: TsPoint? = null
    @Volatile private var running = false
    private var thread: Thread? = null
    private var record: AudioRecord? = null

    /** Vrai si l'enregistrement a bien démarré (permission accordée, micro libre). */
    @Volatile var active = false
        private set

    @SuppressLint("MissingPermission") // la permission est demandée par l'écran avant start()
    @Synchronized
    fun start(): Boolean {
        if (running) return active
        val chunk = 256
        val minBytes = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        if (minBytes <= 0) return false
        val source = if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
        val r = try {
            AudioRecord(source, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, maxOf(minBytes, chunk * 4 * 4))
        } catch (e: Exception) {
            return false
        }
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release(); return false
        }
        try {
            r.startRecording()
        } catch (e: Exception) {
            r.release(); return false
        }
        record = r
        detector.reset()
        onsets.clear()
        tsPoint = null
        running = true
        active = true
        thread = Thread({ loop(r, chunk) }, "xpert-mic").also { it.start() }
        return true
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        thread?.join(500)
        thread = null
        record?.let {
            try {
                it.stop(); it.release()
            } catch (_: Exception) {
            }
        }
        record = null
        active = false
    }

    fun frameToNanos(frame: Long): Long {
        val p = tsPoint ?: return System.nanoTime()
        return p.nanos + (frame - p.frame) * 1_000_000_000L / sampleRate
    }

    private fun loop(r: AudioRecord, chunk: Int) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val buf = FloatArray(chunk)
        val ts = AudioTimestamp()
        var framesRead = 0L
        var lastTsCheck = -sampleRate.toLong()
        var hasRealTimestamp = false
        val emit: (Long, Float) -> Unit = { f, level -> onsets.add(Onset(frameToNanos(f), level)) }

        while (running) {
            val n = r.read(buf, 0, chunk, AudioRecord.READ_BLOCKING)
            if (n <= 0) {
                if (n < 0) break else continue
            }
            val start = framesRead
            framesRead += n

            if (framesRead - lastTsCheck > sampleRate / 4) {
                lastTsCheck = framesRead
                if (r.getTimestamp(ts, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
                    tsPoint = TsPoint(ts.framePosition, ts.nanoTime)
                    hasRealTimestamp = true
                }
            }
            if (!hasRealTimestamp) {
                // Approximation : la dernière frame lue vient d'être capturée. Le réglage du micro corrige le reste.
                tsPoint = TsPoint(framesRead, System.nanoTime())
            }
            detector.process(buf, n, start, emit)
            for (c in consumers) c.process(buf, n, start, ::frameToNanos)
        }
    }
}
