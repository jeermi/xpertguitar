package fr.xpert.guitar.music

/** Notes, types d'accords et positions (doigtés) des accords sur le manche. Pur Kotlin. */
object Notes {
    /** Noms anglo-saxons (utilisés dans les grilles d'accords) et français. */
    val EN = arrayOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
    val FR = arrayOf("Do", "Do#", "Ré", "Mib", "Mi", "Fa", "Fa#", "Sol", "Lab", "La", "Sib", "Si")

    /** Cordes à vide en accordage standard, de la 6e (Mi grave) à la 1re (Mi aigu), en numéros MIDI. */
    val STANDARD_TUNING = intArrayOf(40, 45, 50, 55, 59, 64)
    val STRING_NAMES = arrayOf("Mi grave", "La", "Ré", "Sol", "Si", "Mi aigu")

    fun pc(midi: Int) = ((midi % 12) + 12) % 12
    fun nameFr(midi: Int) = FR[pc(midi)]
    fun nameEn(midi: Int) = EN[pc(midi)]
    fun octave(midi: Int) = midi / 12 - 1
    fun freq(midi: Double) = 440.0 * Math.pow(2.0, (midi - 69.0) / 12.0)
}

enum class ChordType(val suffix: String, val nameFr: String, val intervals: IntArray) {
    MAJ("", "majeur", intArrayOf(0, 4, 7)),
    MIN("m", "mineur", intArrayOf(0, 3, 7)),
    DOM7("7", "septième", intArrayOf(0, 4, 7, 10)),
    MIN7("m7", "mineur septième", intArrayOf(0, 3, 7, 10)),
    MAJ7("maj7", "majeur septième", intArrayOf(0, 4, 7, 11)),
    SUS2("sus2", "sus2", intArrayOf(0, 2, 7)),
    SUS4("sus4", "sus4", intArrayOf(0, 5, 7)),
    POWER("5", "power chord", intArrayOf(0, 7)),
    DIM("dim", "diminué", intArrayOf(0, 3, 6)),
    ADD9("add9", "add9", intArrayOf(0, 2, 4, 7)),
    SIX("6", "sixte", intArrayOf(0, 4, 7, 9)),
}

/** Un accord abstrait : fondamentale (classe de hauteur 0..11) + type. */
data class Chord(val root: Int, val type: ChordType) {
    val symbol: String get() = Notes.EN[root] + type.suffix
    val nameFr: String get() = Notes.FR[root] + " " + type.nameFr
    val pitchClasses: Set<Int> get() = type.intervals.map { (root + it) % 12 }.toSet()

    /** Vrai si toutes les notes de [other] sont dans cet accord (ex. C7 contient C, A contient A5). */
    fun contains(other: Chord) = pitchClasses.containsAll(other.pitchClasses)

    companion object {
        val ALL: List<Chord> = (0 until 12).flatMap { r -> ChordType.entries.map { Chord(r, it) } }

        /** « Am », « F#m7 », « Bb », « E5 »… */
        fun parse(symbol: String): Chord {
            val rootLen = if (symbol.length > 1 && (symbol[1] == '#' || symbol[1] == 'b')) 2 else 1
            val rootName = symbol.substring(0, rootLen)
            val alt = mapOf("Db" to "C#", "D#" to "Eb", "Gb" to "F#", "G#" to "Ab", "A#" to "Bb")
            val root = Notes.EN.indexOf(alt[rootName] ?: rootName)
            require(root >= 0) { "Note inconnue : $symbol" }
            val suffix = symbol.substring(rootLen)
            val type = ChordType.entries.firstOrNull { it.suffix == suffix } ?: error("Type inconnu : $symbol")
            return Chord(root, type)
        }
    }
}

/**
 * Une position d'accord sur le manche.
 * @param frets de la 6e à la 1re corde : « x » = corde non jouée, « 0 » = à vide, chiffre = case (jusqu'à 9) ;
 *              au-delà, séparer par des espaces (« 8 10 10 9 8 8 »).
 * @param fingers doigts de la main gauche, même alignement : 0 = aucun, 1 index, 2 majeur, 3 annulaire, 4 auriculaire.
 * @param barre case du barré (index posé à plat sur plusieurs cordes), ou 0.
 */
data class Shape(
    val id: String,
    val chord: Chord,
    val frets: IntArray,
    val fingers: IntArray,
    val barre: Int = 0,
    val tip: String = "",
) {
    /** Notes MIDI jouées, de la corde grave à l'aiguë. */
    val midiNotes: List<Int> get() = frets.indices.filter { frets[it] >= 0 }.map { Notes.STANDARD_TUNING[it] + frets[it] }
    val symbol get() = chord.symbol
    val playedStrings: Int get() = frets.count { it >= 0 }
    val lowestString: Int get() = frets.indexOfFirst { it >= 0 }
    val minFret: Int get() = frets.filter { it > 0 }.minOrNull() ?: 0
    val maxFret: Int get() = frets.maxOrNull() ?: 0

    override fun equals(other: Any?) = other is Shape && other.id == id
    override fun hashCode() = id.hashCode()

    companion object {
        fun of(id: String, symbol: String, frets: String, fingers: String, barre: Int = 0, tip: String = ""): Shape {
            fun parse(s: String): IntArray =
                (if (s.contains(' ')) s.trim().split(Regex("\\s+")) else s.map { it.toString() })
                    .map { if (it == "x") -1 else it.toInt() }.toIntArray()
            val f = parse(frets)
            val g = parse(fingers).map { maxOf(0, it) }.toIntArray()
            require(f.size == 6 && g.size == 6) { "6 cordes attendues : $id" }
            return Shape(id, Chord.parse(symbol), f, g, barre, tip)
        }
    }
}
