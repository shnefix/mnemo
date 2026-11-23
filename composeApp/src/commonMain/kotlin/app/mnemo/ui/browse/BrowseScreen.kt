package app.mnemo.ui.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.mnemo.LocalAppGraph
import app.mnemo.domain.model.CardState
import app.mnemo.domain.model.DAY_MS
import app.mnemo.domain.search.SortKey
import app.mnemo.ui.components.EmptyState
import app.mnemo.ui.components.Layers
import app.mnemo.ui.components.MnemoButton
import app.mnemo.ui.components.MnemoIcons
import app.mnemo.ui.components.MnemoTextField
import app.mnemo.ui.decks.NameDialog
import app.mnemo.ui.nav.LocalWindowSize
import app.mnemo.ui.nav.WindowSize
import app.mnemo.ui.theme.MnemoColors
import kotlin.math.abs

/** The filter chips: a label, the search term behind it, and its dot colour (null = no dot). */
private class Filter(
    val label: String,
    val term: String?,
    val dot: @Composable () -> Color?,
)

private val filters =
    listOf(
        Filter("All", null) { null },
        Filter("Due now", "is:due") { MnemoColors.coral },
        Filter("New", "is:new") { Layers.dot(CardState.New) },
        Filter("Learning", "is:learn") { Layers.dot(CardState.Learning) },
        Filter("Review", "is:review") { Layers.dot(CardState.Review) },
        Filter("Suspended", "is:suspended") { null },
        Filter("Leeches", "is:leech") { null },
    )

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowseScreen(
    onEdit: (noteId: Long) -> Unit,
    onAdd: () -> Unit,
    initialQuery: String? = null,
) {
    val graph = LocalAppGraph.current
    val vm: BrowseViewModel = viewModel(key = "browse") { BrowseViewModel(graph) }
    DisposableEffect(vm) {
        vm.setVisible(true)
        onDispose { vm.setVisible(false) }
    }
    val state by vm.state.collectAsState()
    val compact = LocalWindowSize.current == WindowSize.Compact
    var tagDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var moveMenu by remember { mutableStateOf(false) }
    var deckMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    // The search is three parts: typed text, one filter chip and one deck. A deck page opens Browse
    // with `deck:"…"` already set.
    var text by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var deck by rememberSaveable { mutableStateOf(initialQuery?.let(::deckOf)) }
    LaunchedEffect(text, filter, deck) {
        vm.setQuery(listOfNotNull(filter, deck?.let { "deck:\"$it\"" }, text.trim().ifEmpty { null }).joinToString(" "))
    }
    val selecting = state.selected.isNotEmpty()
    val now = graph.clock.now()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = if (compact) 20.dp else 40.dp)
            .padding(top = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Browse", style = MaterialTheme.typography.displaySmall, color = MnemoColors.text)
                Text(
                    if (state.matched == state.totalCards) "${state.totalCards} cards" else "${state.matched} of ${state.totalCards} cards",
                    style = MaterialTheme.typography.labelLarge,
                    color = MnemoColors.text2,
                )
            }
            // Phones add cards with the + in the bottom bar.
            if (!compact) MnemoButton("Add card", onClick = onAdd, icon = MnemoIcons.Plus, primary = true, kbd = "A")
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MnemoTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = "Search fronts, backs and tags",
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MnemoColors.surface2)
                    .clickable(role = Role.Button) { help = true }
                    .semantics { contentDescription = "Search help" },
                contentAlignment = Alignment.Center,
            ) {
                Text("?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MnemoColors.text2)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.forEach { f ->
                FilterChip(f.label, f.dot(), selected = filter == f.term) { filter = f.term }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                MenuChip(deck?.replace("::", " › ") ?: "All decks") { deckMenu = true }
                DropdownMenu(expanded = deckMenu, onDismissRequest = { deckMenu = false }) {
                    DropdownMenuItem(text = { Text("All decks") }, onClick = {
                        deckMenu = false
                        deck = null
                    })
                    state.decks.sortedBy { it.path.lowercase() }.forEach { d ->
                        DropdownMenuItem(text = { Text(d.path.replace("::", " › ")) }, onClick = {
                            deckMenu = false
                            deck = d.path
                        })
                    }
                }
            }
            Box {
                MenuChip("${state.sort.label} ${if (state.ascending) "↑" else "↓"}") { sortMenu = true }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortKey.entries.forEach { key ->
                        DropdownMenuItem(
                            text = { Text(if (key == state.sort) "${key.label} — tap again to flip" else key.label) },
                            onClick = {
                                sortMenu = false
                                vm.sortBy(key)
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        if (selecting) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MnemoColors.surface2)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("${state.selected.size} selected", style = MaterialTheme.typography.labelLarge, color = MnemoColors.text)
                Spacer(Modifier.width(8.dp))
                MnemoButton("Tag", onClick = { tagDialog = true }, height = 36.dp)
                Box {
                    MnemoButton("Move", onClick = { moveMenu = true }, height = 36.dp)
                    DropdownMenu(expanded = moveMenu, onDismissRequest = { moveMenu = false }) {
                        state.decks.forEach { d ->
                            DropdownMenuItem(text = { Text(d.path) }, onClick = {
                                moveMenu = false
                                vm.moveToDeck(d)
                            })
                        }
                    }
                }
                MnemoButton("Suspend", onClick = { vm.setSuspended(true) }, height = 36.dp)
                MnemoButton("Unsuspend", onClick = { vm.setSuspended(false) }, height = 36.dp)
                MnemoButton("Reset", onClick = vm::resetSelected, height = 36.dp)
                MnemoButton("Delete", onClick = { deleteDialog = true }, height = 36.dp, danger = true)
                MnemoButton("All", onClick = vm::selectAll, height = 36.dp)
                MnemoButton("Done", onClick = vm::clearSelection, height = 36.dp)
            }
            Spacer(Modifier.height(10.dp))
        }

        if (!state.loading && state.rows.isEmpty()) {
            EmptyState(
                title = if (state.totalCards == 0) "No cards yet" else "No cards match",
                body = if (state.totalCards == 0) "Add a card or import a deck." else "Try a different search or filter.",
                action = if (state.totalCards == 0) ({ MnemoButton("Add card", onClick = onAdd, primary = true) }) else null,
            )
        } else {
            val listState = rememberLazyListState()
            // Page in the next batch as the end of the loaded rows scrolls into view.
            LaunchedEffect(listState, state.rows.size, state.hasMore) {
                if (!state.hasMore) return@LaunchedEffect
                snapshotFlow {
                    listState.layoutInfo.visibleItemsInfo
                        .lastOrNull()
                        ?.index ?: -1
                }.collect { last -> if (last >= state.rows.size - 20) vm.loadMore() }
            }
            LazyColumn(
                Modifier.fillMaxSize().widthIn(max = 1000.dp),
                state = listState,
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!selecting && state.rows.isNotEmpty()) {
                    item(key = "hint") {
                        Text(
                            "Long-press a card to select several",
                            style = MaterialTheme.typography.bodySmall,
                            color = MnemoColors.text3,
                        )
                    }
                }
                items(state.rows, key = { it.view.card.id }) { row ->
                    val c = row.view.card
                    val selected = c.id in state.selected
                    CardRow(
                        row = row,
                        now = now,
                        selected = selected,
                        selecting = selecting,
                        modifier =
                            Modifier.combinedClickable(
                                role = Role.Button,
                                onClick = { if (selecting) vm.toggle(c.id) else onEdit(c.noteId) },
                                onLongClick = { vm.toggle(c.id) },
                            ),
                    )
                }
                if (state.hasMore) {
                    item(key = "more") {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            MnemoButton("Load ${minOf(BrowseViewModel.PAGE, state.matched - state.rows.size)} more", onClick = vm::loadMore)
                        }
                    }
                }
            }
        }
    }

    if (help) {
        AlertDialog(
            onDismissRequest = { help = false },
            containerColor = MnemoColors.surface2,
            title = { Text("Searching", color = MnemoColors.text) },
            text = {
                Text(
                    "Words match the front, back and other fields.\n\n" +
                        "tag:verb — cards with a tag\n" +
                        "deck:German — a deck and its sub-decks\n" +
                        "is:due  is:new  is:learn  is:review\n" +
                        "is:suspended  is:leech\n" +
                        "\"exact phrase\" — words together\n" +
                        "-word — leave out cards with it",
                    color = MnemoColors.text2,
                )
            },
            confirmButton = { TextButton(onClick = { help = false }) { Text("OK", color = MnemoColors.coral) } },
        )
    }
    if (tagDialog) {
        NameDialog(title = "Add tag to selected", hint = null, initial = "", confirm = "Add", onDismiss = {
            tagDialog = false
        }, onConfirm = {
            vm.addTag(it)
            tagDialog = false
        })
    }
    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            containerColor = MnemoColors.surface2,
            title = { Text("Delete ${state.selected.size} cards?", color = MnemoColors.text) },
            text = { Text("Their notes and review history are deleted too. This cannot be undone.", color = MnemoColors.text2) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSelected()
                    deleteDialog = false
                }) { Text("Delete", color = MnemoColors.danger) }
            },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Cancel", color = MnemoColors.text2) } },
        )
    }
}

