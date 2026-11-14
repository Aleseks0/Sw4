package cz.mojekoreny.gedcom

/**
 * Rodokmen načtený z GEDCOM.
 *
 * Co aplikace nezná, ukládá se do polí `extra` (původní řádky) a při exportu
 * se zapíše zpět – import a export tak neztrácí data jiných programů.
 */
data class GedcomTree(
    val header: GedcomHeader = GedcomHeader(),
    val persons: List<Person> = emptyList(),
    val families: List<Family> = emptyList(),
    val sources: List<Source> = emptyList(),
    val repositories: List<Repository> = emptyList(),
    val notes: List<NoteRecord> = emptyList(),
    val media: List<MediaObject> = emptyList(),
    val extra: List<GedcomNode> = emptyList(),
) {
    private val personIndex by lazy { persons.associateBy { it.id } }
    private val familyIndex by lazy { families.associateBy { it.id } }
    private val sourceIndex by lazy { sources.associateBy { it.id } }

    fun person(id: String): Person? = personIndex[id]
    fun family(id: String): Family? = familyIndex[id]
    fun source(id: String): Source? = sourceIndex[id]

    fun parentsOf(p: Person): List<Person> =
        p.familiesAsChild.mapNotNull(::family).flatMap { listOfNotNull(it.husband, it.wife) }.mapNotNull(::person)

    fun spousesOf(p: Person): List<Person> =
        p.familiesAsSpouse.mapNotNull(::family)
            .mapNotNull { f -> if (f.husband == p.id) f.wife else f.husband }
            .mapNotNull(::person)

    fun childrenOf(p: Person): List<Person> =
        p.familiesAsSpouse.mapNotNull(::family).flatMap { it.children }.distinct().mapNotNull(::person)
}

data class GedcomHeader(
    val sourceSystem: String? = null,
    val version: String? = null,
    /** Kódování, ve kterém byl soubor načten (UTF-8, ANSEL, windows-1250 …). */
    val encoding: String? = null,
    val language: String? = null,
    val extra: List<GedcomNode> = emptyList(),
)

enum class Sex(val code: String) {
    MALE("M"), FEMALE("F"), UNKNOWN("U"), OTHER("X");

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code?.trim()?.uppercase() } ?: UNKNOWN
    }
}

data class PersonName(
    val given: String = "",
    val surname: String = "",
    /** birth, married, aka … (NAME.TYPE) */
    val type: String? = null,
    val prefix: String? = null,
    val suffix: String? = null,
    val nickname: String? = null,
    val extra: List<GedcomNode> = emptyList(),
) {
    val full: String get() = listOf(prefix, given, surname, suffix).filterNot { it.isNullOrBlank() }.joinToString(" ")

    /** Zápis do GEDCOM: `Jan /Novák/`. */
    fun toGedcom(): String = buildString {
        if (!prefix.isNullOrBlank()) append(prefix).append(' ')
        append(given)
        if (given.isNotEmpty()) append(' ')
        append('/').append(surname).append('/')
        if (!suffix.isNullOrBlank()) append(' ').append(suffix)
    }.trim()

    companion object {
        private val NAME = Regex("""^(.*?)/(.*?)/(.*)$""")
        fun parse(value: String?): PersonName {
            val v = value?.trim().orEmpty()
            val m = NAME.matchEntire(v) ?: return PersonName(given = v)
            return PersonName(
                given = m.groupValues[1].trim(),
                surname = m.groupValues[2].trim(),
                suffix = m.groupValues[3].trim().ifEmpty { null },
            )
        }
    }
}

/** Odkaz na pramen u údaje (SOUR @S1@ + PAGE). */
data class Citation(
    val sourceId: String? = null,
    /** Pramen zapsaný přímo textem bez záznamu SOUR. */
    val text: String? = null,
    val page: String? = null,
    val note: String? = null,
    val extra: List<GedcomNode> = emptyList(),
)

/** Poznámka – buď přímo text, nebo odkaz na sdílenou poznámku (@N1@). */
data class Note(val text: String? = null, val noteId: String? = null)

/** Odkaz na fotku / dokument – buď záznam OBJE (@M1@), nebo vložený soubor. */
data class MediaLink(
    val mediaId: String? = null,
    val file: MediaFile? = null,
    val extra: List<GedcomNode> = emptyList(),
)

data class MediaFile(
    val path: String,
    val format: String? = null,
    val title: String? = null,
)

