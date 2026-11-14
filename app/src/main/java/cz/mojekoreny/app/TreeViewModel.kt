package cz.mojekoreny.app

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.mojekoreny.gedcom.GedcomReader
import cz.mojekoreny.gedcom.GedcomTree
import cz.mojekoreny.gedcom.GedcomVersion
import cz.mojekoreny.gedcom.GedcomWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

data class UiState(
    val tree: GedcomTree? = null,
    val fileName: String? = null,
    val loading: Boolean = false,
    val message: String? = null,
)

/**
 * Fáze 0: rodokmen se drží v paměti a poslední importovaný soubor se kopíruje
 * do soukromého úložiště aplikace, aby byl k dispozici i po restartu a offline.
 * Ve fázi 1 ho nahradí databáze Room.
 */
class TreeViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val stored = File(app.filesDir, "rodokmen.ged")
    private val storedName = File(app.filesDir, "rodokmen.nazev")

    init {
        if (stored.exists()) {
            viewModelScope.launch {
                _state.update { it.copy(loading = true) }
                val tree = withContext(Dispatchers.IO) { runCatching { GedcomReader.read(stored.readBytes()) } }
                _state.update {
                    it.copy(
                        loading = false,
                        tree = tree.getOrNull(),
                        fileName = runCatching { storedName.readText() }.getOrNull(),
                        message = tree.exceptionOrNull()?.let { e -> "Uložený rodokmen nejde načíst: ${e.message}" },
                    )
                }
            }
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            val ctx = getApplication<Application>()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Soubor nejde otevřít")
                    val tree = GedcomReader.read(bytes)
                    require(tree.persons.isNotEmpty()) { "V souboru nejsou žádné osoby – je to opravdu GEDCOM?" }
                    stored.writeBytes(bytes)
                    val name = displayName(uri) ?: "rodokmen.ged"
                    storedName.writeText(name)
                    tree to name
                }
            }
            _state.update { s ->
                result.fold(
                    onSuccess = { (tree, name) ->
                        s.copy(
                            loading = false, tree = tree, fileName = name,
                            message = "Načteno ${tree.persons.size} osob a ${tree.families.size} rodin (${tree.header.encoding}).",
                        )
                    },
                    onFailure = { e -> s.copy(loading = false, message = "Import se nepovedl: ${e.message}") },
                )
            }
        }
    }

    fun export(uri: Uri, version: GedcomVersion) {
        val tree = _state.value.tree ?: return
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use {
                        GedcomWriter(version, BuildConfigInfo.VERSION, LocalDate.now()).write(tree, it)
                    } ?: error("Soubor nejde vytvořit")
                }
            }
            _state.update {
                it.copy(
                    message = result.fold(
                        { "Uloženo jako GEDCOM ${version.label}." },
                        { e -> "Export se nepovedl: ${e.message}" },
                    ),
                )
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun displayName(uri: Uri): String? =
        getApplication<Application>().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}

internal object BuildConfigInfo {
    const val VERSION = "0.1.0"
}
