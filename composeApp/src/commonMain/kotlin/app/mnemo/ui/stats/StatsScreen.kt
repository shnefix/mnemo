package app.mnemo.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.mnemo.LocalAppGraph
import app.mnemo.domain.cards.CardGenerator
import app.mnemo.domain.graph.WeakSpots
import app.mnemo.domain.model.DAY_MS
import app.mnemo.domain.scheduler.FsrsOptimizer
import app.mnemo.domain.stats.localDate
import app.mnemo.domain.stats.longestStreak
import app.mnemo.domain.stats.reviewsByLocalDate
import app.mnemo.domain.stats.streak
import app.mnemo.ui.components.MasteryBar
import app.mnemo.ui.components.MnemoButton
import app.mnemo.ui.components.Panel
import app.mnemo.ui.components.ScreenHeader
import app.mnemo.ui.components.StatTile
import app.mnemo.ui.nav.LocalWindowSize
import app.mnemo.ui.nav.WindowSize
import app.mnemo.ui.theme.MnemoColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlin.math.roundToInt

private fun fmt3(d: Double): String = ((d * 1000).roundToInt() / 1000.0).toString()

private data class StatsData(
    val heat: List<Pair<LocalDate, Int>>,
    val streak: Int,
    val longest: Int,
    val retention: Double?,
    val retentionN: Int,
    val forecast: List<Int>,
    val minutes30: Int,
    val reviews30: Int,
    val leeches: List<Pair<String, Int>>,
    val hardest: List<Pair<String, Double>>,
    val totalReviews: Int,
    val concepts: List<WeakSpots.ConceptScore>,
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(onEdit: (Long) -> Unit) {
    val graph = LocalAppGraph.current
    val compact = LocalWindowSize.current == WindowSize.Compact
    // Show the last numbers instantly when coming back to the tab, then refresh them.
    var data by remember { mutableStateOf(lastStats) }
    var optimizing by remember { mutableStateOf(false) }
    var optimizeMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        data = withContext(Dispatchers.Default) { loadStats(graph) }.also { lastStats = it }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (compact) 20.dp else 40.dp, vertical = 28.dp),
    ) {
        Column(Modifier.widthIn(max = 1120.dp)) {
            ScreenHeader(title = "Stats", subtitle = data?.let { "${it.totalReviews} reviews logged" } ?: "")
            Spacer(Modifier.height(20.dp))
            val d = data ?: return@Column
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Streak", "${d.streak} d", "longest ${d.longest} d", Modifier.weight(1f))
                StatTile(
                    "True retention",
                    d.retention?.let {
                        "${(it * 100).roundToInt()}%"
                    } ?: "—",
                    "target 90% · ${d.retentionN} reviews / 30 d",
                    Modifier.weight(1f),
                )
                if (!compact) {
                    StatTile("Reviews · 30 d", "${d.reviews30}", "${d.minutes30} min spent", Modifier.weight(1f))
                    StatTile("Due · 30 d", "${d.forecast.sum()}", "today ${d.forecast.firstOrNull() ?: 0}", Modifier.weight(1f))
                }
            }
            if (compact) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Reviews · 30 d", "${d.reviews30}", "${d.minutes30} min spent", Modifier.weight(1f))
                    StatTile("Due · 30 d", "${d.forecast.sum()}", "today ${d.forecast.firstOrNull() ?: 0}", Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(16.dp))
            Panel {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Reviews per day · 26 weeks",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MnemoColors.text,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("less", style = MaterialTheme.typography.labelSmall, color = MnemoColors.text3)
                        heatRamp.forEach { c -> Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(c)) }
                        Text("more", style = MaterialTheme.typography.labelSmall, color = MnemoColors.text3)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Heatmap(d.heat)
            }
            Spacer(Modifier.height(16.dp))
            Panel {
                Text("Due over the next 30 days", style = MaterialTheme.typography.headlineSmall, color = MnemoColors.text)
                Spacer(Modifier.height(14.dp))
                val peak = (d.forecast.maxOrNull() ?: 0).coerceAtLeast(1)
                Row(
                    Modifier.fillMaxWidth().height(150.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    d.forecast.forEachIndexed { i, n ->
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height((150 * n / peak).coerceAtLeast(if (n > 0) 3 else 1).dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(if (i == 0) MnemoColors.coral else MnemoColors.stateReview),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(
                        "today",
                        "+10",
                        "+20",
                        "+30",
                    ).forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MnemoColors.text3) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Panel(Modifier.weight(1f)) {
                    Text("Leeches", style = MaterialTheme.typography.headlineSmall, color = MnemoColors.text)
                    Text("forgotten 8 or more times", style = MaterialTheme.typography.bodySmall, color = MnemoColors.text2)
                    Spacer(Modifier.height(10.dp))
                    if (d.leeches.isEmpty()) Text("None yet.", style = MaterialTheme.typography.bodyMedium, color = MnemoColors.text2)
                    d.leeches.take(8).forEach { (q, lapses) ->
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                q,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MnemoColors.text,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            Text("$lapses×", style = MaterialTheme.typography.labelMedium, color = MnemoColors.danger)
                        }
                    }
                }
                if (!compact) {
                    Panel(Modifier.weight(1f)) {
                        Text("Hardest cards", style = MaterialTheme.typography.headlineSmall, color = MnemoColors.text)
                        Text("by FSRS difficulty (1–10)", style = MaterialTheme.typography.bodySmall, color = MnemoColors.text2)
                        Spacer(Modifier.height(10.dp))
                        if (d.hardest.isEmpty()) {
                            Text(
                                "Review some cards first.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MnemoColors.text2,
                            )
                        }
                        d.hardest.forEach { (q, diff) ->
                            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    q,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MnemoColors.text,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                )
                                Text(
                                    ((diff * 10).roundToInt() / 10.0).toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MnemoColors.gradeHard,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (d.concepts.isNotEmpty()) {
                Panel {
                    Text("Mastery by concept", style = MaterialTheme.typography.headlineSmall, color = MnemoColors.text)
                    Text(
                        "mean recall probability of the cards you have studied · weakest first",
                        style = MaterialTheme.typography.bodySmall,
                        color = MnemoColors.text2,
                    )
                    Spacer(Modifier.height(12.dp))
                    d.concepts.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                c.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MnemoColors.text,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            Text(
                                "${c.studied} of ${c.cardIds.size} cards studied",
                                style = MaterialTheme.typography.bodySmall,
                                color = MnemoColors.text2,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        MasteryBar(c.mastery)
                        Spacer(Modifier.height(10.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Panel {
                Text("FSRS optimizer", style = MaterialTheme.typography.headlineSmall, color = MnemoColors.text)
                Text(
                    "Fits the 21 scheduler weights to your own review history by replaying it and minimising prediction error. " +
                        "Needs ${FsrsOptimizer.MIN_REVIEWS}+ reviews; you have ${d.totalReviews}." +
                        if (graph.usesCustomParameters) " Custom weights are in use." else " Using the published defaults.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MnemoColors.text2,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MnemoButton(
                        if (optimizing) "Optimizing…" else "Optimize parameters",
                        enabled = !optimizing && d.totalReviews >= FsrsOptimizer.MIN_REVIEWS,
                        primary = true,
                        onClick = {
                            optimizing = true
                            scope.launch {
                                val result =
                                    withContext(Dispatchers.Default) {
                                        val reviews =
                                            graph.reviewLogs
                                                .since(
                                                    0,
                                                ).map { FsrsOptimizer.Review(it.cardId, it.rating, it.reviewedAt) }
                                        FsrsOptimizer.optimize(reviews)
                                    }
                                graph.setParameters(result.parameters)
                                val pct = ((1 - result.lossAfter / result.lossBefore) * 100).roundToInt()
                                optimizeMessage =
                                    "Fitted on ${result.reviewsUsed} reviews · prediction error " +
                                    "${fmt3(
                                        result.lossBefore,
                                    )} → ${fmt3(result.lossAfter)} ($pct% better). New intervals apply from your next review."
                                optimizing = false
                            }
                        },
                    )
                    if (graph.usesCustomParameters) {
                        MnemoButton("Reset to defaults", onClick = {
                            graph.setParameters(null)
                            optimizeMessage = "Back to the published FSRS-6 defaults."
                        })
                    }
                }
                optimizeMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MnemoColors.success)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

private val heatRamp = listOf(Color(0xFF171923), Color(0xFF2A3310), Color(0xFF5C6B1C), Color(0xFF9CB32C), Color(0xFFD4F25A))

@Composable
private fun Heatmap(days: List<Pair<LocalDate, Int>>) {
    val max = (days.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)

    fun level(n: Int): Int = if (n == 0) 0 else (1 + (n * 3) / max).coerceIn(1, 4)
    // Columns are weeks, rows Monday..Sunday.
    val firstIso =
        days
            .first()
            .first.dayOfWeek.isoDayNumber - 1 // 0 = Monday
    val cells = List(firstIso) { null } + days
    val weeks = cells.chunked(7)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        weeks.forEach { week ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                week.forEach { cell ->
                    Box(
                        Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(
                            if (cell == null) Color.Transparent else heatRamp[level(cell.second)],
                        ),
                    )
                }
            }
        }
    }
}

/** Kept across tab switches so Stats opens with numbers instead of a blank screen. */
private var lastStats: StatsData? = null

private suspend fun loadStats(graph: app.mnemo.di.AppGraph): StatsData {
    val now = graph.clock.now()
    val today = localDate(now)
    val daily = graph.reviewLogs.dailyCounts(now - 200 * DAY_MS)
    val byDate = reviewsByLocalDate(daily)
    val heat = (181 downTo 0).map { back -> LocalDate.fromEpochDays(today.toEpochDays() - back).let { it to (byDate[it] ?: 0) } }
    val ret = graph.reviewLogs.retentionSince(now - 30 * DAY_MS)
    val decks = graph.decks.all()
    val types = graph.notes.noteTypes().associateBy { it.id }
    // Pick the cards first (no note text), then load and render only the ones shown.
    val leechCards = graph.cards.leeches(8)
    val hardestCards =
        graph.cards
            .all()
            .filter { it.difficulty != null }
            .sortedByDescending { it.difficulty }
            .take(5)
    val viewById = graph.cards.viewsByIds((leechCards + hardestCards).map { it.id }.distinct()).associateBy { it.card.id }

    fun title(cardId: Long): String =
        viewById[cardId]?.let { v ->
            types[v.noteTypeId]?.let { t ->
                CardGenerator
                    .render(t, v.fields, v.card.templateIndex)
                    .question
                    .lineSequence()
                    .first()
            }
        } ?: "card $cardId"
    val leeches = leechCards.map { title(it.id) to it.lapses }
    val hardest = hardestCards.map { title(it.id) to (it.difficulty ?: 0.0) }
    return StatsData(
        heat = heat,
        streak = streak(byDate.keys, today),
        longest = longestStreak(byDate.keys),
        retention = ret.ratio,
        retentionN = ret.total,
        forecast = graph.cards.forecast(decks.map { it.id }, now, 30),
        minutes30 = (graph.reviewLogs.timeSpentSince(now - 30 * DAY_MS) / 60_000L).toInt(),
        reviews30 = daily.filter { it.dayIndex * DAY_MS >= now - 30 * DAY_MS }.sumOf { it.count },
        leeches = leeches,
        hardest = hardest,
        totalReviews = graph.reviewLogs.countAll().toInt(),
        // Started concepts weakest first; concepts not studied yet are not "0%".
        concepts = WeakSpots.scores(graph, now).filter { it.started }.sortedBy { it.mastery },
    )
}
