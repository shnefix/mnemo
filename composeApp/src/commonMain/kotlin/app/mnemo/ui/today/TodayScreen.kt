package app.mnemo.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.mnemo.LocalAppGraph
import app.mnemo.ui.components.EmptyState
import app.mnemo.ui.components.Eyebrow
import app.mnemo.ui.components.LayeredHero
import app.mnemo.ui.components.MasteryBar
import app.mnemo.ui.components.MnemoButton
import app.mnemo.ui.components.MnemoIcons
import app.mnemo.ui.components.Panel
import app.mnemo.ui.nav.LocalWindowSize
import app.mnemo.ui.nav.WindowSize
import app.mnemo.ui.theme.MnemoColors
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(
    onReview: (Long?) -> Unit,
    onAddCards: () -> Unit,
    onDecks: () -> Unit,
    onWeakSpots: () -> Unit,
    onQuiz: () -> Unit = {},
    onReviewLayer: (String) -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val vm: TodayViewModel = viewModel(key = "today") { TodayViewModel(graph) }
    val state by vm.state.collectAsState()
    val compact = LocalWindowSize.current == WindowSize.Compact
    val pad = if (compact) 20.dp else 40.dp
    var layer by rememberSaveable { mutableStateOf<String?>(null) }
    DisposableEffect(vm) {
        vm.setVisible(true)
        onDispose { vm.setVisible(false) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pad, vertical = 28.dp)) {
        Column(Modifier.widthIn(max = 1120.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    if (!compact) {
                        Text(state.today?.let(::prettyDate) ?: "", style = MaterialTheme.typography.labelLarge, color = MnemoColors.text2)
                        Spacer(Modifier.height(2.dp))
                    }
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
            QuizBanner(onQuiz)

            // Hero: due now, with one tab per layer (review / learning / new) stacked above it.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LayeredHero(
                    due = state.due,
                    minutes = state.estimatedMinutes,
                    heading = "Due now",
                    startLabel = "Start review",
                    chosen = layer,
                    onSelect = { layer = it },
                    onStart = { picked -> if (picked == null) onReview(null) else onReviewLayer(picked) },
                    modifier = Modifier.weight(2f),
                )
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
                if (!compact) MnemoButton("All decks", onClick = onDecks, height = 36.dp)
            }
            Spacer(Modifier.height(12.dp))
            if (compact) {
                DeckStrip(state.decks, pad, onOpen = { onReview(it) }, onAll = onDecks)
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
    Panel(color = MnemoColors.surface2, radius = 24.dp, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(MnemoColors.lime), contentAlignment = Alignment.Center) {
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
            val doneToday = state.weekDays.lastOrNull() == true
            Text(
                if (doneToday) "Done today" else "Make it ${state.streak + 1} today",
                style = MaterialTheme.typography.bodySmall,
                color = MnemoColors.text2,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val today = state.today
            state.weekDays.forEachIndexed { i, done ->
                val isToday = i == 6
                val date = today?.let { LocalDate.fromEpochDays(it.toEpochDays() - (6 - i)) }
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        if (isToday) {
                            "Today"
                        } else {
                            date
                                ?.dayOfWeek
                                ?.name
                                ?.take(3)
                                ?.lowercase()
                                ?.replaceFirstChar { it.uppercase() } ?: ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) MnemoColors.lime else MnemoColors.text2,
                        maxLines = 1,
                    )
                    val dash = MnemoColors.lime
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                if (done) {
                                    MnemoColors.lime
                                } else if (isToday) {
                                    Color.Transparent
                                } else {
                                    MnemoColors.surface3
                                },
                            ).then(
                                if (isToday && !done) {
                                    Modifier.drawBehind {
                                        drawCircle(
                                            color = dash,
                                            radius = size.minDimension / 2 - 1.dp.toPx(),
                                            style =
                                                Stroke(
                                                    2.dp.toPx(),
                                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                                                ),
                                        )
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${date?.day ?: ""}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color =
                                if (done) {
                                    MnemoColors.onLime
                                } else if (isToday) {
                                    MnemoColors.lime
                                } else {
                                    MnemoColors.text2
                                },
                        )
                    }
                }
            }
        }
    }
}

/** Phone decks: a swipeable strip of small tiles, then an "All decks" tile. */
@Composable
private fun DeckStrip(
    decks: List<TodayDeck>,
    sidePad: androidx.compose.ui.unit.Dp,
    onOpen: (Long) -> Unit,
    onAll: () -> Unit,
) {
    val bleed = with(androidx.compose.ui.platform.LocalDensity.current) { sidePad.roundToPx() }
    // Runs edge to edge: the screen's side padding becomes the strip's own content padding.
    LazyRow(
        modifier =
            Modifier.layout { measurable, c ->
                val p = measurable.measure(c.copy(minWidth = c.maxWidth + 2 * bleed, maxWidth = c.maxWidth + 2 * bleed))
                layout(c.maxWidth, p.height) { p.place(-bleed, 0) }
            },
        contentPadding = PaddingValues(horizontal = sidePad),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(decks, key = { _, d -> d.deck.id }) { i, d ->
            Column(
                Modifier
                    .width(160.dp)
                    .height(92.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MnemoColors.surface2)
                    .clickable(role = Role.Button) { onOpen(d.deck.id) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(
                                width = 8.dp,
                                height = 24.dp,
                            ).clip(RoundedCornerShape(999.dp))
                            .background(
                                app.mnemo.ui.components.Layers
                                    .deckColor(d.deck.id),
                            ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        d.deck.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MnemoColors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${d.due.total}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MnemoColors.text,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "due",
                        style = MaterialTheme.typography.bodySmall,
                        color = MnemoColors.text2,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
        }
        item {
            val dash = MnemoColors.outlineStrong
            Column(
                Modifier
                    .width(100.dp)
                    .height(92.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(role = Role.Button, onClick = onAll)
                    .drawBehind {
                        drawRoundRect(
                            color = dash,
                            cornerRadius = CornerRadius(20.dp.toPx()),
                            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(MnemoIcons.ArrowRight, contentDescription = null, tint = MnemoColors.text2, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(6.dp))
                Text("All decks", style = MaterialTheme.typography.labelLarge, color = MnemoColors.text2)
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

/** "Today's quiz" from Claude, shown while a quiz is waiting to be taken. */
@Composable
private fun QuizBanner(onQuiz: () -> Unit) {
    var waiting by remember { mutableStateOf<app.mnemo.domain.quiz.DailyQuiz.Quiz?>(null) }
    LaunchedEffect(Unit) {
        waiting =
            app.mnemo.domain.quiz.DailyQuiz
                .latest()
                ?.takeIf { it.second == null }
                ?.first
    }
    val q = waiting ?: return
    Panel(color = MnemoColors.lavender, onClick = onQuiz, modifier = Modifier.fillMaxWidth()) {
        Eyebrow("Today's quiz from Claude", color = MnemoColors.onLavender)
        Spacer(Modifier.height(6.dp))
        Text(
            "${q.questions.size} questions on what you studied — see what stuck.",
            style = MaterialTheme.typography.titleMedium,
            color = MnemoColors.onLavender,
        )
    }
    Spacer(Modifier.height(16.dp))
}
