package cz.mojekoreny.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import cz.mojekoreny.app.ui.components.PersonCard
import cz.mojekoreny.app.ui.components.Pill
import cz.mojekoreny.app.ui.components.SectionHeading
import cz.mojekoreny.app.ui.components.SubHeading
import cz.mojekoreny.app.ui.components.TwoColumns
import cz.mojekoreny.app.ui.components.WebCard
import cz.mojekoreny.app.ui.theme.LocalAppColors
import cz.mojekoreny.gedcom.GedcomTree
import cz.mojekoreny.gedcom.LifeItem
import cz.mojekoreny.gedcom.LifeKind
import cz.mojekoreny.gedcom.Note
import cz.mojekoreny.gedcom.Person
import cz.mojekoreny.gedcom.Sex
import cz.mojekoreny.gedcom.ageAtDeath
import cz.mojekoreny.gedcom.lifeStory
import cz.mojekoreny.gedcom.siblingsOf
import cz.mojekoreny.gedcom.yearsCz

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonScreen(
    tree: GedcomTree,
    person: Person,
    isHome: Boolean,
    onOpenPerson: (String) -> Unit,
    onOpenTree: (String) -> Unit,
    onSetHome: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val story = remember(tree, person) { tree.lifeStory(person) }
    val parents = remember(tree, person) { tree.parentsOf(person) }
    val siblings = remember(tree, person) { tree.siblingsOf(person) }
    val families = remember(tree, person) { person.familiesAsSpouse.mapNotNull(tree::family) }
    val notes = remember(tree, person) { person.notes.mapNotNull { it.resolve(tree) } }
    val sources = remember(tree, person) {
        (person.citations + person.events.flatMap { it.citations }).mapNotNull { it.sourceId }.distinct().mapNotNull(tree::source)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 32.dp)) {
        item {
            Text("Osoba", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            Text(person.name.full.ifEmpty { "(beze jména)" }, style = MaterialTheme.typography.headlineLarge)
            person.birthSurname?.let {
                Text(
                    if (person.sex == Sex.FEMALE) "rozená $it" else "rodné příjmení $it",
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (person.lifeSpan.isNotEmpty()) Pill(person.lifeSpan)
                person.ageAtDeath()?.let {
                    Pill("dožil${if (person.sex == Sex.FEMALE) "a" else ""} se ${yearsCz(it)}")
                }
                if (isHome) Pill("výchozí osoba rodokmenu", cs.tertiaryContainer, cs.onTertiaryContainer)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpenTree(person.id) }, shape = MaterialTheme.shapes.medium) { Text("Strom a vějíř") }
                if (!isHome) {
                    OutlinedButton(
                        onClick = { onSetHome(person.id) },
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, cs.outlineVariant),
                    ) { Text("Nastavit jako výchozí") }
                }
            }
        }

        if (story.isNotEmpty()) {
            item { SectionHeading("Životní příběh") }
            item {
                WebCard {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        story.forEachIndexed { i, item ->
                            TimelineRow(item, last = i == story.lastIndex) { item.relatedPersonId?.let(onOpenPerson) }
                        }
                    }
                }
            }
        }

        item { SectionHeading("Rodina") }
        item {
            Column {
                var any = false
                if (parents.isNotEmpty()) {
                    any = true
                    SubHeading("Rodiče")
                    TwoColumns(parents) { p, m ->
                        PersonCard(p, m, caption = if (p.sex == Sex.FEMALE) "matka" else "otec") { onOpenPerson(p.id) }
                    }
                }
                families.forEach { f ->
                    val partner = (if (f.husband == person.id) f.wife else f.husband)?.let(tree::person)
                    val kids = f.children.mapNotNull(tree::person)
                    if (partner != null || kids.isNotEmpty()) {
                        any = true
                        SubHeading(
                            buildString {
                                append(if (partner != null) "Manželství" else "Děti")
                                f.marriage?.parsedDate?.year?.let { append(" · sňatek $it") }
                            },
                        )
                        val items = buildList {
                            partner?.let { add(it to if (it.sex == Sex.FEMALE) "manželka" else "manžel") }
                            kids.forEach { add(it to if (it.sex == Sex.FEMALE) "dcera" else if (it.sex == Sex.MALE) "syn" else "dítě") }
                        }
                        TwoColumns(items) { (p, label), m -> PersonCard(p, m, caption = label) { onOpenPerson(p.id) } }
                    }
                }
                if (siblings.isNotEmpty()) {
                    any = true
                    SubHeading("Sourozenci")
                    TwoColumns(siblings) { p, m ->
                        PersonCard(p, m, caption = if (p.sex == Sex.FEMALE) "sestra" else "bratr") { onOpenPerson(p.id) }
                    }
                }
                if (!any) Text("Zatím bez zapsaných příbuzných.", color = cs.onSurfaceVariant)
            }
        }

        if (notes.isNotEmpty()) {
            item { SectionHeading("Poznámky") }
            item { WebCard { notes.forEach { ExpandableText(it) } } }
        }

        if (sources.isNotEmpty()) {
            item { SectionHeading("Prameny") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sources.forEach { s ->
                        WebCard {
                            Column(Modifier.padding(14.dp)) {
                                Text(s.title ?: s.abbreviation ?: s.id, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                val sub = listOfNotNull(s.author, s.publication).joinToString(" · ")
                                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Note.resolve(tree: GedcomTree): String? =
    text ?: noteId?.let { id -> tree.notes.firstOrNull { it.id == id }?.text }

@Composable
private fun TimelineRow(item: LifeItem, last: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val app = LocalAppColors.current
    val dot: Color = when (item.kind) {
        LifeKind.BIRTH, LifeKind.BAPTISM, LifeKind.CHILD -> app.doc
        LifeKind.MARRIAGE, LifeKind.DIVORCE -> cs.primary
        LifeKind.DEATH, LifeKind.BURIAL -> cs.outline
        else -> app.probable
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(if (item.relatedPersonId != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(Modifier.width(22.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(7.dp))
            Box(Modifier.size(11.dp).clip(CircleShape).background(dot))
            Box(Modifier.width(2.dp).weight(1f).background(if (last) Color.Transparent else cs.outlineVariant))
        }
        Column(Modifier.weight(1f).padding(bottom = if (last) 2.dp else 14.dp)) {
            item.date?.let { Text(it.toCzech(), style = MaterialTheme.typography.labelLarge, color = cs.primary) }
            Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            val sub = listOfNotNull(item.place, item.detail).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExpandableText(text: String) {
    var expanded by remember { mutableStateOf(false) }
    val long = text.length > 280
    Column(Modifier.padding(14.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = if (expanded || !long) Int.MAX_VALUE else 5,
            overflow = TextOverflow.Ellipsis,
        )
        if (long) {
            Text(
                if (expanded) "Méně" else "Zobrazit celé",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { expanded = !expanded }.padding(top = 8.dp),
            )
        }
    }
}
