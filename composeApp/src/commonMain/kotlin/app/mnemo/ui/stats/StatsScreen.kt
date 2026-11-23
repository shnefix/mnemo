package app.mnemo.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import app.mnemo.ui.components.Eyebrow
import app.mnemo.ui.components.Layers
import app.mnemo.ui.components.MasteryBar
import app.mnemo.ui.components.MnemoButton
import app.mnemo.ui.components.MnemoIcons
import app.mnemo.ui.components.Panel
import app.mnemo.ui.components.ScreenHeader
import app.mnemo.ui.components.Segmented
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

/** The numbers that change with the Week / Month / Year switch. */
private data class PeriodStats(
    val retention: Double?,
    val retentionN: Int,
    val reviews: Int,
    val minutes: Int,
)

private enum class Period(
    val label: String,
    val days: Int,
) {
    Week("Week", 7),
    Month("Month", 30),
    Year("Year", 365),
}

private data class StatsData(
    val today: LocalDate,
    val heat: List<Pair<LocalDate, Int>>,
    val streak: Int,
    val longest: Int,
    val periods: Map<Period, PeriodStats>,
    val forecast: List<Int>,
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
    var period by rememberSaveable { mutableStateOf(Period.Month) }
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
            Spacer(Modifier.height(16.dp))
            val d = data ?: return@Column
            Segmented(
                options = Period.entries.map { it.label },
                selected = period.ordinal,
                onSelect = { period = Period.entries[it] },
            )
            Spacer(Modifier.height(14.dp))
            val p = d.periods.getValue(period)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RetentionCard(p, period, Modifier.weight(1f))
                if (!compact) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SmallStats(d, p)
                    }
                }
            }
            if (compact) {
                Spacer(Modifier.height(12.dp))
                SmallStats(d, p)
            }
            Spacer(Modifier.height(12.dp))
            Panel(radius = 24.dp, padding = PaddingValues(16.dp)) {
                val weeks = if (period == Period.Year) 53 else 12
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "Reviews per day",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MnemoColors.text,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (period ==
                            Period.Year
                        ) {
                            "last year"
                        } else {
                            "last 12 weeks"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MnemoColors.text2,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Heatmap(d.heat, d.today, weeks)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        monthsShown(d.today, weeks),
                        style = MaterialTheme.typography.labelSmall,
                        color = MnemoColors.text3,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Text("less", style = MaterialTheme.typography.labelSmall, color = MnemoColors.text3)
                    heatRamp.forEach { c -> Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(c)) }
                    Text("more", style = MaterialTheme.typography.labelSmall, color = MnemoColors.text3)
                }
            }
            Spacer(Modifier.height(12.dp))
            Forecast(d.forecast.take(if (period == Period.Week) 7 else 30), d.today)
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

private val heatRamp = listOf(Color(0xFF23242D), Color(0xFF3D4818), Color(0xFF6E8228), Color(0xFFA6C23C), Color(0xFFD7F25C))

/** The 90% the scheduler aims for; retention is drawn against it. */
private const val TARGET = 0.9

@Composable
private fun RetentionCard(
    p: PeriodStats,
    period: Period,
    modifier: Modifier = Modifier,
) {
    Panel(modifier = modifier, color = MnemoColors.lime, radius = 26.dp, padding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("True retention", color = MnemoColors.onLime)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MnemoColors.onLime.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text("${p.retentionN} reviews", style = MaterialTheme.typography.labelMedium, color = MnemoColors.onLime)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            p.retention?.let { "${(it * 100).roundToInt()}%" } ?: "—",
            style = MaterialTheme.typography.displayLarge,
            color = MnemoColors.onLime,
        )
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(30.dp)) {
            val w = maxWidth
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MnemoColors.onLime.copy(alpha = 0.15f)),
            )
            Box(
                Modifier
                    .width(w * (p.retention ?: 0.0).coerceIn(0.0, 1.0).toFloat())
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MnemoColors.onLime),
            )
            Box(
                Modifier
                    .offset(x = w * TARGET.toFloat() - 1.dp, y = (-4).dp)
                    .width(2.dp)
                    .height(18.dp)
                    .background(MnemoColors.onLime),
            )
            Text(
                "target 90%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MnemoColors.onLime,
                modifier = Modifier.offset(x = w * TARGET.toFloat() - 54.dp, y = 14.dp),
            )
        }
        Text(
            when {
                p.retention == null -> "No reviews in the last ${period.label.lowercase()} yet."
                p.retention >= TARGET -> "On target: you remember what the scheduler expects."
                p.retention >= TARGET - 0.05 -> "A little under your 90% target."
                else -> "Under your 90% target: you are forgetting more than planned."
            },
            style = MaterialTheme.typography.labelLarge,
            color = MnemoColors.onLime,
        )
    }
}

