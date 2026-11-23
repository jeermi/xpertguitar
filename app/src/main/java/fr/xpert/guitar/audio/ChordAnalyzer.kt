package fr.xpert.guitar.audio

import fr.xpert.guitar.music.Chord
import fr.xpert.guitar.music.ChordType
import fr.xpert.guitar.music.Notes
import fr.xpert.guitar.music.Shape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Profil de classes de hauteur (chroma) d'une trame : l'énergie de chacune des 12 notes,
 * toutes octaves confondues. Normalisé (max = 1) ; [level] = niveau du signal analysé.
 */
class ChromaFrame(val chroma: FloatArray, val level: Float, val peaks: Int, val bass: Int = -1) {
    val silent get() = peaks == 0
}

/**
 * Extraction du chroma d'une guitare (accords joués en grattant), robuste à la saturation.
 *
 * Chaîne de traitement (inspirée du HPCP de Gómez) :
 *  1. sous-échantillonnage à ~12 kHz (filtre passe-bas FIR) : tout ce qui compte pour la
 *     hauteur d'un accord est sous 2 kHz ;
 *  2. fenêtre de Hann de [fftSize] points (341 ms par défaut), FFT ;
 *  3. blanchiment spectral : chaque raie est comparée à la moyenne de son voisinage
 *     (± 1/3 d'octave). On ne garde que les vrais pics, quelle que soit la couleur du son
 *     (micro du téléphone qui coupe les graves, ampli saturé qui gonfle les aigus) ;
 *  4. fréquence de chaque pic affinée par interpolation parabolique, poids compressé
 *     (racine de l'amplitude) pour qu'une corde forte n'écrase pas les autres ;
 *  5. accordage global estimé (moyenne circulaire des écarts en cents, lissée dans le temps) :
 *     une guitare accordée un peu bas ou haut est reconnue quand même ;
 *  6. chaque pic alimente sa classe de hauteur avec un poids cos² de l'écart au demi-ton.
 *
 * Pur Kotlin : testable sur JVM.
 */
class ChromaAnalyzer(val inputRate: Int, val fftSize: Int = 4096) {
    val decim: Int = max(1, (inputRate / 11025.0).roundToInt())
    val rate: Double = inputRate.toDouble() / decim

    // --- Sous-échantillonnage ---
    private val taps: DoubleArray = lowpass(63, 2600.0 / inputRate)
    private val hist = DoubleArray(taps.size)
    private var histPos = 0
    private var phase = 0

    // Signal sous-échantillonné (tampon circulaire)
    private val ring = DoubleArray(fftSize)
    private var ringPos = 0
    /** Nombre d'échantillons sous-échantillonnés reçus depuis le début. */
    var produced = 0L
        private set

    private val fft = Fft(fftSize)
    private val window = DoubleArray(fftSize) { 0.5 - 0.5 * cos(2 * PI * it / fftSize) }
    private val re = DoubleArray(fftSize)
    private val im = DoubleArray(fftSize)
    private val mag = DoubleArray(fftSize / 2)
    private val prefix = DoubleArray(fftSize / 2 + 1)

    /** Écart d'accordage estimé, en demi-tons (-0,5..0,5). */
    @Volatile var tuning = 0.0
        private set
    private var tunSin = 0.0
    private var tunCos = 0.0

    var minHz = 70.0
    var maxHz = 2000.0

    fun reset() {
        hist.fill(0.0); ring.fill(0.0); histPos = 0; phase = 0; ringPos = 0; produced = 0
    }

    fun resetTuning() {
        tuning = 0.0; tunSin = 0.0; tunCos = 0.0
    }

