package cz.mojekoreny.gedcom

enum class DateQualifier { EXACT, ABOUT, CALCULATED, ESTIMATED, BEFORE, AFTER, BETWEEN, FROM, TO, FROM_TO, PHRASE }

/** Část data; den a měsíc můžou chybět („1849“, „květen 1849“). */
data class DatePart(val year: Int, val month: Int? = null, val day: Int? = null) {
    init {
        require(month == null || month in 1..12) { "měsíc $month" }
        require(day == null || (month != null && day in 1..31)) { "den $day" }
    }

    val sortKey: Int get() = year * 10000 + (month ?: 0) * 100 + (day ?: 0)
}

/**
 * Datum události. Rozumí formátu GEDCOM („ABT 9 MAY 1849“) i českému zápisu
 * („asi 9. 5. 1849“, „mezi 1850 a 1855“, „před 1900“).
 */
data class GedcomDate(
    val qualifier: DateQualifier,
    val start: DatePart? = null,
    val end: DatePart? = null,
    val phrase: String? = null,
) {
    val year: Int? get() = (start ?: end)?.year
    val sortKey: Int? get() = (start ?: end)?.sortKey

    fun toGedcom(): String = when (qualifier) {
        DateQualifier.EXACT -> gd(start)
        DateQualifier.ABOUT -> "ABT ${gd(start)}"
        DateQualifier.CALCULATED -> "CAL ${gd(start)}"
        DateQualifier.ESTIMATED -> "EST ${gd(start)}"
        DateQualifier.BEFORE -> "BEF ${gd(end)}"
        DateQualifier.AFTER -> "AFT ${gd(start)}"
        DateQualifier.BETWEEN -> "BET ${gd(start)} AND ${gd(end)}"
        DateQualifier.FROM -> "FROM ${gd(start)}"
        DateQualifier.TO -> "TO ${gd(end)}"
        DateQualifier.FROM_TO -> "FROM ${gd(start)} TO ${gd(end)}"
        DateQualifier.PHRASE -> "(${phrase.orEmpty()})"
    }

    fun toCzech(): String = when (qualifier) {
        DateQualifier.EXACT -> cz(start)
        DateQualifier.ABOUT -> "asi ${cz(start)}"
        DateQualifier.CALCULATED -> "vypočteno ${cz(start)}"
        DateQualifier.ESTIMATED -> "odhadem ${cz(start)}"
        DateQualifier.BEFORE -> "před ${cz(end)}"
        DateQualifier.AFTER -> "po ${cz(start)}"
        DateQualifier.BETWEEN -> "mezi ${cz(start)} a ${cz(end)}"
        DateQualifier.FROM -> "od ${cz(start)}"
        DateQualifier.TO -> "do ${cz(end)}"
        DateQualifier.FROM_TO -> "od ${cz(start)} do ${cz(end)}"
        DateQualifier.PHRASE -> phrase.orEmpty()
    }

    override fun toString() = toCzech()

    companion object {
        private val GED_MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
        private val CZ_NOMINATIVE = listOf(
            "leden", "únor", "březen", "duben", "květen", "červen",
            "červenec", "srpen", "září", "říjen", "listopad", "prosinec",
        )
        private val CZ_GENITIVE = listOf(
            "ledna", "února", "března", "dubna", "května", "června",
            "července", "srpna", "září", "října", "listopadu", "prosince",
        )

        private fun gd(p: DatePart?): String {
            p ?: return ""
            return listOfNotNull(p.day?.toString(), p.month?.let { GED_MONTHS[it - 1] }, p.year.toString()).joinToString(" ")
        }

        private fun cz(p: DatePart?): String {
            p ?: return ""
            return when {
                p.day != null -> "${p.day}. ${p.month}. ${p.year}"
                p.month != null -> "${CZ_NOMINATIVE[p.month - 1]} ${p.year}"
                else -> p.year.toString()
            }
        }

        fun exact(year: Int, month: Int? = null, day: Int? = null) =
            GedcomDate(DateQualifier.EXACT, DatePart(year, month, day))

        /** Vrátí null jen pro prázdný text; nesrozumitelný text vrátí jako [DateQualifier.PHRASE]. */
        fun parse(input: String?): GedcomDate? {
            val text = input?.trim()?.replace(Regex("\\s+"), " ")?.removePrefix("@#DGREGORIAN@ ") ?: return null
            if (text.isEmpty()) return null
            return tryParse(text) ?: GedcomDate(DateQualifier.PHRASE, phrase = text.removeSurrounding("(", ")"))
        }

        private fun tryParse(text: String): GedcomDate? {
            val lower = text.lowercase()
            // Rozsahy
            Regex("^(?:bet|mezi) (.+?) (?:and|a) (.+)$").matchEntire(lower)?.let { m ->
                return range(DateQualifier.BETWEEN, m.groupValues[1], m.groupValues[2])
            }
            Regex("^(?:from|od) (.+?) (?:to|do) (.+)$").matchEntire(lower)?.let { m ->
                return range(DateQualifier.FROM_TO, m.groupValues[1], m.groupValues[2])
            }
            Regex("^(\\d{3,4}) ?[–-] ?(\\d{3,4})$").matchEntire(lower)?.let { m ->
                return range(DateQualifier.BETWEEN, m.groupValues[1], m.groupValues[2])
            }
            if (lower.startsWith("int ")) {
                // INT datum (fráze) – necháme si datum, frázi zahodíme do phrase
                val m = Regex("^int (.+?) \\((.*)\\)$").matchEntire(lower)
                if (m != null) {
                    val p = part(m.groupValues[1]) ?: return null
                    return GedcomDate(DateQualifier.EXACT, p, phrase = text.substringAfter("(").removeSuffix(")"))
                }
            }
            val prefixes = listOf(
                DateQualifier.ABOUT to listOf("abt", "about", "asi", "cca", "cca.", "kolem", "okolo", "přibližně", "~"),
                DateQualifier.CALCULATED to listOf("cal", "vypočteno", "vypočt."),
                DateQualifier.ESTIMATED to listOf("est", "odhadem", "odhad"),
                DateQualifier.BEFORE to listOf("bef", "before", "před"),
                DateQualifier.AFTER to listOf("aft", "after", "po"),
                DateQualifier.FROM to listOf("from", "od"),
                DateQualifier.TO to listOf("to", "do"),
            )
            for ((q, words) in prefixes) {
                for (w in words) {
                    val rest = when {
                        lower.startsWith("$w ") -> lower.substring(w.length + 1)
                        w == "~" && lower.startsWith("~") -> lower.substring(1).trim()
                        else -> null
                    } ?: continue
                    val p = part(rest) ?: return null
                    return when (q) {
                        DateQualifier.BEFORE, DateQualifier.TO -> GedcomDate(q, end = p)
                        else -> GedcomDate(q, start = p)
                    }
                }
            }
            return part(lower)?.let { GedcomDate(DateQualifier.EXACT, it) }
        }

        private fun range(q: DateQualifier, a: String, b: String): GedcomDate? {
            val s = part(a) ?: return null
            val e = part(b) ?: return null
            return GedcomDate(q, s, e)
        }

        private fun monthOf(word: String): Int? {
            val w = word.trimEnd('.')
            GED_MONTHS.indexOfFirst { it.equals(w, ignoreCase = true) }.takeIf { it >= 0 }?.let { return it + 1 }
            CZ_GENITIVE.indexOf(w).takeIf { it >= 0 }?.let { return it + 1 }
            CZ_NOMINATIVE.indexOf(w).takeIf { it >= 0 }?.let { return it + 1 }
            return null
        }

        /** Jedno datum bez kvalifikátoru. Text už je malými písmeny. */
        private fun part(s: String): DatePart? = runCatching {
            val t = s.trim()
            // 1849, 1849/50 (dvojí rok)
            Regex("^(\\d{3,4})(?:/\\d{1,2})?$").matchEntire(t)?.let { return DatePart(it.groupValues[1].toInt()) }
            // 9. 5. 1849 / 9.5.1849
            Regex("^(\\d{1,2})\\. ?(\\d{1,2})\\. ?(\\d{3,4})$").matchEntire(t)?.let {
                val (d, m, y) = it.destructured
                return DatePart(y.toInt(), m.toInt(), d.toInt())
            }
            // 5. 1849 (měsíc a rok)
            Regex("^(\\d{1,2})\\. ?(\\d{3,4})$").matchEntire(t)?.let {
                val (m, y) = it.destructured
                return DatePart(y.toInt(), m.toInt())
            }
            // 1849-05-09
            Regex("^(\\d{4})-(\\d{2})(?:-(\\d{2}))?$").matchEntire(t)?.let {
                val (y, m, d) = it.destructured
                return DatePart(y.toInt(), m.toInt(), d.ifEmpty { null }?.toInt())
            }
            // 9 MAY 1849 / MAY 1849 / 9. května 1849 / květen 1849
            Regex("^(?:(\\d{1,2})\\.? )?(\\p{L}+\\.?) (\\d{3,4})(?:/\\d{1,2})?$").matchEntire(t)?.let {
                val (d, mw, y) = it.destructured
                val m = monthOf(mw) ?: return null
                return DatePart(y.toInt(), m, d.ifEmpty { null }?.toInt())
            }
            null
        }.getOrNull()
    }
}
