package cz.mojekoreny.sync

import cz.mojekoreny.gedcom.Change
import cz.mojekoreny.gedcom.MiniJson
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Base64

/** Chyby komunikace s webem, přeložitelné do hlášky pro uživatele. */
sealed class WebException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class BadAddress(msg: String) : WebException(msg)
    class Unauthorized : WebException("Web odmítl jméno nebo heslo.")
    class NotFound(what: String) : WebException("Na webu nebylo nalezeno: $what")
    class Network(cause: Throwable) : WebException("Nelze se spojit s webem (${cause.message ?: cause.javaClass.simpleName}).", cause)
    class Server(val code: Int, body: String) : WebException("Web vrátil chybu $code${if (body.isNotBlank()) ": ${body.take(200)}" else ""}")
}

data class PushResult(
    /** false = web zatím neumí přijímat změny (adresa /api/v1/changes neexistuje). */
    val supported: Boolean,
    val accepted: Set<String> = emptySet(),
    val rejected: Map<String, String> = emptyMap(),
)

interface WebApi {
    /** Úplný rodokmen ve formátu GEDCOM (včetně žijících osob), vyžaduje přihlášení. */
    fun downloadGedcom(): ByteArray

    fun pushChanges(changes: List<Change>): PushResult

    /** @return false, pokud web nahrávání fotek nepodporuje. */
    fun uploadMedia(sha: String, bytes: ByteArray, mime: String): Boolean

    /** Stáhne soubor z webu (např. fotografii osoby podle cesty z GEDCOM); null = neexistuje. */
    fun downloadFile(path: String): ByteArray?
}

/**
 * Klient webu přes HTTPS a HTTP Basic (jméno může být libovolné, rozhoduje heslo –
 * stejně jako u stažení GEDCOM na webu). [allowInsecure] jen pro testy na localhostu.
 */
class HttpWebApi(
    baseUrl: String,
    private val user: String,
    private val password: String,
    private val allowInsecure: Boolean = false,
    private val gedcomPath: String = "/stazeni/rodokmen.ged",
    private val timeoutMs: Int = 30_000,
) : WebApi {

    private val base: String = normalize(baseUrl)

    private fun normalize(raw: String): String {
        var u = raw.trim().trimEnd('/')
        if (u.isEmpty()) throw WebException.BadAddress("Zadejte adresu webu.")
        if (!u.contains("://")) u = "https://$u"
        val uri = try { URI(u) } catch (e: Exception) { throw WebException.BadAddress("Adresa webu není platná.") }
        if (uri.host.isNullOrEmpty()) throw WebException.BadAddress("Adresa webu není platná.")
        if (uri.scheme != "https" && !(allowInsecure && uri.scheme == "http")) {
            throw WebException.BadAddress("Adresa webu musí začínat https:// (heslo se nesmí posílat nešifrovaně).")
        }
        return u
    }

    private class Response(val code: Int, val body: ByteArray) {
        val text get() = String(body, Charsets.UTF_8)
    }

    private fun request(method: String, path: String, body: ByteArray? = null, contentType: String? = null): Response {
        val conn = try {
            (URL(base + path).openConnection() as HttpURLConnection)
        } catch (e: IOException) { throw WebException.Network(e) }
        try {
            conn.requestMethod = method
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            val token = Base64.getEncoder().encodeToString("$user:$password".toByteArray(Charsets.UTF_8))
            conn.setRequestProperty("Authorization", "Basic $token")
            conn.setRequestProperty("Accept", "*/*")
            conn.setRequestProperty("User-Agent", "MojeKoreny")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType ?: "application/octet-stream")
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val out = ByteArrayOutputStream()
            stream?.use { it.copyTo(out) }
            return Response(code, out.toByteArray())
        } catch (e: IOException) {
            throw WebException.Network(e)
        } finally {
            conn.disconnect()
        }
    }

    private fun check(r: Response, what: String): Response = when {
        r.code == 401 || r.code == 403 -> throw WebException.Unauthorized()
        r.code == 404 -> throw WebException.NotFound(what)
        r.code >= 400 -> throw WebException.Server(r.code, r.text)
        else -> r
    }

    override fun downloadGedcom(): ByteArray = check(request("GET", gedcomPath), "rodokmen ($gedcomPath)").body

    override fun pushChanges(changes: List<Change>): PushResult {
        if (changes.isEmpty()) return PushResult(supported = true)
        val body = MiniJson.write(mapOf("changes" to changes.map { it.toMap() })).toByteArray(Charsets.UTF_8)
        val r = request("POST", "/api/v1/changes", body, "application/json; charset=utf-8")
        if (r.code == 404 || r.code == 405 || r.code == 501) return PushResult(supported = false)
        check(r, "změny")
        val json = try { MiniJson.parseObject(r.text) } catch (e: Exception) {
            throw WebException.Server(r.code, "neplatná odpověď")
        }
        @Suppress("UNCHECKED_CAST")
        val accepted = (json["accepted"] as? List<Any?>)?.mapNotNull { it as? String }?.toSet().orEmpty()
        @Suppress("UNCHECKED_CAST")
        val rejected = (json["rejected"] as? List<Any?>).orEmpty().mapNotNull { it as? Map<String, Any?> }
            .mapNotNull { m -> (m["id"] as? String)?.let { it to (m["reason"] as? String ?: "odmítnuto") } }.toMap()
        return PushResult(supported = true, accepted = accepted, rejected = rejected)
    }

    override fun uploadMedia(sha: String, bytes: ByteArray, mime: String): Boolean {
        require(sha.matches(Regex("[0-9a-f]{16,64}"))) { "neplatný otisk fotografie" }
        val r = request("PUT", "/api/v1/media/$sha", bytes, mime)
        if (r.code == 404 || r.code == 405 || r.code == 501) return false
        check(r, "fotografie")
        return true
    }

    override fun downloadFile(path: String): ByteArray? {
        val clean = path.trimStart('/')
        if (clean.contains("..") || clean.contains("://")) return null
        val encoded = clean.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val r = request("GET", "/$encoded")
        return if (r.code in 200..299) r.body else null
    }
}