@Composable
private fun SmallStats(
    d: StatsData,
    p: PeriodStats,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SmallStat(
            MnemoIcons.Flame,
            MnemoColors.lime,
            "Streak",
            if (d.streak ==
                1
            ) {
                "1 day"
            } else {
                "${d.streak} days"
            },
            "Best: ${d.longest} days",
            Modifier.weight(1f),
        )
        SmallStat(MnemoIcons.Clock, MnemoColors.sky, "Reviews", "${p.reviews}", "${p.minutes} min studying", Modifier.weight(1f))
    }
}

@Composable
private fun SmallStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    label: String,
    value: String,
    note: String,
    modifier: Modifier = Modifier,
) {
    Panel(modifier = modifier, color = MnemoColors.surface2, radius = 22.dp, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (MnemoColors.isDark) tint else MnemoColors.text2,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Eyebrow(label)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MnemoColors.text,
            maxLines = 1,
        )
        Text(note, style = MaterialTheme.typography.bodySmall, color = MnemoColors.text2, maxLines = 1)
    }
}

/** The last [weeks] weeks as columns, Monday at the top; squares fill the width when they fit. */
@Composable
private fun Heatmap(
    all: List<Pair<LocalDate, Int>>,
    today: LocalDate,
    weeks: Int,
) {
    val first = LocalDate.fromEpochDays(today.toEpochDays() - (today.dayOfWeek.isoDayNumber - 1) - (weeks - 1) * 7)
    val byDate = all.toMap()
    val shown = all.filter { it.first >= first }
    val max = (shown.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)

    fun level(n: Int): Int = if (n == 0) 0 else (1 + (n * 3) / max).coerceIn(1, 4)
    val wide = weeks > 20
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("M", "", "W", "", "F", "", "S").forEach {
                Box(Modifier.height(if (wide) 12.dp else 22.dp), contentAlignment = Alignment.Center) {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MnemoColors.text3)
                }
            }
        }
        Row(
            Modifier.weight(1f).then(if (wide) Modifier.horizontalScroll(rememberScrollState(Int.MAX_VALUE)) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(if (wide) 3.dp else 4.dp),
        ) {
            repeat(weeks) { w ->
                Column(
                    if (wide) Modifier else Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(7) { dow ->
                        val day = LocalDate.fromEpochDays(first.toEpochDays() + w * 7 + dow)
                        val future = day > today
                        Box(
                            (if (wide) Modifier.size(12.dp) else Modifier.fillMaxWidth().height(22.dp))
                                .clip(RoundedCornerShape(if (wide) 3.dp else 6.dp))
                                .background(if (future) Color.Transparent else heatRamp[level(byDate[day] ?: 0)]),
                        )
                    }
                }
            }
        }
    }
}

/** "Sep · Oct · Nov": the months the heat map covers. */
private fun monthsShown(
    today: LocalDate,
    weeks: Int,
): String {
    val start = LocalDate.fromEpochDays(today.toEpochDays() - weeks * 7 + 1)
    val months = mutableListOf<String>()
    var e = start.toEpochDays()
    while (e <= today.toEpochDays()) {
        val m = shortMonth(LocalDate.fromEpochDays(e))
        if (months.lastOrNull() != m) months += m
        e += 7
    }
    val last = shortMonth(today)
    if (months.lastOrNull() != last) months += last
    return if (months.size > 4) "${months.first()} – ${months.last()}" else months.joinToString(" · ")
}

