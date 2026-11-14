package cz.mojekoreny.gedcom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GedcomDateTest {
    private fun p(s: String) = GedcomDate.parse(s)!!

    @Test
    fun `GEDCOM formaty`() {
        assertEquals(GedcomDate.exact(1849, 5, 9), p("9 MAY 1849"))
        assertEquals(GedcomDate.exact(1849, 5), p("MAY 1849"))
        assertEquals(GedcomDate.exact(1849), p("1849"))
        assertEquals(GedcomDate(DateQualifier.ABOUT, DatePart(1849)), p("ABT 1849"))
        assertEquals(GedcomDate(DateQualifier.BEFORE, end = DatePart(1900)), p("BEF 1900"))
        assertEquals(GedcomDate(DateQualifier.AFTER, DatePart(1900, 1, 2)), p("AFT 2 JAN 1900"))
        assertEquals(GedcomDate(DateQualifier.BETWEEN, DatePart(1850), DatePart(1855)), p("BET 1850 AND 1855"))
        assertEquals(GedcomDate(DateQualifier.FROM_TO, DatePart(1850), DatePart(1855)), p("FROM 1850 TO 1855"))
        assertEquals(GedcomDate.exact(1749), p("@#DGREGORIAN@ 1749"))
        assertEquals(GedcomDate.exact(1749), p("1749/50"))
    }

    @Test
    fun `ceske formaty`() {
        assertEquals(GedcomDate.exact(1849, 5, 9), p("9. 5. 1849"))
        assertEquals(GedcomDate.exact(1849, 5, 9), p("9.5.1849"))
        assertEquals(GedcomDate.exact(1849, 5, 9), p("9. května 1849"))
        assertEquals(GedcomDate.exact(1849, 5), p("květen 1849"))
        assertEquals(GedcomDate(DateQualifier.ABOUT, DatePart(1849)), p("asi 1849"))
        assertEquals(GedcomDate(DateQualifier.ABOUT, DatePart(1849)), p("cca 1849"))
        assertEquals(GedcomDate(DateQualifier.ABOUT, DatePart(1849)), p("~1849"))
        assertEquals(GedcomDate(DateQualifier.BEFORE, end = DatePart(1900)), p("před 1900"))
        assertEquals(GedcomDate(DateQualifier.BETWEEN, DatePart(1850), DatePart(1855)), p("mezi 1850 a 1855"))
        assertEquals(GedcomDate(DateQualifier.BETWEEN, DatePart(1850), DatePart(1855)), p("1850–1855"))
    }

    @Test
    fun `vystup a zpet`() {
        val all = listOf(
            GedcomDate.exact(1849, 5, 9), GedcomDate.exact(1849, 12), GedcomDate.exact(1849),
            GedcomDate(DateQualifier.ABOUT, DatePart(1849)),
            GedcomDate(DateQualifier.CALCULATED, DatePart(1849)),
            GedcomDate(DateQualifier.ESTIMATED, DatePart(1849)),
            GedcomDate(DateQualifier.BEFORE, end = DatePart(1900, 3)),
            GedcomDate(DateQualifier.AFTER, DatePart(1900)),
            GedcomDate(DateQualifier.BETWEEN, DatePart(1850, 1, 1), DatePart(1855)),
            GedcomDate(DateQualifier.FROM, DatePart(1850)),
            GedcomDate(DateQualifier.TO, end = DatePart(1850)),
            GedcomDate(DateQualifier.FROM_TO, DatePart(1850), DatePart(1855, 6, 30)),
        )
        for (d in all) {
            assertEquals(d, p(d.toGedcom()), d.toGedcom())
            assertEquals(d, p(d.toCzech()), d.toCzech())
        }
        assertEquals("9. 5. 1849", GedcomDate.exact(1849, 5, 9).toCzech())
        assertEquals("ABT 9 MAY 1849", GedcomDate(DateQualifier.ABOUT, DatePart(1849, 5, 9)).toGedcom())
    }

    @Test
    fun `nesrozumitelne jako fraze`() {
        val d = p("na sv. Martina")
        assertEquals(DateQualifier.PHRASE, d.qualifier)
        assertEquals("na sv. Martina", d.phrase)
        assertEquals(DateQualifier.PHRASE, p("32. 13. 1849").qualifier)
        assertNull(GedcomDate.parse("  "))
    }

    @Test
    fun razeni() {
        val sorted = listOf("1850", "9. 5. 1849", "BEF 1849", "MAY 1849").sortedBy { p(it).sortKey }
        assertEquals(listOf("BEF 1849", "MAY 1849", "9. 5. 1849", "1850"), sorted)
    }
}
