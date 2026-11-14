package cz.mojekoreny.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import cz.mojekoreny.app.ui.components.EmptyState
import cz.mojekoreny.app.ui.components.PersonCard
import cz.mojekoreny.app.ui.searchKey
import cz.mojekoreny.gedcom.GedcomTree
import cz.mojekoreny.gedcom.Person
import cz.mojekoreny.gedcom.Sex

private enum class PeopleFilter(val label: String) { ALL("Všichni"), ANCESTORS("Přímí předci"), MEN("Muži"), WOMEN("Ženy") }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PeopleScreen(
    tree: GedcomTree,
    home: Person?,
    focusSearch: Boolean,
    onOpenPerson: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(PeopleFilter.ALL) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusSearch) { if (focusSearch) runCatching { focus.requestFocus() } }
    val cs = MaterialTheme.colorScheme

    val ancestors = remember(tree, home) {
        val out = mutableSetOf<String>()
        var gen = listOfNotNull(home)
        while (gen.isNotEmpty()) gen = gen.flatMap { tree.parentsOf(it) }.filter { out.add(it.id) }
        out
    }
    val indexed = remember(tree) {
        tree.persons
            .sortedWith(
                compareBy(
                    { searchKey(it.name.surname).ifEmpty { "~" } },
                    { searchKey(it.name.given) },
                    { it.birth?.parsedDate?.sortKey ?: Int.MAX_VALUE },
                ),
            )
            .map { p ->
                p to searchKey(listOfNotNull(p.name.full, p.birthSurname, p.birth?.place, p.death?.place, p.lifeSpan).joinToString(" "))
            }
    }
    val words = searchKey(query).split(' ').filter { it.isNotBlank() }
    val shown = indexed.filter { (p, key) ->
        val f = when (filter) {
            PeopleFilter.ALL -> true
            PeopleFilter.ANCESTORS -> p.id in ancestors
            PeopleFilter.MEN -> p.sex == Sex.MALE
            PeopleFilter.WOMEN -> p.sex == Sex.FEMALE
        }
        f && words.all { it in key }
    }.map { it.first }
    val groups = shown.groupBy { it.name.surname.firstOrNull()?.uppercaseChar()?.toString() ?: "#" }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
            Text("Osoby", style = MaterialTheme.typography.headlineLarge)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Smazat") }
                },
                placeholder = { Text("Jméno, místo nebo rok") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = cs.surfaceContainerLowest,
                    focusedContainerColor = cs.surfaceContainerLowest,
                    unfocusedBorderColor = cs.outlineVariant,
                    focusedBorderColor = cs.primary,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp).focusRequester(focus),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PeopleFilter.entries.filter { it != PeopleFilter.ANCESTORS || ancestors.isNotEmpty() }) { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f.label) },
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = cs.surfaceContainerLowest,
                            selectedContainerColor = cs.primaryContainer,
                            selectedLabelColor = cs.onPrimaryContainer,
                        ),
                    )
                }
            }
            Text(
                "${shown.size} z ${tree.persons.size}",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }

        if (shown.isEmpty()) {
            EmptyState(Icons.Outlined.PersonSearch, "Nikdo nenalezen", "Zkuste jiné jméno, místo nebo rok. Diakritiku psát nemusíte.")
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                groups.forEach { (letter, people) ->
                    stickyHeader(key = "h-$letter") {
                        Text(
                            letter,
                            style = MaterialTheme.typography.titleMedium,
                            color = cs.primary,
                            modifier = Modifier.fillMaxWidth().background(cs.background).padding(top = 10.dp, bottom = 4.dp),
                        )
                    }
                    items(people, key = { it.id }) { p ->
                        val detail = listOfNotNull(
                            p.birthSurname?.let { "roz. $it" },
                            (p.birth?.place ?: p.death?.place)?.substringBefore(','),
                        ).joinToString(" · ").ifEmpty { null }
                        PersonCard(p, Modifier.fillMaxWidth(), detail = detail) { onOpenPerson(p.id) }
                    }
                }
            }
        }
    }
}
