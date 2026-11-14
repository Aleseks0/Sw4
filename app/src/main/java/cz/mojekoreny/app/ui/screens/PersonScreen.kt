package cz.mojekoreny.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.mojekoreny.app.ui.components.PersonAvatar
import cz.mojekoreny.app.ui.components.PersonTile
import cz.mojekoreny.app.ui.components.SectionCard
import cz.mojekoreny.app.ui.components.icon
import cz.mojekoreny.app.ui.components.sexColors
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

@Composable
fun PersonScreen(
    tree: GedcomTree,
    person: Person,
    isHome: Boolean,
    onBack: () -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenTree: (String) -> Unit,
    onSetHome: (String) -> Unit,
) {
    val story = remember(tree, person) { tree.lifeStory(person) }
    val parents = remember(tree, person) { tree.parentsOf(person) }
    val siblings = remember(tree, person) { tree.siblingsOf(person) }
    val families = remember(tree, person) { person.familiesAsSpouse.mapNotNull(tree::family) }
    val notes = remember(tree, person) { person.notes.mapNotNull { it.resolve(tree) } }
    val sources = remember(tree, person) {
        (person.citations + person.events.flatMap { it.citations }).mapNotNull { it.sourceId }.distinct().mapNotNull(tree::source)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { Header(person, isHome, onBack, { onOpenTree(person.id) }, { onSetHome(person.id) }) }

        if (story.isNotEmpty()) {
            item {
                SectionCard("Životní příběh", Icons.Outlined.Timeline, Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                    story.forEachIndexed { i, item ->
                        TimelineRow(item, first = i == 0, last = i == story.lastIndex) {
                            item.relatedPersonId?.let(onOpenPerson)
                        }
                    }
                }
            }
        }

        item {
            SectionCard("Rodina", Icons.Outlined.FamilyRestroom, Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                var any = false
                if (parents.isNotEmpty()) {
                    any = true
                    FamilyRow("Rodiče", parents.map { it to if (it.sex == Sex.FEMALE) "matka" else "otec" }, onOpenPerson)
                }
                families.forEach { f ->
                    val partner = (if (f.husband == person.id) f.wife else f.husband)?.let(tree::person)
                    val kids = f.children.mapNotNull(tree::person)
                    val items = buildList {
                        partner?.let { add(it to if (it.sex == Sex.FEMALE) "manželka" else "manžel") }
                        kids.forEach { add(it to if (it.sex == Sex.FEMALE) "dcera" else if (it.sex == Sex.MALE) "syn" else "dítě") }
                    }
                    if (items.isNotEmpty()) {
                        any = true
                        val title = buildString {
                            append(if (partner != null) "Manželství" else "Děti")
                            f.marriage?.parsedDate?.year?.let { append(" · sňatek $it") }
                        }
                        FamilyRow(title, items, onOpenPerson)
                    }
                }
                if (siblings.isNotEmpty()) {
                    any = true
                    FamilyRow("Sourozenci", siblings.map { it to if (it.sex == Sex.FEMALE) "sestra" else "bratr" }, onOpenPerson)
                }
                if (!any) {
                    Text(
                        "Zatím bez zapsaných příbuzných.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }

        if (notes.isNotEmpty()) {
            item {
                SectionCard("Poznámky", Icons.AutoMirrored.Outlined.Notes, Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                    notes.forEach { ExpandableText(it) }
                }
            }
        }

        if (sources.isNotEmpty()) {
            item {
                SectionCard("Prameny", Icons.AutoMirrored.Outlined.MenuBook, Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                    sources.forEach { s ->
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text(s.title ?: s.abbreviation ?: s.id, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            val sub = listOfNotNull(s.author, s.publication).joinToString(" · ")
                            if (sub.isNotEmpty()) {
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun Header(person: Person, isHome: Boolean, onBack: () -> Unit, onTree: () -> Unit, onSetHome: () -> Unit) {
    val (strong, _, container) = sexColors(person.sex)
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brush.verticalGradient(listOf(container, cs.surface))),
    ) {
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = cs.surface.copy(alpha = 0.8f), contentColor = cs.onSurface),
                ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zpět") }
                Spacer(Modifier.weight(1f))
            }
            PersonAvatar(person, 104.dp, ring = true)
            Spacer(Modifier.height(14.dp))
            Text(
                person.name.full.ifEmpty { "(beze jména)" },
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            person.birthSurname?.let {
                Text(if (person.sex == Sex.FEMALE) "rozená $it" else "rodné příjmení $it", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (person.lifeSpan.isNotEmpty()) Pill(person.lifeSpan, strong)
                person.ageAtDeath()?.let { Pill("dožil${if (person.sex == Sex.FEMALE) "a" else ""} se ${yearsCz(it)}", cs.secondary) }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AssistChip(
                    onClick = onTree,
                    label = { Text("Strom") },
                    leadingIcon = { Icon(Icons.Outlined.AccountTree, null, Modifier.size(18.dp)) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = cs.surfaceContainerLowest),
                )
                AssistChip(
                    onClick = onSetHome,
                    enabled = !isHome,
                    label = { Text(if (isHome) "Výchozí osoba" else "Nastavit jako výchozí") },
                    leadingIcon = { Icon(Icons.Outlined.Home, null, Modifier.size(18.dp)) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = cs.surfaceContainerLowest),
                )
            }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f)) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun TimelineRow(item: LifeItem, first: Boolean, last: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val accent = when (item.kind) {
        LifeKind.BIRTH, LifeKind.BAPTISM, LifeKind.CHILD -> cs.primary
        LifeKind.MARRIAGE -> cs.tertiary
        LifeKind.DEATH, LifeKind.BURIAL -> cs.outline
        else -> cs.secondary
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(if (item.relatedPersonId != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
    ) {
        Column(Modifier.width(40.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(2.dp).height(10.dp).background(if (first) Color.Transparent else cs.outlineVariant))
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(item.kind.icon(), null, tint = accent, modifier = Modifier.size(18.dp)) }
            Box(Modifier.width(2.dp).weight(1f).background(if (last) Color.Transparent else cs.outlineVariant))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(top = 10.dp, bottom = 14.dp)) {
            item.date?.let {
                Text(it.toCzech(), style = MaterialTheme.typography.labelLarge, color = accent)
            }
            Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            val sub = listOfNotNull(item.place, item.detail).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FamilyRow(title: String, people: List<Pair<Person, String>>, onOpen: (String) -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 8.dp),
    )
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(people, key = { it.first.id }) { (p, label) -> PersonTile(p, label, { onOpen(p.id) }) }
    }
}

@Composable
private fun ExpandableText(text: String) {
    var expanded by remember { mutableStateOf(false) }
    val long = text.length > 280
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded || !long) Int.MAX_VALUE else 5,
            overflow = TextOverflow.Ellipsis,
        )
        if (long) {
            Text(
                if (expanded) "Méně" else "Zobrazit celé",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 6.dp),
            )
        }
    }
}