/** `deck:"German::Vocab"` → `German::Vocab`; anything else → null. */
private fun deckOf(query: String): String? =
    query
        .trim()
        .removePrefix("deck:")
        .takeIf { it != query.trim() }
        ?.trim('"')

/** One card: front and back, when it's due, its state and deck. */
@Composable
private fun CardRow(
    row: BrowseRow,
    now: Long,
    selected: Boolean,
    selecting: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = row.view.card
    val dueNow = c.due <= now && c.state != CardState.New && !c.suspended
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) MnemoColors.surface3 else MnemoColors.surface2)
            .then(modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) MnemoColors.coral else Color.Transparent)
                    .border(2.dp, if (selected) MnemoColors.coral else MnemoColors.outlineStrong, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        MnemoIcons.Check,
                        contentDescription = "Selected",
                        tint = MnemoColors.onCoral,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.question
                        .lineSequence()
                        .firstOrNull()
                        .orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (c.suspended) MnemoColors.text3 else MnemoColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    dueLabel(c.due, now, c.state),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (dueNow) MnemoColors.coral else MnemoColors.text2,
                )
            }
            val answer =
                row.answer
                    .lineSequence()
                    .firstOrNull()
                    .orEmpty()
            if (answer.isNotBlank()) {
                Text(
                    answer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MnemoColors.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StateChip(c.state, c.suspended, c.lapses)
                Text(
                    " · ${row.view.deckName.replace("::", " › ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MnemoColors.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    dot: Color?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) MnemoColors.text else MnemoColors.surface2)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) MnemoColors.background else MnemoColors.text,
        )
    }
}

