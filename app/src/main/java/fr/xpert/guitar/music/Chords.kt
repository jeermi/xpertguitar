package fr.xpert.guitar.music

/** Catégories du dictionnaire d'accords, dans l'ordre d'apprentissage. */
enum class ShapeFamily(val title: String, val subtitle: String) {
    OPEN("Accords ouverts", "Les premiers accords, avec des cordes à vide"),
    SEVENTH("Accords de septième", "Blues, country, funk : la couleur « 7 »"),
    COLOR("Couleurs", "sus2, sus4, maj7, m7 : quelques doigts qui changent tout"),
    POWER("Power chords", "Deux ou trois notes, la base du rock avec de la saturation"),
    BARRE("Accords barrés", "L'index à plat : un accord qu'on déplace sur tout le manche"),
}

/** Dictionnaire des positions d'accords utilisées dans les leçons et les exercices. */
object Chords {
    private val byFamily = LinkedHashMap<ShapeFamily, List<Shape>>()

    init {
        byFamily[ShapeFamily.OPEN] = listOf(
            Shape.of("Em", "Em", "022000", "023000", tip = "Le plus facile : deux doigts, toutes les cordes sonnent."),
            Shape.of("E", "E", "022100", "023100", tip = "Comme Mi mineur, plus l'index sur la 1re case de la corde de Sol."),
            Shape.of("Am", "Am", "x02210", "x02310", tip = "La même forme que Mi majeur, déplacée d'une corde vers l'aigu. Ne gratte pas la 6e corde."),
            Shape.of("A", "A", "x02220", "x01230", tip = "Trois doigts serrés sur la 2e case. Ne gratte pas la 6e corde."),
            Shape.of("D", "D", "xx0232", "xx0132", tip = "Gratte seulement les 4 cordes aiguës, en partant de la corde de Ré."),
            Shape.of("Dm", "Dm", "xx0231", "xx0231", tip = "Gratte seulement les 4 cordes aiguës."),
            Shape.of("G", "G", "320003", "210003", tip = "Toutes les cordes sonnent. Le majeur sur la 6e corde, l'annulaire sur la 1re."),
            Shape.of("C", "C", "x32010", "x32010", tip = "Les doigts en escalier. Ne gratte pas la 6e corde et garde le Sol à vide bien clair."),
            Shape.of("F", "F", "xx3211", "xx3211", tip = "Le petit Fa : l'index couché sur les deux cordes aiguës, avant le grand barré."),
        )
        byFamily[ShapeFamily.SEVENTH] = listOf(
            Shape.of("E7", "E7", "020100", "020100"),
            Shape.of("A7", "A7", "x02020", "x02030"),
            Shape.of("D7", "D7", "xx0212", "xx0213"),
            Shape.of("G7", "G7", "320001", "320001"),
            Shape.of("C7", "C7", "x32310", "x32410"),
            Shape.of("B7", "B7", "x21202", "x21304", tip = "Le passage obligé pour jouer en Mi : quatre doigts, la 6e corde muette."),
        )
        byFamily[ShapeFamily.COLOR] = listOf(
            Shape.of("Em7", "Em7", "020000", "010000"),
            Shape.of("Am7", "Am7", "x02010", "x02010"),
            Shape.of("Cmaj7", "Cmaj7", "x32000", "x32000"),
            Shape.of("Fmaj7", "Fmaj7", "xx3210", "xx3210"),
            Shape.of("Dsus4", "Dsus4", "xx0233", "xx0134"),
            Shape.of("Dsus2", "Dsus2", "xx0230", "xx0130"),
            Shape.of("Asus4", "Asus4", "x02230", "x01230"),
            Shape.of("Asus2", "Asus2", "x02200", "x01200"),
        )
        byFamily[ShapeFamily.POWER] = listOf(
            Shape.of("E5", "E5", "022xxx", "013xxx", tip = "Gratte seulement les 3 cordes graves. Avec de la saturation, c'est le son du rock."),
            Shape.of("A5", "A5", "x022xx", "x013xx"),
            Shape.of("D5", "D5", "xx023x", "xx013x"),
            Shape.of("F5", "F5", "133xxx", "134xxx", tip = "Index sur la note grave, annulaire et auriculaire deux cases plus loin. La même forme se déplace partout."),
            Shape.of("G5", "G5", "355xxx", "134xxx"),
            Shape.of("A5-5", "A5", "577xxx", "134xxx"),
            Shape.of("C5", "C5", "x355xx", "x134xx"),
            Shape.of("D5-5", "D5", "x577xx", "x134xx"),
            Shape.of("B5", "B5", "x244xx", "x134xx"),
        )
        byFamily[ShapeFamily.BARRE] = listOf(
            Shape.of("F-barre", "F", "133211", "134211", barre = 1, tip = "Le grand barré : l'index couché sur les 6 cordes, le reste en forme de Mi majeur."),
            Shape.of("Fm-barre", "Fm", "133111", "134111", barre = 1),
            Shape.of("G-barre", "G", "355433", "134211", barre = 3, tip = "La forme de Fa déplacée en 3e case : on obtient Sol."),
            Shape.of("Bm", "Bm", "x24432", "x13421", barre = 2, tip = "Barré en forme de La mineur, en 2e case. Ne gratte pas la 6e corde."),
            Shape.of("B", "B", "x24442", "x12341", barre = 2),
            Shape.of("Bb", "Bb", "x13331", "x12341", barre = 1),
            Shape.of("C-barre", "C", "x35553", "x12341", barre = 3),
            Shape.of("F#m", "F#m", "244222", "134111", barre = 2),
            Shape.of("Cm", "Cm", "x35543", "x13421", barre = 3),
        )
    }

    val families: Map<ShapeFamily, List<Shape>> get() = byFamily
    val all: List<Shape> = byFamily.values.flatten()

    fun get(id: String): Shape = all.firstOrNull { it.id == id } ?: error("Position inconnue : $id")
}
