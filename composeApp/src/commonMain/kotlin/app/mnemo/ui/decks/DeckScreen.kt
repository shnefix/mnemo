package app.mnemo.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.mnemo.LocalAppGraph
import app.mnemo.data.io.DeckTrash
import app.mnemo.domain.model.DAY_MS
import app.mnemo.domain.model.ReviewMode
import app.mnemo.ui.components.Eyebrow
import app.mnemo.ui.components.IconActionButton
import app.mnemo.ui.components.LayeredHero
import app.mnemo.ui.components.Layers
import app.mnemo.ui.components.MasteryRing
import app.mnemo.ui.components.MnemoIcons
import app.mnemo.ui.components.Segmented
import app.mnemo.ui.nav.LocalWindowSize
import app.mnemo.ui.nav.WindowSize
import app.mnemo.ui.theme.MnemoColors

/**
 * One deck's own page: what's due (with the same layer tabs as Today), how its cards are
 * answered, its sub-decks, and the deck's actions (add, browse, settings, rename, delete).
 */
@Composable
fun DeckScreen(
    deckId: Long,
    onBack: () -> Unit,
    onReview: (deckId: Long, layer: String?) -> Unit,
    onAddCards: (Long) -> Unit,
    onBrowse: (query: String) -> Unit,
    onSettings: (Long) -> Unit,
    onOpen: (Long) -> Unit,
    onCustomStudy: (deckId: Long, spec: String) -> Unit = { _, _ -> },
) {
    val graph = LocalAppGraph.current
    val vm: DecksViewModel = viewModel(key = "deck-$deckId") { DecksViewModel(graph) }
    DisposableEffect(vm) {
        vm.setVisible(true)
        onDispose { vm.setVisible(false) }
    }
    val state by vm.state.collectAsState()
    val compact = LocalWindowSize.current == WindowSize.Compact
    var layer by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var customStudy by remember { mutableStateOf(false) }
    // Average answer time over the last 30 days, for the "about N min" estimate (8 s before any reviews).
    var avgMs by remember { mutableIntStateOf(8_000) }
    LaunchedEffect(Unit) {
        val now = graph.clock.now()
        val logs = graph.reviewLogs.since(now - 30 * DAY_MS)
        if (logs.isNotEmpty()) {
            avgMs =
                logs
                    .map { it.responseMs }
                    .average()
                    .toInt()
                    .coerceIn(3_000, 30_000)
        }
    }

    val row = state.rows.firstOrNull { it.deck.id == deckId }
    if (row == null) {
        // Deleted (or not loaded yet): nothing to show; leave once the list has loaded without it.
        if (!state.loading) LaunchedEffect(Unit) { onBack() }
        return
    }
    val packs = remember(state.rows) { packsByParent(state.rows) }
    val packIds = remember(packs) { packs.values.flatten().mapTo(HashSet()) { it.deck.id } }
    val children = state.rows.filter { it.deck.parentId == deckId && it.deck.id !in packIds }
    val ownPacks = packs[deckId].orEmpty()
    val subCount = children.size + ownPacks.size
    val color =
        Layers.deckColor(
            state.rows
                .firstOrNull { it.deck.path == row.deck.path.substringBefore("::") }
                ?.deck
                ?.id ?: deckId,
        )
    val minutes = ((row.due.total.toLong() * avgMs) / 60_000L).toInt().coerceAtLeast(if (row.due.total > 0) 1 else 0)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (compact) 20.dp else 40.dp, vertical = 14.dp),
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 0.dp), verticalAlignment = Alignment.CenterVertically) {
                IconActionButton(MnemoIcons.Back, "Back", onClick = onBack, tint = MnemoColors.text, background = Color.Transparent)
                Spacer(Modifier.weight(1f))
                Box {
                    IconActionButton(MnemoIcons.More, "Rename or delete deck", onClick = { menu = true }, background = Color.Transparent)
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = {
                            menu = false
                            renaming = true
                        })
                        DropdownMenuItem(text = { Text("Delete", color = MnemoColors.danger) }, onClick = {
                            menu = false
                            deleting = true
                        })
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val parent = row.deck.path.substringBeforeLast("::", "")
                    if (parent.isNotEmpty()) {
                        Text(parent.replace("::", " › "), style = MaterialTheme.typography.labelLarge, color = MnemoColors.text2)
                    }
                    Text(row.deck.name, style = MaterialTheme.typography.displaySmall, color = MnemoColors.text)
                    Text(
                        "${row.cardCount} cards" + (if (subCount > 0) " · $subCount sub-decks" else "") +
                            " · ${row.deck.settings.mode.label} mode",
                        style = MaterialTheme.typography.labelLarge,
                        color = MnemoColors.text2,
                    )
                }
                Spacer(Modifier.width(12.dp))
                MasteryRing(row.mastery, color, size = 64.dp, stroke = 6.dp, caption = if (row.mastery > 0) "mastered" else null)
            }
            Spacer(Modifier.height(18.dp))

            LayeredHero(
                due = row.due,
                minutes = minutes,
                heading = "Due in ${row.deck.name}",
                startLabel = "Study ${row.deck.name}",
                chosen = layer,
                onSelect = { layer = it },
                onStart = { onReview(deckId, it) },
            )
            Spacer(Modifier.height(22.dp))

            Eyebrow("How you answer")
            Spacer(Modifier.height(8.dp))
            val mode = row.deck.settings.mode
            Segmented(
                options = ReviewMode.entries.map { it.label },
                selected = mode.ordinal,
                onSelect = { vm.updateSettings(deckId, row.deck.settings.copy(mode = ReviewMode.entries[it])) },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when (mode) {
                    ReviewMode.Classic -> "Show the answer, then grade yourself."
                    ReviewMode.Type -> "Type the answer; Mnemo checks it letter by letter."
                    ReviewMode.Speak -> "The question is read out and you answer out loud."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MnemoColors.text2,
            )

            if (subCount > 0) {
                Spacer(Modifier.height(22.dp))
                Eyebrow("Sub-decks")
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MnemoColors.surface2)
                        .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                ) {
                    children.forEachIndexed { i, c ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(MnemoColors.outline))
                        SubDeckRow(
                            name = c.deck.name,
                            due = c.due.total,
                            indent = 0,
                            dot = color,
                            onOpen = { onOpen(c.deck.id) },
                            onStudy = { onReview(c.deck.id, null) },
                        )
                    }
                    if (ownPacks.isNotEmpty()) {
                        PackGrid(ownPacks, depth = 0, onReview = { onReview(it.deck.id, null) })
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile("Add cards", MnemoIcons.Plus, Layers.amber, Modifier.weight(1f)) { onAddCards(deckId) }
                ActionTile("Browse cards", MnemoIcons.Search, MnemoColors.sky, Modifier.weight(1f)) {
                    onBrowse("deck:\"${row.deck.path}\"")
                }
                ActionTile("Deck settings", MnemoIcons.Settings, MnemoColors.lavender, Modifier.weight(1f)) { onSettings(deckId) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile("Custom study", MnemoIcons.Target, MnemoColors.lime, Modifier.weight(1f)) { customStudy = true }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (customStudy) {
        CustomStudyDialog(row.deck.path, onDismiss = { customStudy = false }) { spec ->
            customStudy = false
            onCustomStudy(deckId, spec.encode())
        }
    }
    if (renaming) {
        NameDialog(
            title = "Rename deck",
            hint = null,
            initial = row.deck.name,
            confirm = "Rename",
            onDismiss = { renaming = false },
            onConfirm = {
                vm.rename(deckId, it)
                renaming = false
            },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            containerColor = MnemoColors.surface2,
            title = { Text("Delete “${row.deck.path}”?", color = MnemoColors.text) },
            text = {
                Text(
                    "This deletes ${row.cardCount} cards and their review history. You can bring it back from Settings › Recently deleted for ${DeckTrash.KEEP_DAYS} days.",
                    color = MnemoColors.text2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(deckId)
                    deleting = false
                }) { Text("Delete", color = MnemoColors.danger) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel", color = MnemoColors.text2) } },
        )
    }
}

@Composable
private fun ActionTile(
    label: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .height(80.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MnemoColors.surface2)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (MnemoColors.isDark) tint else MnemoColors.text, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MnemoColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
