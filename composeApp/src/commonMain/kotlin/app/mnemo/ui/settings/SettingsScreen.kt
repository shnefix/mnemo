package app.mnemo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.mnemo.LocalAppGraph
import app.mnemo.data.io.ApkgImporter
import app.mnemo.data.io.AutoBackup
import app.mnemo.data.io.CsvImporter
import app.mnemo.data.io.DeckTrash
import app.mnemo.data.io.JsonBackup
import app.mnemo.data.io.MarkdownImporter
import app.mnemo.domain.model.Deck
import app.mnemo.domain.reminder.Reminder
import app.mnemo.domain.stats.DayStart
import app.mnemo.platform.ReminderScheduler
import app.mnemo.platform.appDataDir
import app.mnemo.platform.refreshWidgets
import app.mnemo.platform.rememberFilePicker
import app.mnemo.platform.rememberFolderPicker
import app.mnemo.platform.rememberNotificationPermissionRequest
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
import app.mnemo.ui.theme.ThemeState
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
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
    var trash by remember { mutableStateOf<List<DeckTrash.Entry>>(emptyList()) }
    var dayStart by remember { mutableStateOf(DayStart.hour) }
    var reminderHour by remember { mutableStateOf(Reminder.hour) }
    var titleFallback by remember { mutableStateOf(false) }
    val requestNotifications =
        rememberNotificationPermissionRequest { granted ->
            if (granted) {
                reminderHour = Reminder.DEFAULT_HOUR
                Reminder.hour = Reminder.DEFAULT_HOUR
            } else {
                message = "Notifications are blocked for Mnemo; allow them in the system settings to get a reminder."
            }
        }

    suspend fun refresh() {
        decks = graph.decks.all()
        if (importDeck == null) importDeck = decks.firstOrNull()
        counts =
            "${graph.decks.count()} decks · ${graph.notes.countAll()} notes · ${graph.cards.countAll()} cards · ${graph.reviewLogs.countAll()} reviews"
    }
    LaunchedEffect(Unit) {
        refresh()
        trash = runCatching { DeckTrash.list(graph.clock.now()) }.getOrDefault(emptyList())
    }

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
    val pickFolder =
        rememberFolderPicker(listOf("md", "markdown")) { folder ->
            val deck = importDeck
            when {
                folder == null -> message = "No folder chosen"
                deck == null -> message = "Create a deck first."
                folder.files.isEmpty() -> message = "No Markdown files in ${folder.name}"
                else -> {
                    busy = true
                    message = "Importing ${folder.files.size} files from ${folder.name}…"
                    scope.launch {
                        val docs = folder.files.map { MarkdownImporter.Doc(it.path, it.text) }
                        val result = runCatching { MarkdownImporter.import(graph, deck.id, docs, titleFallback = titleFallback) }
                        message =
                            result.fold(
                                { r ->
                                    if (r.notes == 0 && r.skipped == 0) {
                                        "No question/answer sections found in ${folder.files.size} files"
                                    } else {
                                        "Imported ${r.notes} notes (${r.cards} cards) from ${r.files} files " +
                                            "into ${r.decks} decks under ${deck.path}" +
                                            if (r.skipped > 0) " · ${r.skipped} already imported" else ""
                                    }
                                },
                                { "Import failed: ${it.message}" },
                            )
                        busy = false
                        refresh()
                    }
                }
            }
        }
    val pickJson =
        rememberFilePicker(listOf("json")) { file ->
            if (file == null) {
                message = "No file chosen"
                return@rememberFilePicker
            }
            scope.launch {
                val n = runCatching { JsonBackup.restoreDeleted(graph, file.text) }
                message = n.fold({ "Restored $it notes" }, { "Restore failed: ${it.message}" })
                refresh()
            }
        }

    val pickSync =
        rememberFilePicker(listOf("json")) { file ->
            if (file == null) {
                message = "No file chosen"
                return@rememberFilePicker
            }
            busy = true
            scope.launch {
                val n = runCatching { JsonBackup.merge(graph, file.text) }
                message = n.fold({ "Synced: $it notes added or updated" }, { "Sync failed: ${it.message}" })
                busy = false
                refreshWidgets()
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
                Spacer(Modifier.height(14.dp))
                Eyebrow("Text size")
                Spacer(Modifier.height(10.dp))
                Segmented(
                    options = ThemeState.TEXT_SIZE_LABELS,
                    selected = theme.textSize,
                    onSelect = { theme.changeTextSize(it) },
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
                Eyebrow("Daily reminder")
                Spacer(Modifier.height(6.dp))
                Text(
                    "One notification a day with how many cards are due. Nothing is sent on a day with nothing due.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MnemoColors.text2,
                )
                Spacer(Modifier.height(12.dp))
                if (!ReminderScheduler.supported) {
                    Text(
                        "Reminders are available in the Android app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MnemoColors.text3,
                    )
                } else {
                    Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = reminderHour != null,
                            onCheckedChange = { on ->
                                if (on) {
                                    requestNotifications()
                                } else {
                                    reminderHour = null
                                    Reminder.hour = null
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = MnemoColors.onCoral, checkedTrackColor = MnemoColors.coral),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Remind me every day", style = MaterialTheme.typography.bodyMedium, color = MnemoColors.text)
                    }
                    reminderHour?.let { h ->
                        Spacer(Modifier.height(12.dp))
                        MnemoSelect(
                            label = "At",
                            options = Reminder.HOURS,
                            selected = h,
                            display = { "${it.toString().padStart(2, '0')}:00" },
                            onSelect = {
                                reminderHour = it
                                Reminder.hour = it
                            },
                        )
                    }
                }
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
                Eyebrow("Import a Markdown folder")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Obsidian-style notes: a “## Question” and “## Answer” section, “Q:” / “A:” lines, or a block split by a line " +
                        "holding only “?”. Frontmatter tags carry over, subfolders become subdecks, and re-importing skips cards you already have.",
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
                Spacer(Modifier.height(10.dp))
                Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = titleFallback,
                        onCheckedChange = { titleFallback = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = MnemoColors.onCoral, checkedTrackColor = MnemoColors.coral),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Also import notes without sections: title asks, body answers",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MnemoColors.text,
                    )
                }
                Spacer(Modifier.height(12.dp))
                MnemoButton(
                    if (busy) "Importing…" else "Choose folder…",
                    icon = MnemoIcons.Import,
                    enabled = !busy,
                    onClick = pickFolder,
                )
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                AccountSection(graph)
                Spacer(Modifier.height(16.dp))
                UpdatesSection()
                Spacer(Modifier.height(16.dp))
                Eyebrow("Sync with a file (no account)")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Save a sync file to Google Drive (or any shared folder) on one phone, then choose it on the other. " +
                        "Cards both phones have are merged, not duplicated: the latest edit and the latest review win, " +
                        "and review history is combined. " +
                        "Do it in both directions to keep the two in step.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MnemoColors.text2,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MnemoButton("Save sync file…", icon = MnemoIcons.Export, enabled = !busy, onClick = {
                        scope.launch {
                            val path = saveTextFile("mnemo-sync.json", JsonBackup.export(graph))
                            message = if (path != null) "Sync file saved: $path" else "Cancelled"
                        }
                    })
                    MnemoButton("Sync from file…", icon = MnemoIcons.Import, enabled = !busy, onClick = pickSync)
                }
                Spacer(Modifier.height(16.dp))
                Eyebrow("Automatic backups")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Written once a day when the app opens; the newest ${AutoBackup.KEEP} are kept in ${appDataDir()}/${AutoBackup.DIR}" +
                        (if (app.mnemo.platform.Cloud.supported) ", with a copy in Download/Mnemo that outlives the app." else "."),
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
                        verticalAlignment = Alignment.CenterVertically,
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
                Eyebrow("Recently deleted")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Deleted decks are kept for ${DeckTrash.KEEP_DAYS} days with their cards and review history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MnemoColors.text2,
                )
                Spacer(Modifier.height(8.dp))
                if (trash.isEmpty()) {
                    Text("Nothing deleted recently.", style = MaterialTheme.typography.bodySmall, color = MnemoColors.text3)
                }
                trash.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.deckPath, style = MaterialTheme.typography.bodyMedium, color = MnemoColors.text)
                            Text(
                                "${t.cards} cards · deleted ${AutoBackup.label(t.deletedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MnemoColors.text2,
                            )
                        }
                        MnemoButton("Restore", height = 36.dp, enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                val n = runCatching { DeckTrash.restore(graph, t) }
                                message = n.fold({ "Restored “${t.deckPath}” ($it notes)" }, { "Restore failed: ${it.message}" })
                                trash = runCatching { DeckTrash.list(graph.clock.now()) }.getOrDefault(emptyList())
                                busy = false
                                refresh()
                            }
                        })
                    }
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
                    "Mnemo 0.2.0 · FSRS-6 scheduler" +
                        (if (graph.usesCustomParameters) " with your optimised parameters." else " with the default parameters."),
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

