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
import app.mnemo.data.io.ApkgImporter
import app.mnemo.data.io.AutoBackup
import app.mnemo.data.io.CsvImporter
import app.mnemo.data.io.JsonBackup
import app.mnemo.domain.model.Deck
import app.mnemo.domain.stats.DayStart
import app.mnemo.platform.appDataDir
import app.mnemo.platform.rememberFilePicker
import app.mnemo.platform.saveTextFile
import app.mnemo.ui.components.Eyebrow
import app.mnemo.ui.components.MnemoButton
import app.mnemo.ui.components.MnemoIcons
import app.mnemo.ui.components.MnemoSelect
import app.mnemo.ui.components.MnemoTextField
import app.mnemo.ui.components.Panel
import app.mnemo.ui.components.ScreenHeader
import app.mnemo.ui.components.Segmented
import app.mnemo.ui.nav.LocalWindowSize
import app.mnemo.ui.nav.WindowSize
import app.mnemo.ui.theme.LocalThemeMode
import app.mnemo.ui.theme.MnemoColors
import app.mnemo.ui.theme.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val compact = LocalWindowSize.current == WindowSize.Compact
    val theme = LocalThemeMode.current
    var decks by remember { mutableStateOf<List<Deck>>(emptyList()) }
    var importDeck by remember { mutableStateOf<Deck?>(null) }
    var pasted by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var counts by remember { mutableStateOf("") }
    var backups by remember { mutableStateOf(AutoBackup.list()) }
    var dayStart by remember { mutableStateOf(DayStart.hour) }

    suspend fun refresh() {
        decks = graph.decks.all()
        if (importDeck == null) importDeck = decks.firstOrNull()
        counts =
            "${graph.decks.count()} decks · ${graph.notes.countAll()} notes · ${graph.cards.countAll()} cards · ${graph.reviewLogs.countAll()} reviews"
    }
    LaunchedEffect(Unit) { refresh() }

    fun runCsv(text: String) {
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

    val pickApkg =
        rememberFilePicker(listOf("apkg", "colpkg")) { file ->
            if (file == null) {
                message = "No file chosen"
                return@rememberFilePicker
            }
            busy = true
            message = "Importing ${file.name}…"
            scope.launch {
                val result = runCatching { ApkgImporter.import(graph, file.bytes) }
                message =
                    result.fold(
                        { r ->
                            "Imported ${r.notes} notes, ${r.cards} cards, ${r.reviews} reviews, " +
                                "${r.media} media files into ${r.decks} decks" +
                                (if (r.skippedNotes > 0) " · ${r.skippedNotes} notes skipped (already imported or empty)" else "") +
                                (if (r.warnings.isNotEmpty()) "\n" + r.warnings.joinToString("\n") else "")
                        },
                        { "Import failed: ${it.message}" },
                    )
                busy = false
                refresh()
            }
        }
    val pickCsv =
        rememberFilePicker(listOf("csv", "txt", "tsv")) { file ->
            if (file ==
                null
            ) {
                message = "No file chosen"
            } else {
                runCsv(file.text)
            }
        }
    val pickJson =
        rememberFilePicker(listOf("json")) { file ->
            if (file == null) {
                message = "No file chosen"
                return@rememberFilePicker
            }
            scope.launch {
                val n = runCatching { JsonBackup.restore(graph, file.text) }
                message = n.fold({ "Restored $it notes" }, { "Restore failed: ${it.message}" })
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
                Eyebrow("Appearance")
                Spacer(Modifier.height(10.dp))
                Segmented(
                    options = listOf("OLED dark", "Light", "System"),
                    selected = theme.mode.ordinal,
                    onSelect = { theme.set(ThemeMode.entries[it]) },
                )
            }
            Panel {
                Eyebrow("Study day")
                Spacer(Modifier.height(6.dp))
                Text(
                    "When the next day starts. Reviews before this hour count toward the previous day, so a late session does not split your streak or reset the daily limits at midnight.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MnemoColors.text2,
                )
                Spacer(Modifier.height(12.dp))
                MnemoSelect(
                    label = "Next day starts at",
                    options = (0..12).toList(),
                    selected = dayStart,
                    display = { "${it.toString().padStart(2, '0')}:00" },
                    onSelect = {
                        dayStart = it
                        DayStart.hour = it
                    },
                )
            }
            Panel {
                Eyebrow("Import an Anki deck")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Any .apkg: shared decks from AnkiWeb or your own export. Decks, notes, cards, scheduling, review history and media come across. Re-importing the same deck skips notes you already have.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MnemoColors.text2,
                )
                Spacer(Modifier.height(12.dp))
                MnemoButton(
                    if (busy) "Importing…" else "Choose .apkg file…",
                    icon = MnemoIcons.Import,
                    primary = true,
                    enabled = !busy,
                    onClick = pickApkg,
                )
            }
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
                MnemoButton("Choose CSV file…", icon = MnemoIcons.Import, onClick = pickCsv)
                Spacer(Modifier.height(12.dp))
                MnemoTextField(value = pasted, onValueChange = {
                    pasted = it
                }, label = "…or paste CSV text", minLines = 4, placeholder = "der Hund,the dog,a1 noun")
                Spacer(Modifier.height(8.dp))
                MnemoButton("Import pasted text", enabled = pasted.isNotBlank(), onClick = { runCsv(pasted) })
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
                            val path = saveTextFile("mnemo-backup.json", text)
                            message = if (path != null) "Backup written to $path" else "Export cancelled"
                        }
                    })
                    MnemoButton("Restore from JSON…", onClick = pickJson)
                }
                Spacer(Modifier.height(16.dp))
                Eyebrow("Automatic backups")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Written once a day when the app opens; the newest ${AutoBackup.KEEP} are kept in ${appDataDir()}/${AutoBackup.DIR}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MnemoColors.text2,
                )
                Spacer(Modifier.height(8.dp))
                if (backups.isEmpty()) {
                    Text("No automatic backup yet.", style = MaterialTheme.typography.bodySmall, color = MnemoColors.text3)
                }
                backups.forEach { b ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "${b.label} · ${b.bytes / 1024} KB",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MnemoColors.text,
                            modifier = Modifier.weight(1f),
                        )
                        MnemoButton("Restore", height = 36.dp, enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                val n = runCatching { AutoBackup.restore(graph, b.name) }
                                message = n.fold({ "Restored $it notes from ${b.label}" }, { "Restore failed: ${it.message}" })
                                busy = false
                                refresh()
                            }
                        })
                    }
                }
                Spacer(Modifier.height(8.dp))
                MnemoButton("Back up now", icon = MnemoIcons.Export, enabled = !busy, onClick = {
                    scope.launch {
                        val name = runCatching { AutoBackup.runIfDue(graph, force = true) }.getOrNull()
                        backups = AutoBackup.list()
                        message = if (name != null) "Backup written: $name" else "Nothing to back up yet"
                    }
                })
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
                    "Mnemo 0.2.0 · Phase 2. Scheduler: FSRS-6 default parameters.",
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
