package app.mnemo.ui.today

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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.mnemo.LocalAppGraph
import app.mnemo.ui.components.EmptyState
import app.mnemo.ui.components.Eyebrow
import app.mnemo.ui.components.MasteryBar
import app.mnemo.ui.components.MnemoButton
import app.mnemo.ui.components.MnemoIcons
import app.mnemo.ui.components.Panel
import app.mnemo.ui.nav.LocalWindowSize
import app.mnemo.ui.nav.WindowSize
import app.mnemo.ui.theme.MnemoColors
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(
    onReview: (Long?) -> Unit,
    onAddCards: () -> Unit,
    onDecks: () -> Unit,
    onWeakSpots: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val vm: TodayViewModel = viewModel(key = "today") { TodayViewModel(graph) }
    val state by vm.state.collectAsState()
    val compact = LocalWindowSize.current == WindowSize.Compact
    val pad = if (compact) 20.dp else 40.dp
    DisposableEffect(vm) {
        vm.setVisible(true)
        onDispose { vm.setVisible(false) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pad, vertical = 28.dp)) {
        Column(Modifier.widthIn(max = 1120.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(state.today?.let(::prettyDate) ?: "", style = MaterialTheme.typography.labelLarge, color = MnemoColors.text2)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (compact) "Ready for today?" else "Today",
                        style = if (compact) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium,
                        color = MnemoColors.text,
                    )
                }
                if (!compact) {
                    MnemoButton("Add card", onClick = onAddCards, icon = MnemoIcons.Plus, kbd = "A")
                }
            }
            Spacer(Modifier.height(20.dp))

            // Hero: due now (Design C block, Design A numbers)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Panel(
                    modifier = Modifier.weight(2f),
                    color = MnemoColors.coral,
                    radius = 28.dp,
                    padding = PaddingValues(24.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Eyebrow("Due now", color = MnemoColors.onCoral)
                        Spacer(Modifier.weight(1f))
                        Pill("about ${state.estimatedMinutes} min")
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${state.due.total}", style = MaterialTheme.typography.displayLarge, color = MnemoColors.onCoral)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "cards",
                            style = MaterialTheme.typography.titleLarge,
                            color = MnemoColors.onCoral,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill("${state.due.new} new")
                        Pill("${state.due.learning} learning")
                        Pill("${state.due.review} review")
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DarkButton(
                            text = "Start review",
                            onClick = { onReview(null) },
                            modifier = Modifier.weight(1f),
                            enabled = state.due.total > 0,
                        )
                    }
                }
                if (!compact) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        StreakPanel(state)
                        Panel(padding = PaddingValues(18.dp)) {
                            StatLine(
                                "True retention",
                                state.retention?.let { "${(it * 100).roundToInt()}%" } ?: "—",
                                "target 90% · last 30 days",
                            )
                            Spacer(Modifier.height(12.dp))
                            StatLine("Reviewed today", "${state.reviewedToday}", "${state.minutesToday} min so far")
                        }
                    }
                }
            }
            if (compact) {
                Spacer(Modifier.height(12.dp))
                StreakPanel(state)
            }
            Spacer(Modifier.height(24.dp))

            if (!state.loading && state.totalCards == 0) {
                EmptyState(
                    title = "Welcome to Mnemo",
                    body = "Create a deck and add a few cards, or import a CSV from Settings. Reviews are scheduled with FSRS.",
                    action = { MnemoButton("Add your first card", onClick = onAddCards, primary = true) },
                )
                Spacer(Modifier.height(24.dp))
            }

            // Decks
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Decks", style = MaterialTheme.typography.headlineMedium, color = MnemoColors.text, modifier = Modifier.weight(1f))
                MnemoButton("All decks", onClick = onDecks, height = 36.dp)
            }
            Spacer(Modifier.height(12.dp))
            if (compact) {
                state.decks.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEachIndexed { i, d ->
                            val tile = listOf(MnemoColors.sky, MnemoColors.lime, MnemoColors.lavender)[(state.decks.indexOf(d)) % 3]
                            Panel(
                                modifier =
                                    Modifier
                                        .weight(
                                            1f,
                                        ).height(110.dp),
                                color = tile,
                                radius = 22.dp,
                                padding = PaddingValues(14.dp),
                                onClick = {
                                    onReview(d.deck.id)
                                },
                            ) {
                                val parent = d.deck.path.substringBeforeLast("::", "")
                                if (parent.isNotEmpty()) {
                                    Text(
                                        parent,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MnemoColors.onLime.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    d.deck.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MnemoColors.onLime,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.weight(1f))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text("${d.due.total}", style = MaterialTheme.typography.headlineLarge, color = MnemoColors.onLime)
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "due",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MnemoColors.onLime,
                                        modifier = Modifier.padding(bottom = 4.dp),
                                    )
                                }
                            }
                            if (i == 0 && pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            } else {
                Panel(padding = PaddingValues(0.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Head("Deck", Modifier.weight(2f))
                        Head("New", Modifier.width(64.dp))
                        Head("Learn", Modifier.width(64.dp))
                        Head("Due", Modifier.width(64.dp))
                        Head("Mastery", Modifier.weight(1.2f))
                    }
                    state.decks.forEach { d ->
                        Box(Modifier.fillMaxWidth().height(1.dp).background(MnemoColors.outline))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                d.deck.path,
                                style = MaterialTheme.typography.titleSmall,
                                color = MnemoColors.text,
                                modifier = Modifier.weight(2f),
                            )
                            Num("${d.due.new}", MnemoColors.stateNew)
                            Num("${d.due.learning}", MnemoColors.stateLearning)
                            Num("${d.due.review}", MnemoColors.stateReview)
                            MasteryBar(d.mastery, Modifier.weight(1.2f))
                            Spacer(Modifier.width(12.dp))
                            MnemoButton("Review", onClick = { onReview(d.deck.id) }, height = 34.dp, enabled = d.due.total > 0)
                        }
                    }
                    if (state.decks.isEmpty()) {
                        Text(
                            "No decks yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MnemoColors.text2,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Panel(modifier = Modifier.weight(1f)) {
                    Text("Weakest concepts", style = MaterialTheme.typography.headlineSmall, color = MnemoColors.text)
                    Text(
                        "Mastery = average recall chance of the cards you have studied",
                        style = MaterialTheme.typography.bodySmall,
                        color = MnemoColors.text2,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (state.weakest.isEmpty()) {
                        Text(
                            "Link cards to concepts on the Graph page to see where you are weak.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MnemoColors.text2,
                        )
                    }
                    state.weakest.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                c.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MnemoColors.text,
                                modifier = Modifier.weight(1f),
                            )
                            Text("${c.cards} cards", style = MaterialTheme.typography.bodySmall, color = MnemoColors.text2)
                        }
                        Spacer(Modifier.height(6.dp))
                        MasteryBar(c.mastery)
                        Spacer(Modifier.height(12.dp))
                    }
                    if (state.weakest.isNotEmpty()) {
                        MnemoButton("Review weak spots", onClick = onWeakSpots, icon = MnemoIcons.Target)
                    }
                }
                if (!compact) {
                    Panel(modifier = Modifier.weight(1f)) {
                        Text("Next 30 days", style = MaterialTheme.typography.headlineSmall, color = MnemoColors.text)
                        Text(
                            "${state.forecast.sum()} reviews forecast",
                            style = MaterialTheme.typography.bodySmall,
                            color = MnemoColors.text2,
                        )
                        Spacer(Modifier.height(16.dp))
                        ForecastBars(state.forecast)
                    }
                }
            }
            if (compact) {
                Spacer(Modifier.height(16.dp))
                Panel {
                    Text("Next 30 days", style = MaterialTheme.typography.headlineSmall, color = MnemoColors.text)
                    Text("${state.forecast.sum()} reviews forecast", style = MaterialTheme.typography.bodySmall, color = MnemoColors.text2)
                    Spacer(Modifier.height(16.dp))
                    ForecastBars(state.forecast)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StreakPanel(state: TodayUiState) {
    Panel(color = MnemoColors.surface2, radius = 22.dp, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).background(MnemoColors.lime), contentAlignment = Alignment.Center) {
                Icon(MnemoIcons.Flame, contentDescription = null, tint = MnemoColors.onLime, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "${state.streak}-day streak",
                style = MaterialTheme.typography.titleMedium,
                color = MnemoColors.text,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("This week", style = MaterialTheme.typography.bodySmall, color = MnemoColors.text2, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                val labels = listOf("M", "T", "W", "T", "F", "S", "S")
                val todayIso = state.today?.dayOfWeek?.isoDayNumber ?: 1 // 1 = Monday
                val startIdx = (todayIso - 1 - 6 + 7) % 7
                state.weekDays.forEachIndexed { i, done ->
                    val isToday = i == 6
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (done) {
                                    MnemoColors.lime
                                } else if (isToday) {
                                    MnemoColors.surface3
                                } else {
                                    MnemoColors.surface3
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            labels[(startIdx + i) % 7],
                            style = MaterialTheme.typography.labelSmall,
                            color = if (done) MnemoColors.onLime else MnemoColors.text2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForecastBars(forecast: List<Int>) {
    val peak = (forecast.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        forecast.forEachIndexed { i, n ->
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((140 * n / peak).coerceAtLeast(if (n > 0) 3 else 1).dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (i == 0) MnemoColors.coral else MnemoColors.stateReview),
                )
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("today", style = MaterialTheme.typography.labelSmall, color = MnemoColors.text3)
        Text("+15 d", style = MaterialTheme.typography.labelSmall, color = MnemoColors.text3)
        Text("+30 d", style = MaterialTheme.typography.labelSmall, color = MnemoColors.text3)
    }
}

@Composable
private fun Pill(text: String) {
    Box(
        Modifier
            .clip(
                RoundedCornerShape(999.dp),
            ).background(MnemoColors.onCoral.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MnemoColors.onCoral)
    }
}

@Composable
private fun DarkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) MnemoColors.onCoral else MnemoColors.onCoral.copy(alpha = 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Color(0xFFF2F3F7))
        Spacer(Modifier.width(10.dp))
        Icon(MnemoIcons.ArrowRight, contentDescription = null, tint = Color(0xFFF2F3F7), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun StatLine(
    label: String,
    value: String,
    note: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MnemoColors.text2)
            Text(note, style = MaterialTheme.typography.bodySmall, color = MnemoColors.text3)
        }
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MnemoColors.text)
    }
}

@Composable
private fun Head(
    text: String,
    modifier: Modifier,
) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MnemoColors.text3, modifier = modifier)
}

@Composable
private fun Num(
    text: String,
    color: Color,
) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.width(64.dp))
}

fun prettyDate(d: LocalDate): String {
    val dow =
        d.dayOfWeek.name
            .lowercase()
            .replaceFirstChar { it.uppercase() }
    val month =
        d.month.name
            .lowercase()
            .replaceFirstChar { it.uppercase() }
    return "$dow, ${d.day} $month"
}
