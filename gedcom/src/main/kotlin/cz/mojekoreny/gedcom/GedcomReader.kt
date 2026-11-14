package cz.mojekoreny.gedcom

import java.io.InputStream

/** Načte GEDCOM 5.5.1 nebo 7.0 do [GedcomTree]. */
object GedcomReader {

    fun read(input: InputStream): GedcomTree = read(input.readBytes())

    fun read(bytes: ByteArray): GedcomTree {
        val decoded = GedcomCharset.decode(bytes)
        val tree = readText(decoded.text)
        return tree.copy(header = tree.header.copy(encoding = decoded.encoding))
    }

    fun readText(text: String): GedcomTree = fromNodes(GedcomParser.parse(text))

    fun fromNodes(records: List<GedcomNode>): GedcomTree {
        var header = GedcomHeader()
        val persons = mutableListOf<Person>()
        val families = mutableListOf<Family>()
        val sources = mutableListOf<Source>()
        val repos = mutableListOf<Repository>()
        val notes = mutableListOf<NoteRecord>()
        val media = mutableListOf<MediaObject>()
        val extra = mutableListOf<GedcomNode>()

        for (r in records) {
            val id = r.xref?.trim('@')
            when {
                r.tag == "HEAD" -> header = header(r)
                r.tag == "TRLR" -> Unit
                r.tag == "INDI" && id != null -> persons += person(id, r)
                r.tag == "FAM" && id != null -> families += family(id, r)
                r.tag == "SOUR" && id != null -> sources += source(id, r)
                r.tag == "REPO" && id != null -> repos += repository(id, r)
                (r.tag == "NOTE" || r.tag == "SNOTE") && id != null ->
                    notes += NoteRecord(id, r.value.orEmpty(), r.children)
                r.tag == "OBJE" && id != null -> media += mediaObject(id, r)
                else -> extra += r
            }
        }
        return GedcomTree(header, persons, families, sources, repos, notes, media, extra)
    }

    private fun header(n: GedcomNode): GedcomHeader {
        val rest = n.children.filterNot { it.tag in setOf("SOUR", "GEDC", "CHAR", "LANG") }
        return GedcomHeader(
            sourceSystem = n.childValue("SOUR"),
            version = n.child("GEDC")?.childValue("VERS"),
            language = n.childValue("LANG"),
            extra = rest,
        )
    }

    // ---------------------------------------------------------------- osoby

    private val PERSON_EVENTS = setOf(
        "BIRT", "CHR", "BAPM", "DEAT", "BURI", "CREM", "ADOP", "CONF", "FCOM", "GRAD", "EMIG", "IMMI",
        "NATU", "CENS", "PROB", "WILL", "RETI", "ORDN", "EVEN",
        "OCCU", "RESI", "RELI", "EDUC", "NATI", "TITL", "DSCR", "PROP", "FACT", "CAST", "NCHI", "NMR", "SSN", "IDNO",
    )
    private val FAMILY_EVENTS = setOf("MARR", "MARB", "MARC", "MARL", "MARS", "ENGA", "DIV", "DIVF", "ANUL", "CENS", "RESI", "EVEN", "FACT", "NCHI")

    private fun person(id: String, n: GedcomNode): Person {
        val names = mutableListOf<PersonName>()
        val events = mutableListOf<Event>()
        val famc = mutableListOf<String>()
        val fams = mutableListOf<String>()
        val notes = mutableListOf<Note>()
        val cites = mutableListOf<Citation>()
        val media = mutableListOf<MediaLink>()
        val extra = mutableListOf<GedcomNode>()
        var sex = Sex.UNKNOWN
        var uid: String? = null
        val famcExtra = mutableMapOf<String, List<GedcomNode>>()

        for (c in n.children) {
            when (c.tag) {
                "NAME" -> names += name(c)
                "SEX" -> sex = Sex.of(c.value)
                "FAMC" -> c.pointer?.let {
                    famc += it.trim('@')
                    if (c.children.isNotEmpty()) famcExtra[it.trim('@')] = c.children
                } ?: run { extra += c }
                "FAMS" -> c.pointer?.let { fams += it.trim('@') } ?: run { extra += c }
                "NOTE", "SNOTE" -> notes += note(c)
                "SOUR" -> cites += citation(c)
                "OBJE" -> media += mediaLink(c)
                "_UID", "UID" -> if (uid == null) uid = c.value else extra += c
                "REFN" -> if (uid == null) uid = c.value else extra += c
                in PERSON_EVENTS -> events += event(c)
                else -> extra += c
            }
        }
        return Person(id, names, sex, events, famc, fams, notes, cites, media, uid, extra, famcExtra)
    }

