package cz.mojekoreny.gedcom

import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit

/** Dotazy nad rodokmenem pro obrazovky aplikace (příbuzní, příběh života, výročí). */

fun GedcomTree.siblingsOf(p: Person): List<Person> =
    p.familiesAsChild.mapNotNull(::family).flatMap { it.children }.distinct()
        .filter { it != p.id }.mapNotNull(::person)
        .sortedBy { it.birth?.parsedDate?.sortKey ?: Int.MAX_VALUE }

fun GedcomTree.fatherOf(p: Person): Person? =
    p.familiesAsChild.firstNotNullOfOrNull(::family)?.husband?.let(::person)

fun GedcomTree.motherOf(p: Person): Person? =
    p.familiesAsChild.firstNotNullOfOrNull(::family)?.wife?.let(::person)

/** Počet generací předků (osoba sama = 1). Chráněno proti cyklům v poškozených datech. */
fun GedcomTree.ancestorGenerations(p: Person, limit: Int = 60): Int {
    var gen = 1
    var current = listOf(p)
    val seen = mutableSetOf(p.id)
    while (gen < limit) {
        val next = current.flatMap { parentsOf(it) }.filter { seen.add(it.id) }
        if (next.isEmpty()) break
        current = next
        gen++
    }
    return gen
}

/** Nejstarší doložený rok narození/křtu v rodokmenu. */
fun GedcomTree.earliestYear(): Int? = persons.mapNotNull { it.birth?.parsedDate?.year }.minOrNull()

/** Odhad, zda osoba žije: nemá úmrtí a narodila se po roce (dnes − 100). */
fun Person.probablyLiving(today: LocalDate = LocalDate.now()): Boolean {
    if (death != null) return false
    val y = birth?.parsedDate?.year ?: return false
    return y > today.year - 100
}

/** České skloňování: 1 rok, 2–4 roky, 5 let. */
fun yearsCz(n: Int): String = when {
    n == 1 -> "1 rok"
    n in 2..4 -> "$n roky"
    else -> "$n let"
}

/** Věk v celých letech mezi dvěma daty (jen když jsou dost přesná). */
fun ageBetween(from: GedcomDate?, to: GedcomDate?): Int? {
    if (from == null || to == null) return null
    if (from.qualifier !in EXACTISH || to.qualifier !in EXACTISH) return null
    val a = from.start ?: return null
    val b = to.start ?: return null
    var years = b.year - a.year
    // Bez měsíců je věk jen přibližný (± 1 rok), pro přehled to stačí.
    if (a.month != null && b.month != null) {
        if (b.month < a.month || (b.month == a.month && a.day != null && b.day != null && b.day < a.day)) years--
    }
    return years.takeIf { it >= 0 }
}

private val EXACTISH = setOf(DateQualifier.EXACT, DateQualifier.ABOUT, DateQualifier.CALCULATED, DateQualifier.ESTIMATED)

/** Věk při úmrtí, pokud jde určit. */
fun Person.ageAtDeath(): Int? = ageBetween(birth?.parsedDate, death?.parsedDate)

enum class LifeKind { BIRTH, BAPTISM, MARRIAGE, DIVORCE, CHILD, OCCUPATION, RESIDENCE, RELIGION, EDUCATION, MIGRATION, DEATH, BURIAL, OTHER }

/** Jedna zastávka na časové ose života. */
data class LifeItem(
    val kind: LifeKind,
    val title: String,
    val date: GedcomDate? = null,
    val place: String? = null,
    val detail: String? = null,
    val relatedPersonId: String? = null,
)

private fun Person.verb(male: String, female: String, neutral: String) = when (sex) {
    Sex.MALE -> male
    Sex.FEMALE -> female
    else -> neutral
}

/**
 * Příběh života: vlastní události osoby, sňatky a narození dětí, seřazené podle data.
 * Události bez data zůstanou na svém logickém místě (narození na začátku, úmrtí na konci).
 */
