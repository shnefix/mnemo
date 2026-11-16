package app.mnemo.domain.review

import app.mnemo.di.AppGraph
import app.mnemo.domain.model.Card
import app.mnemo.domain.model.CardState
import app.mnemo.domain.model.CardView
import app.mnemo.domain.model.DAY_MS
import app.mnemo.domain.model.Deck
import app.mnemo.domain.model.DeckSettings
import app.mnemo.domain.model.MINUTE_MS
import app.mnemo.domain.model.Rating
import app.mnemo.domain.model.ReviewMode
import app.mnemo.domain.scheduler.Scheduled
import app.mnemo.domain.scheduler.Scheduler

/**
 * One sitting of reviews over a deck subtree (or every deck when [deckId] is
 * null). Holds the queue, grades cards through the deck's [Scheduler], logs
 * each answer and supports undo.
 *
 * Learning cards whose next step falls within [LEARN_AHEAD_MS] come back into
 * the queue in the same session, like Anki's learn-ahead limit.
 */
class ReviewSession(
    private val graph: AppGraph,
    private val deckId: Long?,
) {
    private val queue = ArrayDeque<Card>()
    private val undoStack = ArrayDeque<UndoEntry>()
    private val settingsByDeck = HashMap<Long, DeckSettings>()
    private val schedulers = HashMap<Long, Scheduler>()
    private var deckOfNote = HashMap<Long, Long>()

    private class UndoEntry(
        val logId: Long,
        val before: Card,
        val after: Card,
    )

    var total: Int = 0
        private set
    var done: Int = 0
        private set

    val remaining: Int get() = queue.size
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val isFinished: Boolean get() = queue.isEmpty()

    /** Builds the queue. Returns the number of cards in it. */
    suspend fun load(): Int {
        val now = graph.clock.now()
        val decks: List<Deck> = graph.decks.all()
        val scope: List<Deck> =
            if (deckId == null) {
                decks
            } else {
                val ids = graph.decks.subtreeIds(deckId).toSet()
                decks.filter { it.id in ids }
            }
        decks.forEach { settingsByDeck[it.id] = it.settings }
        queue.clear()
        undoStack.clear()
        // Limits come from the chosen deck (or defaults for "all decks").
        val top = if (deckId != null) decks.firstOrNull { it.id == deckId } else null
        val settings = top?.settings ?: DeckSettings()
        val cards =
            graph.cards.dueQueue(
                deckIds = scope.map { it.id },
                now = now,
                newLimit = settings.newPerDay,
                reviewLimit = settings.reviewsPerDay,
            )
        queue.addAll(cards)
        total = cards.size
        done = 0
        return total
    }

    suspend fun current(): CardView? {
        val card = queue.firstOrNull() ?: return null
        val view =
            graph.cards.view(card.id) ?: run {
                queue.removeFirst()
                return current()
            }
        deckOfNote[view.card.noteId] = view.deckId
        return view
    }

    /** Intervals each grade would give the current card, for the buttons. */
    suspend fun preview(view: CardView): Map<Rating, Scheduled> = schedulerFor(view.deckId).preview(view.card, graph.clock.now())

    /** Grades the current card. Returns the schedule applied, or null when nothing is queued. */
    suspend fun answer(
        rating: Rating,
        responseMs: Long,
        mode: ReviewMode = ReviewMode.Classic,
        aiVerdict: String? = null,
        aiScore: Double? = null,
        userOverrode: Boolean = false,
    ): Scheduled? {
        val before = queue.removeFirstOrNull() ?: return null
        val view = graph.cards.view(before.id) ?: return null
        val now = graph.clock.now()
        val settings = settingsByDeck[view.deckId] ?: DeckSettings()
        val scheduled = schedulerFor(view.deckId).schedule(before, rating, now)
        graph.cards.save(scheduled.card)
        val logId = graph.reviewLogs.log(before, rating, responseMs, mode, now, aiVerdict, aiScore, userOverrode)
        undoStack.addLast(UndoEntry(logId, before, scheduled.card))
        done += 1

        if (rating == Rating.Again && before.state == CardState.Review && scheduled.card.lapses >= settings.leechThreshold) {
            graph.notes.addTag(before.noteId, "leech")
            if (settings.leechSuspend) graph.cards.setSuspended(before.id, true)
        }

        if (scheduled.card.due <= now + LEARN_AHEAD_MS) {
            // Back into this session, after everything already queued.
            queue.addLast(scheduled.card)
            total += 1
        }
        return scheduled
    }

    /** Reverts the last answer: card state, log entry and queue position. */
    suspend fun undo(): Boolean {
        val entry = undoStack.removeLastOrNull() ?: return false
        graph.cards.save(entry.before)
        graph.reviewLogs.delete(entry.logId)
        val requeued = queue.indexOfFirst { it.id == entry.after.id }
        if (requeued >= 0) {
            queue.removeAt(requeued)
            total -= 1
        }
        queue.addFirst(entry.before)
        done -= 1
        return true
    }

    suspend fun suspendCurrent(): Boolean {
        val card = queue.removeFirstOrNull() ?: return false
        graph.cards.setSuspended(card.id, true)
        total -= 1
        return true
    }

    suspend fun buryCurrent(): Boolean {
        val card = queue.removeFirstOrNull() ?: return false
        val now = graph.clock.now()
        graph.cards.bury(card.id, now - (now % DAY_MS) + DAY_MS)
        total -= 1
        return true
    }

    /** Re-reads the current card after an edit so the displayed text is fresh. */
    fun skipCurrent() {
        queue.removeFirstOrNull()?.let { queue.addLast(it) }
    }

    private fun schedulerFor(deckId: Long): Scheduler =
        schedulers.getOrPut(deckId) { graph.scheduler(settingsByDeck[deckId] ?: DeckSettings()) }

    companion object {
        const val LEARN_AHEAD_MS: Long = 20 * MINUTE_MS
    }
}
