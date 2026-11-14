package cz.mojekoreny.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.mojekoreny.app.ui.components.SectionHeading
import cz.mojekoreny.app.ui.components.WebCard
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
    val cs = MaterialTheme.colorScheme
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)) {
        item { Text("Více", style = MaterialTheme.typography.headlineLarge) }

        item { SectionHeading("Rodinný web") }
        item {
            WebCard {
                Item("Přihlásit se k webu", "Připravujeme – stahování a úpravy záznamů na webu.", enabled = false) {}
            }
        }

        item { SectionHeading("Soubor GEDCOM") }
        item {
            WebCard {
                Item("Načíst rodokmen", "Ze souboru .ged v telefonu (nahradí současný)", onClick = onImport)
                if (tree != null) {
                    HorizontalDivider(color = cs.outlineVariant)
                    Item("Uložit jako GEDCOM 5.5.1", "Pro MyHeritage, Ancestry, Gramps, FTB", onClick = onExport551)
                    HorizontalDivider(color = cs.outlineVariant)
                    Item("Uložit jako GEDCOM 7.0", "Nejnovější verze formátu", onClick = onExport70)
                }
            }
        }

        if (tree != null) {
            item { SectionHeading("O rodokmenu") }
            item {
                WebCard {
                    Column(Modifier.padding(14.dp)) {
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
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }
}

@Composable
private fun Item(title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) cs.primary else cs.onSurfaceVariant,
        )
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun Info(label: String, value: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