    /** Ajoute des échantillons du micro (à [inputRate]). */
    fun push(buf: FloatArray, n: Int = buf.size) {
        for (i in 0 until n) {
            hist[histPos] = buf[i].toDouble()
            histPos = (histPos + 1) % hist.size
            if (++phase >= decim) {
                phase = 0
                var acc = 0.0
                var j = histPos
                for (t in taps.indices) {
                    j = if (j == 0) hist.size - 1 else j - 1
                    acc += taps[t] * hist[j]
                }
                ring[ringPos] = acc
                ringPos = (ringPos + 1) % fftSize
                produced++
            }
        }
    }

    /** Durée (s) couverte par une trame d'analyse. */
    val frameSeconds: Double get() = fftSize / rate

    /** Analyse les [fftSize] derniers échantillons. */
    fun analyze(): ChromaFrame {
        var sumSq = 0.0
        for (i in 0 until fftSize) {
            val v = ring[(ringPos + i) % fftSize]
            sumSq += v * v
            re[i] = v * window[i]
            im[i] = 0.0
        }
        val level = sqrt(sumSq / fftSize).toFloat()
        fft.transform(re, im)
        val half = fftSize / 2
        for (k in 0 until half) mag[k] = sqrt(re[k] * re[k] + im[k] * im[k])
        prefix[0] = 0.0
        for (k in 0 until half) prefix[k + 1] = prefix[k] + mag[k]

        val binHz = rate / fftSize
        val kmin = max(2, (minHz / binHz).toInt())
        val kmax = min(half - 2, (maxHz / binHz).toInt())
        var maxM = 0.0
        for (k in kmin..kmax) if (mag[k] > maxM) maxM = mag[k]
        val chroma = FloatArray(12)
        if (maxM < 1e-6) return ChromaFrame(chroma, level, 0)

        // Pics spectraux nets (blanchis par la moyenne locale sur ± 1/3 d'octave)
        val pf = DoubleArray(MAX_PEAKS)
        val pa = DoubleArray(MAX_PEAKS)
        var np = 0
        for (k in kmin..kmax) {
            val m = mag[k]
            if (m <= mag[k - 1] || m < mag[k + 1] || m < maxM * PEAK_FLOOR) continue
            val lo = max(1, (k / ENV_SPAN).toInt())
            val hi = min(half, (k * ENV_SPAN).toInt() + 1)
            val env = (prefix[hi] - prefix[lo]) / (hi - lo)
            if (m < PEAK_CONTRAST * env) continue
            val a = ln(mag[k - 1] + 1e-12)
            val b = ln(m + 1e-12)
            val c = ln(mag[k + 1] + 1e-12)
            val den = a - 2 * b + c
            val p = if (den < 0) (0.5 * (a - c) / den).coerceIn(-0.5, 0.5) else 0.0
            val amp = exp(b - 0.25 * (a - c) * p)
            val f = (k + p) * binHz
            // Garde les MAX_PEAKS plus forts
            if (np < MAX_PEAKS) {
                pf[np] = f; pa[np] = amp; np++
            } else {
                var w = 0
                for (q in 1 until MAX_PEAKS) if (pa[q] < pa[w]) w = q
                if (amp > pa[w]) { pf[w] = f; pa[w] = amp }
            }
        }
        if (np == 0) return ChromaFrame(chroma, level, 0)

        var maxA = 0.0
        for (q in 0 until np) if (pa[q] > maxA) maxA = pa[q]

        // Accordage : moyenne circulaire des écarts au demi-ton, lissée dans le temps
        var s = 0.0
        var c = 0.0
        val midi = DoubleArray(np)
        val wts = DoubleArray(np)
        for (q in 0 until np) {
            midi[q] = 69.0 + 12.0 * log2(pf[q] / 440.0)
            wts[q] = (pa[q] / maxA).pow(COMPRESS)
            s += wts[q] * sin(2 * PI * midi[q])
            c += wts[q] * cos(2 * PI * midi[q])
        }
        tunSin = tunSin * TUNING_DECAY + s
        tunCos = tunCos * TUNING_DECAY + c
        tuning = atan2(tunSin, tunCos) / (2 * PI)

        // Basse : le pic net le plus grave (sous 350 Hz) d'un poids suffisant
        var bass = -1
        var bassF = Double.MAX_VALUE
        for (q in 0 until np) if (pf[q] < 350.0 && wts[q] >= BASS_MIN_WEIGHT && pf[q] < bassF) {
            bassF = pf[q]; bass = Notes.pc((midi[q] - tuning).roundToInt())
        }

        for (q in 0 until np) {
            val m = midi[q] - tuning
            val n = m.roundToInt()
            val d = m - n
            val cw = cos(PI * d)
            chroma[Notes.pc(n)] += (wts[q] * cw * cw).toFloat()
        }
        var mx = 0f
        for (v in chroma) if (v > mx) mx = v
        if (mx > 0f) for (i in 0 until 12) chroma[i] /= mx
        return ChromaFrame(chroma, level, np, bass)
    }

