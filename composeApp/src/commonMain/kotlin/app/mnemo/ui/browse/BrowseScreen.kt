package app.mnemo.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.mnemo.LocalAppGraph
import app.mnemo.domain.model.CardState
import app.mnemo.domain.model.DAY_MS
import app.mnemo.domain.search.SortKey
import app.mnemo.ui.components.Chip
import app.mnemo.ui.components.EmptyState
import app.mnemo.ui.components.MnemoButton
import app.mnemo.ui.components.MnemoIcons
import app.mnemo.ui.components.MnemoTextField
import app.mnemo.ui.components.ScreenHeader
import app.mnemo.ui.decks.NameDialog
import app.mnemo.ui.nav.LocalWindowSize
import app.mnemo.ui.nav.WindowSize
import app.mnemo.ui.theme.MnemoColors
import kotlin.math.abs

@Composable
fun BrowseScreen(
    onEdit: (noteId: Long) -> Unit,
    onAdd: () -> Unit,
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
    val now = graph.clock.now()

    Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 20.dp else 40.dp, vertical = 28.dp)) {
        ScreenHeader(title = "Browse", subtitle = "${state.matched} of ${state.totalCards} cards") {
            MnemoButton("Add card", onClick = onAdd, icon = MnemoIcons.Plus, primary = true, kbd = if (compact) null else "A")
        }
        Spacer(Modifier.height(16.dp))
        MnemoTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            singleLine = true,
            placeholder = "Search: tag:verb deck:German is:due is:leech \"exact phrase\" -excluded",
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("is:due", "is:new", "is:learn", "is:review", "is:suspended", "is:leech").forEach { q ->
                Chip(q, selected = state.query.trim() == q, onClick = { vm.setQuery(if (state.query.trim() == q) "" else q) })
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Sort",
                style = MaterialTheme.typography.labelMedium,
                color = MnemoColors.text3,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            SortKey.entries.forEach { key ->
                val on = state.sort == key
                Chip(
                    if (on) "${key.label} ${if (state.ascending) "↑" else "↓"}" else key.label,
                    selected = on,
                    onClick = { vm.sortBy(key) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (state.selected.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
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
                Spacer(Modifier.weight(1f))
                MnemoButton("All", onClick = vm::selectAll, height = 36.dp)
                MnemoButton("None", onClick = vm::clearSelection, height = 36.dp)
            }
            Spacer(Modifier.height(10.dp))
        }

        if (!state.loading && state.rows.isEmpty()) {
            EmptyState(
                title = if (state.totalCards == 0) "No cards yet" else "No cards match",
                body = if (state.totalCards == 0) "Add a card or import a CSV from Settings." else "Try a different search.",
                action = if (state.totalCards == 0) ({ MnemoButton("Add card", onClick = onAdd, primary = true) }) else null,
            )
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Spacer(Modifier.width(36.dp))
                HeaderCell("Question", Modifier.weight(2f))
                if (!compact) HeaderCell("Answer", Modifier.weight(2f))
                if (!compact) HeaderCell("Deck", Modifier.weight(1f))
                HeaderCell("State", Modifier.width(84.dp))
                HeaderCell("Due", Modifier.width(72.dp))
            }
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
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.rows, key = { it.view.card.id }) { row ->
                    val c = row.view.card
                    val selected = c.id in state.selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) MnemoColors.surface2 else MnemoColors.surface)
                            .clickable(role = Role.Button) { onEdit(c.noteId) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { vm.toggle(c.id) },
                            colors =
                                CheckboxDefaults.colors(
                                    checkedColor = MnemoColors.coral,
                                    uncheckedColor = MnemoColors.outlineStrong,
                                    checkmarkColor = MnemoColors.onCoral,
                                ),
                            modifier = Modifier.size(36.dp),
                        )
                        Cell(
                            row.question
                                .lineSequence()
                                .firstOrNull()
                                .orEmpty(),
                            Modifier.weight(2f),
                            dim = c.suspended,
                        )
                        if (!compact) {
                            Cell(
                                row.answer
                                    .lineSequence()
                                    .firstOrNull()
                                    .orEmpty(),
                                Modifier.weight(2f),
                                dim = true,
                            )
                        }
                        if (!compact) Cell(row.view.deckName, Modifier.weight(1f), dim = true)
                        Box(Modifier.width(84.dp)) { StateChip(c.state, c.suspended, c.lapses) }
                        Text(
                            dueLabel(c.due, now, c.state),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (c.due <= now && c.state != CardState.New) MnemoColors.stateReview else MnemoColors.text2,
                            modifier = Modifier.width(72.dp),
                        )
                    }
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

@Composable
private fun HeaderCell(
    text: String,
    modifier: Modifier,
) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MnemoColors.text3, modifier = modifier)
}

@Composable
private fun Cell(
    text: String,
    modifier: Modifier,
    dim: Boolean = false,
) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (dim) MnemoColors.text2 else MnemoColors.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(end = 8.dp),
    )
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
            state == CardState.New -> "New" to MnemoColors.stateNew
            state == CardState.Learning -> "Learning" to MnemoColors.stateLearning
            state == CardState.Relearning -> "Relearn" to MnemoColors.stateLearning
            else -> "Review" to MnemoColors.stateReview
        }
    Text(label, style = MaterialTheme.typography.labelMedium, color = color)
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
