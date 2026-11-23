package fr.xpert.guitar.content

import fr.xpert.guitar.practice.Rhythm
import fr.xpert.guitar.practice.Slot
import fr.xpert.guitar.practice.grid
import fr.xpert.guitar.practice.rhythm

/** Une rythmique à écouter depuis une leçon ([bars] mesures sur la grille, avec ou sans clic). */
data class Example(val rhythm: Rhythm, val grid: List<Slot>, val bpm: Int, val bars: Int = 2, val click: Boolean = true)

sealed interface Block {
    data class Text(val text: String) : Block
    data class Section(val title: String) : Block

    /** Un conseil illustré, avec éventuellement un accord ou une rythmique à écouter. */
    data class Advice(
        val text: String,
        val illus: Illus,
        val title: String? = null,
        val chord: String? = null,
        val example: Example? = null,
    ) : Block

    /** Un accord : diagramme + écoute. */
    data class Chord(val shapeId: String, val text: String) : Block

    data class Tip(val text: String) : Block
    data class Warning(val text: String) : Block
}

data class Lesson(
    val id: String,
    val icon: String,
    val title: String,
    val summary: String,
    val blocks: List<Block>,
)

/** Les bases de la guitare rythmique : à lire avant (et entre) les exercices. */
object Lessons {
    val all = listOf(
        Lesson(
            "materiel", "🎸", "Ta guitare et ton ampli",
            "Les parties de la guitare, les micros, brancher l'ampli, son clair ou saturé.",
            listOf(
                Block.Text(
                    "Une guitare électrique ne fait presque pas de bruit toute seule : ce sont les micros qui captent la vibration " +
                        "des cordes, et l'ampli qui la transforme en son. Avant de jouer, prenons deux minutes pour connaître ton instrument."
                ),
                Block.Advice(
                    "La tête porte les mécaniques (pour accorder), le manche porte les frettes. Une « case », c'est l'espace entre deux frettes : " +
                        "on appuie dans la case pour changer la note. Sur le corps, les micros, le chevalet (où les cordes sont fixées), " +
                        "les boutons de volume et de tonalité, le sélecteur de micros et la prise jack.",
                    Illus.GUITAR_PARTS, "Les parties de la guitare",
                ),
                Block.Advice(
                    "Le micro côté manche donne un son doux et rond, celui côté chevalet un son brillant et mordant. " +
                        "Pour accompagner en son clair, commence par le micro manche ou une position du milieu.",
                    Illus.PICKUP_SELECTOR, "Le sélecteur de micros",
                ),
                Block.Section("L'ampli"),
                Block.Advice(
                    "Volume de l'ampli à zéro, branche le câble jack (guitare puis ampli), allume, puis monte doucement. " +
                        "Fais pareil à l'envers pour débrancher : ça évite un gros « boum » dans le haut-parleur.",
                    Illus.PLUG_IN, "Brancher sans risque",
                ),
                Block.Advice(
                    "Le gain règle la saturation (la « disto »), le volume règle le niveau sonore. Basses, médiums et aigus " +
                        "règlent la couleur : laisse-les au milieu pour commencer.",
                    Illus.AMP_KNOBS, "Les boutons",
                ),
                Block.Advice(
                    "Son clair : gain bas, chaque note est nette (pop, folk, funk, reggae). Crunch : un peu de gain, le son « grogne » quand " +
                        "on gratte fort (blues, rock). Saturé : beaucoup de gain (hard rock, metal), à jouer surtout en power chords.",
                    Illus.CLEAN_CRUNCH, "Clair, crunch, saturé",
                    chord = "E5",
                ),
                Block.Tip(
                    "Pour apprendre les accords, préfère un son clair : on entend tout de suite une corde qui frise ou une note étouffée. " +
                        "La saturation cache les défauts."
                ),
            ),
        ),
        Lesson(
            "posture", "🪑", "S'installer",
            "Assis ou debout, l'angle du manche, les épaules.",
            listOf(
                Block.Text("Une bonne position évite les douleurs et rend les accords bien plus faciles. Prends l'habitude de vérifier ces points à chaque séance."),
                Block.Advice(
                    "Assieds-toi sur une chaise sans accoudoirs, dos droit, pieds à plat. Pose la guitare sur la cuisse droite " +
                        "(gauche si tu es gaucher), le corps de la guitare contre ton ventre.",
                    Illus.SEATED, "Assis",
                ),
                Block.Advice(
                    "Debout, règle la sangle pour que la guitare soit à peu près à la même hauteur qu'assis. Trop basse, elle " +
                        "tord le poignet gauche et rend les accords presque impossibles.",
                    Illus.STANDING_STRAP, "Debout, avec une sangle",
                ),
                Block.Advice(
                    "Relève un peu la tête de la guitare (10 à 20°) : la main gauche arrive naturellement sur le manche et le poignet reste droit.",
                    Illus.NECK_ANGLE, "L'angle du manche",
                ),
                Block.Advice(
                    "Épaules basses et souples. Si elles montent vers les oreilles, tu te crispes : souffle, relâche, et ralentis le tempo.",
                    Illus.RELAX_SHOULDERS, "Les épaules",
                ),
                Block.Warning("Ne te penche pas pour regarder le manche en permanence : jette un œil, puis redresse-toi. Le dos te dira merci."),
            ),
        ),
        Lesson(
            "main-gauche", "✋", "La main gauche",
            "Numéros des doigts, pouce, bout des doigts, où appuyer.",
            listOf(
                Block.Text("La main gauche forme les accords sur le manche. Le but : un son net avec le moins de force possible."),
                Block.Advice(
                    "Sur les diagrammes d'accords, les doigts ont un numéro : 1 index, 2 majeur, 3 annulaire, 4 auriculaire. Le pouce ne compte pas.",
                    Illus.FINGER_NUMBERS, "Les numéros des doigts",
                ),
                Block.Advice(
                    "Le pouce se place derrière le manche, à peu près au milieu, face au majeur. La paume ne colle pas au manche : " +
                        "il reste un petit espace, comme si tu tenais une balle.",
                    Illus.THUMB_BEHIND, "Le pouce",
                ),
                Block.Advice(
                    "Appuie avec le bout du doigt, bien arrondi, presque vertical. Un doigt à plat touche la corde voisine et l'étouffe : " +
                        "c'est la cause n°1 des accords qui sonnent mal.",
                    Illus.FINGERTIPS, "Le bout des doigts",
                ),
                Block.Advice(
                    "Place le doigt juste derrière la frette (côté tête), pas au milieu de la case. Le son est net et il faut beaucoup moins appuyer.",
                    Illus.NEAR_FRET, "Où appuyer",
                ),
                Block.Advice(
                    "Au début, le bout des doigts chauffe et marque : c'est normal pendant 2 ou 3 semaines, la peau durcit. " +
                        "Joue un peu chaque jour plutôt que longtemps une fois par semaine.",
                    Illus.FINGER_PAIN, "Les premiers jours",
                ),
                Block.Tip("Ongles de la main gauche courts : un ongle long empêche de poser le bout du doigt."),
            ),
        ),
        Lesson(
            "main-droite", "🎵", "Le médiator et la main droite",
            "Tenir le médiator, gratter du poignet, vers le bas et vers le haut.",
            listOf(
                Block.Text("En rythmique, la main droite est le moteur : c'est elle qui fait le groove. Elle doit rester souple et régulière comme un balancier."),
                Block.Advice(
                    "Plie l'index, pose le médiator sur le côté de sa dernière phalange, et pince-le avec le pouce à plat. " +
                        "Seule la pointe dépasse, de 2 ou 3 mm.",
                    Illus.PICK_GRIP, "Tenir le médiator",
                ),
                Block.Advice(
                    "Trop de médiator qui dépasse, et il accroche les cordes. Pas assez, et tu les rates. Tiens-le fermement mais sans serrer : " +
                        "il peut bouger un tout petit peu.",
                    Illus.PICK_TIP, "Combien doit dépasser",
                ),
                Block.Advice(
                    "L'avant-bras se pose sur le bord de la guitare. Le mouvement vient du poignet et d'une rotation de l'avant-bras, " +
                        "comme pour secouer de l'eau de ta main. Le bras ne fait pas de grands gestes.",
                    Illus.STRUM_WRIST, "Le geste",
                ),
                Block.Advice(
                    "Vers le bas (vers le sol), tu traverses toutes les cordes de l'accord. Vers le haut, seulement 3 ou 4 cordes aiguës, plus légèrement.",
                    Illus.STRUM_UP_LIGHT, "Vers le bas, vers le haut",
                    example = Example(rhythm(2, "DUDUDUDU"), grid("G"), 70),
                ),
                Block.Advice(
                    "Le secret des bonnes rythmiques : la main fait des allers-retours réguliers en continu, vers le bas sur les temps, " +
                        "vers le haut entre les temps. Pour faire un silence, elle passe juste au-dessus des cordes sans les toucher.",
                    Illus.STRUM_PENDULUM, "Le balancier qui ne s'arrête jamais",
                    example = Example(rhythm(2, "D.DU.UDU"), grid("G D"), 80),
                ),
                Block.Tip("Médiator médium (0,6 à 0,8 mm) pour commencer : assez souple pour gratter, assez ferme pour être précis."),
            ),
        ),
        Lesson(
            "lire", "📖", "Lire les accords et les rythmiques",
            "Les cordes, les diagrammes d'accords, les flèches de rythmique.",
            listOf(
                Block.Advice(
                    "Les cordes se numérotent de la plus fine (1, Mi aigu) à la plus grosse (6, Mi grave). Leurs noms, de la grosse à la fine : Mi, La, Ré, Sol, Si, Mi.",
                    Illus.STRING_NUMBERS, "Les six cordes",
                ),
                Block.Advice(
                    "Un diagramme montre le manche debout, face à toi : la 6e corde à gauche, le sillet en haut. Les ronds numérotés " +
                        "indiquent quel doigt poser et où. « o » : corde jouée à vide ; « x » : corde à ne pas gratter.",
                    Illus.DIAGRAM_READ, "Les diagrammes d'accords",
                    chord = "Em",
                ),
                Block.Advice(
                    "Dans l'appli, ↓ = coup vers le bas, ↑ = vers le haut. On compte « 1 et 2 et 3 et 4 et » : les flèches vers le bas tombent sur les chiffres.",
                    Illus.STRUM_ARROWS, "Les flèches de rythmique",
                    example = Example(rhythm(2, "D.DU.UDU"), grid("C"), 70),
                ),
                Block.Section("Les noms des accords"),
                Block.Text(
                    "Les grilles utilisent la notation anglaise : C = Do, D = Ré, E = Mi, F = Fa, G = Sol, A = La, B = Si. " +
                        "Un « m » veut dire mineur (Am = La mineur), un « 7 » ajoute une septième (E7), un « 5 » désigne un power chord (E5)."
                ),
                Block.Chord("C", "C = Do majeur. Sonne « joyeux », lumineux."),
                Block.Chord("Am", "Am = La mineur. Sonne plus « triste », plus sombre."),
            ),
        ),
        Lesson(
            "accorder", "🎛️", "S'accorder",
            "Mi La Ré Sol Si Mi, les mécaniques, l'accordeur de l'appli.",
            listOf(
                Block.Text(
                    "Une guitare se désaccorde un peu à chaque séance (température, cordes neuves, transport). Accorde-la toujours avant de jouer : " +
                        "sans ça, même un accord parfaitement posé sonne faux, et l'appli ne le reconnaîtra pas."
                ),
                Block.Advice(
                    "Chaque mécanique tend une corde. Tourne doucement en jouant la corde, et finis toujours en remontant vers la note " +
                        "(si tu es trop haut, redescends un peu en dessous, puis remonte).",
                    Illus.TUNING_PEGS, "Les mécaniques",
                ),
                Block.Advice(
                    "L'accordeur de l'appli écoute la corde jouée et te dit si elle est trop basse, trop haute ou juste. Vise l'aiguille au milieu, dans le vert.",
                    Illus.TUNER_GAUGE, "L'accordeur",
                ),
                Block.Tip("Joue une corde à la fois, laisse-la sonner, et coupe les autres avec la main droite pour que l'accordeur n'entende qu'elle."),
            ),
        ),
        Lesson(
            "etouffer", "🤫", "Étouffer les cordes",
            "Le palm mute, l'étouffement main gauche.",
            listOf(
                Block.Text("Savoir arrêter le son est aussi important que le produire : c'est ce qui rend une rythmique précise et percutante."),
                Block.Advice(
                    "Le palm mute : la tranche de la main droite se pose sur les cordes, tout contre le chevalet. La note reste audible mais " +
                        "devient sourde et percutante. C'est le son du rock et du metal, en power chords.",
                    Illus.PALM_MUTE, "Le palm mute",
                    example = Example(rhythm(2, "dddddddd"), grid("E5"), 110),
                ),
                Block.Advice(
                    "Main gauche : garde les doigts posés mais relâche la pression. Les cordes ne sonnent plus. On s'en sert pour les coupures, " +
                        "le reggae et le funk.",
                    Illus.LEFT_MUTE, "L'étouffement main gauche",
                ),
            ),
        ),
        Lesson(
            "travailler", "📅", "Bien travailler avec l'appli",
            "Placer le téléphone, une routine de 30 minutes, progresser.",
            listOf(
                Block.Advice(
                    "L'appli écoute ton ampli avec le micro du téléphone. Pose-le face à l'ampli, à 1 ou 2 m, sur un meuble. " +
                        "Volume moyen : trop fort, le micro du téléphone sature et les accords deviennent difficiles à reconnaître.",
                    Illus.PHONE_PLACEMENT, "Placer le téléphone",
                ),
                Block.Advice(
                    "Une routine qui marche : accorder, s'échauffer (quelques changements d'accords lents), travailler les accords du moment, " +
                        "puis la rythmique au métronome, et finir par jouer pour le plaisir.",
                    Illus.ROUTINE, "30 minutes par jour",
                ),
                Block.Tip(
                    "Fais le « Réglage du micro » une fois (1 minute) : l'appli mesure le bruit de la pièce, le délai de ton téléphone " +
                        "et la force de tes coups de médiator."
                ),
                Block.Warning(
                    "Protège tes oreilles : à fort volume, des bouchons d'oreilles en mousse ou de musicien. Une perte d'audition ne se répare pas."
                ),
            ),
        ),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}
