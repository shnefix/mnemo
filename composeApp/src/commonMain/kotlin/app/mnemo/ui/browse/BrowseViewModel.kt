package app.mnemo.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mnemo.data.repo.BrowseRepository
import app.mnemo.di.AppGraph
import app.mnemo.domain.cards.CardGenerator
import app.mnemo.domain.model.CardView
import app.mnemo.domain.model.Deck
import app.mnemo.domain.model.NoteType
import app.mnemo.domain.search.SearchQuery
import app.mnemo.domain.search.SortKey
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BrowseRow(
    val view: CardView,
    val question: String,
    val answer: String,
)

data class BrowseUiState(
    val query: String = "",
    /** The rows loaded so far (the first [BrowseViewModel.PAGE] matches, plus whatever "load more" added). */
    val rows: List<BrowseRow> = emptyList(),
    /** How many cards match the query in total. */
    val matched: Int = 0,
    val totalCards: Int = 0,
    val sort: SortKey = SortKey.Due,
    val ascending: Boolean = true,
    val selected: Set<Long> = emptySet(),
    val decks: List<Deck> = emptyList(),
    val loading: Boolean = true,
    val noteTypes: List<NoteType> = emptyList(),
    /** The result of the last bulk action ("Flagged 3 cards"), shown briefly. */
    val message: String? = null,
) {
    val hasMore: Boolean get() = rows.size < matched
}

/**
 * Browse filters and sorts in SQL and loads card views one page at a time.
 * Selection is by card id and may cover matches that are not loaded yet
 * ("select all"), so bulk actions resolve note ids from the match list.
 */
