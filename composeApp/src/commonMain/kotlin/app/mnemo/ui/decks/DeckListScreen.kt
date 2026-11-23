package app.mnemo.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.mnemo.LocalAppGraph
import app.mnemo.data.io.ApkgImporter
import app.mnemo.platform.Prefs
import app.mnemo.platform.rememberFilePicker
import app.mnemo.ui.components.EmptyState
import app.mnemo.ui.components.IconActionButton
import app.mnemo.ui.components.LayerBar
import app.mnemo.ui.components.Layers
import app.mnemo.ui.components.MasteryRing
import app.mnemo.ui.components.MnemoButton
import app.mnemo.ui.components.MnemoIcons
import app.mnemo.ui.components.MnemoTextField
import app.mnemo.ui.components.dueSummary
import app.mnemo.ui.nav.LocalWindowSize
import app.mnemo.ui.nav.WindowSize
import app.mnemo.ui.theme.MnemoColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How the deck list is ordered. */
private enum class DeckSort(
    val label: String,
) {
    Due("Due first"),
    Name("A–Z"),
    Weakest("Weakest first"),
}

@Composable
fun DeckListScreen(
    onReview: (Long) -> Unit,
    onAddCards: (Long) -> Unit,
    onSettings: (Long) -> Unit,
    onBrowse: () -> Unit = {},
    onAppSettings: () -> Unit = {},
    onOpen: (Long) -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val vm: DecksViewModel = viewModel(key = "decks") { DecksViewModel(graph) }
    DisposableEffect(vm) {
        vm.setVisible(true)
        onDispose { vm.setVisible(false) }
    }
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    var showCreate by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(loadSort()) }
    val compact = LocalWindowSize.current == WindowSize.Compact
    // Cards start folded (the English deck alone has ~60 packs); what you open is remembered.
    var expanded by remember { mutableStateOf(loadExpanded()) }

    fun toggle(id: Long) {
        expanded = if (id in expanded) expanded - id else expanded + id
        saveExpanded(expanded)
    }

    val pickApkg =
        rememberFilePicker(listOf("apkg", "colpkg")) { file ->
            if (file == null) return@rememberFilePicker
            importing = true
            message = "Importing ${file.name}…"
            scope.launch {
                message =
                    runCatching { ApkgImporter.import(graph, file.bytes) }.fold(
                        { r -> "Imported ${r.notes} notes and ${r.cards} cards into ${r.decks} decks" },
                        { "Import failed: ${it.message}" },
                    )
                importing = false
                vm.refresh()
            }
        }

    // Numbered packs show as tiles inside their parent instead of ~100 rows of their own.
    val packs = remember(state.rows) { packsByParent(state.rows) }
    val packIds = remember(packs) { packs.values.flatten().mapTo(HashSet()) { it.deck.id } }
    val children = remember(state.rows, packIds) { state.rows.filter { it.deck.id !in packIds }.groupBy { it.deck.parentId } }
    val top =
        remember(state.rows, sort) {
            val roots = state.rows.filter { it.deck.parentId == null }
            when (sort) {
                DeckSort.Due -> roots.sortedWith(compareByDescending<DeckRow> { it.due.total }.thenBy { it.deck.name.lowercase() })
                DeckSort.Name -> roots.sortedBy { it.deck.name.lowercase() }
                // Not-started decks last: they are new, not weak.
                DeckSort.Weakest -> roots.sortedBy { if (it.mastery > 0) it.mastery else 2.0 }
            }
        }
    val matches =
        remember(state.rows, query) {
            val q = query.trim()
            if (q.isEmpty()) emptyList() else state.rows.filter { it.deck.path.contains(q, ignoreCase = true) }
        }

    val deleted by vm.deleted.collectAsState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 900.dp),
            contentPadding = PaddingValues(horizontal = if (compact) 20.dp else 40.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Decks", style = MaterialTheme.typography.displaySmall, color = MnemoColors.text)
                        val subs = state.rows.size - top.size
                        Text(
                            "${top.size} decks" + (if (subs > 0) " · $subs sub-decks" else "") +
                                " · ${top.sumOf { it.cardCount }} cards",
                            style = MaterialTheme.typography.labelLarge,
                            color = MnemoColors.text2,
                        )
                    }
                    IconActionButton(
                        if (searching) MnemoIcons.Close else MnemoIcons.Search,
                        if (searching) "Close search" else "Search decks",
                        onClick = {
                            searching = !searching
                            if (!searching) query = ""
                        },
                        tint = MnemoColors.text,
                    )
                    Spacer(Modifier.width(6.dp))
                    IconActionButton(MnemoIcons.Settings, "Settings", onClick = onAppSettings, tint = MnemoColors.text)
                }
                Spacer(Modifier.height(6.dp))
            }
            if (searching) {
                item(key = "search") {
                    MnemoTextField(value = query, onValueChange = { query = it }, singleLine = true, placeholder = "Find a deck")
                }
                items(matches, key = { "m${it.deck.id}" }) { row ->
                    SubDeckRow(
                        name = row.deck.path.replace("::", " › "),
                        due = row.due.total,
                        indent = 0,
                        onOpen = { onOpen(row.deck.id) },
                        onStudy = { onReview(row.deck.id) },
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(MnemoColors.surface2).padding(start = 14.dp),
                    )
                }
                if (query.isNotBlank() && matches.isEmpty()) {
                    item(key = "nomatch") {
                        Text("No deck matches “$query”.", style = MaterialTheme.typography.bodyMedium, color = MnemoColors.text2)
                    }
                }
            } else {
                item(key = "sort") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DeckSort.entries.forEach { s ->
                            SortChip(s.label, selected = s == sort) {
                                sort = s
                                Prefs.set(Prefs.DECKS_SORT, s.name)
                            }
                        }
                    }
                }
                if (!state.loading && state.rows.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            title = "No decks yet",
                            body = "Create one, then add cards or import an Anki deck.",
                            action = { MnemoButton("Create a deck", onClick = { showCreate = true }, primary = true) },
                        )
                    }
                }
                items(top, key = { it.deck.id }) { row ->
                    DeckCard(
                        row = row,
                        children = children[row.deck.id].orEmpty(),
                        packs = packs[row.deck.id].orEmpty(),
                        subCount = children[row.deck.id].orEmpty().size + packs[row.deck.id].orEmpty().size,
                        open = row.deck.id in expanded,
                        onToggle = { toggle(row.deck.id) },
                        onOpen = onOpen,
                        onReview = onReview,
                    )
                }
                item(key = "add") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DashedTile("New deck", MnemoIcons.Plus, Modifier.weight(1f)) { showCreate = true }
                        DashedTile(if (importing) "Importing…" else "Import .apkg", MnemoIcons.Import, Modifier.weight(1f)) {
                            if (!importing) pickApkg()
                        }
                    }
                    message?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MnemoColors.success)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
        deleted?.let { entry ->
            // Undo for a few seconds; after that the deck is still in Settings › Recently deleted.
            LaunchedEffect(entry) {
                delay(UNDO_MS)
                vm.dismissUndo()
            }
            UndoBar(
                text = "Deleted “${entry.deckPath}”",
                onUndo = vm::undoDelete,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }

    if (showCreate) {
        NameDialog(
            title = "New deck",
            hint = "Use :: for nested decks, e.g. German::Vocab::A1",
            initial = "",
            confirm = "Create",
            onDismiss = { showCreate = false },
            onConfirm = {
                vm.create(it)
                showCreate = false
            },
        )
    }
}