    companion object {
        const val MAX_PEAKS = 40
        const val PEAK_FLOOR = 0.01
        const val PEAK_CONTRAST = 2.0
        const val ENV_SPAN = 1.26
        const val COMPRESS = 0.5
        const val TUNING_DECAY = 0.97
        var BASS_MIN_WEIGHT = 0.2

        /** Passe-bas FIR (sinus cardinal fenêtré par Blackman), [fc] en cycles par échantillon. */
        fun lowpass(n: Int, fc: Double): DoubleArray {
            val m = n - 1
            val h = DoubleArray(n) { i ->
                val x = i - m / 2.0
                val sinc = if (x == 0.0) 2 * fc else sin(2 * PI * fc * x) / (PI * x)
                val w = 0.42 - 0.5 * cos(2 * PI * i / m) + 0.08 * cos(4 * PI * i / m)
                sinc * w
            }
            val sum = h.sum()
            for (i in h.indices) h[i] /= sum
            return h
        }
    }
}

/** Accumule plusieurs trames (le temps d'un accord) en un seul profil. */
class ChromaAccumulator {
    val sum = FloatArray(12)
    private val bassVotes = IntArray(12)
    var frames = 0
        private set

    fun add(f: ChromaFrame) {
        if (f.silent) return
        for (i in 0 until 12) sum[i] += f.chroma[i]
        if (f.bass >= 0) bassVotes[f.bass]++
        frames++
    }

    fun clear() {
        sum.fill(0f); bassVotes.fill(0); frames = 0
    }

    /** Note de basse la plus souvent entendue, ou -1. */
    fun bass(): Int {
        var b = -1
        var best = 0
        for (i in 0 until 12) if (bassVotes[i] > best) { best = bassVotes[i]; b = i }
        return if (best * 2 >= frames && frames > 0) b else -1
    }

    fun profile(): FloatArray {
        val out = sum.copyOf()
        val mx = out.maxOrNull() ?: 0f
        if (mx > 0f) for (i in 0 until 12) out[i] /= mx
        return out
    }
}

/** Un accord candidat et sa ressemblance (corrélation -1..1) avec ce qui a été entendu. */
data class ChordScore(val chord: Chord, val score: Float)

/** Verdict sur un accord attendu. */
data class ChordVerdict(
    val ok: Boolean,
    /** Ressemblance avec l'accord attendu (corrélation, -1..1). */
    val score: Float,
    /** Accord le plus ressemblant parmi tous les candidats. */
    val heard: Chord?,
    /** Notes entendues qui ne font pas partie de l'accord (classes de hauteur). */
    val extra: List<Int>,
    /** Notes de l'accord qu'on n'entend pas, ou à peine. */
    val missing: List<Int>,
    /** Rien d'assez fort pour juger. */
    val silent: Boolean = false,
    /** Corde (0 = 6e corde, Mi grave) qui semble jouée une ou deux cases à côté, ou -1. */
    val wrongString: Int = -1,
    /** Écart probable sur cette corde, en cases (négatif = trop bas). */
    val wrongDelta: Int = 0,
)

