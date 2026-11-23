package fr.xpert.guitar.practice

import fr.xpert.guitar.practice.ExerciseKind.CHANGES
import fr.xpert.guitar.practice.ExerciseKind.CHORD_CHECK
import fr.xpert.guitar.practice.ExerciseKind.RHYTHM

/**
 * Parcours de guitare rythmique : poser les premiers accords, les enchaîner,
 * puis gratter en rythme (noires, croches, rythmique pop), power chords étouffés,
 * styles (ballade 6/8, reggae, funk) et accords barrés.
 */
object Exercises {
    private val quarters = rhythm(1, "DDDD")
    private val halfNotes = rhythm(2, "D...D...")
    private val wholeNote = rhythm(1, "D...")
    private val eighths = rhythm(2, "DUDUDUDU")
    private val downEighths = rhythm(2, "DDDDDDDD")
    private val pop = rhythm(2, "D.DU.UDU")
    private val popShort = rhythm(2, "D.DUD.DU")
    private val folk = rhythm(2, "D.D.DUDU")

    val all: List<Exercise> = listOf(
        // 1. Premiers accords
        Exercise(
            "acc-em-e", 1, "Mi mineur et Mi majeur",
            "Les deux accords les plus simples : pose les doigts, gratte, l'appli te dit si tout sonne juste.",
            "Appuie avec le bout des doigts, juste derrière la frette. Si une corde frise ou sonne étouffée, arrondis davantage les doigts.",
            CHORD_CHECK, grid("Em E"),
        ),
        Exercise(
            "acc-am-a", 1, "La mineur et La majeur",
            "Même principe, une corde plus haut. Ne gratte pas la grosse corde de Mi.",
            "Pour La majeur, les trois doigts se serrent sur la 2e case : laisse la corde de Mi aigu sonner sous ton annulaire.",
            CHORD_CHECK, grid("Am A"),
        ),
        Exercise(
            "acc-d-dm", 1, "Ré majeur et Ré mineur",
            "Quatre cordes seulement : on part de la corde de Ré.",
            "Vise la corde de Ré avec le médiator. Si tu touches les deux cordes graves, l'accord sonne « boueux ».",
            CHORD_CHECK, grid("D Dm"),
        ),
        Exercise(
            "acc-g-c", 1, "Sol majeur et Do majeur",
            "Les deux grands classiques des chansons.",
            "Pour Do, garde la corde de Sol à vide bien claire : le doigt de la corde de Ré ne doit pas la toucher.",
            CHORD_CHECK, grid("G C"),
        ),
        Exercise(
            "acc-tous", 1, "Les 7 accords de base",
            "Mi mineur, Mi, La mineur, La, Ré, Sol, Do à la suite.",
            "Regarde le diagramme, pose tous les doigts en même temps, puis gratte. Pas grave si c'est lent au début.",
            CHORD_CHECK, grid("Em E Am A D G C"),
        ),

        // 2. Enchaîner les accords
        Exercise(
            "chg-em-am", 2, "Changement Mi mineur ↔ La mineur",
            "Une minute pour passer de l'un à l'autre le plus de fois possible, en jouant juste.",
            "Les deux accords ont la même forme : déplace les deux doigts d'une corde vers l'aigu sans les décoller de plus d'un centimètre.",
            CHANGES, grid("Em Am"),
        ),
        Exercise(
            "chg-a-d", 2, "Changement La ↔ Ré",
            "Une minute de changements entre La et Ré.",
            "Prépare le doigt qui bouge le moins, il sert de pivot. Vise 30 changements justes en une minute.",
            CHANGES, grid("A D"),
        ),
        Exercise(
            "chg-g-c", 2, "Changement Sol ↔ Do",
            "Une minute de changements entre Sol et Do.",
            "L'annulaire et le majeur descendent ensemble d'une corde : pense « bloc » plutôt que doigt par doigt.",
            CHANGES, grid("G C"),
        ),
        Exercise(
            "chg-d-g", 2, "Changement Ré ↔ Sol",
            "Une minute de changements entre Ré et Sol.",
            "Le geste le plus long du débutant. Lève les doigts juste assez, et pose d'abord le doigt de la corde grave.",
            CHANGES, grid("D G"),
        ),
        Exercise(
            "chg-e-am", 2, "Changement Mi ↔ La mineur",
            "Même forme, une corde plus haut : l'un des plus beaux enchaînements.",
            "Garde la forme de la main identique et glisse tout le bloc d'une corde.",
            CHANGES, grid("E Am"),
        ),

        // 3. Gratter en rythme
        Exercise(
            "rq-em", 3, "Rondes sur Mi mineur",
            "Un seul coup vers le bas au début de chaque mesure, et on laisse sonner.",
            "Compte « 1, 2, 3, 4 » à voix haute et gratte sur le « 1 ». Laisse sonner jusqu'au bout de la mesure.",
            RHYTHM, grid("Em"), wholeNote, 60, 90,
        ),
        Exercise(
            "rq-em-am", 3, "Blanches : Mi mineur et La mineur",
            "Deux coups vers le bas par mesure, deux mesures par accord.",
            "Change d'accord pendant le temps 4 : la main gauche doit être prête pour le « 1 ».",
            RHYTHM, grid("Em:2 Am:2"), halfNotes, 60, 90,
        ),
        Exercise(
            "rq-noires-g-d", 3, "Noires : Sol et Ré",
            "Quatre coups vers le bas par mesure, un par temps.",
            "Le poignet fait le geste, pas le bras entier. Le médiator traverse les cordes sans s'y accrocher.",
            RHYTHM, grid("G:2 D:2"), quarters, 60, 100,
        ),
        Exercise(
            "rq-noires-4", 3, "Noires : Do, La mineur, Ré, Sol",
            "La grille d'une foule de chansons, un accord par mesure.",
            "Si un changement est trop dur, joue seulement les 3 premiers temps et utilise le 4e pour changer.",
            RHYTHM, grid("C Am D G"), quarters, 60, 100,
        ),

        // 4. Croches et aller-retour
        Exercise(
            "cr-em", 4, "Croches aller-retour sur Mi mineur",
            "Vers le bas sur les temps, vers le haut entre les temps : la main fait un aller-retour régulier.",
            "Compte « 1 et 2 et 3 et 4 et ». La main descend sur les chiffres, remonte sur les « et ». Remontée plus légère, sur les cordes aiguës.",
            RHYTHM, grid("Em"), eighths, 60, 100,
        ),
        Exercise(
            "cr-g-c", 4, "Croches : Sol et Do",
            "Aller-retour continu, deux mesures par accord.",
            "Le balancier ne s'arrête jamais, même pendant le changement d'accord : au pire, la dernière remontée se joue à vide.",
            RHYTHM, grid("G:2 C:2"), eighths, 60, 100,
        ),
        Exercise(
            "cr-am-dm-e", 4, "Croches : La mineur, Ré mineur, Mi",
            "Une grille mineure, un accord par mesure.",
            "Pour Ré mineur, vise les 4 cordes aiguës en descendant ; les remontées ne prennent que 3 ou 4 cordes de toute façon.",
            RHYTHM, grid("Am Dm Am E"), eighths, 60, 96,
        ),

        // 5. La rythmique pop
        Exercise(
            "pop-g-d-em-c", 5, "La rythmique pop : Sol, Ré, Mi mineur, Do",
            "Le grattage le plus joué au monde : bas, bas-haut, haut-bas-haut.",
            "La main fait toujours l'aller-retour en croches ; elle passe juste au-dessus des cordes sur les « trous » (temps 2 et 3).",
            RHYTHM, grid("G D Em C"), pop, 60, 100,
        ),
        Exercise(
            "pop-c-g-am-f", 5, "Rythmique pop : Do, Sol, La mineur, Fa",
            "La même rythmique sur la grille « I-V-VI-IV » en Do, avec le petit Fa.",
            "Le petit Fa : l'index couché sur les deux cordes aiguës. Ne gratte que les 4 cordes aiguës.",
            RHYTHM, grid("C G Am F"), pop, 60, 96,
        ),
        Exercise(
            "pop-folk-d-a", 5, "Rythmique folk : Ré, La, Sol",
            "Bas, bas, bas-haut, bas-haut : un grattage folk très entraînant.",
            "Accentue légèrement les temps 2 et 4 pour faire « danser » la rythmique.",
            RHYTHM, grid("D:2 A G"), folk, 64, 104,
        ),
        Exercise(
            "pop-short-am-c", 5, "Rythmique pop variée : La mineur, Do, Sol, Ré",
            "Bas, bas-haut, bas, bas-haut.",
            "Une variante plus régulière : très pratique pour accompagner une chanson qu'on chante.",
            RHYTHM, grid("Am C G D"), popShort, 64, 104,
        ),

        // 6. Power chords et palm mute
        Exercise(
            "pw-e5", 6, "Power chord Mi5 en croches",
            "Huit coups vers le bas par mesure sur Mi5, les trois cordes graves.",
            "Uniquement des coups vers le bas, petits et réguliers. Avec de la saturation, c'est la base du rock.",
            RHYTHM, grid("E5"), downEighths, 70, 130,
        ),
        Exercise(
            "pw-pm-e5-g5-a5", 6, "Palm mute : Mi5, Sol5, La5",
            "Croches étouffées avec la tranche de la main sur le chevalet, un accord par mesure.",
            "Pose la tranche de la main droite sur les cordes, tout contre le chevalet : le son devient sourd et percutant mais on entend encore la note.",
            RHYTHM, grid("E5 G5 A5 G5"), rhythm(2, "dddddddd"), 70, 130,
        ),
        Exercise(
            "pw-accents", 6, "Palm mute et accents",
            "Étouffé, sauf les coups accentués qu'on laisse sonner fort.",
            "Sur les accents, soulève la main du chevalet et frappe plus fort, puis reviens aussitôt étouffer.",
            RHYTHM, grid("E5:2 C5 D5"), rhythm(2, "DdddDdDd", accents = ">...>.>."), 70, 120,
        ),
        Exercise(
            "pw-punk", 6, "Punk : power chords qui bougent",
            "Croches vers le bas, la forme de power chord se déplace sur le manche.",
            "Garde la forme index-annulaire-auriculaire bloquée et déplace tout le bras. Relâche la pression pendant le déplacement.",
            RHYTHM, grid("F5 G5 A5-5 G5"), downEighths, 80, 150,
        ),

        // 7. Styles
        Exercise(
            "st-ballade-68", 7, "Ballade en 6/8 : Sol, Mi mineur, Do, Ré",
            "Deux temps par mesure, chacun divisé en trois : bas, bas-haut / bas, bas-haut.",
            "Compte « 1 2 3, 4 5 6 » et appuie le 1 et le 4. Ça balance comme une berceuse.",
            RHYTHM, grid("G Em C D"), rhythm(3, "D.DD.D", beatsPerBar = 2, accents = ">..>.."), 50, 76,
        ),
        Exercise(
            "st-reggae", 7, "Reggae : les contretemps",
            "On ne joue qu'entre les temps, des petits coups vers le haut, courts et secs.",
            "Joue sur les « et » et étouffe juste après avec la main gauche (relâche la pression sans quitter les cordes).",
            RHYTHM, grid("Am:2 D:2"), rhythm(2, ".U.U.U.U"), 70, 100,
        ),
        Exercise(
            "st-funk", 7, "Funk en doubles croches",
            "La main fait des doubles croches, la plupart étouffées ; seuls les accents sonnent.",
            "Le poignet est très souple et ne s'arrête jamais. Les coups étouffés donnent la pulsation, les accents la couleur.",
            RHYTHM, grid("E7"), rhythm(4, "DuduDuduDuduDudu", accents = ">...>.>.>...>.>."), 60, 96,
        ),
        Exercise(
            "st-blues-shuffle", 7, "Blues en 12/8 : Mi7, La7, Si7",
            "La grille de blues en 12 mesures, avec un balancement ternaire.",
            "Bas sur le temps, haut sur la 3e croche du triolet : « Taaa-ga Taaa-ga ».",
            RHYTHM, grid("E7:4 A7:2 E7:2 B7 A7 E7 B7"), rhythm(3, "D.UD.UD.UD.U"), 60, 96, rampEveryBars = 12,
        ),

        // 8. Accords barrés
        Exercise(
            "bar-f", 8, "Le barré de Fa",
            "L'accord qui fait peur : prends le temps de faire sonner chaque corde.",
            "Index bien droit, légèrement tourné sur sa tranche, coude près du corps. C'est le poids du bras qui appuie, pas seulement le pouce.",
            CHORD_CHECK, grid("F-barre Bm"),
        ),
        Exercise(
            "bar-chg-c-f", 8, "Changement Do ↔ Fa barré",
            "Une minute de changements entre Do et le grand Fa.",
            "Ne relâche pas tout entre les deux : l'index glisse vers la 1re case pendant que les autres doigts se replacent.",
            CHANGES, grid("C F-barre"),
        ),
        Exercise(
            "bar-pop-f-bm", 8, "Grille avec barrés : Ré, Si mineur, Sol, La",
            "La rythmique pop sur une grille avec Si mineur barré.",
            "Si le barré ne sonne pas parfaitement à tempo, garde le rythme quand même : la main droite ne s'arrête pas.",
            RHYTHM, grid("D Bm G A"), pop, 60, 96,
        ),
        Exercise(
            "bar-c-f-g", 8, "Do, Fa, Sol en barrés",
            "Le Fa et le Sol en barré forme de Mi, croches aller-retour.",
            "Fa et Sol ont la même forme : déplace la main de deux cases sans relâcher complètement.",
            RHYTHM, grid("C F-barre G-barre C"), eighths, 60, 96,
        ),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }

    fun levelName(level: Int) = when (level) {
        1 -> "Niveau 1 · Premiers accords"
        2 -> "Niveau 2 · Enchaîner les accords"
        3 -> "Niveau 3 · Gratter en rythme"
        4 -> "Niveau 4 · Croches et aller-retour"
        5 -> "Niveau 5 · La rythmique pop"
        6 -> "Niveau 6 · Power chords et palm mute"
        7 -> "Niveau 7 · Styles"
        8 -> "Niveau 8 · Accords barrés"
        else -> "Niveau $level"
    }
}
