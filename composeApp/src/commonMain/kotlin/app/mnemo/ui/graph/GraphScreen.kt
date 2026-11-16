package app.mnemo.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.mnemo.LocalAppGraph
import app.mnemo.domain.model.Relation
import app.mnemo.ui.components.Eyebrow
import app.mnemo.ui.components.MasteryBar
import app.mnemo.ui.components.MnemoButton
import app.mnemo.ui.components.MnemoIcons
import app.mnemo.ui.components.MnemoSelect
import app.mnemo.ui.components.Panel
import app.mnemo.ui.components.ScreenHeader
import app.mnemo.ui.decks.NameDialog
import app.mnemo.ui.nav.LocalWindowSize
import app.mnemo.ui.nav.WindowSize
import app.mnemo.ui.theme.MnemoColors
import app.mnemo.ui.theme.masteryColor
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun GraphScreen(
    onEditCard: (noteId: Long) -> Unit,
    onWeakSpots: () -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val vm: GraphViewModel = viewModel(key = "graph") { GraphViewModel(graph) }
    val state by vm.state.collectAsState()
    val compact = LocalWindowSize.current == WindowSize.Compact
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (compact) 20.dp else 40.dp, vertical = 28.dp),
    ) {
        ScreenHeader(title = "Knowledge graph", subtitle = "${state.nodes.size} concepts · nodes colored by mastery") {
            MnemoButton("Suggest concepts", onClick = vm::suggest, icon = MnemoIcons.Bulb)
            MnemoButton("Weak spots", onClick = onWeakSpots, icon = MnemoIcons.Target, enabled = state.nodes.isNotEmpty())
            MnemoButton("New concept", onClick = { creating = true }, icon = MnemoIcons.Plus, primary = true)
        }
        if (state.suggested) {
            Spacer(Modifier.height(12.dp))
            Panel(padding = PaddingValues(16.dp)) {
                Eyebrow(
                    if (state.suggestions.isEmpty()) {
                        "No new concepts to suggest"
                    } else {
                        "Suggested concepts · shared tags and similar wording"
                    },
                )
                if (state.suggestions.isEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Add tags or more cards on a topic and try again. Groups need at least three cards.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MnemoColors.text2,
                    )
                }
                state.suggestions.forEach { sg ->
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(sg.name, style = MaterialTheme.typography.titleSmall, color = MnemoColors.text)
                            Text(sg.reason, style = MaterialTheme.typography.bodySmall, color = MnemoColors.text2)
                        }
                        MnemoButton("Accept", onClick = { vm.acceptSuggestion(sg) }, height = 36.dp, primary = true)
                        Spacer(Modifier.width(6.dp))
                        MnemoButton("Dismiss", onClick = { vm.dismissSuggestion(sg) }, height = 36.dp)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (compact) {
            GraphCanvas(state, Modifier.fillMaxWidth().height(380.dp), onSelect = vm::select)
            Spacer(Modifier.height(16.dp))
            SidePanel(state, vm, onEditCard, onRename = { renaming = true })
        } else {
            Row(Modifier.fillMaxWidth().height(620.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GraphCanvas(state, Modifier.weight(1f).fillMaxSize(), onSelect = vm::select)
                Box(Modifier.width(360.dp).fillMaxSize()) { SidePanel(state, vm, onEditCard, onRename = { renaming = true }) }
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (creating) {
        NameDialog("New concept", "e.g. German dative case", "", "Create", onDismiss = { creating = false }) {
            vm.createConcept(it)
            creating = false
        }
    }
    if (renaming) {
        state.selected?.let { n ->
            NameDialog("Rename concept", null, n.concept.name, "Rename", onDismiss = { renaming = false }) {
                vm.rename(n.concept.id, it)
                renaming = false
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GraphCanvas(
    state: GraphUiState,
    modifier: Modifier,
    onSelect: (Long?) -> Unit,
) {
    BoxWithConstraints(
        modifier.clip(RoundedCornerShape(20.dp)).background(MnemoColors.surface).clickable(role = Role.Button) { onSelect(null) },
    ) {
        val w = maxWidth
        val h = maxHeight
        val byId = state.nodes.associateBy { it.concept.id }
        Canvas(Modifier.fillMaxSize()) {
            // dot grid
            val step = 24.dp.toPx()
            var x = step
            while (x < size.width) {
                var y = step
                while (y < size.height) {
                    drawCircle(MnemoColors.surface3, radius = 1f, center = Offset(x, y))
                    y += step
                }
                x += step
            }
            state.edges.forEach { e ->
                val a = byId[e.fromId] ?: return@forEach
                val b = byId[e.toId] ?: return@forEach
                val hi = state.selectedId == e.fromId || state.selectedId == e.toId
                val effect =
                    when (e.relation) {
                        Relation.RelatedTo -> null
                        Relation.PrerequisiteOf -> PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                        Relation.PartOf -> PathEffect.dashPathEffect(floatArrayOf(3f, 10f))
                    }
                drawLine(
                    color = if (hi) MnemoColors.coral else MnemoColors.outlineStrong,
                    start = Offset(a.x * size.width, a.y * size.height),
                    end = Offset(b.x * size.width, b.y * size.height),
                    strokeWidth = if (hi) 3f else 2f,
                    pathEffect = effect,
                )
            }
        }
        state.nodes.forEach { n ->
            val d = (36 + 6 * sqrt(n.cardCount.toFloat())).coerceAtMost(84f).dp
            val selected = n.concept.id == state.selectedId
            val color = masteryColor(n.mastery)
            Column(
                Modifier
                    .offset { IntOffset((n.x * w.toPx() - d.toPx() / 2).roundToInt(), (n.y * h.toPx() - d.toPx() / 2).roundToInt()) }
                    .width(140.dp)
                    .offset(x = (d / 2) - 70.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(d)
                        .clip(CircleShape)
                        .background(if (selected) MnemoColors.coral else MnemoColors.surface)
                        .padding(if (selected) 3.dp else 0.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable(role = Role.Button) { onSelect(n.concept.id) }
                        .semantics {
                            contentDescription =
                                "${n.concept.name}, mastery ${(n.mastery * 100).roundToInt()}%, ${n.cardCount} cards"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${(n.mastery * 100).roundToInt()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (n.mastery < 0.55) MnemoColors.text else MnemoColors.background,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    n.concept.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MnemoColors.coral else MnemoColors.text,
                    maxLines = 2,
                    modifier =
                        Modifier
                            .clip(
                                RoundedCornerShape(4.dp),
                            ).background(MnemoColors.background.copy(alpha = 0.7f))
                            .padding(horizontal = 4.dp),
                )
            }
        }
        if (state.nodes.isEmpty() && !state.loading) {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No concepts yet", style = MaterialTheme.typography.headlineSmall, color = MnemoColors.text)
                Text(
                    "Create a concept, then link cards to it. AI suggestions come in Phase 5.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MnemoColors.text2,
                )
            }
        }
        FlowRow(
            Modifier.align(Alignment.BottomStart).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Legend("related", null)
            Legend("prerequisite", "- -")
            Legend("part of", "···")
            Text("size = cards", style = MaterialTheme.typography.labelSmall, color = MnemoColors.text3)
        }
    }
}

@Composable
private fun Legend(
    label: String,
    dash: String?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dash == null) {
            Box(Modifier.width(18.dp).height(2.dp).background(MnemoColors.outlineStrong))
        } else {
            Text(dash, style = MaterialTheme.typography.labelSmall, color = MnemoColors.outlineStrong)
        }
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MnemoColors.text3)
    }
}

@Composable
private fun SidePanel(
    state: GraphUiState,
    vm: GraphViewModel,
    onEditCard: (Long) -> Unit,
    onRename: () -> Unit,
) {
    val node = state.selected
    Panel(Modifier.fillMaxSize(), padding = PaddingValues(20.dp)) {
        if (node == null) {
            Eyebrow("Concept")
            Spacer(Modifier.height(8.dp))
            Text("Select a node to see its cards and connections.", style = MaterialTheme.typography.bodyMedium, color = MnemoColors.text2)
            return@Panel
        }
        var menu by remember { mutableStateOf(false) }
        var linkPick by remember { mutableStateOf(false) }
        var edgeTarget by remember { mutableStateOf<GraphNode?>(null) }
        var edgeRelation by remember { mutableStateOf(Relation.RelatedTo) }

        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Concept")
                    Text(node.concept.name, style = MaterialTheme.typography.headlineMedium, color = MnemoColors.text)
                    Text(
                        "${node.cardCount} cards · ${node.dueCount} due",
                        style = MaterialTheme.typography.bodySmall,
                        color = MnemoColors.text2,
                    )
                }
                Box {
                    MnemoButton("⋯", onClick = { menu = true }, height = 36.dp)
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = {
                            menu = false
                            onRename()
                        })
                        DropdownMenuItem(text = { Text("Delete", color = MnemoColors.danger) }, onClick = {
                            menu = false
                            vm.delete(node.concept.id)
                        })
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Mastery", style = MaterialTheme.typography.bodySmall, color = MnemoColors.text2, modifier = Modifier.weight(1f))
                Text("${(node.mastery * 100).roundToInt()}%", style = MaterialTheme.typography.displaySmall, color = MnemoColors.text)
            }
            MasteryBar(node.mastery, showLabel = false)
            Spacer(Modifier.height(18.dp))

            Eyebrow("Connections")
            Spacer(Modifier.height(6.dp))
            val related = state.edges.filter { it.fromId == node.concept.id || it.toId == node.concept.id }
            if (related.isEmpty()) Text("None yet.", style = MaterialTheme.typography.bodySmall, color = MnemoColors.text2)
            related.forEach { e ->
                val otherId = if (e.fromId == node.concept.id) e.toId else e.fromId
                val other = state.nodes.firstOrNull { it.concept.id == otherId } ?: return@forEach
                val kind =
                    when (e.relation) {
                        Relation.RelatedTo -> "related"
                        Relation.PrerequisiteOf -> if (e.fromId == node.concept.id) "unlocks" else "needs"
                        Relation.PartOf -> if (e.fromId == node.concept.id) "part of" else "contains"
                    }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) {
                            vm.select(other.concept.id)
                        }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(kind, style = MaterialTheme.typography.labelSmall, color = MnemoColors.text3, modifier = Modifier.width(64.dp))
                    Text(
                        other.concept.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MnemoColors.text,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${(other.mastery * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MnemoColors.text2)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "×",
                        style = MaterialTheme.typography.labelLarge,
                        color = MnemoColors.text3,
                        modifier = Modifier.clickable { vm.removeEdge(e) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val others = state.nodes.filter { it.concept.id != node.concept.id }
            if (others.isNotEmpty()) {
                MnemoSelect(label = null, options = others, selected = edgeTarget, display = { it.concept.name }, onSelect = {
                    edgeTarget =
                        it
                }, placeholder = "Connect to…")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MnemoSelect(label = null, options = Relation.entries, selected = edgeRelation, display = { it.label }, onSelect = {
                        edgeRelation =
                            it
                    }, modifier = Modifier.weight(1f))
                    MnemoButton("Add", enabled = edgeTarget != null, onClick = {
                        edgeTarget?.let { vm.addEdge(node.concept.id, it.concept.id, edgeRelation) }
                        edgeTarget = null
                    })
                }
            }
            Spacer(Modifier.height(18.dp))

            Eyebrow("Cards · weakest first")
            Spacer(Modifier.height(6.dp))
            state.selectedCards.forEach { c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) {
                            onEditCard(c.view.card.noteId)
                        }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        c.question,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MnemoColors.text,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Text(
                        "${(c.retrievability * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MnemoColors.text2,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "×",
                        style = MaterialTheme.typography.labelLarge,
                        color = MnemoColors.text3,
                        modifier =
                            Modifier.clickable {
                                vm.unlink(c.view.card.id, node.concept.id)
                            },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Box {
                MnemoButton("Link a card", onClick = { linkPick = true }, icon = MnemoIcons.Plus)
                DropdownMenu(expanded = linkPick, onDismissRequest = { linkPick = false }) {
                    val linked = state.selectedCards.map { it.view.card.id }.toSet()
                    state.allCards.filter { it.first.card.id !in linked }.take(60).forEach { (v, q) ->
                        DropdownMenuItem(text = { Text(q.ifBlank { "card ${v.card.id}" }, maxLines = 1) }, onClick = {
                            linkPick = false
                            vm.link(v.card.id, node.concept.id)
                        })
                    }
                }
            }
        }
    }
}