/**
 * Reconnaissance d'accords par comparaison du profil chroma à des modèles.
 *
 * Chaque modèle contient les notes de l'accord ET leurs harmoniques (une corde de La
 * produit aussi du Mi, du Do#…), avec un poids qui décroît : c'est ce qui permet de ne pas
 * confondre Do majeur et La mineur, ou de reconnaître un power chord saturé.
 * Pour une position précise (Shape), le modèle est construit à partir des notes réellement
 * jouées sur chaque corde (doublures comprises), puis comparé par corrélation.
 */
object ChordMatcher {
    var harmonics = 6
    var decay = 0.9

    private val templateCache = HashMap<Chord, FloatArray>()

    /** Modèle d'une liste de notes MIDI (avec harmoniques sous 2 kHz). */
    fun templateOfNotes(notes: List<Int>): FloatArray {
        val t = FloatArray(12)
        for (n in notes) {
            var w = 1.0
            for (h in 1..harmonics) {
                val m = n + 12.0 * log2(h.toDouble())
                if (Notes.freq(m) > 2000.0) break
                val r = m.roundToInt()
                // Harmoniques désaccordées (7e, 11e…) : poids réduit selon l'écart
                val d = m - r
                val cw = cos(PI * d)
                t[Notes.pc(r)] += (w * cw * cw).toFloat()
                w *= decay
            }
        }
        val mx = t.maxOrNull() ?: 1f
        for (i in 0 until 12) t[i] /= mx
        return t
    }

    /** Modèle générique d'un accord : fondamentale à la 5e corde, notes dans une position serrée, fondamentale doublée. */
    fun template(chord: Chord): FloatArray = templateCache.getOrPut(chord) {
        val base = 45 + ((chord.root - 9 + 12) % 12) // fondamentale entre La2 et Sol#3
        val notes = ArrayList<Int>()
        notes.add(base)
        notes.add(base + 12)
        for (iv in chord.type.intervals) if (iv != 0) notes.add(base + iv + 12)
        if (chord.type.intervals.contains(7)) notes.add(base + 7)
        templateOfNotes(notes)
    }

    fun correlation(a: FloatArray, b: FloatArray): Float {
        var ma = 0.0
        var mb = 0.0
        for (i in 0 until 12) { ma += a[i]; mb += b[i] }
        ma /= 12; mb /= 12
        var num = 0.0
        var da = 0.0
        var db = 0.0
        for (i in 0 until 12) {
            val x = a[i] - ma
            val y = b[i] - mb
            num += x * y; da += x * x; db += y * y
        }
        if (da < 1e-12 || db < 1e-12) return 0f
        return (num / sqrt(da * db)).toFloat()
    }

    /** Tous les accords candidats classés du plus au moins ressemblant. */
    fun rank(profile: FloatArray, bass: Int = -1, candidates: List<Chord> = Chord.ALL): List<ChordScore> =
        candidates.map {
            val bonus = if (bass >= 0 && it.root == bass) bassBonus else 0f
            ChordScore(it, correlation(profile, template(it)) - penalty(it) + bonus)
        }.sortedByDescending { it.score }

    /** Bonus de l'accord dont la fondamentale est la note de basse entendue (Fa maj7 plutôt que La mineur). */
    var bassBonus = 0.06f

    /**
     * Pénalité des accords à 4 sons : les harmoniques d'un simple accord parfait ressemblent
     * un peu à une 7e ou une 9e. On ne choisit l'accord enrichi que s'il colle nettement mieux.
     */
    var richPenalty = 0.04f
    var powerPenalty = -0.03f
    fun penalty(c: Chord): Float = when (c.type.intervals.size) {
        2 -> powerPenalty
        3 -> 0f
        else -> richPenalty
    }

    /** Score d'une position précise : le meilleur entre le modèle de la position et le modèle générique. */
    fun scoreShape(profile: FloatArray, shape: Shape): Float =
        max(correlation(profile, templateOfNotes(shape.midiNotes)), correlation(profile, template(shape.chord)))

