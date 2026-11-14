package cz.mojekoreny.app.ui

import cz.mojekoreny.gedcom.Event
import java.text.Normalizer

/** Text pro hledání bez ohledu na diakritiku a velikost písmen („rehor“ najde „Řehoř“). */
fun searchKey(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

val EVENT_LABELS = mapOf(
    "BIRT" to "Narození", "CHR" to "Křest", "BAPM" to "Křest", "DEAT" to "Úmrtí", "BURI" to "Pohřeb",
    "CREM" to "Kremace", "MARR" to "Sňatek", "DIV" to "Rozvod", "ENGA" to "Zasnoubení", "OCCU" to "Povolání",
    "RESI" to "Bydliště", "RELI" to "Vyznání", "EDUC" to "Vzdělání", "EMIG" to "Vystěhování",
    "IMMI" to "Přistěhování", "CENS" to "Sčítání lidu", "CONF" to "Biřmování", "TITL" to "Titul",
    "NATI" to "Národnost", "GRAD" to "Absolvování", "RETI" to "Odchod do důchodu", "WILL" to "Závěť",
    "PROB" to "Dědické řízení", "ADOP" to "Adopce", "FCOM" to "První svaté přijímání",
)

fun Event.label(): String = type?.takeIf { tag == "EVEN" || tag == "FACT" } ?: EVENT_LABELS[tag] ?: tag

fun Event.summary(): String = listOfNotNull(
    value?.takeIf { it.isNotBlank() && it != "Y" },
    parsedDate?.toCzech(),
    place,
).joinToString(" · ")
