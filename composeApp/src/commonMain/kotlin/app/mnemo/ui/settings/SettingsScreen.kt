package app.mnemo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.mnemo.LocalAppGraph
import app.mnemo.data.io.CsvImporter
import app.mnemo.data.io.JsonBackup
import app.mnemo.domain.model.Deck
import app.mnemo.platform.FileIo
import app.mnemo.platform.appDataDir
import app.mnemo.ui.components.Eyebrow
import app.mnemo.ui.components.MnemoButton
import app.mnemo.ui.components.MnemoIcons
import app.mnemo.ui.components.MnemoSelect
import app.mnemo.ui.components.MnemoTextField
import app.mnemo.ui.components.Panel
import app.mnemo.ui.components.ScreenHeader
import app.mnemo.ui.nav.LocalWindowSize
import app.mnemo.ui.nav.WindowSize
import app.mnemo.ui.theme.MnemoColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val compact = LocalWindowSize.current == WindowSize.Compact
    var decks by remember { mutableStateOf<List<Deck>>(emptyList()) }
    var importDeck by remember { mutableStateOf<Deck?>(null) }
    var pasted by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var counts by remember { mutableStateOf("") }

    suspend fun refresh() {
        decks = graph.decks.all()
        if (importDeck == null) importDeck = decks.firstOrNull()
        counts =
            "${graph.decks.count()} decks · ${graph.notes.countAll()} notes · ${graph.cards.countAll()} cards · ${graph.reviewLogs.countAll()} reviews"
    }
    LaunchedEffect(Unit) { refresh() }

    fun runImport(text: String) {
        val deck =
            importDeck ?: run {
                message = "Create a deck first."
                return
            }
        scope.launch {
            val r = CsvImporter.import(graph, deck.id, text)
            message =
                "Imported ${r.notes} notes (${r.cards} cards) into ${deck.path}" +
                if (r.skipped > 0) " · ${r.skipped} rows skipped (no back)" else ""
            pasted = ""
            refresh()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (compact) 20.dp else 40.dp, vertical = 28.dp),
    ) {
        ScreenHeader(title = "Settings", subtitle = counts)
        Spacer(Modifier.height(20.dp))
        Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Panel {
                Eyebrow("Import CSV")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Columns: front, back, tags (optional). Comma, semicolon or tab separated. A front with {{c1::…}} becomes a cloze note.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MnemoColors.text2,
                )
                Spacer(Modifier.height(12.dp))
                MnemoSelect(
                    label = "Into deck",
                    options = decks,
                    selected = importDeck,
                    display = { it.path },
                    onSelect = { importDeck = it },
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MnemoButton("Choose CSV file…", icon = MnemoIcons.Import, primary = true, onClick = {
                        scope.launch {
                            val text = FileIo.openText(listOf("csv", "txt", "tsv"))
                            if (text == null) message = "No file chosen (on Android, paste the text below)." else runImport(text)
                        }
                    })
                }
                Spacer(Modifier.height(12.dp))
                MnemoTextField(value = pasted, onValueChange = {
                    pasted = it
                }, label = "…or paste CSV text", minLines = 4, placeholder = "der Hund,the dog,a1 noun")
                Spacer(Modifier.height(8.dp))
                MnemoButton("Import pasted text", enabled = pasted.isNotBlank(), onClick = { runImport(pasted) })
            }
            Panel {
                Eyebrow("Backup")
                Spacer(Modifier.height(6.dp))
                Text(
                    "A JSON backup holds decks, notes, cards, review history and concepts. Restoring merges into the current data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MnemoColors.text2,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MnemoButton("Export JSON backup", icon = MnemoIcons.Export, onClick = {
                        scope.launch {
                            val text = JsonBackup.export(graph)
                            val path = FileIo.saveText("mnemo-backup.json", text)
                            message = if (path != null) "Backup written to $path" else "Export cancelled"
                        }
                    })
                    MnemoButton("Restore from JSON…", onClick = {
                        scope.launch {
                            val text = FileIo.openText(listOf("json"))
                            if (text == null) {
                                message = "No file chosen"
                            } else {
                                val n = runCatching { JsonBackup.restore(graph, text) }
                                message = n.fold({ "Restored $it notes" }, { "Restore failed: ${it.message}" })
                                refresh()
                            }
                        }
                    })
                }
            }
            Panel {
                Eyebrow("AI")
                Spacer(Modifier.height(6.dp))
                Text(
                    "No AI key is configured. Card generation, answer grading, the tutor and concept suggestions arrive in Phases 3–6 and stay optional; everything else works offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MnemoColors.text2,
                )
            }
            Panel {
                Eyebrow("About")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Mnemo 0.1.0 · Phase 1 (Anki parity). Scheduler: FSRS-6 default parameters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MnemoColors.text2,
                )
                Text("Data: ${appDataDir()}", style = MaterialTheme.typography.bodySmall, color = MnemoColors.text3)
            }
            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MnemoColors.success,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