fun GedcomTree.lifeStory(p: Person): List<LifeItem> {
    val items = mutableListOf<Pair<Int, LifeItem>>() // (pořadí bez data, položka)
    val born = p.birth?.parsedDate

    fun ageNote(d: GedcomDate?): String? = ageBetween(born, d)?.takeIf { it > 0 }?.let { "ve věku ${yearsCz(it)}" }

    for (e in p.events) {
        val d = e.parsedDate
        val item = when (e.tag) {
            "BIRT" -> LifeItem(LifeKind.BIRTH, p.verb("Narodil se", "Narodila se", "Narození"), d, e.place)
            "CHR", "BAPM" -> LifeItem(LifeKind.BAPTISM, p.verb("Pokřtěn", "Pokřtěna", "Křest"), d, e.place)
            "DEAT" -> LifeItem(LifeKind.DEATH, p.verb("Zemřel", "Zemřela", "Úmrtí"), d, e.place, listOfNotNull(ageNote(d), e.cause).joinToString(" · ").ifEmpty { null })
            "BURI" -> LifeItem(LifeKind.BURIAL, p.verb("Pohřben", "Pohřbena", "Pohřeb"), d, e.place)
            "OCCU" -> LifeItem(LifeKind.OCCUPATION, e.value?.replaceFirstChar { it.uppercase() } ?: "Povolání", d, e.place)
            "RESI" -> LifeItem(LifeKind.RESIDENCE, "Bydliště", d, e.place ?: e.address, e.value?.takeIf { it != "Y" })
            "RELI" -> LifeItem(LifeKind.RELIGION, "Vyznání: ${e.value.orEmpty()}".trim().removeSuffix(":"), d, e.place)
            "EDUC", "GRAD" -> LifeItem(LifeKind.EDUCATION, e.value ?: "Vzdělání", d, e.place)
            "EMIG", "IMMI" -> LifeItem(LifeKind.MIGRATION, if (e.tag == "EMIG") "Vystěhování" else "Přistěhování", d, e.place)
            else -> LifeItem(LifeKind.OTHER, e.type ?: e.value ?: e.tag, d, e.place, e.value.takeIf { e.type != null })
        }
        val order = when (item.kind) {
            LifeKind.BIRTH -> 0; LifeKind.BAPTISM -> 1; LifeKind.DEATH -> 8; LifeKind.BURIAL -> 9; else -> 5
        }
        items += order to item
    }

    for (f in p.familiesAsSpouse.mapNotNull(::family)) {
        val partner = (if (f.husband == p.id) f.wife else f.husband)?.let(::person)
        f.events.filter { it.tag == "MARR" || it.tag == "DIV" }.forEach { e ->
            val d = e.parsedDate
            val title = if (e.tag == "MARR") {
                p.verb("Oženil se", "Vdala se", "Sňatek") + (partner?.let { " – ${it.name.full}" } ?: "")
            } else {
                "Rozvod" + (partner?.let { " – ${it.name.full}" } ?: "")
            }
            items += 3 to LifeItem(
                if (e.tag == "MARR") LifeKind.MARRIAGE else LifeKind.DIVORCE, title, d, e.place, ageNote(d), partner?.id,
            )
        }
        for (c in f.children.mapNotNull(::person)) {
            val d = c.birth?.parsedDate ?: continue
            val who = when (c.sex) {
                Sex.MALE -> "Narodil se syn"
                Sex.FEMALE -> "Narodila se dcera"
                else -> "Narodilo se dítě"
            }
            items += 4 to LifeItem(LifeKind.CHILD, "$who ${c.name.given}".trim(), d, c.birth?.place, null, c.id)
        }
    }

    // Seřadit: podle data, události bez data podle logického pořadí mezi ostatní.
    val dated = items.filter { it.second.date?.sortKey != null }.sortedBy { it.second.date!!.sortKey }
    val undated = items.filter { it.second.date?.sortKey == null }
    val result = mutableListOf<LifeItem>()
    result += undated.filter { it.first <= 1 }.map { it.second }
    result += dated.map { it.second }
    result += undated.filter { it.first in 2..7 }.map { it.second }
    result += undated.filter { it.first >= 8 }.map { it.second }
    return result
}

enum class AnniversaryKind { BIRTH, DEATH, MARRIAGE }

data class Anniversary(
    val personId: String,
    val kind: AnniversaryKind,
    val year: Int,
    /** Kolik let uplyne v den výročí. */
    val years: Int,
    /** Za kolik dní (0 = dnes). */
    val inDays: Int,
    val partnerId: String? = null,
)

/** Výročí narození, úmrtí a sňatků v příštích [days] dnech (jen data s dnem a měsícem). */
fun GedcomTree.anniversaries(today: LocalDate, days: Int = 30): List<Anniversary> {
    val out = mutableListOf<Anniversary>()
    fun add(id: String, kind: AnniversaryKind, d: GedcomDate?, partner: String? = null) {
        if (d?.qualifier != DateQualifier.EXACT) return
        val p = d.start ?: return
        val m = p.month ?: return
        val day = p.day ?: return
        val md = runCatching { MonthDay.of(m, day) }.getOrNull() ?: return
        var next = runCatching { md.atYear(today.year) }.getOrNull() ?: return
        if (next.isBefore(today)) next = runCatching { md.atYear(today.year + 1) }.getOrNull() ?: return
        val inDays = ChronoUnit.DAYS.between(today, next).toInt()
        if (inDays > days) return
        val years = next.year - p.year
        if (years <= 0) return
        out += Anniversary(id, kind, p.year, years, inDays, partner)
    }
    for (p in persons) {
        add(p.id, AnniversaryKind.BIRTH, p.birth?.parsedDate)
        add(p.id, AnniversaryKind.DEATH, p.event("DEAT")?.parsedDate)
    }
    for (f in families) {
        val h = f.husband ?: f.wife ?: continue
        add(h, AnniversaryKind.MARRIAGE, f.marriage?.parsedDate, if (f.husband != null) f.wife else null)
    }
    // Kulatá výročí (dělitelná 25) mají přednost, pak nejbližší.
    return out.sortedWith(compareBy({ it.inDays }, { if (it.years % 25 == 0) 0 else 1 }, { it.year }))
}
