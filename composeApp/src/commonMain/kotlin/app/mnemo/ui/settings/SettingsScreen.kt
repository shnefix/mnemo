package app.mnemo.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mnemo.LocalAppGraph
import app.mnemo.data.io.ApkgExporter
import app.mnemo.data.io.ApkgImporter
import app.mnemo.data.io.AutoBackup
import app.mnemo.data.io.CsvExporter
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
import app.mnemo.platform.saveBinaryFile
import app.mnemo.platform.saveTextFile
import app.mnemo.ui.components.Eyebrow
import app.mnemo.ui.components.Layers
import app.mnemo.ui.components.MnemoButton
import app.mnemo.ui.components.MnemoIcons
import app.mnemo.ui.components.MnemoSelect
import app.mnemo.ui.components.MnemoTextField
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
fun SettingsScreen(onNoteTypes: () -> Unit = {}) {
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
    var exportDeck by remember { mutableStateOf<Deck?>(null) }
    var exportScheduling by remember { mutableStateOf(true) }
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

    var open by rememberSaveable { mutableStateOf<String?>(null) }

    @Composable
    fun row(
        id: String,
        title: String,
        hint: String,
        icon: ImageVector,
        tint: Color,
        value: String = "",
        first: Boolean = false,
        content: @Composable ColumnScope.() -> Unit,
    ) = SettingRow(title, hint, icon, tint, value, first, open == id, { open = if (open == id) null else id }, content)

    val hh: (Int) -> String = { "${it.toString().padStart(2, '0')}:00" }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (compact) 20.dp else 40.dp, vertical = 24.dp),
    ) {
        ScreenHeader(title = "Settings", subtitle = counts)
        message?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MnemoColors.success, modifier = Modifier.widthIn(max = 720.dp))
        }
        Spacer(Modifier.height(18.dp))
        Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SettingsGroup("Look") {
                row(
                    "theme",
                    "Theme",
                    "OLED dark, light or follow the phone",
                    MnemoIcons.Settings,
                    MnemoColors.lavender,
                    listOf("OLED dark", "Light", "System")[theme.mode.ordinal],
                    first = true,
                ) {
                    Segmented(
                        options = listOf("OLED dark", "Light", "System"),
                        selected = theme.mode.ordinal,
                        onSelect = { theme.set(ThemeMode.entries[it]) },
                    )
                }
                row(
                    "text",
                    "Text size",
                    "Bigger text on cards and lists",
                    MnemoIcons.Edit,
                    MnemoColors.lavender,
                    ThemeState.TEXT_SIZE_LABELS[theme.textSize],
                ) {
                    Segmented(
                        options = ThemeState.TEXT_SIZE_LABELS,
                        selected = theme.textSize,
                        onSelect = { theme.changeTextSize(it) },
                    )
                }
            }
            SettingsGroup("Studying") {
                row(
                    "day",
                    "New day starts at",
                    "Late sessions still count for the day before",
                    MnemoIcons.Clock,
                    MnemoColors.lime,
                    hh(dayStart),
                    first = true,
                ) {
                    Hint(
                        "Reviews before this hour count toward the previous day, so a late session does not split your streak or reset the daily limits at midnight.",
                    )
                    MnemoSelect(
                        label = "Next day starts at",
                        options = (0..12).toList(),
                        selected = dayStart,
                        display = hh,
                        onSelect = {
                            dayStart = it
                            DayStart.hour = it
                        },
                    )
                }
                row(
                    "reminder",
                    "Daily reminder",
                    "One notification when cards are due",
                    MnemoIcons.Flame,
                    MnemoColors.lime,
                    if (!ReminderScheduler.supported) "Android only" else reminderHour?.let(hh) ?: "Off",
                ) {
                    Hint("Nothing is sent on a day with nothing due.")
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
                                colors =
                                    SwitchDefaults.colors(
                                        checkedThumbColor = MnemoColors.onCoral,
                                        checkedTrackColor = MnemoColors.coral,
                                    ),
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
                                display = hh,
                                onSelect = {
                                    reminderHour = it
                                    Reminder.hour = it
                                },
                            )
                        }
                    }
                }
                row("ai", "AI", "Card help and grading", MnemoIcons.Bulb, MnemoColors.lime, "Not set up") {
                    Hint(
                        "No AI key is configured. Card generation, answer grading, the tutor and concept suggestions stay optional; everything else works offline.",
                    )
                }
            }
            SettingsGroup("Sync") {
                if (app.mnemo.platform.Cloud.supported) {
                    row("account", "Account & sync", "Keep your phones in step", MnemoIcons.Export, MnemoColors.sky, first = true) {
                        AccountSection(graph)
                    }
                }
                row(
                    "syncfile",
                    "Sync with a file",
                    "No account needed",
                    MnemoIcons.Copy,
                    MnemoColors.sky,
                    first = !app.mnemo.platform.Cloud.supported,
                ) {
                    Hint(
                        "Save a sync file to Google Drive (or any shared folder) on one phone, then choose it on the other. " +
                            "Cards both phones have are merged, not duplicated: the latest edit and the latest review win, " +
                            "and review history is combined. Do it in both directions to keep the two in step.",
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MnemoButton("Save sync file…", icon = MnemoIcons.Export, enabled = !busy, onClick = {
                            scope.launch {
                                val path = saveTextFile("mnemo-sync.json", JsonBackup.export(graph))
                                message = if (path != null) "Sync file saved: $path" else "Cancelled"
                            }
                        })
                        MnemoButton("Sync from file…", icon = MnemoIcons.Import, enabled = !busy, onClick = pickSync)
                    }
                }
            }
            SettingsGroup("Your data") {
                row("import", "Import", "Anki .apkg, CSV or a Markdown folder", MnemoIcons.Import, Layers.amber, first = true) {
                    Eyebrow("Anki deck (.apkg)")
                    Spacer(Modifier.height(4.dp))
                    Hint(
                        "Shared decks from AnkiWeb or your own export. Decks, notes, cards, scheduling, review history and media come across. Re-importing the same deck skips notes you already have.",
                    )
                    MnemoButton(
                        if (busy) "Importing…" else "Choose .apkg file…",
                        icon = MnemoIcons.Import,
                        primary = true,
                        enabled = !busy,
                        onClick = pickApkg,
                    )
                    Spacer(Modifier.height(18.dp))
                    Eyebrow("CSV")
                    Spacer(Modifier.height(4.dp))
                    Hint(
                        "Columns: front, back, tags (optional). Comma, semicolon or tab separated. A front with {{c1::…}} becomes a cloze note.",
                    )
                    MnemoSelect(
                        label = "Into deck",
                        options = decks,
                        selected = importDeck,
                        display = { it.path },
                        onSelect = { importDeck = it },
                    )
                    Spacer(Modifier.height(10.dp))
                    MnemoButton("Choose CSV file…", icon = MnemoIcons.Import, onClick = pickCsv)
                    Spacer(Modifier.height(10.dp))
                    MnemoTextField(value = pasted, onValueChange = {
                        pasted = it
                    }, label = "…or paste CSV text", minLines = 4, placeholder = "der Hund,the dog,a1 noun")
                    Spacer(Modifier.height(8.dp))
                    MnemoButton("Import pasted text", enabled = pasted.isNotBlank(), onClick = { runCsv(pasted) })
                    Spacer(Modifier.height(18.dp))
                    Eyebrow("Markdown folder")
                    Spacer(Modifier.height(4.dp))
                    Hint(
                        "Obsidian-style notes: a “## Question” and “## Answer” section, “Q:” / “A:” lines, or a block split by a line " +
                            "holding only “?”. Frontmatter tags carry over, subfolders become subdecks, and re-importing skips cards you already have.",
                    )
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
                    Spacer(Modifier.height(10.dp))
                    MnemoButton(
                        if (busy) "Importing…" else "Choose folder…",
                        icon = MnemoIcons.Import,
                        enabled = !busy,
                        onClick = pickFolder,
                    )
                }
                row("export", "Export", "Anki .apkg or CSV", MnemoIcons.Export, Layers.amber) {
                    Hint(
                        "An .apkg opens in Anki (desktop, AnkiDroid, AnkiMobile): decks, note types, cards, media and, if you like, " +
                            "scheduling, flags and review history. CSV holds front, back and tags.",
                    )
                    MnemoSelect(
                        label = "Deck",
                        options = listOf<Deck?>(null) + decks,
                        selected = exportDeck,
                        display = { it?.path ?: "All decks" },
                        onSelect = { exportDeck = it },
                        placeholder = "All decks",
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = exportScheduling,
                            onCheckedChange = { exportScheduling = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = MnemoColors.onCoral, checkedTrackColor = MnemoColors.coral),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Include scheduling and review history", style = MaterialTheme.typography.bodyMedium, color = MnemoColors.text)
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MnemoButton("Export .apkg", icon = MnemoIcons.Export, primary = true, enabled = !busy, onClick = {
                            scope.launch {
                                busy = true
                                message =
                                    runCatching {
                                        val r = ApkgExporter.export(graph, exportDeck?.id, exportScheduling)
                                        val name = (exportDeck?.path?.replace("::", " - ") ?: "Mnemo") + ".apkg"
                                        val path = saveBinaryFile(name, r.bytes, "application/apkg")
                                        if (path == null) {
                                            "Export cancelled"
                                        } else {
                                            "Exported ${r.notes} notes, ${r.cards} cards, ${r.reviews} reviews and ${r.media} media files to $path"
                                        }
                                    }.getOrElse { "Export failed: ${it.message}" }
                                busy = false
                            }
                        })
                        MnemoButton("Export CSV", icon = MnemoIcons.Export, enabled = !busy, onClick = {
                            scope.launch {
                                val r = CsvExporter.export(graph, exportDeck?.id)
                                val name = (exportDeck?.path?.replace("::", " - ") ?: "Mnemo") + ".csv"
                                val path = saveTextFile(name, r.text)
                                message =
                                    if (path == null) {
                                        "Export cancelled"
                                    } else {
                                        "Exported ${r.notes} notes to $path" +
                                            if (r.skipped > 0) " · ${r.skipped} image-occlusion notes left out" else ""
                                    }
                            }
                        })
                    }
                }
                row("notetypes", "Note types", "Your own fields and card templates", MnemoIcons.Edit, Layers.amber) {
                    Hint("Make note types with the fields you need, and card templates that decide what each card asks.")
                    MnemoButton("Manage note types", icon = MnemoIcons.Edit, onClick = onNoteTypes)
                }
                row("backup", "Backup file", "Save or restore everything as one file", MnemoIcons.Export, Layers.amber) {
                    Hint("A JSON backup holds decks, notes, cards, review history and concepts. Restoring merges into the current data.")
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
                }
                row(
                    "auto",
                    "Automatic backups",
                    "One a day when the app opens",
                    MnemoIcons.Clock,
                    Layers.amber,
                    if (backups.isEmpty()) "None yet" else "${backups.size}",
                ) {
                    Hint(
                        "The newest ${AutoBackup.KEEP} are kept in ${appDataDir()}/${AutoBackup.DIR}" +
                            (if (app.mnemo.platform.Cloud.supported) ", with a copy in Download/Mnemo that outlives the app." else "."),
                    )
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
                row(
                    "trash",
                    "Recently deleted",
                    "Decks are kept for ${DeckTrash.KEEP_DAYS} days",
                    MnemoIcons.Trash,
                    Layers.amber,
                    if (trash.isEmpty()) "Empty" else "${trash.size}",
                ) {
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
            }
            SettingsGroup("About") {
                row(
                    "about",
                    "Mnemo",
                    "FSRS-6 scheduler" + if (graph.usesCustomParameters) " · your own weights" else " · default weights",
                    MnemoIcons.Bulb,
                    MnemoColors.coral,
                    if (app.mnemo.platform.Updater.supported) {
                        app.mnemo.platform.Updater
                            .currentVersionName()
                    } else {
                        ""
                    },
                    first = true,
                ) {
                    UpdatesSection()
                    Spacer(Modifier.height(8.dp))
                    Text("Data: ${appDataDir()}", style = MaterialTheme.typography.bodySmall, color = MnemoColors.text3)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** A titled group of rows on one rounded surface. */
@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Box(Modifier.padding(start = 4.dp)) { Eyebrow(title) }
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MnemoColors.surface2),
            content = content,
        )
    }
}

/** One setting: icon, name, one-line hint and current value; tapping it opens its controls underneath. */
@Composable
private fun SettingRow(
    title: String,
    hint: String,
    icon: ImageVector,
    tint: Color,
    value: String,
    first: Boolean,
    open: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!first) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 60.dp)
                .height(1.dp)
                .background(MnemoColors.outline),
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(tint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MnemoColors.onLime, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MnemoColors.text)
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MnemoColors.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(value, style = MaterialTheme.typography.labelLarge, color = MnemoColors.text2, maxLines = 1)
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            MnemoIcons.Chevron,
            contentDescription = if (open) "Close" else "Open",
            tint = MnemoColors.text3,
            modifier = Modifier.size(16.dp).rotate(if (open) 90f else 0f),
        )
    }
    if (open) {
        Column(Modifier.fillMaxWidth().padding(start = 60.dp, end = 16.dp, bottom = 16.dp), content = content)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MnemoColors.text2)
    Spacer(Modifier.height(10.dp))
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
