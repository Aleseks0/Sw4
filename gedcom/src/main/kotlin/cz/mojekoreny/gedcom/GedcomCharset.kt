package cz.mojekoreny.gedcom

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.Normalizer

/** Rozpozná kódování souboru GEDCOM a převede bajty na text. */
object GedcomCharset {
    private val WIN1250: Charset = Charset.forName("windows-1250")
    private val WIN1252: Charset = Charset.forName("windows-1252")
    private val CHAR_LINE = Regex("""(?m)^\s*1\s+CHAR\s+(\S+)""")

    data class Decoded(val text: String, val encoding: String)

    fun decode(bytes: ByteArray): Decoded {
        // 1) BOM
        if (bytes.startsWith(0xEF, 0xBB, 0xBF)) return Decoded(String(bytes, 3, bytes.size - 3, Charsets.UTF_8), "UTF-8")
        if (bytes.startsWith(0xFF, 0xFE)) return Decoded(String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE), "UTF-16LE")
        if (bytes.startsWith(0xFE, 0xFF)) return Decoded(String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE), "UTF-16BE")
        // 2) UTF-16 bez BOM: "0" a nulový bajt
        if (bytes.size >= 2 && bytes[0] == '0'.code.toByte() && bytes[1] == 0.toByte()) {
            return Decoded(String(bytes, Charsets.UTF_16LE), "UTF-16LE")
        }
        if (bytes.size >= 2 && bytes[0] == 0.toByte() && bytes[1] == '0'.code.toByte()) {
            return Decoded(String(bytes, Charsets.UTF_16BE), "UTF-16BE")
        }

        // 3) Hlavička 1 CHAR …
        val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1)
        val declared = CHAR_LINE.find(head)?.groupValues?.get(1)?.uppercase()

        // Platné UTF-8 s ne-ASCII znaky je UTF-8 téměř jistě, ať hlavička tvrdí cokoli.
        val utf8 = strictUtf8(bytes)
        if (utf8 != null && (declared == null || declared in setOf("UTF-8", "UTF8", "UNICODE") || bytes.any { it < 0 })) {
            return Decoded(utf8, "UTF-8")
        }

        return when (declared) {
            "ANSEL" -> Decoded(Ansel.decode(bytes), "ANSEL")
            "ASCII" -> Decoded(String(bytes, Charsets.ISO_8859_1), "ASCII")
            "CP1250", "WINDOWS-1250", "WIN1250", "MS-EE" -> Decoded(String(bytes, WIN1250), "windows-1250")
            "CP1252", "WINDOWS-1252", "WIN1252" -> Decoded(String(bytes, WIN1252), "windows-1252")
            "IBMPC", "IBM", "CP852" -> Decoded(String(bytes, Charset.forName("IBM852")), "IBM852")
            "ISO-8859-2", "LATIN2" -> Decoded(String(bytes, Charset.forName("ISO-8859-2")), "ISO-8859-2")
            else -> guessAnsi(bytes) // "ANSI", neznámé nebo chybějící
        }
    }

    /** „ANSI“ znamená kódovou stránku Windows – v Česku obvykle 1250, jinde 1252. */
    private fun guessAnsi(bytes: ByteArray): Decoded {
        val czech = "ěščřžýáíéůúťďňĚŠČŘŽÝÁÍÉŮÚŤĎŇ"
        val a = String(bytes, WIN1250)
        val b = String(bytes, WIN1252)
        val scoreA = a.count { it in czech }
        val scoreB = b.count { it in czech }
        return if (scoreA >= scoreB) Decoded(a, "windows-1250") else Decoded(b, "windows-1252")
    }

    private fun strictUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun ByteArray.startsWith(vararg b: Int) =
        size >= b.size && b.indices.all { (this[it].toInt() and 0xFF) == b[it] }
}

/**
 * ANSEL (ANSI Z39.47) – kódování starších genealogických programů.
 * Diakritika je samostatný bajt *před* písmenem; Unicode ji chce *za* písmenem.
 */
