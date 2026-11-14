package cz.mojekoreny.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.mojekoreny.app.ui.components.PersonAvatar
import cz.mojekoreny.app.ui.components.PersonTile
import cz.mojekoreny.app.ui.components.SectionCard
import cz.mojekoreny.app.ui.components.StatTile
import cz.mojekoreny.app.ui.components.sexColors
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
        tree.persons.filter { it.birth?.parsedDate?.year != null }.sortedBy { it.birth!!.parsedDate!!.sortKey }.take(6)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Hero(title, tree.persons.size, earliest, onSearch) }

        item {
            Row(
                Modifier.padding(horizontal = 16.dp).padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatTile("${tree.persons.size}", "osob", Icons.Outlined.Groups, Modifier.weight(1f))
                StatTile("${tree.families.size}", "rodin", Icons.Outlined.FamilyRestroom, Modifier.weight(1f))
                StatTile(if (generations > 0) "$generations" else "–", "generací", Icons.Outlined.AccountTree, Modifier.weight(1f))
            }
        }

        if (home != null) {
            item {
                SectionCard("Výchozí osoba", modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        PersonAvatar(home, 64.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(home.name.full, style = MaterialTheme.typography.titleLarge)
                            if (home.lifeSpan.isNotEmpty()) {
                                Text(home.lifeSpan, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Row(
                        Modifier.padding(horizontal = 16.dp).padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledTonalButton(onClick = { onOpenTree(home.id) }, Modifier.weight(1f)) {
                            Icon(Icons.Outlined.AccountTree, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Strom")
                        }
                        OutlinedButton(onClick = { onOpenPerson(home.id) }, Modifier.weight(1f)) { Text("Životní příběh") }
                    }
                }
            }
        }

        if (anniversaries.isNotEmpty()) {
            item {
                SectionCard("Výročí v rodu", Icons.Outlined.Celebration, Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                    anniversaries.forEach { a ->
                        tree.person(a.personId)?.let { AnniversaryRow(a, it, tree, onOpenPerson) }
                    }
                }
            }
        }

        if (recent.isNotEmpty()) {
            item { SectionTitle("Naposledy prohlížené", Icons.Outlined.History) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recent, key = { it.id }) { p -> PersonTile(p, null, { onOpenPerson(p.id) }) }
                }
            }
        }

        if (oldest.isNotEmpty()) {
            item { SectionTitle("Nejstarší předci", Icons.Outlined.HourglassBottom) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(oldest, key = { it.id }) { p -> PersonTile(p, null, { onOpenPerson(p.id) }) }
                }
            }
        }
    }
}

@Composable
private fun Hero(title: String, persons: Int, earliest: Int?, onSearch: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF244A37), Color(0xFF2F5D45), Color(0xFF6B4E2E)))),
    ) {
        // Jemné „letokruhy“ v pozadí
        Box(
            Modifier.padding(start = 220.dp, top = 8.dp).size(220.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f)),
        )
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 24.dp),
        ) {
            Text("MOJE KOŘENY", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text(
                buildString {
                    append("$persons osob")
                    if (earliest != null) append(" · od roku $earliest")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(20.dp))
            Surface(
                onClick = onSearch,
                shape = RoundedCornerShape(28.dp),
                color = cs.surface,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, null, tint = cs.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text("Hledat předka, místo, rok…", color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier.padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun AnniversaryRow(a: Anniversary, p: Person, tree: GedcomTree, onOpen: (String) -> Unit) {
    val (strong, _, container) = sexColors(p.sex)
    val icon = when (a.kind) {
        AnniversaryKind.BIRTH -> Icons.Outlined.Cake
        AnniversaryKind.DEATH -> Icons.Outlined.Spa
        AnniversaryKind.MARRIAGE -> Icons.Outlined.Favorite
    }
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
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(p.id) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = strong, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                "$what v roce ${a.year} · ${yearsCz(a.years)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (a.inDays == 0) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                whenText,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}