    /**
     * L'accord [expected] (position [shape] si connue) a-t-il été joué ?
     * Accepté si sa ressemblance dépasse [minScore] et qu'aucun autre accord ne ressemble
     * nettement plus à ce qu'on entend — sauf un accord qui le contient (Do7 au lieu de Do :
     * une corde à vide qui sonne en trop n'est pas une faute de rythmique).
     */
    fun verify(profile: FloatArray, expected: Chord, shape: Shape? = null, bass: Int = -1, minScore: Float = MIN_SCORE): ChordVerdict {
        if (profile.all { it == 0f }) return ChordVerdict(false, 0f, null, emptyList(), emptyList(), silent = true)
        val exp = if (shape != null) scoreShape(profile, shape) else correlation(profile, template(expected))
        val best = rank(profile, bass).first()
        // Les rivaux sont comparés sans pénalité : un accord enrichi qui colle mieux est un vrai rival.
        val rival = Chord.ALL.map { ChordScore(it, correlation(profile, template(it))) }
            .sortedByDescending { it.score }.firstOrNull { !it.chord.contains(expected) && !expected.contains(it.chord) }
        val tolerated = rival == null || exp >= rival.score - MARGIN

        // Diagnostic : notes en trop / qui manquent
        val pcs = expected.pitchClasses
        val tmpl = if (shape != null) templateOfNotes(shape.midiNotes) else template(expected)
        val extra = (0 until 12).filter { it !in pcs && profile[it] > extraLevel && profile[it] > tmpl[it] + extraMargin }
            .sortedByDescending { profile[it] }
        val missing = pcs.filter { profile[it] < 0.2f }.sortedBy { profile[it] }
        // Un doigt à côté ? On compare la position attendue à la même position avec une corde
        // jouée une ou deux cases plus haut / plus bas : si une variante colle nettement mieux,
        // c'est très probablement cette corde qui sonne faux.
        var wrongString = -1
        var wrongDelta = 0
        if (shape != null) {
            val played = (0 until 6).filter { shape.frets[it] >= 0 }
            var base = correlation(profile, templateOfNotes(shape.midiNotes))
            // Une corde étouffée par mégarde n'est pas une fausse note : la position sans cette corde sert aussi de référence.
            if (played.size > 3) for (si in played) {
                val notes = played.filter { it != si }.map { Notes.STANDARD_TUNING[it] + shape.frets[it] }
                base = max(base, correlation(profile, templateOfNotes(notes)))
            }
            var bestVar = base + wrongMargin
            for (si in 0 until 6) {
                if (shape.frets[si] < 0) continue
                for (d in intArrayOf(-2, -1, 1, 2)) {
                    if (shape.frets[si] + d < 0) continue
                    val notes = (0 until 6).filter { shape.frets[it] >= 0 }
                        .map { Notes.STANDARD_TUNING[it] + shape.frets[it] + if (it == si) d else 0 }
                    if (notes.all { Notes.pc(it) in pcs }) continue
                    val sv = correlation(profile, templateOfNotes(notes))
                    if (sv > bestVar) { bestVar = sv; wrongString = si; wrongDelta = d }
                }
            }
        }
        val ok = exp >= minScore && tolerated && wrongString < 0 && (!strictExtra || extra.isEmpty())
        return ChordVerdict(ok, exp, best.chord, extra, missing, wrongString = wrongString, wrongDelta = wrongDelta)
    }

    /** Une note étrangère à l'accord, nettement plus forte que ce que les harmoniques expliquent, fait refuser l'accord. */
    var strictExtra = false
    /** Avance qu'une variante « un doigt à côté » doit avoir pour faire refuser l'accord. */
    var wrongMargin = 0.04f
    var extraLevel = 0.45f
    var extraMargin = 0.3f

    const val MIN_SCORE = 0.6f
    const val MARGIN = 0.05f
}
