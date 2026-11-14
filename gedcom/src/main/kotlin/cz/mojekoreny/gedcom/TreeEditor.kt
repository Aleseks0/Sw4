package cz.mojekoreny.gedcom

/** Pole osoby upravitelná z aplikace. Hodnota null nebo prázdný text = smazat. */
object PersonFields {
    const val GIVEN = "given"
    const val SURNAME = "surname"
    const val BIRTH_SURNAME = "birthSurname"
    const val SEX = "sex"
    const val BIRTH_DATE = "birthDate"
    const val BIRTH_PLACE = "birthPlace"
    const val DEATH_DATE = "deathDate"
    const val DEATH_PLACE = "deathPlace"
    const val OCCUPATION = "occupation"
    const val NOTE = "note"

    val ALL = listOf(GIVEN, SURNAME, BIRTH_SURNAME, SEX, BIRTH_DATE, BIRTH_PLACE, DEATH_DATE, DEATH_PLACE, OCCUPATION, NOTE)

    /** Aktuální hodnoty (data v zápisu GEDCOM; prázdné pole = null). */
    fun read(p: Person): Map<String, String?> {
        val first = p.names.firstOrNull()
        return linkedMapOf(
            GIVEN to first?.given.blankToNull(),
            SURNAME to first?.surname.blankToNull(),
            BIRTH_SURNAME to p.birthSurname.blankToNull(),
            SEX to p.sex.code,
            BIRTH_DATE to p.event("BIRT")?.date.blankToNull(),
            BIRTH_PLACE to p.event("BIRT")?.place.blankToNull(),
            DEATH_DATE to p.event("DEAT")?.date.blankToNull(),
            DEATH_PLACE to p.event("DEAT")?.place.blankToNull(),
            OCCUPATION to p.event("OCCU")?.value.blankToNull(),
            NOTE to p.notes.firstOrNull { it.text != null }?.text.blankToNull(),
        )
    }

    /** Zapíše zadané hodnoty do osoby; pole, která v [f] nejsou, nechá být. */
    fun apply(p: Person, f: Map<String, String?>): Person {
        var out = p
        if (GIVEN in f || SURNAME in f) {
            val cur = out.names.firstOrNull() ?: PersonName()
            val n = cur.copy(
                given = if (GIVEN in f) f[GIVEN].orEmpty().trim() else cur.given,
                surname = if (SURNAME in f) f[SURNAME].orEmpty().trim() else cur.surname,
            )
            out = out.copy(names = if (out.names.isEmpty()) listOf(n) else listOf(n) + out.names.drop(1))
        }
        if (BIRTH_SURNAME in f) {
            val value = f[BIRTH_SURNAME].blankToNull()
            val idx = out.names.indexOfFirst { it !== out.names.first() && it.type?.lowercase() in setOf("birth", "maiden") }
            val given = out.names.firstOrNull()?.given.orEmpty()
            out = out.copy(
                names = when {
                    value == null && idx >= 0 -> out.names.filterIndexed { i, _ -> i != idx }
                    value == null -> out.names
                    idx >= 0 -> out.names.mapIndexed { i, n -> if (i == idx) n.copy(surname = value) else n }
                    else -> out.names + PersonName(given = given, surname = value, type = "birth")
                },
            )
        }
        if (SEX in f) out = out.copy(sex = Sex.of(f[SEX]))
        if (BIRTH_DATE in f || BIRTH_PLACE in f) {
            out = out.copy(events = upsert(out.events, "BIRT", BIRTH_DATE in f, f[BIRTH_DATE], BIRTH_PLACE in f, f[BIRTH_PLACE]))
        }
        if (DEATH_DATE in f || DEATH_PLACE in f) {
            out = out.copy(events = upsert(out.events, "DEAT", DEATH_DATE in f, f[DEATH_DATE], DEATH_PLACE in f, f[DEATH_PLACE]))
        }
        if (OCCUPATION in f) {
            val v = f[OCCUPATION].blankToNull()
            val idx = out.events.indexOfFirst { it.tag == "OCCU" }
            out = out.copy(
                events = when {
                    v == null && idx >= 0 -> out.events.filterIndexed { i, _ -> i != idx }
                    v == null -> out.events
                    idx >= 0 -> out.events.mapIndexed { i, e -> if (i == idx) e.copy(value = v) else e }
                    else -> out.events + Event("OCCU", value = v)
                },
            )
        }
        if (NOTE in f) {
            val v = f[NOTE].blankToNull()
            val idx = out.notes.indexOfFirst { it.text != null }
            out = out.copy(
                notes = when {
                    v == null && idx >= 0 -> out.notes.filterIndexed { i, _ -> i != idx }
                    v == null -> out.notes
                    idx >= 0 -> out.notes.mapIndexed { i, n -> if (i == idx) Note(text = v) else n }
                    else -> out.notes + Note(text = v)
                },
            )
        }
        return out
    }

