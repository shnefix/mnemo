package app.mnemo.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mnemo.data.repo.DueCounts
import app.mnemo.di.AppGraph
import app.mnemo.domain.graph.WeakSpots
import app.mnemo.domain.model.DAY_MS
import app.mnemo.domain.model.Deck
import app.mnemo.domain.review.DailyQueue
import app.mnemo.domain.stats.localDate
import app.mnemo.domain.stats.reviewsByLocalDate
import app.mnemo.domain.stats.streak
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class TodayDeck(
    val deck: Deck,
    val due: DueCounts,
    val mastery: Double,
    val cards: Int,
)

data class ConceptMastery(
    val id: Long,
    val name: String,
    val mastery: Double,
    val cards: Int,
)

data class TodayUiState(
    val loading: Boolean = true,
    val today: LocalDate? = null,
    val due: DueCounts = DueCounts(0, 0, 0),
    val estimatedMinutes: Int = 0,
    val streak: Int = 0,
    val weekDays: List<Boolean> = List(7) { false },
    val retention: Double? = null,
    val reviewedToday: Int = 0,
    val minutesToday: Int = 0,
    val decks: List<TodayDeck> = emptyList(),
    val forecast: List<Int> = emptyList(),
    val weakest: List<ConceptMastery> = emptyList(),
    val totalCards: Int = 0,
)

@OptIn(FlowPreview::class)
class TodayViewModel(
    private val graph: AppGraph,
) : ViewModel() {
    private val _state = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    private var visible = false
    private var stale = true

    init {
        viewModelScope.launch {
            // Grading a card touches the card table on every answer; coalesce those
            // bursts and only recompute while the screen is actually showing.
            graph.cards.observeChanges().debounce(DEBOUNCE_MS).collect {
                if (visible) refresh() else stale = true
            }
        }
    }

    /** Called by the screen as it enters/leaves composition; a stale Today recomputes once on return. */
    fun setVisible(shown: Boolean) {
        visible = shown
        if (shown && stale) refresh()
    }

    fun refresh() {
        stale = false
        viewModelScope.launch(Dispatchers.Default) {
            val now = graph.clock.now()
            val today = localDate(now)
            val decks = graph.decks.all()
            val allIds = decks.map { it.id }
            // Same queue "Start review" builds for all decks, so the headline matches the session.
            val due = DailyQueue.counts(graph, null, now, decks)
            val rows =
                decks.map { d ->
                    val ids = graph.decks.subtreeIds(d.id)
                    val dueD = DailyQueue.counts(graph, d.id, now, decks)
                    val review = graph.cards.reviewCards(ids)
                    val sched = graph.scheduler(d.settings)
                    val mastery = if (review.isEmpty()) 0.0 else review.map { sched.retrievability(it, now) }.average()
                    TodayDeck(d, dueD, mastery, 0)
                }
            val daily = graph.reviewLogs.dailyCounts(now - 400 * DAY_MS)
            val byDate = reviewsByLocalDate(daily)
            val days = byDate.keys
            val week = (6 downTo 0).map { back -> LocalDate.fromEpochDays(today.toEpochDays() - back) in days }
            val retention = graph.reviewLogs.retentionSince(now - 30 * DAY_MS).ratio
            val reviewedToday = byDate[today] ?: 0
            val minutesToday =
                (
                    daily
                        .filter {
                            localDate(
                                it.dayIndex * DAY_MS + DAY_MS / 2,
                            ) == today
                        }.sumOf { it.timeMs } / 60_000L
                ).toInt()
            val forecast = graph.cards.forecast(allIds, now, 30)
            val weakest = conceptMastery(now).sortedBy { it.mastery }.take(3)
            val avgMs =
                graph.reviewLogs.since(now - 30 * DAY_MS).let { logs ->
                    if (logs.isEmpty()) 8_000L else logs.map { it.responseMs }.average().toLong()
                }
            _state.value =
                TodayUiState(
                    loading = false,
                    today = today,
                    due = due,
                    estimatedMinutes =
                        ((due.total * avgMs.coerceIn(3_000, 30_000)) / 60_000L).toInt().coerceAtLeast(
                            if (due.total >
                                0
                            ) {
                                1
                            } else {
                                0
                            },
                        ),
                    streak = streak(days, today),
                    weekDays = week,
                    retention = retention,
                    reviewedToday = reviewedToday,
                    minutesToday = minutesToday,
                    decks = rows,
                    forecast = forecast,
                    weakest = weakest,
                    totalCards = graph.cards.countAll().toInt(),
                )
        }
    }

    /** Concepts you have started (see [WeakSpots.mastery]); unstudied ones are not "weak", just new. */
    private suspend fun conceptMastery(now: Long): List<ConceptMastery> =
        WeakSpots
            .scores(graph, now)
            .filter { it.started }
            .map { ConceptMastery(it.conceptId, it.name, it.mastery, it.cardIds.size) }

    companion object {
        private const val DEBOUNCE_MS = 400L
    }
}