    private fun name(c: GedcomNode): PersonName {
        val base = PersonName.parse(c.value)
        var given = base.given
        var surname = base.surname
        var type: String? = null
        var prefix: String? = null
        var suffix = base.suffix
        var nick: String? = null
        val extra = mutableListOf<GedcomNode>()
        for (s in c.children) {
            when (s.tag) {
                "GIVN" -> if (given.isEmpty()) given = s.value.orEmpty() else if (s.value != given) extra += s
                "SURN" -> if (surname.isEmpty()) surname = s.value.orEmpty() else if (s.value != surname) extra += s
                "TYPE" -> type = s.value
                "NPFX" -> prefix = s.value
                "NSFX" -> suffix = s.value
                "NICK" -> nick = s.value
                else -> extra += s
            }
        }
        // Předpona je obvykle už ve hodnotě NAME („Ing. Jan /Novák/“) – nepřidávat ji dvakrát.
        if (prefix != null && given.startsWith("$prefix ")) given = given.removePrefix("$prefix ")
        return PersonName(given, surname, type, prefix, suffix, nick, extra)
    }

    private fun event(c: GedcomNode): Event {
        val notes = mutableListOf<Note>()
        val cites = mutableListOf<Citation>()
        val media = mutableListOf<MediaLink>()
        val extra = mutableListOf<GedcomNode>()
        var type: String? = null
        var date: String? = null
        var place: String? = null
        var addr: String? = null
        var age: String? = null
        var cause: String? = null
        for (s in c.children) {
            when (s.tag) {
                "TYPE" -> type = s.value
                "DATE" -> if (date == null && s.children.isEmpty()) date = s.value else extra += s
                "PLAC" -> if (place == null && s.children.isEmpty()) place = s.value else extra += s
                "ADDR" -> if (addr == null && s.children.isEmpty()) addr = s.value else extra += s
                "AGE" -> age = s.value
                "CAUS" -> cause = s.value
                "NOTE", "SNOTE" -> notes += note(s)
                "SOUR" -> cites += citation(s)
                "OBJE" -> media += mediaLink(s)
                else -> extra += s
            }
        }
        return Event(c.tag, c.value, type, date, place, addr, age, cause, notes, cites, media, extra)
    }

    private fun note(c: GedcomNode): Note =
        c.pointer?.let { Note(noteId = it.trim('@')) } ?: Note(text = c.value.orEmpty())

    private fun citation(c: GedcomNode): Citation {
        val ptr = c.pointer
        var page: String? = null
        var note: String? = null
        val extra = mutableListOf<GedcomNode>()
        for (s in c.children) {
            when (s.tag) {
                "PAGE" -> page = s.value
                "NOTE" -> if (note == null && s.pointer == null) note = s.value else extra += s
                else -> extra += s
            }
        }
        return Citation(ptr?.trim('@'), if (ptr == null) c.value else null, page, note, extra)
    }

    private fun mediaLink(c: GedcomNode): MediaLink {
        c.pointer?.let { return MediaLink(mediaId = it.trim('@'), extra = c.children) }
        val files = mediaFiles(c)
        val extra = c.children.filterNot { it.tag == "FILE" || it.tag == "FORM" || it.tag == "TITL" }
        return MediaLink(file = files.firstOrNull(), extra = extra + files.drop(1).map { GedcomWriter.fileNode(it) })
    }

