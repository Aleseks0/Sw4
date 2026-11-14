package cz.mojekoreny.sync

import cz.mojekoreny.gedcom.Change
import cz.mojekoreny.gedcom.ChangeApplier
import cz.mojekoreny.gedcom.ChangeState
import cz.mojekoreny.gedcom.GedcomReader
import cz.mojekoreny.gedcom.GedcomTree
import cz.mojekoreny.gedcom.GedcomWriter
import cz.mojekoreny.gedcom.MediaRef
import cz.mojekoreny.gedcom.Op
import cz.mojekoreny.gedcom.Relation
import cz.mojekoreny.gedcom.findByKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Falešný web: drží rodokmen, přijímá změny do fronty; „schválení“ je [merge]. */
private class FakeWeb(var tree: GedcomTree, var acceptsChanges: Boolean = true) : WebApi {
    val received = mutableListOf<Change>()
    val media = mutableMapOf<String, ByteArray>()
    var failDownload: WebException? = null
    var rejectIds = mapOf<String, String>()

    override fun downloadGedcom(): ByteArray {
        failDownload?.let { throw it }
        return GedcomWriter().write(tree).toByteArray()
    }
    override fun pushChanges(changes: List<Change>): PushResult {
        if (!acceptsChanges) return PushResult(false)
        val ok = changes.filter { it.id !in rejectIds }
        received += ok
        return PushResult(true, ok.map { it.id }.toSet(), rejectIds)
    }
    override fun uploadMedia(sha: String, bytes: ByteArray, mime: String): Boolean {
        if (!acceptsChanges) return false
        media[sha] = bytes; return true
    }
    override fun downloadFile(path: String): ByteArray? = null

    /** Správce rodokmenu změny schválí a nasadí web. */
    fun merge() { tree = ChangeApplier.applyAll(tree, received).tree; received.clear() }
}

class SyncEngineTest {
    private val base = GedcomReader.read(javaClass.getResourceAsStream("/ukazka.ged")!!)
    private var n = 0
    private fun ch(op: Op, key: String, fields: Map<String, String?> = emptyMap(), rel: Relation? = null,
                   old: Map<String, String?> = emptyMap(), media: MediaRef? = null) =
        Change("c${n++}", "2026-11-14T10:00:00Z", "ales", op, key, fields, old, rel, null, media)

    private val newPerson get() = ch(Op.CREATE_PERSON, "app-1", mapOf("given" to "Anna", "surname" to "Nová", "sex" to "F"), Relation("child", "I1"))
    private val update get() = ch(Op.UPDATE_PERSON, "jan-novak-1849", mapOf("occupation" to "kovář"), old = mapOf("occupation" to "horník"))

    @Test
    fun `odeslani schvaleni a zmizeni z fronty`() {
        val web = FakeWeb(base)
        val local = listOf(newPerson, update)
        val r1 = SyncEngine(web).sync(local) { null }
        assertEquals(2, r1.pushed)
        assertEquals(listOf(ChangeState.SENT, ChangeState.SENT), r1.changes.map { it.state })
        // web změny ještě nemá nasazené → v telefonu zůstávají (přehrané na stažený rodokmen)
        assertNotNull(r1.tree!!.findByKey("app-1"))
        assertEquals("kovář", r1.tree!!.findByKey("jan-novak-1849")!!.event("OCCU")?.value)
        assertNull(web.tree.findByKey("app-1"))

        // další synchronizace před schválením: nic se neposílá znovu, změny dál platí
        val r2 = SyncEngine(web).sync(r1.changes) { null }
        assertEquals(0, r2.pushed)
        assertEquals(2, r2.changes.size)
        assertEquals(2, web.received.size)

        // správce schválí a nasadí
        web.merge()
        val r3 = SyncEngine(web).sync(r2.changes) { null }
        assertEquals(0, r3.changes.size)
        assertEquals(2, r3.mergedOnWeb)
        assertNotNull(r3.tree!!.findByKey("app-1"))
    }

    @Test
    fun `web nepodporuje prijem zmen`() {
        val web = FakeWeb(base, acceptsChanges = false)
        val r = SyncEngine(web).sync(listOf(newPerson)) { null }
        assertTrue(!r.pushSupported)
        assertEquals(listOf(ChangeState.PENDING), r.changes.map { it.state })
        assertNotNull(r.tree!!.findByKey("app-1")) // místní změna se zachová i po stažení
    }

    @Test
    fun `odmitnute zmeny se zahodi s duvodem`() {
        val web = FakeWeb(base)
        val a = update
        web.rejectIds = mapOf(a.id to "neplatné datum")
        val r = SyncEngine(web).sync(listOf(a, newPerson)) { null }
        assertEquals(mapOf(a.id to "neplatné datum"), r.dropped)
        assertEquals(1, r.changes.size)
        assertEquals("horník", r.tree!!.findByKey("jan-novak-1849")!!.event("OCCU")?.value)
    }

    @Test
    fun `konflikt s upravou na webu`() {
        val web = FakeWeb(base)
        web.tree = ChangeApplier.apply(web.tree, ch(Op.UPDATE_PERSON, "jan-novak-1849", mapOf("occupation" to "tesař"))).tree
        val r = SyncEngine(web).sync(listOf(update)) { null }
        assertEquals(1, r.conflicts.size)
        assertEquals(listOf("occupation"), r.conflicts.single().second)
        assertEquals("kovář", r.tree!!.findByKey("jan-novak-1849")!!.event("OCCU")?.value)
    }

    @Test
    fun `zmena osoby smazane na webu se zahodi`() {
        val web = FakeWeb(base)
        web.tree = ChangeApplier.apply(web.tree, ch(Op.DELETE_PERSON, "jan-novak-1849")).tree
        val r = SyncEngine(web).sync(listOf(update)) { null }
        assertEquals(0, r.changes.size)
        assertEquals(1, r.dropped.size)
    }

    @Test
    fun `fotky se nahraji pred zmenami`() {
        val web = FakeWeb(base)
        val m = MediaRef("0123456789abcdef", "media/0123456789abcdef.jpg", "Portrét", "jpg")
        val c = ch(Op.ADD_MEDIA, "jan-novak-1849", media = m)
        val r = SyncEngine(web).sync(listOf(c)) { sha -> if (sha == m.sha) byteArrayOf(1, 2, 3) else null }
        assertEquals(1, r.mediaUploaded)
        assertEquals(listOf<Byte>(1, 2, 3), web.media.getValue(m.sha).toList())
    }

    @Test
    fun `chyba stazeni po odeslani zachova stav zprav`() {
        val web = FakeWeb(base)
        web.failDownload = WebException.Network(java.io.IOException("offline"))
        val r = SyncEngine(web).sync(listOf(update)) { null }
        assertNull(r.tree)
        assertEquals(ChangeState.SENT, r.changes.single().state) // odesláno, nebude se posílat znovu
        assertNotNull(r.downloadError)
    }

    @Test
    fun `chybne prihlaseni nic nezmeni`() {
        val web = object : WebApi by FakeWeb(base) {
            override fun pushChanges(changes: List<Change>): PushResult = throw WebException.Unauthorized()
        }
        assertFailsWith<WebException.Unauthorized> { SyncEngine(web).sync(listOf(update)) { null } }
    }

    @Test
    fun `bez zmen jen stahne`() {
        val web = FakeWeb(base)
        val r = SyncEngine(web).sync(emptyList()) { null }
        assertEquals(base.persons.size, r.tree!!.persons.size)
        assertEquals(0, r.pushed)
    }
}
