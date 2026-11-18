package app.mnemo.ui.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mnemo.data.io.DeckTrash
import app.mnemo.data.repo.DueCounts
import app.mnemo.di.AppGraph
import app.mnemo.domain.model.Deck
import app.mnemo.domain.model.DeckSettings
import app.mnemo.domain.review.DailyQueue
import app.mnemo.domain.stats.DeckOverview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.withIndex
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

@OptIn(FlowPreview::class)
class DecksViewModel(
    private val graph: AppGraph,
) : ViewModel() {
    private val _state = MutableStateFlow(DecksUiState())
    val state: StateFlow<DecksUiState> = _state.asStateFlow()

    private val visible = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            // Recompute when decks change and after reviews (due counts move), but only while the
            // list shows: coming back after a review session recomputes once. collectLatest makes
            // a burst of changes (seeding, import, grading) compute only the last state.
            val changes =
                graph.cards
                    .observeChanges()
                    .withIndex()
                    .map { it.index }
            combine(graph.decks.observeAll(), changes, visible) { decks, change, shown ->
                if (shown) decks to change else null
            }.filterNotNull()
                .distinctUntilChanged()
                .debounce(DEBOUNCE_MS)
                .collectLatest { (decks, _) -> _state.value = DecksUiState(rows = rowsFor(decks), loading = false) }
        }
    }

    /** Called by the screen as it enters/leaves composition. */
    fun setVisible(shown: Boolean) {
        visible.value = shown
    }

    fun refresh() {
        viewModelScope.launch { _state.value = DecksUiState(rows = rowsFor(graph.decks.all()), loading = false) }
    }

    private suspend fun rowsFor(decks: List<Deck>): List<DeckRow> = withContext(Dispatchers.Default) { computeRows(decks) }

    private suspend fun computeRows(decks: List<Deck>): List<DeckRow> {
        val now = graph.clock.now()
        // A few grouped queries for the whole list; per-deck queries were O(decks × sub-decks).
        val queue = DailyQueue.snapshot(graph, now, decks)
        val overview = DeckOverview.load(graph, decks)
        return decks.map { deck ->
            DeckRow(
                deck = deck,
                depth = deck.path.count { it == ':' } / 2,
                due = queue.counts(deck.id),
                cardCount = overview.cardCount(deck),
                mastery = overview.mastery(graph, deck, now),
            )
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

    private val _deleted = MutableStateFlow<DeckTrash.Entry?>(null)

    /** The deck just deleted, while its Undo is offered. */
    val deleted: StateFlow<DeckTrash.Entry?> = _deleted.asStateFlow()

    /** Moves the deck (and its sub-decks) to the trash and offers Undo. */
    fun delete(id: Long) {
        viewModelScope.launch {
            _deleted.value = runCatching { DeckTrash.delete(graph, id) }.getOrNull()
            refresh()
        }
    }

    fun undoDelete() {
        val entry = _deleted.value ?: return
        _deleted.value = null
        viewModelScope.launch {
            runCatching { DeckTrash.restore(graph, entry) }
            refresh()
        }
    }

    fun dismissUndo() {
        _deleted.value = null
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

    companion object {
        private const val DEBOUNCE_MS = 150L
    }
}
