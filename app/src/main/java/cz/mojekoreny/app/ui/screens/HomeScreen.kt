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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import cz.mojekoreny.app.ui.components.initials
import cz.mojekoreny.app.ui.theme.LocalFamilyColors
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

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { TopArea(title, onSearch) }

        item {
            Column(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("${tree.persons.size}", "osob", Icons.Outlined.Groups, 0, Modifier.weight(1f))
                    StatTile("${tree.families.size}", "rodin", Icons.Outlined.FamilyRestroom, 1, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(if (generations > 0) "$generations" else "–", "generací", Icons.Outlined.AccountTree, 2, Modifier.weight(1f))
                    StatTile(earliest?.toString() ?: "–", "nejstarší záznam", Icons.Outlined.HourglassBottom, 3, Modifier.weight(1f))
                }
            }
        }

        if (home != null) {
            item { HomePersonCard(home, onOpenPerson, onOpenTree) }
        }

        if (anniversaries.isNotEmpty()) {
            item {
                SectionCard("Výročí v rodu", Icons.Outlined.Celebration, Modifier.padding(horizontal = 16.dp).padding(top = 14.dp)) {
                    anniversaries.forEach { a ->
                        tree.person(a.personId)?.let { AnniversaryRow(a, it, tree, onOpenPerson) }
                    }
                }
            }
        }

        if (recent.isNotEmpty()) {
            item { SectionTitle("Naposledy prohlížené", Icons.Outlined.History) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recent, key = { it.id }) { p -> PersonTile(p, null, { onOpenPerson(p.id) }) }
                }
            }
        }

        if (oldest.isNotEmpty()) {
            item { SectionTitle("Nejstarší předci", Icons.Outlined.HourglassBottom) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(oldest, key = { it.id }) { p -> PersonTile(p, null, { onOpenPerson(p.id) }) }
                }
            }
        }
    }
}

@Composable
private fun TopArea(title: String, onSearch: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp)
            .padding(top = 14.dp),
    ) {
        Text("Dobrý den 👋", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Surface(
            onClick = onSearch,
            shape = RoundedCornerShape(24.dp),
            color = cs.surfaceContainerLowest,
            border = androidx.compose.foundation.BorderStroke(1.dp, cs.outlineVariant),
            modifier = Modifier.fillMaxWidth().height(46.dp),
        ) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Hledat předka, místo, rok…", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun HomePersonCard(home: Person, onOpenPerson: (String) -> Unit, onOpenTree: (String) -> Unit) {
    val accents = LocalFamilyColors.current.accents
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 14.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(Color(0xFF7A4FE6), Color(0xFF2F6FE4))))
            .clickable { onOpenPerson(home.id) },
    ) {
        Box(
            Modifier.padding(start = 230.dp).size(160.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
        )
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) { Text(initials(home), color = Color.White, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Výchozí osoba", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                    Text(home.name.full, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1)
                    if (home.lifeSpan.isNotEmpty()) {
                        Text(home.lifeSpan, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onOpenTree(home.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = accents[0].first),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Icon(Icons.Outlined.AccountTree, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Strom", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = { onOpenPerson(home.id) },
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp),
                ) { Text("Životní příběh", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier.padding(horizontal = 18.dp).padding(top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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
        Modifier.fillMaxWidth().clickable { onOpen(p.id) }.padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = strong, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                "$what v roce ${a.year} · ${yearsCz(a.years)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (a.inDays == 0) LocalFamilyColors.current.accents[5].second else MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                whenText,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}
