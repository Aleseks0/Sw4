package cz.mojekoreny.gedcom

/**
 * Jeden řádek GEDCOM i se svými podřízenými řádky.
 *
 * Hodnota [value] už má spojené řádky CONC/CONT (CONT = nový řádek `\n`),
 * takže CONC ani CONT se mezi [children] nikdy neobjeví.
 */
data class GedcomNode(
    val tag: String,
    val value: String? = null,
    val xref: String? = null,
    val children: List<GedcomNode> = emptyList(),
) {
    fun child(tag: String): GedcomNode? = children.firstOrNull { it.tag == tag }
    fun childrenOf(tag: String): List<GedcomNode> = children.filter { it.tag == tag }
    fun childValue(tag: String): String? = child(tag)?.value

    /** Hodnota je ukazatel na záznam (`@I1@`). */
    val pointer: String?
        get() = value?.takeIf { it.length > 2 && it.startsWith("@") && it.endsWith("@") && !it.startsWith("@#") }
}

class GedcomSyntaxException(message: String, val line: Int) : Exception("Řádek $line: $message")

/** Rozdělí text GEDCOM na stromy záznamů úrovně 0. */
object GedcomParser {
    private val LINE = Regex("""^\s*(\d{1,2})\s+(?:(@[^@\s][^@]*@)\s+)?([A-Za-z0-9_]+)(?: (.*))?$""")

    private class Builder(val level: Int, val tag: String, var value: String?, val xref: String?) {
        val children = mutableListOf<Builder>()
        fun build(): GedcomNode {
            val out = mutableListOf<GedcomNode>()
            var v = value
            for (c in children) {
                when (c.tag) {
                    "CONT" -> v = (v ?: "") + "\n" + (c.value ?: "")
                    "CONC" -> v = (v ?: "") + (c.value ?: "")
                    else -> out += c.build()
                }
            }
            return GedcomNode(tag, v, xref, out)
        }
    }

    /**
     * @param lenient přeskočí nečitelné řádky místo vyhození výjimky
     *        (skutečné soubory z různých programů bývají drobně poškozené).
     */
    fun parse(text: String, lenient: Boolean = true): List<GedcomNode> {
        val roots = mutableListOf<Builder>()
        val stack = ArrayDeque<Builder>()
        var lineNo = 0
        for (raw in text.removePrefix("﻿").split('\n')) {
            lineNo++
            val line = raw.trimEnd('\r')
            if (line.isBlank()) continue
            val m = LINE.matchEntire(line)
            if (m == null) {
                // Některé programy zalomí dlouhou poznámku bez CONT – připojíme ji k předchozímu řádku.
                val last = stack.lastOrNull()
                if (lenient && last != null) {
                    last.value = (last.value ?: "") + "\n" + line
                    continue
                }
                if (lenient) continue
                throw GedcomSyntaxException("nečitelný řádek „$line“", lineNo)
            }
            val level = m.groupValues[1].toInt()
            val xref = m.groupValues[2].ifEmpty { null }
            val tag = m.groupValues[3].uppercase()
            val value = m.groups[4]?.value?.let { if (it.isEmpty()) null else it }

            while (stack.isNotEmpty() && stack.last().level >= level) stack.removeLast()
            val node = Builder(level, tag, value, xref)
            if (level == 0 || stack.isEmpty()) {
                if (level != 0 && !lenient) throw GedcomSyntaxException("chybí nadřazený řádek", lineNo)
                roots += node
            } else {
                if (level > stack.last().level + 1 && !lenient) {
                    throw GedcomSyntaxException("přeskočená úroveň", lineNo)
                }
                stack.last().children += node
            }
            stack.addLast(node)
        }
        return roots.map { it.build() }
    }
}
