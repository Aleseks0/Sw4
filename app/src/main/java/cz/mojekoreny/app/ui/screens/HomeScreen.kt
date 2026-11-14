package cz.mojekoreny.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.mojekoreny.app.ui.components.Pill
import cz.mojekoreny.app.ui.components.PersonCard
import cz.mojekoreny.app.ui.components.SectionHeading
import cz.mojekoreny.app.ui.components.StatBlock
import cz.mojekoreny.app.ui.components.TwoColumns
import cz.mojekoreny.gedcom.Anniversary
import cz.mojekoreny.gedcom.AnniversaryKind
import cz.mojekoreny.gedcom.GedcomTree
import cz.mojekoreny.gedcom.Person
import cz.mojekoreny.gedcom.Sex
import cz.mojekoreny.gedcom.ancestorGenerations
import cz.mojekoreny.gedcom.anniversaries
import cz.mojekoreny.gedcom.earliestYear
import cz.mojekoreny.gedcom.yearsCz
import java.time.LocalDate

@Composable
fun HomeScreen(
    tree: GedcomTree,
    title: String,
    home: Person?,
    recent: List<Person>,
    today: LocalDate,
    onSearch: () -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenTree: (String) -> Unit,
) {
    val generations = remember(tree, home) { home?.let { tree.ancestorGenerations(it) } ?: 0 }
    val earliest = remember(tree) { tree.earliestYear() }
    val anniversaries = remember(tree, today) { tree.anniversaries(today, days = 30).take(5) }
    val oldest = remember(tree) {
        tree.persons.filter { it.birth?.parsedDate?.year != null }.sortedBy { it.birth!!.parsedDate!!.sortKey }.take(4)
    }
    val cs = MaterialTheme.colorScheme

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)) {
        item { Text(title, style = MaterialTheme.typography.headlineLarge) }

        item {
            Spacer(Modifier.height(14.dp))
            Surface(
                onClick = onSearch,
                shape = MaterialTheme.shapes.medium,
                color = cs.surfaceContainerLowest,
                border = BorderStroke(1.dp, cs.outlineVariant),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Hledat předka, místo, rok…", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                }
            }
        }

        if (home != null) {
            item {
                Spacer(Modifier.height(14.dp))
                cz.mojekoreny.app.ui.components.WebCard(Modifier, onClick = { onOpenPerson(home.id) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text("VÝCHOZÍ OSOBA", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(home.name.full, style = MaterialTheme.typography.headlineSmall)
                        if (home.lifeSpan.isNotEmpty()) {
                            Text(home.lifeSpan, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onOpenTree(home.id) }, shape = MaterialTheme.shapes.medium) { Text("Strom a vějíř") }
                            OutlinedButton(
                                onClick = { onOpenPerson(home.id) },
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, cs.outlineVariant),
                            ) { Text("Příběh") }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(18.dp))
            val stats = listOf(
                "${tree.persons.size}" to "osob",
                "${tree.families.size}" to "rodin",
                (if (generations > 0) "$generations" else "–") to "generací",
                (earliest?.toString() ?: "–") to "nejstarší záznam",
                "${tree.sources.size}" to "pramenů",
            ).filterNot { it.second == "pramenů" && tree.sources.isEmpty() }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                stats.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { (v, l) -> StatBlock(v, l, Modifier.weight(1f)) }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        if (anniversaries.isNotEmpty()) {
            item { SectionHeading("Výročí v rodu") }
            item {
                cz.mojekoreny.app.ui.components.WebCard {
                    anniversaries.forEachIndexed { i, a ->
                        tree.person(a.personId)?.let {
                            AnniversaryRow(a, it, tree, onOpenPerson)
                            if (i < anniversaries.lastIndex) {
                                androidx.compose.material3.HorizontalDivider(color = cs.outlineVariant)
                            }
                        }
                    }
                }
            }
        }

        if (recent.isNotEmpty()) {
            item { SectionHeading("Naposledy prohlížené") }
            item { TwoColumns(recent.take(6)) { p, m -> PersonCard(p, m) { onOpenPerson(p.id) } } }
        }

        if (oldest.isNotEmpty()) {
            item { SectionHeading("Nejstarší předci") }
            item { TwoColumns(oldest) { p, m -> PersonCard(p, m) { onOpenPerson(p.id) } } }
        }
    }
}

@Composable
private fun AnniversaryRow(a: Anniversary, p: Person, tree: GedcomTree, onOpen: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val what = when (a.kind) {
        AnniversaryKind.BIRTH -> if (p.sex == Sex.FEMALE) "narodila se" else "narodil se"
        AnniversaryKind.DEATH -> if (p.sex == Sex.FEMALE) "zemřela" else "zemřel"
        AnniversaryKind.MARRIAGE -> "sňatek"
    }
    val name = if (a.kind == AnniversaryKind.MARRIAGE) {
        listOfNotNull(p.name.full, a.partnerId?.let(tree::person)?.name?.full).joinToString(" a ")
    } else p.name.full
    val whenText = when (a.inDays) {
        0 -> "dnes"
        1 -> "zítra"
        in 2..4 -> "za ${a.inDays} dny"
        else -> "za ${a.inDays} dní"
    }
    Surface(onClick = { onOpen(p.id) }, color = cs.surfaceContainerLowest) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text(
                    "$what v roce ${a.year} · ${yearsCz(a.years)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (a.inDays == 0) Pill(whenText, cs.secondaryContainer, cs.onSecondaryContainer)
            else Pill(whenText, cs.surfaceContainerHigh, cs.onSurfaceVariant)
        }
    }
}
