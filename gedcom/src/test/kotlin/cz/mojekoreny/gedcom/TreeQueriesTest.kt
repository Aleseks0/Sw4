package cz.mojekoreny.gedcom

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TreeQueriesTest {
    private val tree = GedcomReader.read(javaClass.getResourceAsStream("/ukazka.ged")!!)

    @Test
    fun `generace a sourozenci`() {
        val josef = tree.person("I3")!!
        assertEquals(3, tree.ancestorGenerations(josef))
        assertEquals(1809, tree.earliestYear())
        assertEquals(emptyList(), tree.siblingsOf(josef))
        assertEquals("I1", tree.fatherOf(josef)?.id)
        assertEquals("I2", tree.motherOf(josef)?.id)
    }

    @Test
    fun `pribeh zivota`() {
        val jan = tree.person("I1")!!
        val story = tree.lifeStory(jan)
        assertEquals(LifeKind.BIRTH, story.first().kind)
        assertEquals("Narodil se", story.first().title)
        val kinds = story.map { it.kind }
        assertTrue(kinds.indexOf(LifeKind.MARRIAGE) < kinds.indexOf(LifeKind.CHILD), kinds.toString())
        val wedding = story.first { it.kind == LifeKind.MARRIAGE }
        assertEquals("Oženil se – Marie Nováková", wedding.title)
        assertEquals("ve věku 23 let", wedding.detail)
        assertEquals("Narodil se syn Josef", story.first { it.kind == LifeKind.CHILD }.title)
        assertEquals(LifeKind.DEATH, story.last().kind)
    }

    @Test
    fun vyroci() {
        val list = tree.anniversaries(LocalDate.of(2026, 5, 1), days = 10)
        val jan = list.single { it.personId == "I1" }
        assertEquals(AnniversaryKind.BIRTH, jan.kind)
        assertEquals(8, jan.inDays)
        assertEquals(177, jan.years)
    }

    @Test
    fun sklonovani() {
        assertEquals("1 rok", yearsCz(1))
        assertEquals("3 roky", yearsCz(3))
        assertEquals("72 let", yearsCz(72))
    }
}
