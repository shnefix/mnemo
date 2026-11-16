package app.mnemo.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mnemo.di.AppGraph
import app.mnemo.domain.cards.CardGenerator
import app.mnemo.domain.model.CardView
import app.mnemo.domain.model.Deck
import app.mnemo.domain.model.NoteType
import app.mnemo.domain.search.SearchQuery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SortKey(
    val label: String,
) {
    Due("Due"),
    Created("Created"),
    Deck("Deck"),
    State("State"),
    Lapses("Lapses"),
}

data class BrowseRow(
    val view: CardView,
    val question: String,
    val answer: String,
)

data class BrowseUiState(
    val query: String = "",
    val rows: List<BrowseRow> = emptyList(),
    val totalCards: Int = 0,
    val sort: SortKey = SortKey.Due,
    val ascending: Boolean = true,
    val selected: Set<Long> = emptySet(),
    val decks: List<Deck> = emptyList(),
    val loading: Boolean = true,
)

class BrowseViewModel(
    private val graph: AppGraph,
) : ViewModel() {
    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()
    private val queryFlow = MutableStateFlow("")
    private val sortFlow = MutableStateFlow(SortKey.Due to true)
    private var types: Map<Long, NoteType> = emptyMap()

    init {
        viewModelScope.launch {
            types = graph.notes.noteTypes().associateBy { it.id }
            combine(graph.cards.observeAllViews(), queryFlow, sortFlow, graph.decks.observeAll()) { views, q, sort, decks ->
                Triple(views, q, sort) to decks
            }.collect { (triple, decks) ->
                val (views, q, sort) = triple
                val now = graph.clock.now()
                val parsed = SearchQuery.parse(q)
                val filtered = views.filter { parsed.matches(it, now) }
                val rows = filtered.map { v -> toRow(v) }
                val sorted =
                    when (sort.first) {
                        SortKey.Due -> rows.sortedBy { it.view.card.due }
                        SortKey.Created -> rows.sortedBy { it.view.card.id }
                        SortKey.Deck -> rows.sortedBy { it.view.deckName.lowercase() }
                        SortKey.State -> rows.sortedBy { it.view.card.state.code }
                        SortKey.Lapses -> rows.sortedBy { it.view.card.lapses }
                    }.let { if (sort.second) it else it.reversed() }
                _state.update {
                    it.copy(
                        rows = sorted,
                        totalCards = views.size,
                        sort = sort.first,
                        ascending = sort.second,
                        decks = decks,
                        loading = false,
                        selected = it.selected.filter { id -> sorted.any { r -> r.view.card.id == id } }.toSet(),
                    )
                }
            }
        }
    }

    private fun toRow(v: CardView): BrowseRow {
        val type = types[v.noteTypeId]
        val rendered =
            if (type != null) {
                CardGenerator.render(type, v.fields, v.card.templateIndex)
            } else {
                null
            }
        return BrowseRow(
            view = v,
            question =
                plainText(
                    rendered?.question ?: v.fields.values
                        .firstOrNull()
                        .orEmpty(),
                ),
            answer =
                plainText(
                    rendered?.answer ?: v.fields.values
                        .drop(1)
                        .firstOrNull()
                        .orEmpty(),
                ),
        )
    }

    /** Strips the Markdown marks a table cell shouldn't show. */
    private fun plainText(s: String): String = s.replace(Regex("""[*_`#>]+"""), "").replace(Regex("""\s+"""), " ").trim()

    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
        queryFlow.value = q
    }

    fun sortBy(key: SortKey) {
        val (current, asc) = sortFlow.value
        sortFlow.value = if (current == key) key to !asc else key to true
    }

    fun toggle(id: Long) = _state.update { it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id) }

    fun selectAll() = _state.update { s -> s.copy(selected = s.rows.map { it.view.card.id }.toSet()) }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    private fun selectedNoteIds(): Set<Long> =
        _state.value.rows
            .filter {
                it.view.card.id in _state.value.selected
            }.map { it.view.card.noteId }
            .toSet()

    fun addTag(tag: String) {
        viewModelScope.launch {
            selectedNoteIds().forEach { graph.notes.addTag(it, tag) }
        }
    }

    fun removeTag(tag: String) {
        viewModelScope.launch {
            selectedNoteIds().forEach { graph.notes.removeTag(it, tag) }
        }
    }

    fun moveToDeck(deck: Deck) {
        viewModelScope.launch {
            selectedNoteIds().forEach { graph.notes.moveToDeck(it, deck.id) }
        }
    }

    fun setSuspended(suspended: Boolean) {
        viewModelScope.launch {
            _state.value.selected.forEach { graph.cards.setSuspended(it, suspended) }
        }
    }

    fun resetSelected() {
        viewModelScope.launch {
            val now = graph.clock.now()
            _state.value.selected.forEach { graph.cards.reset(it, now) }
        }
    }

    fun deleteSelected() {
        viewModelScope.launch {
            selectedNoteIds().forEach { graph.notes.delete(it) }
            clearSelection()
        }
    }
}
