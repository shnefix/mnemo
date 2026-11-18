package app.mnemo.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mnemo.di.AppGraph
import app.mnemo.domain.cards.CardGenerator
import app.mnemo.domain.cards.RenderedCard
import app.mnemo.domain.graph.WeakSpots
import app.mnemo.domain.model.CardView
import app.mnemo.domain.model.NoteType
import app.mnemo.domain.model.Rating
import app.mnemo.domain.model.ReviewMode
import app.mnemo.domain.review.AnswerMatcher
import app.mnemo.domain.review.ReviewSession
import app.mnemo.domain.scheduler.Scheduled
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewUiState(
    val loading: Boolean = true,
    val deckName: String = "All decks",
    val language: String? = null,
    val view: CardView? = null,
    val rendered: RenderedCard? = null,
    val revealed: Boolean = false,
    val previews: Map<Rating, Scheduled> = emptyMap(),
    val done: Int = 0,
    val total: Int = 0,
    val canUndo: Boolean = false,
    val finished: Boolean = false,
    val mode: ReviewMode = ReviewMode.Classic,
    val typed: String = "",
    val verdict: AnswerMatcher.Verdict? = null,
    /** Typed-answer comparison (Type mode, after reveal): verdict plus a character diff. */
    val comparison: AnswerMatcher.Result? = null,
    val suggested: Rating? = null,
    val toast: String? = null,
    val retrievability: Double = 0.0,
    val sessionCorrect: Int = 0,
    val sessionAgain: Int = 0,
    /** The current card's deck leech threshold, for the Leech pill. */
    val leechThreshold: Int = 8,
) {
    val progress: Float get() = if (total == 0) 0f else done.toFloat() / total

    /** Typing the answer: in Type mode, and always on dictation cards (there is nothing else to do). */
    val typing: Boolean get() = rendered?.expected != null && (mode == ReviewMode.Type || rendered.listen != null)
}

class ReviewViewModel(
    private val graph: AppGraph,
    private val deckId: Long?,
    private val weak: Boolean = false,
) : ViewModel() {
    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()
    private lateinit var session: ReviewSession
    private var types: Map<Long, NoteType> = emptyMap()
    private var shownAt: Long = 0
    private var deckLanguage: String? = null

    init {
        viewModelScope.launch {
            types = graph.notes.noteTypes().associateBy { it.id }
            val deck = deckId?.let { graph.decks.byId(it) }
            session =
                if (weak) {
                    ReviewSession(graph, null, WeakSpots.sessionCards(graph, graph.clock.now()))
                } else {
                    ReviewSession(graph, deckId)
                }
            session.load()
            _state.update {
                it.copy(
                    deckName = if (weak) "Weak spots" else deck?.path ?: "All decks",
                    mode = deck?.settings?.mode ?: ReviewMode.Classic,
                    language = deck?.settings?.language,
                )
            }
            deckLanguage = deck?.settings?.language
            showNext()
        }
    }

    private suspend fun showNext() {
        val view = session.current()
        if (view == null) {
            _state.update {
                it.copy(
                    loading = false,
                    view = null,
                    rendered = null,
                    finished = true,
                    done = session.done,
                    total = session.total,
                    canUndo = session.canUndo,
                )
            }
            return
        }
        val type = types[view.noteTypeId]
        val rendered = type?.let { CardGenerator.render(it, view.fields, view.card.templateIndex) }
        val previews = session.preview(view)
        val deckSettings =
            graph.decks.byId(view.deckId)?.settings ?: app.mnemo.domain.model
                .DeckSettings()
        val scheduler = graph.scheduler(deckSettings)
        shownAt = graph.clock.now()
        _state.update {
            it.copy(
                loading = false,
                view = view,
                rendered = rendered,
                revealed = false,
                previews = previews,
                done = session.done,
                total = session.total,
                canUndo = session.canUndo,
                finished = false,
                typed = "",
                verdict = null,
                comparison = null,
                suggested = null,
                retrievability = scheduler.retrievability(view.card, shownAt),
                // The card's own deck decides the voice, so "All decks" can still read English cards.
                language = deckSettings.language ?: deckLanguage,
                leechThreshold = deckSettings.leechThreshold,
            )
        }
    }

    fun reveal() {
        val s = _state.value
        if (s.revealed || s.view == null) return
        if (s.typing && s.rendered?.expected != null) {
            val comparison = AnswerMatcher.compare(s.typed, s.rendered.expected)
            val fast = graph.clock.now() - shownAt < 6_000
            val suggested =
                when (comparison.verdict) {
                    AnswerMatcher.Verdict.Correct -> if (fast) Rating.Easy else Rating.Good
                    AnswerMatcher.Verdict.Close -> Rating.Hard
                    AnswerMatcher.Verdict.Wrong -> Rating.Again
                }
            _state.update { it.copy(revealed = true, verdict = comparison.verdict, comparison = comparison, suggested = suggested) }
        } else {
            _state.update { it.copy(revealed = true) }
        }
    }

    fun setTyped(text: String) = _state.update { it.copy(typed = text) }

    fun setMode(mode: ReviewMode) = _state.update { it.copy(mode = mode) }

    fun grade(rating: Rating) {
        val s = _state.value
        if (!s.revealed || s.view == null) return
        viewModelScope.launch {
            val responseMs = graph.clock.now() - shownAt
            val overrode = s.suggested != null && s.suggested != rating
            val result = session.answer(rating, responseMs, s.mode, s.verdict?.name?.lowercase(), null, overrode)
            val label = result?.intervalLabel ?: ""
            _state.update {
                it.copy(
                    toast = "${rating.label} · next in $label" + if (overrode) " · override logged" else "",
                    sessionCorrect = it.sessionCorrect + if (rating != Rating.Again) 1 else 0,
                    sessionAgain = it.sessionAgain + if (rating == Rating.Again) 1 else 0,
                )
            }
            showNext()
        }
    }

    fun undo() {
        viewModelScope.launch {
            when (val undone = session.undo()) {
                null -> return@launch
                is ReviewSession.Undone.Answer -> {
                    _state.update {
                        it.copy(
                            toast = "Last review undone",
                            sessionCorrect = it.sessionCorrect - if (undone.rating != Rating.Again) 1 else 0,
                            sessionAgain = it.sessionAgain - if (undone.rating == Rating.Again) 1 else 0,
                        )
                    }
                    showNext()
                    _state.update { it.copy(revealed = true) }
                }
                ReviewSession.Undone.Suspend -> {
                    _state.update { it.copy(toast = "Suspend undone") }
                    showNext()
                }
                ReviewSession.Undone.Bury -> {
                    _state.update { it.copy(toast = "Bury undone") }
                    showNext()
                }
            }
        }
    }

    fun suspendCurrent() {
        viewModelScope.launch {
            if (session.suspendCurrent()) {
                _state.update { it.copy(toast = "Card suspended") }
                showNext()
            }
        }
    }

    fun buryCurrent() {
        viewModelScope.launch {
            if (session.buryCurrent()) {
                _state.update { it.copy(toast = "Card buried until tomorrow") }
                showNext()
            }
        }
    }

    /** Re-reads the current card (after returning from the editor). */
    fun refreshCurrent() {
        viewModelScope.launch {
            types = graph.notes.noteTypes().associateBy { it.id }
            val wasRevealed = _state.value.revealed
            showNext()
            if (wasRevealed) _state.update { it.copy(revealed = true) }
        }
    }

    fun dismissToast() = _state.update { it.copy(toast = null) }
}
