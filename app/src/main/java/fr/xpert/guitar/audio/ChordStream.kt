package fr.xpert.guitar.audio

/** Une trame d'analyse horodatée : la fenêtre se termine à [endNanos] et dure [spanNanos]. */
class TimedChroma(val endNanos: Long, val spanNanos: Long, val frame: ChromaFrame)

/**
 * Analyse d'accords en continu sur le micro : deux analyseurs chroma (fenêtre longue de ~340 ms,
 * la plus fiable, et courte de ~170 ms pour les changements d'accords rapides), une trame
 * toutes les ~43 ms. Les trames récentes (8 s) sont gardées avec leur instant, ce qui permet
 * de juger l'accord joué entre deux instants (un temps, une mesure…).
 */
class ChordStream(sampleRate: Int) : MicConsumer {
    private val long = ChromaAnalyzer(sampleRate, 4096)
    private val short = ChromaAnalyzer(sampleRate, 2048)
    private val hop = 2048 * long.decim / 4 // ~43 ms
    private var sinceHop = 0
    private val longFrames = ArrayDeque<TimedChroma>()
    private val shortFrames = ArrayDeque<TimedChroma>()
    private val longSpan = (long.frameSeconds * 1e9).toLong()
    private val shortSpan = (short.frameSeconds * 1e9).toLong()

    /** Accordage estimé de la guitare, en cents (écart au La 440). */
    val tuningCents: Int get() = (long.tuning * 100).toInt()

    @Synchronized
    fun clear() {
        longFrames.clear(); shortFrames.clear()
    }

    override fun process(buf: FloatArray, n: Int, startFrame: Long, toNanos: (Long) -> Long) {
        long.push(buf, n)
        short.push(buf, n)
        sinceHop += n
        if (sinceHop >= hop) {
            sinceHop = 0
            val end = toNanos(startFrame + n)
            val lf = long.analyze()
            val sf = short.analyze()
            synchronized(this) {
                longFrames.addLast(TimedChroma(end, longSpan, lf))
                shortFrames.addLast(TimedChroma(end, shortSpan, sf))
                while (longFrames.size > KEEP) longFrames.removeFirst()
                while (shortFrames.size > KEEP) shortFrames.removeFirst()
            }
        }
    }

    /**
     * Profil de ce qui a sonné entre [fromNanos] et [toNanos] : uniquement les trames dont la fenêtre
     * tient entièrement dans l'intervalle. Fenêtre longue si l'intervalle le permet, sinon courte.
     * [minLevel] écarte les trames trop faibles (accord qui s'éteint, bruit de la pièce).
     */
    @Synchronized
    fun collect(fromNanos: Long, toNanos: Long, minLevel: Float = 0f): ChromaAccumulator {
        val acc = ChromaAccumulator()
        val useLong = toNanos - fromNanos >= longSpan + 40_000_000L
        val frames = if (useLong) longFrames else shortFrames
        for (f in frames) {
            if (f.endNanos - f.spanNanos >= fromNanos && f.endNanos <= toNanos && f.frame.level >= minLevel) acc.add(f.frame)
        }
        if (acc.frames == 0 && useLong) {
            for (f in shortFrames) {
                if (f.endNanos - f.spanNanos >= fromNanos && f.endNanos <= toNanos && f.frame.level >= minLevel) acc.add(f.frame)
            }
        }
        return acc
    }

    /** Dernière trame longue (affichage en direct). */
    @Synchronized
    fun latest(): TimedChroma? = longFrames.lastOrNull()

    companion object {
        const val KEEP = 200
    }
}
