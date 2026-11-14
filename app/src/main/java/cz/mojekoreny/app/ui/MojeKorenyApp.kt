package cz.mojekoreny.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.mojekoreny.app.BuildConfigInfo
import cz.mojekoreny.app.TreeViewModel
import cz.mojekoreny.app.ui.components.EmptyState
import cz.mojekoreny.app.ui.screens.HomeScreen
import cz.mojekoreny.app.ui.screens.MoreScreen
import cz.mojekoreny.app.ui.screens.PeopleScreen
import cz.mojekoreny.app.ui.screens.PersonScreen
import cz.mojekoreny.app.ui.screens.TreeScreen
import cz.mojekoreny.gedcom.GedcomVersion
import java.time.LocalDate

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("Domů", Icons.Outlined.Home, Icons.Filled.Home),
    PEOPLE("Osoby", Icons.Outlined.Groups, Icons.Filled.Groups),
    TREE("Strom", Icons.Outlined.AccountTree, Icons.Filled.AccountTree),
    MORE("Více", Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz),
}

private val GEDCOM_TYPES = arrayOf("application/x-gedcom", "text/x-gedcom", "application/octet-stream", "text/plain", "*/*")

@Composable
fun MojeKorenyApp(vm: TreeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var focusSearch by remember { mutableStateOf(false) }
    var treeRoot by rememberSaveable { mutableStateOf<String?>(null) }
    // Otevřené detaily osob (detail → rodič → dítě …); prázdný = záložky.
    val stack = rememberSaveable(
        saver = listSaver<SnapshotStateList<String>, String>(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) { mutableStateListOf() }
    var exportVersion by remember { mutableStateOf(GedcomVersion.V551) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            stack.clear(); treeRoot = null; tab = Tab.HOME
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
    BackHandler(enabled = stack.isEmpty() && tab != Tab.HOME) { tab = Tab.HOME }

    val tree = state.tree
    val openPerson: (String) -> Unit = { id -> stack.add(id); vm.visited(id) }
    val openTree: (String) -> Unit = { id -> treeRoot = id; stack.clear(); tab = Tab.TREE }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (stack.isEmpty()) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { focusSearch = false; tab = t },
                            icon = { Icon(if (tab == t) t.selectedIcon else t.icon, null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                tree == null && tab != Tab.MORE -> EmptyState(
                    Icons.Outlined.Park,
                    "Vítejte v aplikaci Moje kořeny",
                    "Načtěte rodokmen ze souboru GEDCOM (.ged). Data zůstanou v telefonu a fungují i bez internetu.",
                ) {
                    Button(onClick = { importer.launch(GEDCOM_TYPES) }) {
                        Icon(Icons.Outlined.FileUpload, null)
                        Text("  Načíst rodokmen")
                    }
                }
                else -> AnimatedContent(
                    targetState = stack.lastOrNull(),
                    transitionSpec = {
                        if (targetState != null && (initialState == null || stack.size > 0)) {
                            (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (fadeOut())
                        } else {
                            fadeIn() togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
                        }
                    },
                    label = "detail",
                ) { personId ->
                    val person = personId?.let { tree?.person(it) }
                    if (tree != null && person != null) {
                        PersonScreen(
                            tree, person,
                            isHome = person.id == state.homeId,
                            onBack = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) },
                            onOpenPerson = openPerson,
                            onOpenTree = openTree,
                            onSetHome = vm::setHome,
                        )
                    } else {
                        AnimatedContent(targetState = tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { t ->
                            when (t) {
                                Tab.HOME -> HomeScreen(
                                    tree = tree!!,
                                    title = homeTitle(state.fileName, state.homeId?.let(tree::person)?.name?.surname),
                                    home = state.homeId?.let(tree::person),
                                    recent = state.recent.mapNotNull(tree::person),
                                    today = LocalDate.now(),
                                    onSearch = { focusSearch = true; tab = Tab.PEOPLE },
                                    onOpenPerson = openPerson,
                                    onOpenTree = openTree,
                                )
                                Tab.PEOPLE -> PeopleScreen(tree!!, state.homeId?.let(tree::person), focusSearch, openPerson)
                                Tab.TREE -> TreeScreen(
                                    tree!!,
                                    root = (treeRoot ?: state.homeId)?.let(tree::person) ?: tree.persons.firstOrNull(),
                                    onFocus = { treeRoot = it },
                                    onOpenPerson = openPerson,
                                )
                                Tab.MORE -> MoreScreen(
                                    tree, state.fileName, BuildConfigInfo.VERSION,
                                    onImport = { importer.launch(GEDCOM_TYPES) },
                                    onExport551 = { exportVersion = GedcomVersion.V551; exporter.launch("rodokmen.ged") },
                                    onExport70 = { exportVersion = GedcomVersion.V70; exporter.launch("rodokmen-7.ged") },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** „Rod Novákových“ by chtělo skloňování; stačí „Rodokmen – Novák“. */
private fun homeTitle(fileName: String?, surname: String?): String = when {
    !surname.isNullOrBlank() -> "Rodokmen $surname"
    !fileName.isNullOrBlank() -> fileName.substringBeforeLast('.')
    else -> "Rodokmen"
}