@Composable
private fun MenuChip(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, MnemoColors.outlineStrong, RoundedCornerShape(999.dp))
            .clickable(role = Role.DropdownList, onClick = onClick)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MnemoColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp),
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            MnemoIcons.Chevron,
            contentDescription = null,
            tint = MnemoColors.text2,
            modifier = Modifier.size(14.dp).rotate(90f),
        )
    }
}

@Composable
fun StateChip(
    state: CardState,
    suspended: Boolean,
    lapses: Int = 0,
) {
    val (label, color) =
        when {
            suspended -> "Suspended" to MnemoColors.stateSuspended
            lapses >= 8 -> "Leech" to MnemoColors.danger
            state == CardState.New -> "New" to Layers.dot(state)
            state == CardState.Learning -> "Learning" to Layers.dot(state)
            state == CardState.Relearning -> "Relearning" to Layers.dot(state)
            else -> "Review" to Layers.dot(state)
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MnemoColors.text)
    }
}

fun dueLabel(
    due: Long,
    now: Long,
    state: CardState,
): String {
    if (state == CardState.New) return "new"
    val diff = due - now
    val days = diff / DAY_MS
    return when {
        diff <= 0 && abs(diff) < DAY_MS -> "now"
        diff <= 0 -> "${abs(days)}d ago"
        diff < DAY_MS -> "${(diff / 60_000).coerceAtLeast(1)}m"
        days < 30 -> "${days}d"
        days < 365 -> "${days / 30}mo"
        else -> "${days / 365}y"
    }
}
