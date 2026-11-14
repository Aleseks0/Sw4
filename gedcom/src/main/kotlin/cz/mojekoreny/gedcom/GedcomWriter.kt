package cz.mojekoreny.gedcom

import java.io.OutputStream
import java.time.LocalDate

enum class GedcomVersion(val label: String) { V551("5.5.1"), V70("7.0") }

/** Zápis [GedcomTree] do GEDCOM 5.5.1 (nejširší podpora) nebo 7.0, vždy v UTF-8. */
class GedcomWriter(
    private val version: GedcomVersion = GedcomVersion.V551,
    private val appVersion: String = "1.0",
    /** Datum vytvoření do hlavičky; null = nezapisovat (kvůli porovnatelným výstupům v testech). */
    private val date: LocalDate? = null,
) {
    companion object {
        /** Délka hodnoty, po které se v 5.5.1 zalamuje pomocí CONC (limit řádku je 255 znaků). */
        private const val CHUNK = 200
        private val MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
        private val VALID_XREF = Regex("^[A-Z0-9_]{1,20}$")

        internal fun fileNode(f: MediaFile, titleUnderFile: Boolean = true): GedcomNode = GedcomNode(
            "FILE", f.path,
            children = listOfNotNull(
                f.format?.let { GedcomNode("FORM", it) },
                f.title?.takeIf { titleUnderFile }?.let { GedcomNode("TITL", it) },
            ),
        )
    }

    fun write(tree: GedcomTree, out: OutputStream) {
        out.write(write(tree).toByteArray(Charsets.UTF_8))
    }

    fun write(tree: GedcomTree): String {
        val prepared = if (version == GedcomVersion.V70) inlineMediaToRecords(tree) else tree
        val ids = XrefMap(prepared)
        val sb = StringBuilder()
        for (n in toNodes(prepared, ids)) serialize(n, 0, sb)
        return sb.toString()
    }

    // ------------------------------------------------------------ ID záznamů

    /** GEDCOM 7 povoluje v ID jen A–Z, 0–9 a _. Nevhodná ID (např. slugy) se přečíslují. */
    private inner class XrefMap(tree: GedcomTree) {
        private val map = HashMap<String, String>()

        init {
            val used = HashSet<String>()
            val all = tree.persons.map { it.id to "I" } + tree.families.map { it.id to "F" } +
                tree.sources.map { it.id to "S" } + tree.repositories.map { it.id to "R" } +
                tree.notes.map { it.id to "N" } + tree.media.map { it.id to "M" } +
                tree.extra.mapNotNull { n -> n.xref?.trim('@')?.let { it to "X" } }
            for ((id, _) in all) if (VALID_XREF.matches(id)) used += id
            val counters = HashMap<String, Int>()
            for ((id, prefix) in all) {
                if (id in map) continue
                map[id] = if (VALID_XREF.matches(id)) id else {
                    var candidate: String
                    do {
                        val n = (counters[prefix] ?: 0) + 1
                        counters[prefix] = n
                        candidate = "$prefix$n"
                    } while (candidate in used)
                    used += candidate
                    candidate
                }
            }
        }

        fun ref(id: String?): String? = id?.let { "@${map[it] ?: it}@" }
    }

    // ---------------------------------------------------------------- uzly

    private fun toNodes(t: GedcomTree, ids: XrefMap): List<GedcomNode> {
        val out = mutableListOf<GedcomNode>()
        val hasSubm = t.extra.any { it.tag == "SUBM" }
        val subm = t.extra.firstOrNull { it.tag == "SUBM" }?.xref?.trim('@') ?: "SUBM1"
        out += header(t.header, ids.ref(subm)!!)
        if (!hasSubm) out += GedcomNode("SUBM", xref = "@$subm@", children = listOf(GedcomNode("NAME", "Moje kořeny")))
        t.persons.forEach { out += person(it, ids) }
        t.families.forEach { out += family(it, ids) }
        t.sources.forEach { out += source(it, ids) }
        t.repositories.forEach { out += repository(it, ids) }
        t.notes.forEach {
            out += GedcomNode(if (version == GedcomVersion.V70) "SNOTE" else "NOTE", it.text, ids.ref(it.id), it.extra)
        }
        t.media.forEach { m ->
            out += GedcomNode(
                "OBJE", xref = ids.ref(m.id),
                children = m.files.map { fileNode(it) } + m.notes.map { note(it, ids) } + m.extra,
            )
        }
        t.extra.forEach { n -> out += n.copy(xref = n.xref?.let { ids.ref(it.trim('@')) }) }
        out += GedcomNode("TRLR")
        return out
    }

    private fun header(h: GedcomHeader, subm: String): GedcomNode {
        val skip = setOf("SUBM", "DATE", "FILE", "DEST")
        val children = buildList {
            add(
                GedcomNode(
                    "GEDC",
                    children = listOfNotNull(
                        GedcomNode("VERS", version.label),
                        GedcomNode("FORM", "LINEAGE-LINKED").takeIf { version == GedcomVersion.V551 },
                    ),
                ),
            )
            if (version == GedcomVersion.V551) add(GedcomNode("CHAR", "UTF-8"))
            add(
                GedcomNode(
                    "SOUR", "MOJE_KORENY",
                    children = listOf(GedcomNode("NAME", "Moje kořeny"), GedcomNode("VERS", appVersion)),
                ),
            )
            date?.let { add(GedcomNode("DATE", "${it.dayOfMonth} ${MONTHS[it.monthValue - 1]} ${it.year}")) }
            add(GedcomNode("SUBM", subm))
            h.language?.let { add(GedcomNode("LANG", it)) }
            addAll(h.extra.filterNot { it.tag in skip })
        }
        return GedcomNode("HEAD", children = children)
    }

    private fun person(p: Person, ids: XrefMap): GedcomNode {
        val c = mutableListOf<GedcomNode>()
        p.names.forEach { c += name(it) }
        if (p.sex != Sex.UNKNOWN || version == GedcomVersion.V551) c += GedcomNode("SEX", p.sex.code)
        p.events.forEach { c += event(it, ids) }
        p.familiesAsChild.forEach { c += GedcomNode("FAMC", ids.ref(it), children = p.childLinkExtra[it].orEmpty()) }
        p.familiesAsSpouse.forEach { c += GedcomNode("FAMS", ids.ref(it)) }
        p.notes.forEach { c += note(it, ids) }
        p.citations.forEach { c += citation(it, ids) }
        p.media.forEach { c += mediaLink(it, ids) }
        p.uid?.let { c += GedcomNode("_UID", it) }
        c += p.extra
        return GedcomNode("INDI", xref = ids.ref(p.id), children = c)
    }

    private fun name(n: PersonName): GedcomNode = GedcomNode(
        "NAME", n.toGedcom(),
        children = listOfNotNull(
            n.type?.let { GedcomNode("TYPE", it) },
            n.prefix?.let { GedcomNode("NPFX", it) },
            n.nickname?.let { GedcomNode("NICK", it) },
        ) + n.extra,
    )

    private fun event(e: Event, ids: XrefMap): GedcomNode = GedcomNode(
        e.tag, e.value,
        children = listOfNotNull(
            e.type?.let { GedcomNode("TYPE", it) },
            e.date?.let { GedcomNode("DATE", it) },
            e.place?.let { GedcomNode("PLAC", it) },
            e.address?.let { GedcomNode("ADDR", it) },
            e.age?.let { GedcomNode("AGE", it) },
            e.cause?.let { GedcomNode("CAUS", it) },
        ) + e.notes.map { note(it, ids) } + e.citations.map { citation(it, ids) } +
            e.media.map { mediaLink(it, ids) } + e.extra,
    )

    private fun note(n: Note, ids: XrefMap): GedcomNode = when {
        n.noteId != null -> GedcomNode(if (version == GedcomVersion.V70) "SNOTE" else "NOTE", ids.ref(n.noteId))
        else -> GedcomNode("NOTE", n.text.orEmpty())
    }

    private fun citation(c: Citation, ids: XrefMap): GedcomNode = GedcomNode(
        "SOUR", c.sourceId?.let { ids.ref(it) } ?: c.text,
        children = listOfNotNull(
            c.page?.let { GedcomNode("PAGE", it) },
            c.note?.let { GedcomNode("NOTE", it) },
        ) + c.extra,
    )

    private fun mediaLink(m: MediaLink, ids: XrefMap): GedcomNode = when {
        m.mediaId != null -> GedcomNode("OBJE", ids.ref(m.mediaId), children = m.extra)
        else -> GedcomNode(
            "OBJE",
            children = listOfNotNull(
                m.file?.let { fileNode(it, titleUnderFile = false) },
                m.file?.title?.let { GedcomNode("TITL", it) },
            ) + m.extra,
        )
    }

    private fun family(f: Family, ids: XrefMap): GedcomNode {
        val c = mutableListOf<GedcomNode>()
        f.husband?.let { c += GedcomNode("HUSB", ids.ref(it), children = f.linkExtra["HUSB"].orEmpty()) }
        f.wife?.let { c += GedcomNode("WIFE", ids.ref(it), children = f.linkExtra["WIFE"].orEmpty()) }
        f.children.forEach { c += GedcomNode("CHIL", ids.ref(it), children = f.linkExtra["CHIL:$it"].orEmpty()) }
        f.events.forEach { c += event(it, ids) }
        f.notes.forEach { c += note(it, ids) }
        f.citations.forEach { c += citation(it, ids) }
        f.media.forEach { c += mediaLink(it, ids) }
        f.uid?.let { c += GedcomNode("_UID", it) }
        c += f.extra
        return GedcomNode("FAM", xref = ids.ref(f.id), children = c)
    }

    private fun source(s: Source, ids: XrefMap): GedcomNode = GedcomNode(
        "SOUR", xref = ids.ref(s.id),
        children = listOfNotNull(
            s.title?.let { GedcomNode("TITL", it) },
            s.abbreviation?.let { GedcomNode("ABBR", it) },
            s.author?.let { GedcomNode("AUTH", it) },
            s.publication?.let { GedcomNode("PUBL", it) },
            s.text?.let { GedcomNode("TEXT", it) },
            s.repositoryId?.let { r ->
                GedcomNode("REPO", ids.ref(r), children = listOfNotNull(s.callNumber?.let { GedcomNode("CALN", it) }))
            },
        ) + s.notes.map { note(it, ids) } + s.media.map { mediaLink(it, ids) } + s.extra,
    )

    private fun repository(r: Repository, ids: XrefMap): GedcomNode = GedcomNode(
        "REPO", xref = ids.ref(r.id),
        children = listOfNotNull(
            r.name?.let { GedcomNode("NAME", it) },
            r.address?.let { GedcomNode("ADDR", it) },
            r.www?.let { GedcomNode("WWW", it) },
        ) + r.extra,
    )

    /** GEDCOM 7 nezná vložené OBJE – udělá z nich samostatné záznamy. */
    private fun inlineMediaToRecords(t: GedcomTree): GedcomTree {
        val newRecords = mutableListOf<MediaObject>()
        val taken = t.media.map { it.id }.toMutableSet()
        var n = 0
        fun convert(links: List<MediaLink>): List<MediaLink> = links.map { l ->
            if (l.mediaId != null || l.file == null) l else {
                var id: String
                do { id = "M${++n}" } while (id in taken)
                taken += id
                newRecords += MediaObject(id, listOf(l.file))
                MediaLink(mediaId = id, extra = l.extra)
            }
        }
        fun convertEvents(es: List<Event>) = es.map { it.copy(media = convert(it.media)) }
        val persons = t.persons.map { it.copy(media = convert(it.media), events = convertEvents(it.events)) }
        val families = t.families.map { it.copy(media = convert(it.media), events = convertEvents(it.events)) }
        val sources = t.sources.map { it.copy(media = convert(it.media)) }
        return t.copy(persons = persons, families = families, sources = sources, media = t.media + newRecords)
    }

    // -------------------------------------------------------------- text

    private fun serialize(n: GedcomNode, level: Int, sb: StringBuilder) {
        val lines = n.value?.replace("\r\n", "\n")?.replace('\r', '\n')?.split('\n')
        line(sb, level, n.xref, n.tag, lines?.first())
        lines?.drop(1)?.forEach { line(sb, level + 1, null, "CONT", it) }
        for (c in n.children) serialize(c, level + 1, sb)
    }

    private fun line(sb: StringBuilder, level: Int, xref: String?, tag: String, value: String?) {
        if (version == GedcomVersion.V551 && value != null && value.length > CHUNK) {
            val parts = splitForConc(value)
            emit(sb, level, xref, tag, parts.first())
            parts.drop(1).forEach { emit(sb, level + 1, null, "CONC", it) }
        } else {
            emit(sb, level, xref, tag, value)
        }
    }

    private fun emit(sb: StringBuilder, level: Int, xref: String?, tag: String, value: String?) {
        sb.append(level)
        if (xref != null) sb.append(' ').append(xref)
        sb.append(' ').append(tag)
        if (!value.isNullOrEmpty()) sb.append(' ').append(value)
        sb.append('\n')
    }

    /** Rozdělí dlouhý text tak, aby kousky nezačínaly ani nekončily mezerou (některé programy je ořezávají). */
    private fun splitForConc(v: String): List<String> {
        val parts = mutableListOf<String>()
        var i = 0
        while (v.length - i > CHUNK) {
            var cut = i + CHUNK
            while (cut > i + 1 && (v[cut - 1] == ' ' || v[cut] == ' ' || v[cut - 1].isHighSurrogate())) cut--
            if (cut <= i + 1) cut = i + CHUNK
            parts += v.substring(i, cut)
            i = cut
        }
        parts += v.substring(i)
        return parts
    }
}
