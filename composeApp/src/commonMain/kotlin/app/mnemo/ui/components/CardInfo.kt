package app.mnemo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mnemo.LocalAppGraph
import app.mnemo.domain.model.CardState
import app.mnemo.domain.model.CardView
import app.mnemo.domain.model.DAY_MS
import app.mnemo.domain.model.Flag
import app.mnemo.domain.model.Note
import app.mnemo.domain.model.ReviewLog
import app.mnemo.domain.scheduler.formatInterval
import app.mnemo.domain.stats.localDate
import app.mnemo.ui.theme.MnemoColors
import kotlin.math.roundToInt

/** Everything about one card, like Anki's Card Info: dates, memory state, totals and every review. */
@Composable
fun CardInfoDialog(
    cardId: Long,
    onDismiss: () -> Unit,
) {
    val graph = LocalAppGraph.current
    var view by remember { mutableStateOf<CardView?>(null) }
    var note by remember { mutableStateOf<Note?>(null) }
    var logs by remember { mutableStateOf<List<ReviewLog>>(emptyList()) }
    var typeName by remember { mutableStateOf("") }
    var recall by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(cardId) {
        val v = graph.cards.view(cardId) ?: return@LaunchedEffect
        view = v
        note = graph.notes.byId(v.card.noteId)
        logs = graph.reviewLogs.byCard(cardId)
        val type = graph.notes.noteType(v.noteTypeId)
        typeName =
            type
                ?.let { t ->
                    "${t.name} · ${t.templates.getOrNull(v.card.templateIndex)?.name ?: "card ${v.card.templateIndex + 1}"}"
                }.orEmpty()
        val settings =
            graph.decks.byId(v.deckId)?.settings ?: app.mnemo.domain.model
                .DeckSettings()
        recall = if (v.card.stability != null) graph.scheduler(settings).retrievability(v.card, graph.clock.now()) else null
    }
    val now = graph.clock.now()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MnemoColors.surface2,
        title = { Text("Card info", color = MnemoColors.text) },
        text = {
            val v = view
            if (v == null) {
                Text("Loading…", color = MnemoColors.text2)
            } else {
                val c = v.card
                Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                    val rows =
                        buildList {
                            note?.let { add("Added" to date(it.createdAt)) }
                            logs.firstOrNull()?.let { add("First review" to date(it.reviewedAt)) }
                            logs.lastOrNull()?.let { add("Latest review" to date(it.reviewedAt)) }
                            add(
                                "Due" to
                                    when {
                                        c.suspended -> "Suspended"
                                        c.state == CardState.New -> "New"
                                        else -> date(c.due) + " (" + relative(c.due - now) + ")"
                                    },
                            )
                            if (c.state !=
                                CardState.New
                            ) {
                                add(
                                    "Interval" to
                                        formatInterval(c.scheduledDays * DAY_MS).let { if (c.scheduledDays == 0) "under a day" else it },
                                )
                            }
                            c.stability?.let { add("Stability" to "${fmt(it)} days") }
                            c.difficulty?.let { add("Difficulty" to "${((it - 1.0) / 9.0 * 100).roundToInt()}%") }
                            recall?.let { add("Recall now" to "${(it * 100).roundToInt()}%") }
                            add("Reviews" to c.reps.toString())
                            add("Lapses" to c.lapses.toString())
                            if (logs.isNotEmpty()) {
                                val total = logs.sumOf { it.responseMs }
                                add("Average time" to seconds(total / logs.size))
                                add("Total time" to seconds(total))
                            }
                            add("State" to c.state.name + if (c.suspended) " · suspended" else "")
                            Flag.of(c.flag)?.let { add("Flag" to it.label) }
                            add("Note type" to typeName)
                            add("Deck" to v.deckName.replace("::", " › "))
                            add("Card ID" to c.id.toString())
                            add("Note ID" to c.noteId.toString())
                        }
                    rows.forEach { (k, value) -> InfoRow(k, value) }
                    if (logs.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text("Reviews", style = MaterialTheme.typography.titleSmall, color = MnemoColors.text)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth()) {
                            listOf("Date", "Type", "Grade", "Time").forEachIndexed { i, h ->
                                Text(
                                    h,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MnemoColors.text3,
                                    modifier =
                                        Modifier.weight(
                                            if (i ==
                                                0
                                            ) {
                                                1.4f
                                            } else {
                                                1f
                                            },
                                        ),
                                )
                            }
                        }
                        logs.reversed().forEach { l ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text(
                                    date(l.reviewedAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MnemoColors.text2,
                                    modifier = Modifier.weight(1.4f),
                                )
                                Text(
                                    when (l.before.state) {
                                        CardState.New, CardState.Learning -> "Learn"
                                        CardState.Review -> "Review"
                                        CardState.Relearning -> "Relearn"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MnemoColors.text2,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    l.rating.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (l.rating.code == 1L) MnemoColors.danger else MnemoColors.text,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    seconds(l.responseMs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MnemoColors.text2,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = MnemoColors.coral) } },
    )
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MnemoColors.text2)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MnemoColors.text)
    }
}

private fun date(ms: Long): String = localDate(ms).toString()

private fun relative(ms: Long): String = if (ms <= 0) "now" else "in " + formatInterval(ms)

private fun seconds(ms: Long): String =
    if (ms <
        60_000
    ) {
        "${((ms / 100.0).roundToInt() / 10.0)}s"
    } else {
        "${ms / 60_000}m ${(ms % 60_000) / 1000}s"
    }

private fun fmt(d: Double): String = ((d * 10).roundToInt() / 10.0).toString()