/** One top-level deck: mastery ring, what's due, a start button, and its sub-decks when opened. */
@Composable
private fun DeckCard(
    row: DeckRow,
    children: List<DeckRow>,
    packs: List<DeckRow>,
    subCount: Int,
    open: Boolean,
    onToggle: () -> Unit,
    onOpen: (Long) -> Unit,
    onReview: (Long) -> Unit,
) {
    val hasDue = row.due.total > 0
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (hasDue) MnemoColors.surface2 else MnemoColors.surface)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button) { onOpen(row.deck.id) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MasteryRing(row.mastery, Layers.deckColor(row.deck.id))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        row.deck.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (hasDue) MnemoColors.text else MnemoColors.text2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${row.cardCount} cards · " +
                            if (row.mastery > 0) "${(row.mastery * 100).toInt()}% mastered" else "not started",
                        style = MaterialTheme.typography.bodySmall,
                        color = MnemoColors.text2,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (hasDue) {
                StudyButton(row.due.total, "Study ${row.due.total} cards in ${row.deck.name}") { onReview(row.deck.id) }
            } else {
                CaughtUp()
            }
        }
        Spacer(Modifier.height(12.dp))
        LayerBar(row.due)
        Row(Modifier.heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                dueSummary(row.due),
                style = MaterialTheme.typography.labelMedium,
                color = MnemoColors.text2,
                modifier = Modifier.weight(1f),
            )
            if (subCount > 0) {
                Row(
                    Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(role = Role.Button, onClick = onToggle)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (subCount == 1) "1 sub-deck" else "$subCount sub-decks",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MnemoColors.text,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        MnemoIcons.Chevron,
                        contentDescription = if (open) "Hide sub-decks" else "Show sub-decks",
                        tint = MnemoColors.text,
                        modifier = Modifier.size(14.dp).rotate(if (open) 270f else 90f),
                    )
                }
            }
        }
        if (open && subCount > 0) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(MnemoColors.outline))
            Spacer(Modifier.height(4.dp))
            children.forEach { c ->
                SubDeckRow(
                    name = c.deck.name,
                    due = c.due.total,
                    indent = 0,
                    dot = Layers.deckColor(row.deck.id),
                    onOpen = { onOpen(c.deck.id) },
                    onStudy = { onReview(c.deck.id) },
                )
            }
            if (packs.isNotEmpty()) PackGrid(packs, depth = 0, onReview = { onReview(it.deck.id) })
        }
    }
}