@OptIn(FlowPreview::class)
class BrowseViewModel(
    private val graph: AppGraph,
) : ViewModel() {
    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()
    private val queryFlow = MutableStateFlow("")
    private val sortFlow = MutableStateFlow(SortKey.Due to true)
    private val visible = MutableStateFlow(false)
    private var types: Map<Long, NoteType> = emptyMap()

    private data class LoadKey(
        val change: Int,
        val query: String,
        val sort: Pair<SortKey, Boolean>,
        val decks: List<Deck>,
    )

    /** Called by the screen as it enters/leaves composition. */
    fun setVisible(shown: Boolean) {
        visible.value = shown
    }

    private var matches: List<BrowseRepository.Match> = emptyList()

    init {
        viewModelScope.launch {
            types = graph.notes.noteTypes().associateBy { it.id }
            _state.update { it.copy(noteTypes = types.values.toList()) }
            // Reload only while the screen shows: once opened, this view model outlives the tab,
            // and reloading on every grade of a review session made answering cards stutter.
            // Coming back reloads once if anything changed in the meantime.
            val changes =
                graph.cards
                    .observeChanges()
                    .withIndex()
                    .map { it.index }
            combine(changes, queryFlow, sortFlow, graph.decks.observeAll(), visible) { change, q, sort, decks, shown ->
                if (shown) LoadKey(change, q, sort, decks) else null
            }.filterNotNull().distinctUntilChanged().debounce(DEBOUNCE_MS).collectLatest { key ->
                reload(key.query, key.sort, key.decks)
            }
        }
    }

    private suspend fun reload(
        q: String,
        sort: Pair<SortKey, Boolean>,
        decks: List<Deck>,
    ) {
        val now = graph.clock.now()
        matches = graph.browse.matches(SearchQuery.parse(q), sort.first, sort.second, now)
        val total = graph.browse.countAll().toInt()
        // Keep what was scrolled into view, so a grade or tag edit does not jump back to the top.
        val keep =
            _state.value.rows.size
                .coerceIn(PAGE, MAX_RELOAD)
        val rows = loadRows(matches.take(keep))
        val ids = matches.map { it.cardId }.toSet()
        _state.update {
            it.copy(
                rows = rows,
                matched = matches.size,
                totalCards = total,
                sort = sort.first,
                ascending = sort.second,
                decks = decks,
                loading = false,
                selected = it.selected.filter { id -> id in ids }.toSet(),
            )
        }
    }

    /** Appends the next page of matches to [BrowseUiState.rows]. */
    fun loadMore() {
        val s = _state.value
        if (!s.hasMore) return
        viewModelScope.launch {
            val next = matches.drop(s.rows.size).take(PAGE)
            val rows = loadRows(next)
            _state.update { it.copy(rows = it.rows + rows) }
        }
    }

    /** Rendering card text for up to [MAX_RELOAD] rows is real work: keep it off the main thread. */
    private suspend fun loadRows(page: List<BrowseRepository.Match>): List<BrowseRow> {
        val views = graph.cards.viewsByIds(page.map { it.cardId })
        return withContext(Dispatchers.Default) { views.map { toRow(it) } }
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
                    rendered?.occlusion?.let { o -> rendered.question.ifBlank { "Image occlusion · ${o.image}" } }
                        ?: rendered?.question ?: v.fields.values
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
    private fun plainText(s: String): String = s.replace(MARKS, "").replace(SPACES, " ").trim()

    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
        queryFlow.value = q
    }

    fun sortBy(key: SortKey) {
        val (current, asc) = sortFlow.value
        sortFlow.value = if (current == key) key to !asc else key to true
    }

    fun toggle(id: Long) = _state.update { it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id) }

    /** Selects every match, loaded or not. */
    fun selectAll() = _state.update { s -> s.copy(selected = matches.map { it.cardId }.toSet()) }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    private fun selectedNoteIds(): Set<Long> {
        val selected = _state.value.selected
        return matches.filter { it.cardId in selected }.map { it.noteId }.toSet()
    }

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

    fun setFlag(flag: Int) {
        viewModelScope.launch {
            val ids = _state.value.selected
            graph.cards.setFlag(ids, flag)
            say(if (flag == 0) "Flag removed from ${ids.size} cards" else "Flagged ${ids.size} cards")
        }
    }

    /** Anki's Set due date on the selection; false when [spec] can't be read. */
    fun setDueDate(spec: String): Boolean {
        if (app.mnemo.data.repo
                .parseDueSpec(spec) == null
        ) {
            return false
        }
        viewModelScope.launch {
            val n = graph.cards.setDueDate(_state.value.selected, spec, graph.clock.now()) ?: 0
            say("Due date set on $n cards")
        }
        return true
    }

    fun reposition(how: app.mnemo.data.repo.CardRepository.Reposition) {
        viewModelScope.launch {
            val n = graph.cards.reposition(_state.value.selected, how)
            say(if (n == 0) "Only new cards can be repositioned" else "Repositioned $n new cards")
        }
    }

    /**
     * Find and replace in the selected notes, or in every match when nothing is selected.
     * Returns an error for a bad regular expression.
     */
    fun findReplace(
        find: String,
        replace: String,
        field: String?,
        regex: Boolean,
        matchCase: Boolean,
    ): String? {
        if (regex) runCatching { Regex(find) }.onFailure { return "Not a valid regular expression: ${it.message}" }
        val notes = if (_state.value.selected.isEmpty()) matches.map { it.noteId }.toSet() else selectedNoteIds()
        viewModelScope.launch {
            val n = graph.notes.findReplace(notes, find, replace, field, regex, matchCase)
            say("Replaced in $n of ${notes.size} notes")
        }
        return null
    }

    /** Groups of notes whose [field] holds the same text. */
    suspend fun duplicates(
        type: NoteType,
        field: String,
    ): List<Pair<String, List<Long>>> = graph.notes.duplicates(type.id, field)

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun say(text: String) = _state.update { it.copy(message = text) }

    fun deleteSelected() {
        viewModelScope.launch {
            selectedNoteIds().forEach { graph.notes.delete(it) }
            clearSelection()
        }
    }

    companion object {
        const val PAGE = 100
        private const val MAX_RELOAD = 1_000
        private const val DEBOUNCE_MS = 150L
        private val MARKS = Regex("""[*_`#>]+""")
        private val SPACES = Regex("""\s+""")
    }
}