private fun shortMonth(d: LocalDate): String =
    d.month.name
        .take(3)
        .lowercase()
        .replaceFirstChar { it.uppercase() }

private fun shortDay(d: LocalDate): String =
    d.dayOfWeek.name
        .take(3)
        .lowercase()
        .replaceFirstChar { it.uppercase() }

@Composable
private fun Forecast(
    forecast: List<Int>,
    today: LocalDate,
) {
    Panel(radius = 24.dp, padding = PaddingValues(16.dp)) {
        Text("Coming up", style = MaterialTheme.typography.headlineSmall, color = MnemoColors.text)
        val total = forecast.sum()
        val peak = forecast.withIndex().maxByOrNull { it.value }
        Text(
            if (total == 0 || peak == null) {
                "Nothing due in the next ${forecast.size} days"
            } else {
                val day = LocalDate.fromEpochDays(today.toEpochDays() + peak.index)
                "$total reviews in ${forecast.size} days · busiest: " +
                    (if (peak.index == 0) "today" else "${shortDay(day)} ${day.day} ${shortMonth(day)}") + " (${peak.value})"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MnemoColors.text2,
        )
        Spacer(Modifier.height(12.dp))
        val max = (forecast.maxOrNull() ?: 0).coerceAtLeast(1)
        Row(
            Modifier.fillMaxWidth().height(110.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            forecast.forEachIndexed { i, n ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(if (n == 0) 3.dp else (110 * n / max).coerceAtLeast(6).dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 1.dp, bottomEnd = 1.dp))
                        .background(
                            when {
                                n == 0 -> MnemoColors.surface3
                                i == 0 -> MnemoColors.coral
                                else -> Layers.dot(app.mnemo.domain.model.CardState.Review)
                            },
                        ),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val mid = LocalDate.fromEpochDays(today.toEpochDays() + forecast.size / 2)
            val end = LocalDate.fromEpochDays(today.toEpochDays() + forecast.size - 1)
            listOf("Today", "${mid.day} ${shortMonth(mid)}", "${end.day} ${shortMonth(end)}").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MnemoColors.text3)
            }
        }
    }
}

/** Kept across tab switches so Stats opens with numbers instead of a blank screen. */
private var lastStats: StatsData? = null

private suspend fun loadStats(graph: app.mnemo.di.AppGraph): StatsData {
    val now = graph.clock.now()
    val today = localDate(now)
    val daily = graph.reviewLogs.dailyCounts(now - 400 * DAY_MS)
    val byDate = reviewsByLocalDate(daily)
    val heat = (371 downTo 0).map { back -> LocalDate.fromEpochDays(today.toEpochDays() - back).let { it to (byDate[it] ?: 0) } }
    val periods =
        Period.entries.associateWith { p ->
            val since = now - p.days * DAY_MS
            val ret = graph.reviewLogs.retentionSince(since)
            PeriodStats(
                retention = ret.ratio,
                retentionN = ret.total,
                reviews = daily.filter { it.dayIndex * DAY_MS >= since }.sumOf { it.count },
                minutes = (graph.reviewLogs.timeSpentSince(since) / 60_000L).toInt(),
            )
        }
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
        today = today,
        heat = heat,
        streak = streak(byDate.keys, today),
        longest = longestStreak(byDate.keys),
        periods = periods,
        forecast = graph.cards.forecast(decks.map { it.id }, now, 30),
        leeches = leeches,
        hardest = hardest,
        totalReviews = graph.reviewLogs.countAll().toInt(),
        // Started concepts weakest first; concepts not studied yet are not "0%".
        concepts = WeakSpots.scores(graph, now).filter { it.started }.sortedBy { it.mastery },
    )
}
