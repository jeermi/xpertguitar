package fr.xpert.guitar.practice

import fr.xpert.guitar.audio.StrumDir
import fr.xpert.guitar.music.Chords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExercisesTest {
    @Test
    fun tousLesExercicesSontValides() {
        val all = Exercises.all
        assertTrue(all.size >= 20)
        assertEquals("identifiants uniques", all.size, all.map { it.id }.toSet().size)
        val ids = Chords.all.map { it.id }.toSet()
        for (ex in all) {
            assertTrue("${ex.id} : accords", ex.chords.isNotEmpty())
            for (s in ex.chords) assertTrue("${ex.id} : ${s.shapeId}", s.shapeId in ids)
            assertTrue("${ex.id} : niveau", Exercises.levelName(ex.level).isNotEmpty())
            if (ex.kind == ExerciseKind.RHYTHM) {
                val r = ex.rhythm!!
                assertTrue("${ex.id} : rythmique vide", r.strums.isNotEmpty())
                assertTrue("${ex.id} : tempo", ex.startBpm in 30..ex.targetBpm)
                assertTrue(r.strums.all { it.step in 0 until r.stepsPerBar })
            }
            if (ex.kind == ExerciseKind.CHANGES) assertTrue("${ex.id} : deux accords", ex.chords.map { it.shapeId }.distinct().size >= 2)
        }
        // Les niveaux sont dans l'ordre
        assertEquals(all.map { it.level }.sorted(), all.map { it.level })
    }

    @Test
    fun rythmiqueEcrite() {
        val r = rhythm(2, "D.DUD.DU")
        assertEquals(8, r.stepsPerBar)
        assertEquals(listOf(0, 2, 3, 4, 6, 7), r.strums.map { it.step })
        assertEquals(StrumDir.UP, r.strums[2].dir)
        // Les coups vers le haut tombent sur les « et »
        assertTrue(r.strums.filter { it.dir == StrumDir.UP }.all { it.step % 2 == 1 })
    }

    @Test
    fun grilleEnBoucle() {
        val ex = Exercise("t", 1, "t", "", "", ExerciseKind.RHYTHM, grid("G:2 C D"), rhythm(1, "DDDD"))
        assertEquals(4, ex.gridBars)
        assertEquals(listOf("G", "G", "C", "D", "G"), (0..4).map { ex.slotAt(it).shapeId })
        assertTrue(ex.changesAt(0))
        assertFalse(ex.changesAt(1))
        assertTrue(ex.changesAt(2))
        assertTrue(ex.changesAt(4))
    }
}
