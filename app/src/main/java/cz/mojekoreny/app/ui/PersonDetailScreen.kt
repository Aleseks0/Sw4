package cz.mojekoreny.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cz.mojekoreny.gedcom.GedcomTree
import cz.mojekoreny.gedcom.Note
import cz.mojekoreny.gedcom.Person
import cz.mojekoreny.gedcom.Sex

@Composable
fun PersonDetailScreen(tree: GedcomTree, person: Person, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(person.name.full.ifEmpty { "(beze jména)" }, style = MaterialTheme.typography.headlineSmall)
            val sub = listOfNotNull(
                person.birthSurname?.let { "roz. $it" },
                person.lifeSpan.ifEmpty { null },
                when (person.sex) { Sex.MALE -> "muž"; Sex.FEMALE -> "žena"; else -> null },
            ).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (person.events.isNotEmpty()) {
            Section("Životní události")
            person.events.sortedBy { it.parsedDate?.sortKey ?: Int.MAX_VALUE }.forEach { e ->
                ListItem(
                    overlineContent = { Text(e.label()) },
                    headlineContent = { Text(e.summary().ifEmpty { "—" }) },
                    supportingContent = e.citations.mapNotNull { c ->
                        c.sourceId?.let { tree.source(it)?.title } ?: c.text
                    }.takeIf { it.isNotEmpty() }?.let { srcs -> { Text("Pramen: " + srcs.joinToString("; ")) } },
                )
            }
        }

        val parents = tree.parentsOf(person)
        val spouses = tree.spousesOf(person)
        val children = tree.childrenOf(person)
        if (parents.isNotEmpty()) {
            Section("Rodiče")
            parents.forEach { PersonLink(it, onOpen) }
        }
        person.familiesAsSpouse.mapNotNull(tree::family).forEach { f ->
            val partner = (if (f.husband == person.id) f.wife else f.husband)?.let(tree::person)
            Section(if (partner != null) "Rodina s: ${partner.name.full}" else "Rodina")
            f.marriage?.let { m ->
                Text("Sňatek: ${m.summary().ifEmpty { "ano" }}", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            partner?.let { PersonLink(it, onOpen, prefix = if (it.sex == Sex.FEMALE) "Manželka" else "Manžel") }
            f.children.mapNotNull(tree::person).forEach { PersonLink(it, onOpen, prefix = "Dítě") }
        }
        if (spouses.isEmpty() && children.isNotEmpty()) {
            Section("Děti")
            children.forEach { PersonLink(it, onOpen) }
        }

        val notes = person.notes.mapNotNull { it.resolve(tree) }
        if (notes.isNotEmpty()) {
            Section("Poznámky")
            notes.forEach { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
        }

        val sources = (person.citations + person.events.flatMap { it.citations }).mapNotNull { it.sourceId }.distinct()
            .mapNotNull(tree::source)
        if (sources.isNotEmpty()) {
            Section("Prameny")
            sources.forEach { s ->
                ListItem(
                    headlineContent = { Text(s.title ?: s.abbreviation ?: s.id) },
                    supportingContent = listOfNotNull(s.author, s.publication).joinToString(" · ").takeIf { it.isNotEmpty() }
                        ?.let { t -> { Text(t) } },
                )
            }
        }
    }
}

private fun Note.resolve(tree: GedcomTree): String? =
    text ?: noteId?.let { id -> tree.notes.firstOrNull { it.id == id }?.text }

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun PersonLink(p: Person, onOpen: (String) -> Unit, prefix: String? = null) {
    ListItem(
        overlineContent = prefix?.let { { Text(it) } },
        headlineContent = { Text(p.name.full.ifEmpty { "(beze jména)" }) },
        supportingContent = p.lifeSpan.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
        modifier = Modifier.clickable { onOpen(p.id) },
    )
}
