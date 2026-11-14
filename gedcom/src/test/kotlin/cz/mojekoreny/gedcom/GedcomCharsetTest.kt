package cz.mojekoreny.gedcom

import kotlin.test.Test
import kotlin.test.assertEquals

class GedcomCharsetTest {
    private val czech = "Řehoř Šťastný z Příbrami, Žluťoučký kůň, Ďáblice, Ůvaly"

    private fun ged(charLine: String, name: String) = "0 HEAD\n1 CHAR $charLine\n0 @I1@ INDI\n1 NAME $name\n0 TRLR\n"

    private fun nameOf(bytes: ByteArray) = GedcomReader.read(bytes).persons.single().name.given

    @Test
    fun utf8() = assertEquals(czech, nameOf(ged("UTF-8", czech).toByteArray()))

    @Test
    fun `utf8 s BOM`() = assertEquals(czech, nameOf(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + ged("UTF-8", czech).toByteArray()))

    @Test
    fun `utf16 LE s BOM`() = assertEquals(czech, nameOf(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + ged("UNICODE", czech).toByteArray(Charsets.UTF_16LE)))

    @Test
    fun `utf16 BE bez BOM`() = assertEquals(czech, nameOf(ged("UNICODE", czech).toByteArray(Charsets.UTF_16BE)))

    @Test
    fun `ANSI jako windows-1250`() {
        val bytes = ged("ANSI", czech).toByteArray(charset("windows-1250"))
        val t = GedcomReader.read(bytes)
        assertEquals("windows-1250", t.header.encoding)
        assertEquals(czech, t.persons.single().name.given)
    }

    @Test
    fun ansel() {
        val bytes = "0 HEAD\n1 CHAR ANSEL\n0 @I1@ INDI\n1 NAME ".toByteArray() + Ansel.encode(czech) + "\n0 TRLR\n".toByteArray()
        val t = GedcomReader.read(bytes)
        assertEquals("ANSEL", t.header.encoding)
        assertEquals(czech, t.persons.single().name.given)
    }

    @Test
    fun `ANSEL hacek pred pismenem`() {
        // 0xE9 = háček, 0xE2 = čárka; v ANSEL stojí před písmenem
        val bytes = byteArrayOf(0xE9.toByte(), 'R'.code.toByte(), 'e'.code.toByte(), 0xE9.toByte(), 'r'.code.toByte(), 0xE2.toByte(), 'a'.code.toByte())
        assertEquals("Řeřá", Ansel.decode(bytes))
    }
}
