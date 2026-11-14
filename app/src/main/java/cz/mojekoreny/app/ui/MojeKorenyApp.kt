package cz.mojekoreny.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.mojekoreny.app.TreeViewModel
import cz.mojekoreny.gedcom.GedcomVersion

private val GEDCOM_TYPES = arrayOf("application/x-gedcom", "text/x-gedcom", "application/octet-stream", "text/plain", "*/*")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MojeKorenyApp(vm: TreeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // Zásobník otevřených osob (detail → rodič → dítě …); prázdný = seznam.
    val stack = rememberSaveable(
        saver = listSaver<SnapshotStateList<String>, String>(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) { mutableStateListOf() }
    var menu by remember { mutableStateOf(false) }
    var exportVersion by remember { mutableStateOf(GedcomVersion.V551) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            stack.clear()
            vm.import(uri)
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-gedcom")) { uri ->
        if (uri != null) vm.export(uri, exportVersion)
    }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.messageShown() }
    }
    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

    val tree = state.tree
    val current = stack.lastOrNull()?.let { tree?.person(it) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(current?.name?.full ?: "Moje kořeny") },
                navigationIcon = {
                    if (stack.isNotEmpty()) {
                        IconButton(onClick = { stack.removeAt(stack.lastIndex) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Nabídka") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Importovat GEDCOM…") }, onClick = {
                            menu = false; importer.launch(GEDCOM_TYPES)
                        })
                        if (tree != null) {
                            DropdownMenuItem(text = { Text("Exportovat GEDCOM 5.5.1…") }, onClick = {
                                menu = false; exportVersion = GedcomVersion.V551; exporter.launch("rodokmen.ged")
                            })
                            DropdownMenuItem(text = { Text("Exportovat GEDCOM 7.0…") }, onClick = {
                                menu = false; exportVersion = GedcomVersion.V70; exporter.launch("rodokmen-7.ged")
                            })
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                tree == null -> Welcome(onImport = { importer.launch(GEDCOM_TYPES) })
                current != null -> PersonDetailScreen(tree, current, onOpen = { stack.add(it) })
                else -> PersonListScreen(tree, state.fileName, onOpen = { stack.add(it) })
            }
        }
    }
}

@Composable
private fun Welcome(onImport: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Vítejte v aplikaci Moje kořeny", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            "Načtěte rodokmen ze souboru GEDCOM (.ged). Data zůstanou v telefonu a fungují i bez internetu. " +
                "Propojení s rodinným webem přibude v další verzi.",
            Modifier.padding(top = 12.dp, bottom = 24.dp).widthIn(max = 420.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onImport, Modifier.fillMaxWidth().widthIn(max = 320.dp)) { Text("Importovat GEDCOM") }
    }
}
