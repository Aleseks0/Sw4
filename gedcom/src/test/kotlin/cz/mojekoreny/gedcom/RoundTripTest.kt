package cz.mojekoreny.gedcom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoundTripTest {
    private val original = GedcomReader.read(javaClass.getResourceAsStream("/ukazka.ged")!!)

    private fun content(t: GedcomTree) = listOf(t.persons, t.families, t.sources, t.repositories, t.notes, t.media)

    @Test
    fun `5_5_1 tam a zpet beze ztraty`() {
        val text = GedcomWriter(GedcomVersion.V551).write(original)
        val again = GedcomReader.read(text.toByteArray())
        assertEquals(content(original), content(again))
        // Druhý zápis je totožný s prvním (stabilní výstup).
        assertEquals(text, GedcomWriter(GedcomVersion.V551).write(again))
    }

    @Test
    fun `5_5_1 dodrzi delku radku`() {
        val text = GedcomWriter(GedcomVersion.V551).write(original)
        text.lines().forEach { assertTrue(it.length <= 255, "dlouhý řádek: $it") }
        assertTrue(text.startsWith("0 HEAD\n1 GEDC\n2 VERS 5.5.1\n2 FORM LINEAGE-LINKED\n1 CHAR UTF-8\n"))
        assertTrue(text.contains("\n1 SUBM @U1@\n"))
        assertTrue(text.endsWith("0 TRLR\n"))
    }

    @Test
    fun `7_0 tam a zpet`() {
        val text = GedcomWriter(GedcomVersion.V70).write(original)
        assertTrue(text.contains("2 VERS 7.0"))
        assertTrue(text.lines().none { Regex("^\\d+ CONC").containsMatchIn(it) }, "GEDCOM 7 nezná CONC")
        assertTrue(text.contains("1 SNOTE @N1@"))
        assertTrue(text.contains("0 @N1@ SNOTE"))
        val again = GedcomReader.read(text.toByteArray())
        assertEquals(content(original), content(again))
    }

    @Test
    fun `nevhodna ID se precisluji`() {
        val t = GedcomTree(
            persons = listOf(
                Person("jan-novak-1849", listOf(PersonName("Jan", "Novák")), Sex.MALE, familiesAsSpouse = listOf("rodina-1")),
                Person("marie-1850", listOf(PersonName("Marie", "Nová")), Sex.FEMALE, familiesAsSpouse = listOf("rodina-1")),
            ),
            families = listOf(Family("rodina-1", husband = "jan-novak-1849", wife = "marie-1850")),
        )
        val text = GedcomWriter(GedcomVersion.V70).write(t)
        assertTrue(text.contains("0 @I1@ INDI"), text)
        assertTrue(text.contains("0 @F1@ FAM\n1 HUSB @I1@\n1 WIFE @I2@"), text)
        val again = GedcomReader.readText(text)
        assertEquals(listOf("I1", "I2"), again.persons.map { it.id })
        assertEquals(listOf("F1"), again.persons.first().familiesAsSpouse)
    }

    @Test
    fun `vlozene fotky v 7_0 jako zaznamy`() {
        val t = GedcomTree(
            persons = listOf(
                Person("I1", listOf(PersonName("Jan", "Novák")), media = listOf(MediaLink(file = MediaFile("media/a.jpg", "image/jpeg", "Portrét")))),
            ),
        )
        val back = GedcomReader.readText(GedcomWriter(GedcomVersion.V70).write(t))
        val link = back.persons.single().media.single()
        val rec = back.media.single { it.id == link.mediaId }
        assertEquals(MediaFile("media/a.jpg", "image/jpeg", "Portrét"), rec.files.single())

        val back551 = GedcomReader.readText(GedcomWriter(GedcomVersion.V551).write(t))
        assertEquals(t.persons, back551.persons)
    }
}
