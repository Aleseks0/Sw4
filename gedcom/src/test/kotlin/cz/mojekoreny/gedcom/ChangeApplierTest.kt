package cz.mojekoreny.gedcom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChangeApplierTest {
    private val tree = GedcomReader.read(javaClass.getResourceAsStream("/ukazka.ged")!!)
    private var n = 0

    private fun ch(op: Op, key: String, fields: Map<String, String?> = emptyMap(), rel: Relation? = null,
                   partner: String? = null, media: MediaRef? = null, old: Map<String, String?> = emptyMap()) =
        Change("c${n++}", "2026-11-14T10:00:00Z", "test", op, key, fields, old, rel, partner, media)

    private fun create(key: String, fields: Map<String, String?>, rel: Relation? = null) = ch(Op.CREATE_PERSON, key, fields, rel)

    @Test
    fun `uprava poli osoby`() {
        val r = ChangeApplier.apply(
            tree,
            ch(Op.UPDATE_PERSON, "jan-novak-1849", mapOf(
                "deathDate" to "3 FEB 1921", "deathPlace" to "Kladno", "occupation" to "kovář", "birthSurname" to "Novotný",
            )),
        )
        assertEquals(Outcome.APPLIED, r.outcome)
        val jan = r.tree.findByKey("jan-novak-1849")!!
        assertEquals("3 FEB 1921", jan.death?.date)
        assertEquals("Kladno", jan.death?.place)
        assertEquals("kovář", jan.event("OCCU")?.value) // první povolání nahrazeno
        assertEquals("Novotný", jan.birthSurname)
        // zachované: ostatní data, citace u narození, vlastní značky
        assertEquals("9 MAY 1849", jan.event("BIRT")?.date)
        assertEquals("S1", jan.event("BIRT")?.citations?.single()?.sourceId)
        assertTrue(jan.extra.any { it.tag == "_MOJE" })
    }

    @Test
    fun `stejna uprava podruhe je noop`() {
        val c = ch(Op.UPDATE_PERSON, "jan-novak-1849", mapOf("occupation" to "kovář"))
        val once = ChangeApplier.apply(tree, c)
        assertEquals(Outcome.APPLIED, once.outcome)
        assertEquals(Outcome.NOOP, ChangeApplier.apply(once.tree, c).outcome)
    }

    @Test
    fun `smazani pole`() {
        val r = ChangeApplier.apply(tree, ch(Op.UPDATE_PERSON, "jan-novak-1849", mapOf("deathDate" to null, "deathPlace" to "", "note" to null)))
        val jan = r.tree.findByKey("jan-novak-1849")!!
        assertNull(jan.event("DEAT"))
        assertTrue(jan.notes.isEmpty())
    }

    @Test
    fun `konflikt pri zmene stejneho pole jinde`() {
        // Aplikace upravovala povolání z „horník“, na webu je mezitím jiné
        val webTree = ChangeApplier.apply(tree, ch(Op.UPDATE_PERSON, "jan-novak-1849", mapOf("occupation" to "tesař"))).tree
        val local = ch(Op.UPDATE_PERSON, "jan-novak-1849", mapOf("occupation" to "kovář"), old = mapOf("occupation" to "horník"))
        val r = ChangeApplier.apply(webTree, local)
        assertEquals(Outcome.APPLIED, r.outcome)
        assertEquals(listOf("occupation"), r.conflicts)
        assertEquals("kovář", r.tree.findByKey("jan-novak-1849")!!.event("OCCU")?.value)
    }

    @Test
    fun `uprava nenalezene osoby selze`() {
        val r = ChangeApplier.apply(tree, ch(Op.UPDATE_PERSON, "nikdo", mapOf("given" to "X")))
        assertEquals(Outcome.FAILED, r.outcome)
    }

    @Test
    fun `nova samostatna osoba a idempotence`() {
        val c = create("app-1", mapOf("given" to "Anna", "surname" to "Nová", "sex" to "F", "birthDate" to "1.2.1900", "birthPlace" to "Kladno"))
        val r = ChangeApplier.apply(tree, c)
        assertEquals(Outcome.APPLIED, r.outcome)
        val a = r.tree.findByKey("app-1")!!
        assertEquals("Anna Nová", a.name.full)
        assertEquals(Sex.FEMALE, a.sex)
        assertEquals("Kladno", a.birth?.place)
        assertEquals(tree.persons.size + 1, r.tree.persons.size)
        assertEquals(Outcome.NOOP, ChangeApplier.apply(r.tree, c).outcome)
    }

    @Test
    fun `pridani matky k osobe s rodinou bez matky`() {
        // I4 Matěj nemá rodiče; I1 má rodinu F0 jen s otcem I4 → doplnit matku
        val r = ChangeApplier.apply(tree, create("app-m", mapOf("given" to "Eva", "surname" to "Nováková", "sex" to "F"), Relation("parent", "jan-novak-1849")))
        assertEquals(Outcome.APPLIED, r.outcome, r.message)
        val eva = r.tree.findByKey("app-m")!!
        val fam = r.tree.family(eva.familiesAsSpouse.single())!!
        assertEquals("F0", fam.id)
        assertEquals(eva.id, fam.wife)
        assertEquals("I4", fam.husband)
        assertEquals(listOf("I2"), r.tree.spousesOf(r.tree.person("I1")!!).map { it.id })
    }

    @Test
    fun `obsazeny otec nelze pridat`() {
        val r = ChangeApplier.apply(tree, create("app-o", mapOf("given" to "Karel", "sex" to "M"), Relation("parent", "jan-novak-1849")))
        assertEquals(Outcome.FAILED, r.outcome)
        assertTrue(r.message!!.contains("už má otce"))
    }

    @Test
    fun `rodic osobe bez rodicu vytvori rodinu`() {
        val r = ChangeApplier.apply(tree, create("app-p", mapOf("given" to "Tomáš", "sex" to "M"), Relation("parent", "matej-x")))
        assertEquals(Outcome.FAILED, r.outcome) // matej-x neexistuje
        val r2 = ChangeApplier.apply(tree, create("app-p", mapOf("given" to "Tomáš", "sex" to "M"), Relation("parent", "I4")))
        assertEquals(Outcome.APPLIED, r2.outcome)
        val matej = r2.tree.person("I4")!!
        assertEquals(1, matej.familiesAsChild.size)
        assertEquals("app-p", r2.tree.fatherOf(matej)?.uid)
    }

    @Test
    fun `partner a dite`() {
        var t = ChangeApplier.apply(tree, create("app-s", mapOf("given" to "Jana", "sex" to "F"), Relation("spouse", "I3"))).tree
        val josef = t.person("I3")!!
        assertEquals("app-s", t.spousesOf(josef).single().uid)
        t = ChangeApplier.apply(t, create("app-k", mapOf("given" to "Petr", "sex" to "M"), Relation("child", "I3", "app-s"))).tree
        val petr = t.findByKey("app-k")!!
        assertEquals(listOf("I3"), t.parentsOf(petr).filter { it.sex == Sex.MALE }.map { it.id })
        assertEquals(2, t.parentsOf(petr).size)
        assertEquals(listOf(petr.id), t.childrenOf(t.person("I3")!!).map { it.id })
    }

    @Test
    fun `dite k existujici rodine bez zadani partnera`() {
        val t = ChangeApplier.apply(tree, create("app-d", mapOf("given" to "Marie", "sex" to "F"), Relation("child", "I1"))).tree
        assertTrue(t.family("F1")!!.children.contains(t.findByKey("app-d")!!.id))
        assertEquals(tree.families.size, t.families.size)
    }

    @Test
    fun `smazani osoby uklidi vazby`() {
        val r = ChangeApplier.apply(tree, ch(Op.DELETE_PERSON, "josef-novak-1874"))
        assertEquals(Outcome.APPLIED, r.outcome)
        assertNull(r.tree.findByKey("josef-novak-1874"))
        assertTrue(r.tree.family("F1")!!.children.isEmpty())
        assertEquals(Outcome.NOOP, ChangeApplier.apply(r.tree, ch(Op.DELETE_PERSON, "josef-novak-1874")).outcome)
        // smazání matky rodiny F1 → rodina zůstane (otec s dětmi), ale po smazání obou rodičů zanikne
        var t = ChangeApplier.apply(tree, ch(Op.DELETE_PERSON, "marie-melicharova-1850")).tree
        assertNotNull(t.family("F1"))
        t = ChangeApplier.apply(t, ch(Op.DELETE_PERSON, "jan-novak-1849")).tree
        assertNull(t.family("F1"))
        assertTrue(t.person("I3")!!.familiesAsChild.isEmpty())
        assertNull(t.family("F0")?.let { if (it.husband == null && it.wife == null) it else null })
    }

    @Test
    fun `snatek se zalozi nebo upravi`() {
        val r = ChangeApplier.apply(tree, ch(Op.SET_MARRIAGE, "jan-novak-1849", mapOf("date" to "5 FEB 1873", "place" to "Hluboš"), partner = "marie-melicharova-1850"))
        assertEquals(Outcome.APPLIED, r.outcome)
        assertEquals("5 FEB 1873", r.tree.family("F1")!!.marriage?.date)
        assertEquals("Hluboš", r.tree.family("F1")!!.marriage?.place)
        assertEquals(1, r.tree.families.count { it.husband == "I1" && it.wife == "I2" })
        // nový pár
        val c = create("app-x", mapOf("given" to "Ema", "sex" to "F"))
        val t = ChangeApplier.apply(tree, c).tree
        val r2 = ChangeApplier.apply(t, ch(Op.SET_MARRIAGE, "josef-novak-1874", mapOf("date" to "1900"), partner = "app-x"))
        assertEquals(Outcome.APPLIED, r2.outcome)
        assertEquals(tree.families.size + 1, r2.tree.families.size)
    }

    @Test
    fun fotografie() {
        val m = MediaRef("abc123", "media/abc123.jpg", "Portrét", "jpg")
        val t1 = ChangeApplier.apply(tree, ch(Op.ADD_MEDIA, "jan-novak-1849", media = m)).tree
        val jan = t1.findByKey("jan-novak-1849")!!
        assertEquals(listOf("media/abc123.jpg"), t1.photosOf(jan).map { it.path }.filter { it.startsWith("media/") })
        // fotka z původního souboru (záznam OBJE) + nová
        assertEquals(2, t1.photosOf(jan).size)
        assertEquals(Outcome.NOOP, ChangeApplier.apply(t1, ch(Op.ADD_MEDIA, "jan-novak-1849", media = m)).outcome)
        // hlavní fotka
        val t2 = ChangeApplier.apply(t1, ch(Op.SET_PORTRAIT, "jan-novak-1849", media = m)).tree
        assertEquals("media/abc123.jpg", t2.portraitOf(t2.findByKey("jan-novak-1849")!!)?.path)
        assertEquals(Outcome.NOOP, ChangeApplier.apply(t2, ch(Op.SET_PORTRAIT, "jan-novak-1849", media = m)).outcome)
        // odebrání
        val t3 = ChangeApplier.apply(t2, ch(Op.REMOVE_MEDIA, "jan-novak-1849", media = m)).tree
        assertEquals(listOf("fotky/jan-novak.jpg"), t3.photosOf(t3.findByKey("jan-novak-1849")!!).map { it.path })
    }

    @Test
    fun `vse prezije export a import GEDCOM`() {
        val changes = listOf(
            create("app-1", mapOf("given" to "Anna", "surname" to "Nová", "sex" to "F", "birthDate" to "1900"), Relation("child", "I1")),
            ch(Op.UPDATE_PERSON, "jan-novak-1849", mapOf("occupation" to "kovář")),
            ch(Op.ADD_MEDIA, "app-1", media = MediaRef("ff00", "media/ff00.jpg", "Anna", "jpg")),
        )
        val t = ChangeApplier.applyAll(tree, changes).tree
        for (v in GedcomVersion.entries) {
            val back = GedcomReader.readText(GedcomWriter(v).write(t))
            val a = back.findByKey("app-1")!!
            assertEquals("Anna Nová", a.name.full)
            assertEquals("media/ff00.jpg", back.photosOf(a).single().path)
            assertEquals(back.person("I1")!!.id, back.parentsOf(a).first { it.sex == Sex.MALE }.id)
            assertEquals("kovář", back.findByKey("jan-novak-1849")!!.event("OCCU")?.value)
        }
    }

    @Test
    fun `zmeny tam a zpet pres JSON`() {
        val list = listOf(
            create("app-1", mapOf("given" to "Anna \"Ája\"", "note" to "řádek 1\nřádek 2 – čeština ěščř"), Relation("child", "I1", "I2")),
            ch(Op.UPDATE_PERSON, "x", mapOf("deathDate" to null), old = mapOf("deathDate" to "1 JAN 1900")).copy(state = ChangeState.SENT),
            ch(Op.ADD_MEDIA, "x", media = MediaRef("aa", "media/aa.jpg", null, "jpg")),
            ch(Op.SET_MARRIAGE, "x", mapOf("date" to "1900"), partner = "y"),
        )
        val back = Change.listFromJson(Change.listToJson(list, pretty = true))
        assertEquals(list, back)
    }

    @Test
    fun `applyAll hlasi nepovedene a noop`() {
        val a = ch(Op.UPDATE_PERSON, "jan-novak-1849", mapOf("occupation" to "horník")) // už tak je → noop
        val b = ch(Op.UPDATE_PERSON, "neexistuje", mapOf("given" to "X"))
        val c = create("app-1", mapOf("given" to "Ema"))
        val rep = ChangeApplier.applyAll(tree, listOf(a, b, c))
        assertEquals(listOf(a), rep.noop.map { it.first })
        assertEquals(listOf(b), rep.failed.map { it.first })
        assertNotNull(rep.tree.findByKey("app-1"))
    }
}