    private fun upsert(events: List<Event>, tag: String, setDate: Boolean, date: String?, setPlace: Boolean, place: String?): List<Event> {
        val idx = events.indexOfFirst { it.tag == tag }
        val cur = events.getOrNull(idx) ?: Event(tag, value = if (tag == "DEAT") "Y" else null)
        val updated = cur.copy(
            date = if (setDate) date.blankToNull() else cur.date,
            place = if (setPlace) place.blankToNull() else cur.place,
        )
        val hasContent = updated.date != null || updated.place != null || updated.notes.isNotEmpty() ||
            updated.citations.isNotEmpty() || updated.media.isNotEmpty() || updated.extra.isNotEmpty() ||
            updated.cause != null || updated.type != null
        return when {
            idx >= 0 && !hasContent -> events.filterIndexed { i, _ -> i != idx }
            idx >= 0 -> events.mapIndexed { i, e -> if (i == idx) updated else e }
            !hasContent -> events
            else -> events + updated
        }
    }

    /** Zadaný text data převede do zápisu GEDCOM (česky i anglicky); nerozpoznané vrátí jako frázi. */
    fun normalizeDate(text: String?): String? = GedcomDate.parse(text)?.toGedcom()

    /** Datum z GEDCOM pro zobrazení ve formuláři („9. 5. 1849“). */
    fun displayDate(raw: String?): String = GedcomDate.parse(raw)?.toCzech().orEmpty()
}

private fun String?.blankToNull(): String? = this?.trim()?.ifEmpty { null }

// ------------------------------------------------------------------ klíče a vyhledání

/** Stálý klíč osoby: `_UID` (slug z webu) nebo ID v souboru. */
fun GedcomTree.keyOf(p: Person): String = p.uid ?: p.id

fun GedcomTree.findByKey(key: String): Person? =
    persons.firstOrNull { it.uid == key } ?: persons.firstOrNull { it.id == key }

internal fun GedcomTree.replace(p: Person): GedcomTree = copy(persons = persons.map { if (it.id == p.id) p else it })
internal fun GedcomTree.replace(f: Family): GedcomTree = copy(families = families.map { if (it.id == f.id) f else it })

internal fun nextId(prefix: String, existing: Collection<String>): String {
    val used = existing.toHashSet()
    var n = (existing.mapNotNull { Regex("^${prefix}(\\d+)$").matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0) + 1
    while ("$prefix$n" in used) n++
    return "$prefix$n"
}

// ------------------------------------------------------------------ fotografie

data class Photo(val path: String, val title: String?, val format: String?, val primary: Boolean)

private fun MediaLink.isPrimary() = extra.any { it.tag == "_PRIM" && it.value.equals("Y", ignoreCase = true) }

/** Fotky osoby (vložené i odkazem na záznam OBJE) v pořadí, v jakém jsou zapsány. */
fun GedcomTree.photosOf(p: Person): List<Photo> = p.media.flatMap { link ->
    val prim = link.isPrimary()
    when {
        link.file != null -> listOf(Photo(link.file.path, link.file.title, link.file.format, prim))
        link.mediaId != null -> media.firstOrNull { it.id == link.mediaId }?.files.orEmpty()
            .map { Photo(it.path, it.title, it.format, prim) }
        else -> emptyList()
    }
}.filter { it.path.isNotBlank() }

/** Portrét: fotka označená jako hlavní, jinak první. */
fun GedcomTree.portraitOf(p: Person): Photo? = photosOf(p).let { l -> l.firstOrNull { it.primary } ?: l.firstOrNull() }