/** Google account: the same decks and progress on every phone signed in to it. */
@Composable
private fun AccountSection(graph: app.mnemo.di.AppGraph) {
    if (!app.mnemo.platform.Cloud.supported) return
    val scope = rememberCoroutineScope()
    val status by app.mnemo.domain.sync.CloudSync.status
        .collectAsState()
    var email by remember {
        mutableStateOf(
            app.mnemo.platform.Cloud
                .userEmail(),
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    Eyebrow("Account & sync")
    Spacer(Modifier.height(6.dp))
    Text(
        if (email == null) {
            "Sign in with Google on each phone to keep the same decks and progress on all of them. " +
                "Syncs by itself when you open and leave the app."
        } else {
            "Signed in as $email. " +
                when (val st = status) {
                    is app.mnemo.domain.sync.CloudSync.Status.Syncing -> "Syncing…"
                    is app.mnemo.domain.sync.CloudSync.Status.Done ->
                        "Synced${if (st.changed > 0) " — ${st.changed} notes updated from your other phone" else ""}."
                    is app.mnemo.domain.sync.CloudSync.Status.Failed -> "Last sync failed: ${st.message}"
                    else -> ""
                }
        },
        style = MaterialTheme.typography.bodySmall,
        color = MnemoColors.text2,
    )
    error?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (email == null) {
            MnemoButton("Sign in with Google", onClick = {
                scope.launch {
                    error = null
                    app.mnemo.domain.sync.CloudSync
                        .signIn(graph)
                        .onSuccess { email = it }
                        .onFailure { error = "Sign-in failed: ${it.message}" }
                }
            })
        } else {
            MnemoButton(
                "Sync now",
                enabled = status !is app.mnemo.domain.sync.CloudSync.Status.Syncing,
                onClick = {
                    scope.launch {
                        app.mnemo.domain.sync.CloudSync
                            .sync(graph)
                    }
                },
            )
            MnemoButton("Sign out", onClick = {
                scope.launch {
                    app.mnemo.domain.sync.CloudSync
                        .signOut()
                    email = null
                }
            })
        }
    }
    if (email != null) CloudHistory(graph)
}

/** The daily cloud copies: a way back when a deletion has already synced to every phone. */
@Composable
private fun CloudHistory(graph: app.mnemo.di.AppGraph) {
    val scope = rememberCoroutineScope()
    var days by remember { mutableStateOf<List<String>?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        days =
            runCatching {
                app.mnemo.domain.sync.CloudSync
                    .history()
            }.getOrDefault(emptyList())
    }
    Spacer(Modifier.height(16.dp))
    Eyebrow("Cloud copies")
    Spacer(Modifier.height(6.dp))
    Text(
        "One copy a day, the last ${app.mnemo.domain.sync.CloudSync.HISTORY_KEEP} kept. Restoring brings back what that day had " +
            "and this phone has lost; nothing here is duplicated or undone." + (note?.let { " $it" } ?: ""),
        style = MaterialTheme.typography.bodySmall,
        color = MnemoColors.text2,
    )
    Spacer(Modifier.height(8.dp))
    when (val d = days) {
        null -> Text("Loading…", style = MaterialTheme.typography.bodySmall, color = MnemoColors.text3)
        emptyList<String>() ->
            Text(
                "No cloud copy yet; the first is made at the next sync.",
                style = MaterialTheme.typography.bodySmall,
                color = MnemoColors.text3,
            )
        else ->
            d.forEach { day ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "${day.substring(6, 8)}.${day.substring(4, 6)}.${day.substring(0, 4)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MnemoColors.text,
                        modifier = Modifier.weight(1f),
                    )
                    MnemoButton("Restore", height = 36.dp, enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            note =
                                runCatching {
                                    app.mnemo.domain.sync.CloudSync
                                        .restoreFromHistory(graph, day)
                                }.fold({ "Restored $it notes." }, { "Restore failed: ${it.message}" })
                            busy = false
                        }
                    })
                }
            }
    }
}

@Composable
private fun UpdatesSection() {
    if (!app.mnemo.platform.Updater.supported) return
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf<String?>(null) }
    Eyebrow("Updates")
    Spacer(Modifier.height(6.dp))
    Text(
        "Version ${app.mnemo.platform.Updater.currentVersionName()}. New versions are checked for each time the app opens." +
            (note?.let { " $it" } ?: ""),
        style = MaterialTheme.typography.bodySmall,
        color = MnemoColors.text2,
    )
    Spacer(Modifier.height(8.dp))
    MnemoButton("Check for updates", onClick = {
        scope.launch {
            note = "Checking…"
            val u =
                app.mnemo.platform.Updater
                    .check()
            note =
                if (u == null) {
                    "You have the latest version."
                } else {
                    app.mnemo.platform.Updater
                        .install(u)
                    "Installing ${u.versionName}…"
                }
        }
    })
}
