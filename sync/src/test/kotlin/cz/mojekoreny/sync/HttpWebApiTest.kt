package cz.mojekoreny.sync

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import cz.mojekoreny.gedcom.Change
import cz.mojekoreny.gedcom.MediaRef
import cz.mojekoreny.gedcom.MiniJson
import cz.mojekoreny.gedcom.Op
import java.net.InetSocketAddress
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpWebApiTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val url get() = "http://127.0.0.1:${server.address.port}"
    private val gedcom = "0 HEAD\n1 CHAR UTF-8\n0 @I1@ INDI\n1 NAME Jan /Novák/\n0 TRLR\n"
    private var lastBody = ""
    private var lastPath = ""

    private fun HttpExchange.reply(code: Int, body: ByteArray = ByteArray(0), type: String = "text/plain") {
        responseHeaders.add("Content-Type", type)
        sendResponseHeaders(code, if (body.isEmpty()) -1 else body.size.toLong())
        if (body.isNotEmpty()) responseBody.use { it.write(body) }
        close()
    }

    private fun auth(e: HttpExchange): Boolean {
        val h = e.requestHeaders.getFirst("Authorization") ?: return false
        return h == "Basic " + Base64.getEncoder().encodeToString("ales:tajne".toByteArray())
    }

    private fun start() {
        server.createContext("/") { e ->
            lastPath = e.requestURI.rawPath
            if (!auth(e)) { e.responseHeaders.add("WWW-Authenticate", "Basic"); e.reply(401, "no".toByteArray()); return@createContext }
            when {
                e.requestURI.path == "/stazeni/rodokmen.ged" -> e.reply(200, gedcom.toByteArray())
                e.requestURI.path == "/api/v1/changes" && e.requestMethod == "POST" -> {
                    lastBody = String(e.requestBody.readBytes(), Charsets.UTF_8)
                    val ids = (MiniJson.parseObject(lastBody)["changes"] as List<*>).map { (it as Map<*, *>)["id"] as String }
                    val rejected = ids.filter { it == "bad" }.map { mapOf("id" to it, "reason" to "špatně") }
                    e.reply(200, MiniJson.write(mapOf("accepted" to ids.filter { it != "bad" }, "rejected" to rejected)).toByteArray(), "application/json")
                }
                e.requestURI.path.startsWith("/api/v1/media/") && e.requestMethod == "PUT" -> {
                    lastBody = e.requestBody.readBytes().joinToString(",")
                    e.reply(201)
                }
                e.requestURI.path == "/media/foto%20a.jpg" || e.requestURI.rawPath == "/media/foto%20a.jpg" -> e.reply(200, byteArrayOf(9, 8, 7))
                else -> e.reply(404, "nic".toByteArray())
            }
        }
        server.start()
    }

    @AfterTest fun stop() = server.stop(0)

    private fun change(id: String) = Change(id, "2026-11-14T10:00:00Z", "ales", Op.UPDATE_PERSON, "k", mapOf("given" to "Čeněk"))

    @Test
    fun `stazeni s heslem`() {
        start()
        assertEquals(gedcom, String(HttpWebApi(url, "ales", "tajne", allowInsecure = true).downloadGedcom()))
    }

    @Test
    fun `spatne heslo`() {
        start()
        assertFailsWith<WebException.Unauthorized> { HttpWebApi(url, "ales", "spatne", allowInsecure = true).downloadGedcom() }
    }

    @Test
    fun `odeslani zmen cte prijate a odmitnute`() {
        start()
        val r = HttpWebApi(url, "ales", "tajne", allowInsecure = true).pushChanges(listOf(change("a"), change("bad")))
        assertTrue(r.supported)
        assertEquals(setOf("a"), r.accepted)
        assertEquals(mapOf("bad" to "špatně"), r.rejected)
        assertTrue(lastBody.contains("Čeněk"))
    }

    @Test
    fun `web bez rozhrani pro zmeny`() {
        server.createContext("/") { e -> e.reply(404) }
        server.start()
        val api = HttpWebApi(url, "ales", "tajne", allowInsecure = true)
        assertTrue(!api.pushChanges(listOf(change("a"))).supported)
        assertTrue(!api.uploadMedia("0123456789abcdef", byteArrayOf(1), "image/jpeg"))
    }

    @Test
    fun `nahrani fotky a stazeni souboru`() {
        start()
        val api = HttpWebApi(url, "ales", "tajne", allowInsecure = true)
        assertTrue(api.uploadMedia("0123456789abcdef", byteArrayOf(5, 6), "image/jpeg"))
        assertEquals("5,6", lastBody)
        assertContentEquals(byteArrayOf(9, 8, 7), api.downloadFile("media/foto a.jpg"))
        assertNull(api.downloadFile("../etc/passwd"))
        assertNull(api.downloadFile("neni.jpg"))
    }

    @Test
    fun `adresa musi byt https`() {
        assertFailsWith<WebException.BadAddress> { HttpWebApi("http://example.com", "a", "b") }
        assertFailsWith<WebException.BadAddress> { HttpWebApi("  ", "a", "b") }
        // bez schématu se doplní https
        HttpWebApi("rodokmen.example.workers.dev", "a", "b")
    }

    @Test
    fun `nedostupny server`() {
        val port = server.address.port
        server.stop(0)
        assertFailsWith<WebException.Network> {
            HttpWebApi("http://127.0.0.1:$port", "a", "b", allowInsecure = true, timeoutMs = 2000).downloadGedcom()
        }
    }
}
