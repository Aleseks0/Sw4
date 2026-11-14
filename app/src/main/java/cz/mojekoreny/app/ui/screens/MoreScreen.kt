package cz.mojekoreny.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import cz.mojekoreny.app.ui.components.SectionCard
import cz.mojekoreny.gedcom.GedcomTree

@Composable
fun MoreScreen(
    tree: GedcomTree?,
    fileName: String?,
    appVersion: String,
    onImport: () -> Unit,
    onExport551: () -> Unit,
    onExport70: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                "Více",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 20.dp, top = 12.dp, bottom = 8.dp),
            )
        }
        item {
            SectionCard("Rodinný web", Icons.Outlined.CloudSync, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    Icons.Outlined.Lock, "Přihlásit se k webu",
                    "Připravujeme – stahování a úpravy záznamů na webu.", enabled = false,
                ) {}
            }
        }
        item {
            SectionCard("Soubor GEDCOM", Icons.Outlined.Description, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(Icons.Outlined.FileUpload, "Načíst rodokmen", "Ze souboru .ged v telefonu (nahradí současný)", onClick = onImport)
                if (tree != null) {
                    Row(Icons.Outlined.FileDownload, "Uložit jako GEDCOM 5.5.1", "Pro MyHeritage, Ancestry, Gramps, FTB", onClick = onExport551)
                    Row(Icons.Outlined.FileDownload, "Uložit jako GEDCOM 7.0", "Nejnovější verze formátu", onClick = onExport70)
                }
            }
        }
        if (tree != null) {
            item {
                SectionCard("O rodokmenu", Icons.Outlined.Info, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Info("Soubor", fileName ?: "–")
                        Info("Osob / rodin", "${tree.persons.size} / ${tree.families.size}")
                        Info("Pramenů", "${tree.sources.size}")
                        Info("Formát", "GEDCOM ${tree.header.version ?: "?"} · ${tree.header.encoding ?: "?"}")
                        tree.header.sourceSystem?.let { Info("Vytvořeno v", it) }
                    }
                }
            }
        }
        item {
            Text(
                "Moje kořeny $appVersion",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}

@Composable
private fun Row(icon: ImageVector, title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            headlineColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
private fun Info(label: String, value: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