    /** Soubory v OBJE – GEDCOM 5.5.1 (FORM a TITL na úrovni OBJE i pod FILE) i 7.0 (FORM pod FILE). */
    private fun mediaFiles(c: GedcomNode): List<MediaFile> {
        val objForm = c.childValue("FORM")
        val objTitle = c.childValue("TITL")
        return c.childrenOf("FILE").map { f ->
            MediaFile(
                path = f.value.orEmpty(),
                format = f.childValue("FORM") ?: objForm,
                title = f.childValue("TITL") ?: f.child("FORM")?.childValue("TITL") ?: objTitle,
            )
        }
    }

    // --------------------------------------------------------------- rodiny

    private fun family(id: String, n: GedcomNode): Family {
        var husb: String? = null
        var wife: String? = null
        var uid: String? = null
        val children = mutableListOf<String>()
        val events = mutableListOf<Event>()
        val notes = mutableListOf<Note>()
        val cites = mutableListOf<Citation>()
        val media = mutableListOf<MediaLink>()
        val extra = mutableListOf<GedcomNode>()
        val linkExtra = mutableMapOf<String, List<GedcomNode>>()
        for (c in n.children) {
            when (c.tag) {
                "HUSB" -> if (husb == null && c.pointer != null) {
                    husb = c.pointer!!.trim('@')
                    if (c.children.isNotEmpty()) linkExtra["HUSB"] = c.children
                } else extra += c
                "WIFE" -> if (wife == null && c.pointer != null) {
                    wife = c.pointer!!.trim('@')
                    if (c.children.isNotEmpty()) linkExtra["WIFE"] = c.children
                } else extra += c
                "CHIL" -> if (c.pointer != null) {
                    val ch = c.pointer!!.trim('@')
                    children += ch
                    if (c.children.isNotEmpty()) linkExtra["CHIL:$ch"] = c.children
                } else extra += c
                "NOTE", "SNOTE" -> notes += note(c)
                "SOUR" -> cites += citation(c)
                "OBJE" -> media += mediaLink(c)
                "_UID", "UID", "REFN" -> if (uid == null) uid = c.value else extra += c
                in FAMILY_EVENTS -> events += event(c)
                else -> extra += c
            }
        }
        return Family(id, husb, wife, children, events, notes, cites, media, uid, extra, linkExtra)
    }

    // --------------------------------------------------------------- prameny

    private fun source(id: String, n: GedcomNode): Source {
        val notes = mutableListOf<Note>()
        val media = mutableListOf<MediaLink>()
        val extra = mutableListOf<GedcomNode>()
        var title: String? = null
        var abbr: String? = null
        var auth: String? = null
        var publ: String? = null
        var text: String? = null
        var repo: String? = null
        var caln: String? = null
        for (c in n.children) {
            when (c.tag) {
                "TITL" -> title = c.value
                "ABBR" -> abbr = c.value
                "AUTH" -> auth = c.value
                "PUBL" -> publ = c.value
                "TEXT" -> text = c.value
                "REPO" -> if (repo == null && c.pointer != null && c.children.all { it.tag == "CALN" && it.children.isEmpty() }) {
                    repo = c.pointer!!.trim('@'); caln = c.childValue("CALN")
                } else extra += c
                "NOTE", "SNOTE" -> notes += note(c)
                "OBJE" -> media += mediaLink(c)
                else -> extra += c
            }
        }
        return Source(id, title, abbr, auth, publ, text, repo, caln, notes, media, extra)
    }

    private fun repository(id: String, n: GedcomNode): Repository {
        var name: String? = null
        var addr: String? = null
        var www: String? = null
        val extra = mutableListOf<GedcomNode>()
        for (c in n.children) {
            when (c.tag) {
                "NAME" -> name = c.value
                "ADDR" -> if (addr == null && c.children.isEmpty()) addr = c.value else extra += c
                "WWW" -> www = c.value
                else -> extra += c
            }
        }
        return Repository(id, name, addr, www, extra)
    }

    private fun mediaObject(id: String, n: GedcomNode): MediaObject {
        val notes = n.children.filter { it.tag == "NOTE" || it.tag == "SNOTE" }.map(::note)
        val extra = n.children.filterNot { it.tag in setOf("FILE", "FORM", "TITL", "NOTE", "SNOTE") }
        return MediaObject(id, mediaFiles(n), notes, extra)
    }
}