object Ansel {
    private val SPECIAL = mapOf(
        0xA1 to 'Ł', 0xA2 to 'Ø', 0xA3 to 'Đ', 0xA4 to 'Þ', 0xA5 to 'Æ',
        0xA6 to 'Œ', 0xA7 to 'ʹ', 0xA8 to '·', 0xA9 to '♭', 0xAA to '®',
        0xAB to '±', 0xAC to 'Ơ', 0xAD to 'Ư', 0xAE to 'ʼ', 0xB0 to 'ʻ',
        0xB1 to 'ł', 0xB2 to 'ø', 0xB3 to 'đ', 0xB4 to 'þ', 0xB5 to 'æ',
        0xB6 to 'œ', 0xB7 to 'ʺ', 0xB8 to 'ı', 0xB9 to '£', 0xBA to 'ð',
        0xBC to 'ơ', 0xBD to 'ư', 0xC0 to '°', 0xC1 to 'ℓ', 0xC2 to '℗',
        0xC3 to '©', 0xC4 to '♯', 0xC5 to '¿', 0xC6 to '¡', 0xC7 to 'ß',
        0xC8 to '€',
    )
    private val COMBINING = mapOf(
        0xE0 to '̉', 0xE1 to '̀', 0xE2 to '́', 0xE3 to '̂', 0xE4 to '̃',
        0xE5 to '̄', 0xE6 to '̆', 0xE7 to '̇', 0xE8 to '̈', 0xE9 to '̌',
        0xEA to '̊', 0xEB to '︠', 0xEC to '︡', 0xED to '̕', 0xEE to '̋',
        0xEF to '̐', 0xF0 to '̧', 0xF1 to '̨', 0xF2 to '̣', 0xF3 to '̤',
        0xF4 to '̥', 0xF5 to '̳', 0xF6 to '̲', 0xF7 to '̦', 0xF8 to '̜',
        0xF9 to '̮', 0xFA to '︢', 0xFB to '︣', 0xFE to '̓',
    )
    private val REVERSE_SPECIAL = SPECIAL.entries.associate { (k, v) -> v to k }
    private val REVERSE_COMBINING = COMBINING.entries.associate { (k, v) -> v to k }

    fun decode(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size)
        val pending = StringBuilder()
        for (byte in bytes) {
            val b = byte.toInt() and 0xFF
            val comb = COMBINING[b]
            if (comb != null) {
                pending.append(comb)
                continue
            }
            val ch = when {
                b < 0x80 -> b.toChar()
                else -> SPECIAL[b] ?: '�'
            }
            sb.append(ch)
            if (pending.isNotEmpty()) {
                // ANSEL: první uvedená značka patří nejblíž k písmenu.
                sb.append(pending)
                pending.clear()
            }
        }
        sb.append(pending)
        return Normalizer.normalize(sb, Normalizer.Form.NFC)
    }

    /** Převod do ANSEL (pro export do starých programů). Neznámé znaky → „?“. */
    fun encode(text: String): ByteArray {
        val out = java.io.ByteArrayOutputStream(text.length)
        val nfd = Normalizer.normalize(text, Normalizer.Form.NFD)
        var i = 0
        while (i < nfd.length) {
            val base = nfd[i]
            var j = i + 1
            val marks = StringBuilder()
            while (j < nfd.length && Character.getType(nfd[j]) == Character.NON_SPACING_MARK.toInt()) {
                marks.append(nfd[j]); j++
            }
            for (m in marks) out.write(REVERSE_COMBINING[m] ?: continue)
            when {
                base.code < 0x80 -> out.write(base.code)
                REVERSE_SPECIAL[base] != null -> out.write(REVERSE_SPECIAL[base]!!)
                else -> out.write('?'.code)
            }
            i = j
        }
        return out.toByteArray()
    }
}