data class Event(
    /** Značka GEDCOM: BIRT, CHR, DEAT, BURI, MARR, DIV, OCCU, RESI, RELI, EVEN … */
    val tag: String,
    /** Hodnota řádku (u OCCU povolání, u RELI vyznání; u BIRT obvykle „Y“ nebo nic). */
    val value: String? = null,
    /** Upřesnění (TYPE) – u EVEN/FACT název události. */
    val type: String? = null,
    /** Datum tak, jak je v souboru; rozbor viz [parsedDate]. */
    val date: String? = null,
    val place: String? = null,
    val address: String? = null,
    val age: String? = null,
    val cause: String? = null,
    val notes: List<Note> = emptyList(),
    val citations: List<Citation> = emptyList(),
    val media: List<MediaLink> = emptyList(),
    val extra: List<GedcomNode> = emptyList(),
) {
    val parsedDate: GedcomDate? get() = GedcomDate.parse(date)
}

data class Person(
    val id: String,
    val names: List<PersonName> = emptyList(),
    val sex: Sex = Sex.UNKNOWN,
    val events: List<Event> = emptyList(),
    val familiesAsChild: List<String> = emptyList(),
    val familiesAsSpouse: List<String> = emptyList(),
    val notes: List<Note> = emptyList(),
    val citations: List<Citation> = emptyList(),
    val media: List<MediaLink> = emptyList(),
    /** Trvalý identifikátor (_UID nebo REFN) – u dat z webu slug, např. `jan-novak-1849`. */
    val uid: String? = null,
    val extra: List<GedcomNode> = emptyList(),
    /** Podřízené řádky FAMC (PEDI = adopce …) podle ID rodiny. */
    val childLinkExtra: Map<String, List<GedcomNode>> = emptyMap(),
) {
    val name: PersonName get() = names.firstOrNull() ?: PersonName()

    /** Rodné příjmení, pokud je uvedeno jako druhé jméno typu „birth“/„maiden“. */
    val birthSurname: String?
        get() = names.firstOrNull { it.type?.lowercase() in setOf("birth", "maiden") && it !== names.first() }?.surname

    fun event(tag: String): Event? = events.firstOrNull { it.tag == tag }
    val birth: Event? get() = event("BIRT") ?: event("CHR") ?: event("BAPM")
    val death: Event? get() = event("DEAT") ?: event("BURI")

    /** „1849–1921“, „* 1849“, „† 1921“ nebo prázdné. */
    val lifeSpan: String
        get() {
            val b = birth?.parsedDate?.year
            val d = death?.parsedDate?.year
            return when {
                b != null && d != null -> "$b–$d"
                b != null -> "* $b"
                d != null -> "† $d"
                else -> ""
            }
        }
}

data class Family(
    val id: String,
    val husband: String? = null,
    val wife: String? = null,
    val children: List<String> = emptyList(),
    val events: List<Event> = emptyList(),
    val notes: List<Note> = emptyList(),
    val citations: List<Citation> = emptyList(),
    val media: List<MediaLink> = emptyList(),
    val uid: String? = null,
    val extra: List<GedcomNode> = emptyList(),
    /** Podřízené řádky HUSB, WIFE a CHIL (klíč „HUSB“, „WIFE“, „CHIL:<id>“) – např. _FREL/_MREL. */
    val linkExtra: Map<String, List<GedcomNode>> = emptyMap(),
) {
    val marriage: Event? get() = events.firstOrNull { it.tag == "MARR" }
}

data class Source(
    val id: String,
    val title: String? = null,
    val abbreviation: String? = null,
    val author: String? = null,
    val publication: String? = null,
    val text: String? = null,
    val repositoryId: String? = null,
    val callNumber: String? = null,
    val notes: List<Note> = emptyList(),
    val media: List<MediaLink> = emptyList(),
    val extra: List<GedcomNode> = emptyList(),
)

data class Repository(
    val id: String,
    val name: String? = null,
    val address: String? = null,
    val www: String? = null,
    val extra: List<GedcomNode> = emptyList(),
)

data class NoteRecord(val id: String, val text: String, val extra: List<GedcomNode> = emptyList())

data class MediaObject(
    val id: String,
    val files: List<MediaFile> = emptyList(),
    val notes: List<Note> = emptyList(),
    val extra: List<GedcomNode> = emptyList(),
)
