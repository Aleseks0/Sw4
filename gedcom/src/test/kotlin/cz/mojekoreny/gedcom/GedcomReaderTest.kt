package cz.mojekoreny.gedcom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GedcomReaderTest {
    private val tree = GedcomReader.read(javaClass.getResourceAsStream("/ukazka.ged")!!)

    @Test
    fun `nacte osoby rodiny a prameny`() {
        assertEquals(4, tree.persons.size)
        assertEquals(2, tree.families.size)
        assertEquals(2, tree.sources.size)
        assertEquals(1, tree.repositories.size)
        assertEquals(1, tree.notes.size)
        assertEquals(1, tree.media.size)
        assertEquals("UTF-8", tree.header.encoding)
        assertEquals("5.5.1", tree.header.version)
    }

    @Test
    fun `jmeno pohlavi a udalosti`() {
        val jan = tree.person("I1")!!
        assertEquals("Jan", jan.name.given)
        assertEquals("Novák", jan.name.surname)
        assertEquals(Sex.MALE, jan.sex)
        assertEquals("jan-novak-1849", jan.uid)
        assertEquals("9 MAY 1849", jan.birth?.date)
        assertEquals("9. 5. 1849", jan.birth?.parsedDate?.toCzech())
        assertEquals("Drahlín čp. 6, Příbram, Středočeský kraj, Česko", jan.birth?.place)
        assertEquals("S1", jan.birth?.citations?.single()?.sourceId)
        assertEquals("fol. 112", jan.birth?.citations?.single()?.page)
        assertEquals("horník", jan.event("OCCU")?.value)
        assertEquals("1849–1925", jan.lifeSpan)
    }

    @Test
    fun `spoji CONC a CONT`() {
        val text = tree.person("I1")!!.notes.single().text!!
        assertTrue(text.contains("zalomit pomocí řádků CONC – přesně"), text)
        assertTrue(text.contains("znaků. Žluťoučký kůň úpěl ďábelské ódy.\nDruhý řádek poznámky."), text)
    }

    @Test
    fun `rodne prijmeni a vztahy`() {
        val marie = tree.person("I2")!!
        assertEquals("Melicharová", marie.birthSurname)
        val jan = tree.person("I1")!!
        assertEquals(listOf("I2"), tree.spousesOf(jan).map { it.id })
        assertEquals(listOf("I3"), tree.childrenOf(jan).map { it.id })
        assertEquals(listOf("I4"), tree.parentsOf(jan).map { it.id })
    }

    @Test
    fun `zachova nezname znacky`() {
        val jan = tree.person("I1")!!
        val vlastni = jan.extra.single { it.tag == "_MOJE" }
        assertEquals("vnořená", vlastni.childValue("_VNOR"))
        val f1 = tree.family("F1")!!
        assertEquals(listOf("I3"), f1.children)
        assertEquals("Natural", f1.linkExtra["CHIL:I3"]?.first { it.tag == "_FREL" }?.value)
        assertEquals("birth", tree.person("I3")!!.childLinkExtra["F1"]?.single()?.value)
    }

    @Test
    fun `pramen s archivem a fotka`() {
        val s1 = tree.source("S1")!!
        assertEquals("R1", s1.repositoryId)
        assertEquals("2156", s1.callNumber)
        val s2 = tree.source("S2")!!
        assertEquals("Jan Novák, horník z Drahlína čp. 6, syn Matěje Nováka\na Marie Melicharová, dcera …", s2.text)
        val m = tree.media.single()
        assertEquals("fotky/jan-novak.jpg", m.files.single().path)
        assertEquals("jpg", m.files.single().format)
        assertEquals("Jan Novák kolem roku 1900", m.files.single().title)
        assertNotNull(tree.person("I1")!!.media.single().mediaId)
    }

    @Test
    fun `tolerantni ke chybam`() {
        val t = GedcomReader.readText("0 HEAD\n1 CHAR UTF-8\n0 @I1@ INDI\n1 NAME Anna /Malá/\n1 NOTE první\npokračování bez CONT\n\n0 TRLR\r\n")
        assertEquals("první\npokračování bez CONT", t.persons.single().notes.single().text)
    }
}
