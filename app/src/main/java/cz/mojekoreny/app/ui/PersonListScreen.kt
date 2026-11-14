package cz.mojekoreny.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cz.mojekoreny.gedcom.GedcomTree
import cz.mojekoreny.gedcom.Sex

@Composable
fun PersonListScreen(tree: GedcomTree, fileName: String?, onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }

    // Seřazeno podle příjmení, jména a roku narození; klíče pro hledání bez diakritiky.
    val sorted = remember(tree) {
        tree.persons
            .sortedWith(
                compareBy(
                    { searchKey(it.name.surname) },
                    { searchKey(it.name.given) },
                    { it.birth?.parsedDate?.sortKey ?: Int.MAX_VALUE },
                ),
            )
            .map { p ->
                val key = searchKey(
                    listOfNotNull(p.name.full, p.birthSurname, p.birth?.place, p.lifeSpan).joinToString(" "),
                )
                p to key
            }
    }
    val words = searchKey(query).split(' ').filter { it.isNotBlank() }
    val shown = if (words.isEmpty()) sorted else sorted.filter { (_, key) -> words.all { it in key } }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Hledat jméno, místo, rok…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            "${shown.size} z ${tree.persons.size} osob" + (fileName?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.first.id }) { (p, _) ->
                ListItem(
                    headlineContent = {
                        Text(
                            listOf(p.name.surname, p.name.given).filter { it.isNotBlank() }.joinToString(" ")
                                .ifEmpty { "(beze jména)" },
                        )
                    },
                    supportingContent = {
                        val born = p.birthSurname?.let { if (p.sex == Sex.FEMALE) "roz. $it" else "rodné příjmení $it" }
                        val line = listOfNotNull(p.lifeSpan.ifEmpty { null }, born, p.birth?.place).joinToString(" · ")
                        if (line.isNotEmpty()) Text(line, maxLines = 1)
                    },
                    modifier = Modifier.clickable { onOpen(p.id) },
                )
                HorizontalDivider()
            }
        }
    }
}
