package fr.xpert.guitar.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordsTest {
    @Test
    fun positionsCoherentes() {
        val all = Chords.all
        assertEquals("identifiants uniques", all.size, all.map { it.id }.toSet().size)
        for (sh in all) {
            val pcs = sh.midiNotes.map { Notes.pc(it) }.toSet()
            // Chaque note jouée appartient à l'accord, et la fondamentale est bien là.
            assertTrue("${sh.id} : notes hors accord $pcs", sh.chord.pitchClasses.containsAll(pcs))
            assertTrue("${sh.id} : fondamentale", sh.chord.root in pcs)
            assertTrue("${sh.id} : au moins 2 cordes", sh.playedStrings >= 2)
            for (s in 0 until 6) {
                if (sh.frets[s] <= 0) assertEquals("${sh.id} : pas de doigt sur une corde à vide", 0, sh.fingers[s])
                else assertTrue("${sh.id} : doigt manquant corde $s", sh.fingers[s] in 1..4)
            }
            if (sh.barre > 0) assertEquals("${sh.id} : barré", sh.barre, sh.minFret)
        }
    }

    @Test
    fun accordsConnus() {
        assertEquals(listOf(40, 47, 52, 55, 59, 64), Chords.get("Em").midiNotes)
        assertEquals(setOf(0, 4, 7), Chords.get("C").chord.pitchClasses)
        assertEquals(Chord(9, ChordType.MIN), Chord.parse("Am"))
        assertEquals(Chord(10, ChordType.MAJ), Chord.parse("A#"))
        assertEquals("Fa# mineur", Chord.parse("F#m").nameFr)
    }
}
