package cz.mojekoreny.app.ui

import java.text.Normalizer

/** Text pro hledání bez ohledu na diakritiku a velikost písmen („rehor“ najde „Řehoř“). */
fun searchKey(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