/** A sub-deck line: name (opens its page), due count, and a start arrow. */
@Composable
internal fun SubDeckRow(
    name: String,
    due: Int,
    indent: Int,
    onOpen: () -> Unit,
    onStudy: () -> Unit,
    modifier: Modifier = Modifier,
    dot: androidx.compose.ui.graphics.Color? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = (indent * 18).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            color = MnemoColors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onOpen)
                    .padding(vertical = 12.dp),
        )
        Text(
            if (due > 0) "$due due" else "none due",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (due > 0) MnemoColors.coral else MnemoColors.text3,
        )
        IconActionButton(
            MnemoIcons.ArrowRight,
            "Study $name",
            onClick = onStudy,
            background = androidx.compose.ui.graphics.Color.Transparent,
            tint = if (due > 0) MnemoColors.text else MnemoColors.text3,
        )
    }
}

/** The coral start button with the number of cards due. */
@Composable
internal fun StudyButton(
    count: Int,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MnemoColors.coral)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MnemoColors.onCoral)
        Spacer(Modifier.width(8.dp))
        Icon(MnemoIcons.ArrowRight, contentDescription = null, tint = MnemoColors.onCoral, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun CaughtUp() {
    Row(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MnemoColors.surface3)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            MnemoIcons.Check,
            contentDescription = null,
            tint = if (MnemoColors.isDark) MnemoColors.lime else MnemoColors.success,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "All caught up",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (MnemoColors.isDark) MnemoColors.lime else MnemoColors.success,
        )
    }
}

@Composable
private fun SortChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) MnemoColors.text else MnemoColors.surface2)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) MnemoColors.background else MnemoColors.text2,
        )
    }
}

@Composable
private fun DashedTile(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val dash = MnemoColors.outlineStrong
    Row(
        modifier
            .height(64.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .drawBehind {
                drawRoundRect(
                    color = dash,
                    cornerRadius = CornerRadius(22.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MnemoColors.text2, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MnemoColors.text2)
    }
}

@Composable
fun NameDialog(
    title: String,
    hint: String?,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MnemoColors.surface2,
        title = { Text(title, color = MnemoColors.text) },
        text = {
            Column {
                MnemoTextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = "Name", onSubmit = {
                    if (name.isNotBlank()) onConfirm(name.trim())
                })
                if (hint != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = MnemoColors.text2)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }) { Text(confirm, color = MnemoColors.coral) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MnemoColors.text2) } },
    )
}

private fun loadSort(): DeckSort = DeckSort.entries.firstOrNull { it.name == Prefs.get(Prefs.DECKS_SORT) } ?: DeckSort.Due

private fun loadExpanded(): Set<Long> =
    Prefs
        .get(Prefs.DECKS_EXPANDED)
        .orEmpty()
        .split(',')
        .mapNotNull { it.trim().toLongOrNull() }
        .toSet()

private fun saveExpanded(ids: Set<Long>) = Prefs.set(Prefs.DECKS_EXPANDED, ids.sorted().joinToString(","))

private const val UNDO_MS = 8_000L

@Composable
private fun UndoBar(
    text: String,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MnemoColors.text)
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MnemoColors.background,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onUndo) { Text("Undo", color = MnemoColors.coral) }
    }
}

/** A section's packs as a grid of numbered tiles; tapping one studies that pack. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PackGrid(
    packs: List<DeckRow>,
    depth: Int,
    onReview: (DeckRow) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(start = (depth * 20).dp, top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        packs.forEach { p ->
            val due = p.due.total
            Column(
                modifier =
                    Modifier
                        .width(72.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (due > 0) MnemoColors.lime else MnemoColors.surface3)
                        .clickable(role = Role.Button) { onReview(p) }
                        .semantics { contentDescription = "${p.deck.name}: ${p.cardCount} cards, $due due" }
                        .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    packNumber(p.deck.name),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (due > 0) MnemoColors.onLime else MnemoColors.text,
                )
                Text(
                    if (due > 0) "$due due" else "${(p.mastery * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (due > 0) MnemoColors.onLime else MnemoColors.text2,
                )
            }
        }
    }
}
