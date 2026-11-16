package app.mnemo.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.mnemo.LocalAppGraph
import app.mnemo.ui.components.DueBar
import app.mnemo.ui.components.EmptyState
import app.mnemo.ui.components.IconActionButton
import app.mnemo.ui.components.LegendDot
import app.mnemo.ui.components.MasteryBar
import app.mnemo.ui.components.MnemoButton
import app.mnemo.ui.components.MnemoIcons
import app.mnemo.ui.components.MnemoTextField
import app.mnemo.ui.components.Panel
import app.mnemo.ui.components.ScreenHeader
import app.mnemo.ui.nav.LocalWindowSize
import app.mnemo.ui.nav.WindowSize
import app.mnemo.ui.theme.MnemoColors

@Composable
fun DeckListScreen(
    onReview: (Long) -> Unit,
    onAddCards: (Long) -> Unit,
    onSettings: (Long) -> Unit,
    onBrowse: () -> Unit = {},
    onAppSettings: () -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val vm: DecksViewModel = viewModel(key = "decks") { DecksViewModel(graph) }
    val state by vm.state.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<DeckRow?>(null) }
    var deleting by remember { mutableStateOf<DeckRow?>(null) }
    val compact = LocalWindowSize.current == WindowSize.Compact

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = if (compact) 20.dp else 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenHeader(title = "Decks", subtitle = "${state.rows.size} decks") {
                MnemoButton("New deck", onClick = { showCreate = true }, icon = MnemoIcons.Plus, primary = true)
                // Phones have no sidebar, so Browse and Settings are reached from here.
                if (compact) {
                    MnemoButton("Browse", onClick = onBrowse, icon = MnemoIcons.Search)
                    MnemoButton("Settings", onClick = onAppSettings, icon = MnemoIcons.Settings)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (!state.loading && state.rows.isEmpty()) {
            item {
                EmptyState(
                    title = "No decks yet",
                    body = "Create one, then add cards or import a CSV.",
                    action = { MnemoButton("Create a deck", onClick = { showCreate = true }, primary = true) },
                )
            }
        }
        items(state.rows, key = { it.deck.id }) { row ->
            DeckRowItem(
                row = row,
                onReview = { onReview(row.deck.id) },
                onAddCards = { onAddCards(row.deck.id) },
                onSettings = { onSettings(row.deck.id) },
                onRename = { renaming = row },
                onDelete = { deleting = row },
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
    renaming?.let { row ->
        NameDialog(
            title = "Rename deck",
            hint = null,
            initial = row.deck.name,
            confirm = "Rename",
            onDismiss = { renaming = null },
            onConfirm = {
                vm.rename(row.deck.id, it)
                renaming = null
            },
        )
    }
    deleting?.let { row ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = MnemoColors.surface2,
            title = { Text("Delete “${row.deck.path}”?", color = MnemoColors.text) },
            text = {
                Text(
                    "This deletes ${row.cardCount} cards and their review history. It cannot be undone.",
                    color = MnemoColors.text2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(row.deck.id)
                    deleting = null
                }) { Text("Delete", color = MnemoColors.danger) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel", color = MnemoColors.text2) } },
        )
    }
}

@Composable
private fun DeckRowItem(
    row: DeckRow,
    onReview: () -> Unit,
    onAddCards: () -> Unit,
    onSettings: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val tile = listOf(MnemoColors.surface, MnemoColors.surface, MnemoColors.surface)[row.depth.coerceAtMost(2)]
    Panel(
        modifier = Modifier.fillMaxWidth().padding(start = (row.depth * 20).dp),
        color = tile,
        padding = PaddingValues(18.dp),
        onClick = onReview,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    row.deck.name,
                    style =
                        if (LocalWindowSize.current ==
                            WindowSize.Compact
                        ) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.titleLarge
                        },
                    color = MnemoColors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${row.cardCount} cards · ${row.due.total} due",
                    style = MaterialTheme.typography.bodySmall,
                    color = MnemoColors.text2,
                )
            }
            if (row.due.total > 0) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(MnemoColors.lime)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("${row.due.total}", style = MaterialTheme.typography.labelLarge, color = MnemoColors.onLime)
                }
                Spacer(Modifier.width(8.dp))
            }
            Box {
                IconActionButton(MnemoIcons.More, "Deck menu", onClick = { menu = true }, size = 40.dp)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Add cards") }, onClick = {
                        menu = false
                        onAddCards()
                    })
                    DropdownMenuItem(text = { Text("Deck settings") }, onClick = {
                        menu = false
                        onSettings()
                    })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = {
                        menu = false
                        onRename()
                    })
                    DropdownMenuItem(text = { Text("Delete", color = MnemoColors.danger) }, onClick = {
                        menu = false
                        onDelete()
                    })
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        DueBar(row.due.new, row.due.learning, row.due.review)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendDot(MnemoColors.stateNew, "New", "${row.due.new}")
            LegendDot(MnemoColors.stateLearning, "Learning", "${row.due.learning}")
            LegendDot(MnemoColors.stateReview, "Review", "${row.due.review}")
            Spacer(Modifier.weight(1f))
            MasteryBar(row.mastery, modifier = Modifier.widthIn(max = 180.dp))
        }
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

/** Small row used in sidebars: a deck name with its due count. */
@Composable
fun DeckMiniRow(
    name: String,
    due: Int,
    depth: Int,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = (10 + depth * 14).dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium, color = MnemoColors.text, modifier = Modifier.weight(1f))
        if (due > 0) Text("$due", style = MaterialTheme.typography.labelMedium, color = MnemoColors.text2)
    }
}
