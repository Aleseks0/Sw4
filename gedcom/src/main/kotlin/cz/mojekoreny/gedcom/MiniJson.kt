package cz.mojekoreny.gedcom

/**
 * Malý JSON čtečka/zapisovač bez závislostí (modul `gedcom` je čistá JVM knihovna).
 * Hodnoty: Map<String, Any?>, List<Any?>, String, Double/Long, Boolean, null.
 */
object MiniJson {
    class JsonException(message: String) : Exception(message)

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.ws()
        val v = p.value()
        p.ws()
        if (p.i != text.length) throw JsonException("nadbytečný text na pozici ${p.i}")
        return v
    }

    @Suppress("UNCHECKED_CAST")
    fun parseObject(text: String): Map<String, Any?> =
        parse(text) as? Map<String, Any?> ?: throw JsonException("očekáván objekt")

    fun write(value: Any?, pretty: Boolean = false): String = StringBuilder().also { write(it, value, pretty, 0) }.toString()

    private fun write(sb: StringBuilder, v: Any?, pretty: Boolean, depth: Int) {
        when (v) {
            null -> sb.append("null")
            is String -> quote(sb, v)
            is Boolean -> sb.append(v)
            is Int, is Long -> sb.append(v.toString())
            is Number -> sb.append(if (v.toDouble() % 1.0 == 0.0 && kotlin.math.abs(v.toDouble()) < 1e15) v.toLong().toString() else v.toString())
            is Map<*, *> -> {
                if (v.isEmpty()) { sb.append("{}"); return }
                sb.append('{')
                var first = true
                for ((k, x) in v) {
                    if (!first) sb.append(',')
                    first = false
                    if (pretty) { sb.append('\n'); repeat(depth + 1) { sb.append("  ") } }
                    quote(sb, k.toString()); sb.append(':'); if (pretty) sb.append(' ')
                    write(sb, x, pretty, depth + 1)
                }
                if (pretty) { sb.append('\n'); repeat(depth) { sb.append("  ") } }
                sb.append('}')
            }
            is Iterable<*> -> {
                val items = v.toList()
                if (items.isEmpty()) { sb.append("[]"); return }
                sb.append('[')
                items.forEachIndexed { i, x ->
                    if (i > 0) sb.append(',')
                    if (pretty) { sb.append('\n'); repeat(depth + 1) { sb.append("  ") } }
                    write(sb, x, pretty, depth + 1)
                }
                if (pretty) { sb.append('\n'); repeat(depth) { sb.append("  ") } }
                sb.append(']')
            }
            else -> quote(sb, v.toString())
        }
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            if (i >= s.length) throw JsonException("neočekávaný konec")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw JsonException("neočekávaný znak „$c“ na pozici $i")
            }
        }
        fun lit(w: String, v: Any?): Any? {
            if (!s.startsWith(w, i)) throw JsonException("neplatná hodnota na pozici $i")
            i += w.length; return v
        }
        fun num(): Any {
            val st = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val t = s.substring(st, i)
            return t.toLongOrNull() ?: t.toDoubleOrNull() ?: throw JsonException("neplatné číslo „$t“")
        }
        fun str(): String {
            i++ // "
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) throw JsonException("nedokončený řetězec")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) throw JsonException("nedokončený řetězec")
                        when (val e = s[i++]) {
                            'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                            '/' -> sb.append('/'); '\\' -> sb.append('\\'); '"' -> sb.append('"')
                            'u' -> {
                                if (i + 4 > s.length) throw JsonException("neplatné \\u")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4
                            }
                            else -> throw JsonException("neplatný escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun arr(): List<Any?> {
            i++
            val out = mutableListOf<Any?>()
            ws()
            if (i < s.length && s[i] == ']') { i++; return out }
            while (true) {
                ws(); out += value(); ws()
                if (i >= s.length) throw JsonException("nedokončené pole")
                when (s[i++]) { ',' -> continue; ']' -> return out; else -> throw JsonException("očekáváno , nebo ] na pozici ${i - 1}") }
            }
        }
        fun obj(): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            ws()
            if (i < s.length && s[i] == '}') { i++; return out }
            while (true) {
                ws()
                if (i >= s.length || s[i] != '"') throw JsonException("očekáván klíč na pozici $i")
                val k = str(); ws()
                if (i >= s.length || s[i++] != ':') throw JsonException("očekáváno : na pozici ${i - 1}")
                ws(); out[k] = value(); ws()
                if (i >= s.length) throw JsonException("nedokončený objekt")
                when (s[i++]) { ',' -> continue; '}' -> return out; else -> throw JsonException("očekáváno , nebo } na pozici ${i - 1}") }
            }
        }
    }
}
