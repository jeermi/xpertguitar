package fr.xpert.guitar.practice

import fr.xpert.guitar.audio.StrumDir
import fr.xpert.guitar.audio.StrumKind
import fr.xpert.guitar.music.Chords
import fr.xpert.guitar.music.Shape

/** Un coup de médiator à jouer : [step] = indice de pas dans la mesure. */
data class Strum(val step: Int, val dir: StrumDir, val kind: StrumKind = StrumKind.OPEN, val accent: Boolean = false)

/**
 * Rythmique d'une mesure, répétée en boucle.
 * @param stepsPerBeat 1 = noires, 2 = croches, 3 = triolets (ou croches en 6/8), 4 = doubles croches
 */
data class Rhythm(val beatsPerBar: Int, val stepsPerBeat: Int, val strums: List<Strum>) {
    val stepsPerBar get() = beatsPerBar * stepsPerBeat
    val hasAccents get() = strums.any { it.accent }

    fun gainOf(s: Strum): Float = when {
        hasAccents -> if (s.accent) 0.95f else 0.5f
        s.dir == StrumDir.UP -> 0.7f
        else -> 0.85f
    }

    /** Notation compacte « ↓ ↓↑ ↑↓↑ » pour les listes. */
    val arrows: String
        get() = (0 until stepsPerBar).joinToString("") { i ->
            val s = strums.firstOrNull { it.step == i }
            when {
                s == null -> "·"
                s.dir == StrumDir.DOWN -> "↓"
                else -> "↑"
            }
        }
}

/**
 * Rythmique écrite pas par pas : « D » = vers le bas, « U » = vers le haut,
 * minuscules « d » / « u » = étouffé (palm mute), « . » = la main passe sans toucher les cordes.
 * [accents] aligné : « > » = coup accentué.
 */
fun rhythm(stepsPerBeat: Int, spec: String, beatsPerBar: Int = 4, accents: String? = null): Rhythm {
    require(spec.length == stepsPerBeat * beatsPerBar) { "Rythmique de longueur invalide : $spec" }
    require(accents == null || accents.length == spec.length) { "Accents mal alignés : $spec" }
    val strums = spec.mapIndexedNotNull { i, c ->
        val acc = accents?.get(i) == '>'
        when (c) {
            'D' -> Strum(i, StrumDir.DOWN, StrumKind.OPEN, acc)
            'U' -> Strum(i, StrumDir.UP, StrumKind.OPEN, acc)
            'd' -> Strum(i, StrumDir.DOWN, StrumKind.PALM_MUTE, acc)
            'u' -> Strum(i, StrumDir.UP, StrumKind.PALM_MUTE, acc)
            '.' -> null
            else -> error("Caractère inconnu « $c » dans $spec")
        }
    }
    return Rhythm(beatsPerBar, stepsPerBeat, strums)
}

/** Un accord de la grille et sa durée en mesures. */
data class Slot(val shapeId: String, val bars: Int = 1) {
    val shape: Shape get() = Chords.get(shapeId)
}

/** « G:2 C D:2 » → G deux mesures, C une, D deux. */
fun grid(spec: String): List<Slot> = spec.trim().split(Regex("\\s+")).map {
    val p = it.split(':')
    Slot(p[0], p.getOrNull(1)?.toInt() ?: 1)
}

enum class ExerciseKind {
    /** Jouer un accord à la fois, sans tempo : l'appli dit s'il sonne juste. */
    CHORD_CHECK,
    /** Passer d'un accord à l'autre le plus de fois possible en une minute. */
    CHANGES,
    /** Rythmique avec métronome : placement de chaque coup et justesse de chaque accord. */
    RHYTHM,
}

data class Exercise(
    val id: String,
    val level: Int,
    val title: String,
    val description: String,
    val tip: String,
    val kind: ExerciseKind,
    /** Accords à travailler (CHORD_CHECK, CHANGES) ou grille d'accords (RHYTHM). */
    val chords: List<Slot>,
    val rhythm: Rhythm? = null,
    val startBpm: Int = 60,
    val targetBpm: Int = 100,
    val rampStep: Int = 4,
    /** Monter le tempo toutes les N mesures. */
    val rampEveryBars: Int = 4,
) {
    val gridBars: Int get() = chords.sumOf { it.bars }

    /** Accord à jouer pendant la mesure n° [bar] (la grille tourne en boucle). */
    fun slotAt(bar: Int): Slot {
        var b = bar % gridBars
        for (s in chords) {
            if (b < s.bars) return s
            b -= s.bars
        }
        return chords.last()
    }

    /** Vrai si la mesure [bar] commence un nouvel accord. */
    fun changesAt(bar: Int): Boolean {
        var b = bar % gridBars
        for (s in chords) {
            if (b == 0) return true
            if (b < s.bars) return false
            b -= s.bars
        }
        return false
    }
}

enum class Judgement(val label: String) {
    PERFECT("Pile !"),
    EARLY("En avance"),
    LATE("En retard"),
    MISS("Raté"),
    EXTRA("Coup en trop"),
}
