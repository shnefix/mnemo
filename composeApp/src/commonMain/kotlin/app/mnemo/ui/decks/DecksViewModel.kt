package app.mnemo.ui.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mnemo.data.repo.DueCounts
import app.mnemo.di.AppGraph
import app.mnemo.domain.model.Deck
import app.mnemo.domain.model.DeckSettings
import app.mnemo.domain.review.DailyQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeckRow(
    val deck: Deck,
    val depth: Int,
    val due: DueCounts,
    val cardCount: Int,
    val mastery: Double,
)

data class DecksUiState(
    val rows: List<DeckRow> = emptyList(),
    val loading: Boolean = true,
)

class DecksViewModel(
    private val graph: AppGraph,
) : ViewModel() {
    private val _state = MutableStateFlow(DecksUiState())
    val state: StateFlow<DecksUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            graph.decks.observeAll().collect { decks -> _state.value = DecksUiState(rows = rowsFor(decks), loading = false) }
        }
    }

    fun refresh() {
        viewModelScope.launch { _state.value = DecksUiState(rows = rowsFor(graph.decks.all()), loading = false) }
    }

    private suspend fun rowsFor(decks: List<Deck>): List<DeckRow> = withContext(Dispatchers.Default) { computeRows(decks) }

    private suspend fun computeRows(decks: List<Deck>): List<DeckRow> {
        val now = graph.clock.now()
        // One pass over the cards for every row (loading them all per deck was O(decks × cards)).
        val cardsPerDeck =
            graph.cards
                .allViews()
                .groupingBy { it.deckId }
                .eachCount()
        return decks.map { deck ->
            val ids = graph.decks.subtreeIds(deck.id)
            val due = DailyQueue.counts(graph, deck.id, now, decks)
            val review = graph.cards.reviewCards(ids)
            val scheduler = graph.scheduler(deck.settings)
            val mastery = if (review.isEmpty()) 0.0 else review.map { scheduler.retrievability(it, now) }.average()
            val count = ids.sumOf { cardsPerDeck[it] ?: 0 }
            DeckRow(deck = deck, depth = deck.path.count { it == ':' } / 2, due = due, cardCount = count, mastery = mastery)
        }
    }

    fun create(path: String) {
        viewModelScope.launch {
            graph.decks.createByPath(path)
            refresh()
        }
    }

    fun rename(
        id: Long,
        name: String,
    ) {
        viewModelScope.launch {
            graph.decks.rename(id, name)
            refresh()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            graph.decks.delete(id)
            refresh()
        }
    }

    fun updateSettings(
        id: Long,
        settings: DeckSettings,
    ) {
        viewModelScope.launch {
            graph.decks.updateSettings(id, settings)
            refresh()
        }
    }
}
