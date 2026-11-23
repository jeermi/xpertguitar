package fr.xpert.guitar.content

import fr.xpert.guitar.music.Chords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonsTest {
    @Test
    fun leconsValidesEtIllustrees() {
        // Construire les leçons vérifie toutes les rythmiques d'exemple.
        assertTrue(Lessons.all.size >= 7)
        assertEquals("identifiants uniques", Lessons.all.size, Lessons.all.map { it.id }.toSet().size)
        for (l in Lessons.all) {
            val advices = l.blocks.filterIsInstance<Block.Advice>()
            assertTrue("${l.id} : au moins une illustration", advices.isNotEmpty())
        }
    }

    @Test
    fun accordsDesLeconsExistent() {
        val ids = Chords.all.map { it.id }.toSet()
        for (l in Lessons.all) for (b in l.blocks) {
            when (b) {
                is Block.Chord -> assertTrue("${l.id} : ${b.shapeId}", b.shapeId in ids)
                is Block.Advice -> {
                    b.chord?.let { assertTrue("${l.id} : $it", it in ids) }
                    b.example?.grid?.forEach { assertTrue("${l.id} : ${it.shapeId}", it.shapeId in ids) }
                }
                else -> {}
            }
        }
    }
}
