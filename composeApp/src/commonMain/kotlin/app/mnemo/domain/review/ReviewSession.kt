package app.mnemo.domain.review

import app.mnemo.di.AppGraph
import app.mnemo.domain.graph.WeakSpots
import app.mnemo.domain.model.Card
import app.mnemo.domain.model.CardState
import app.mnemo.domain.model.CardView
import app.mnemo.domain.model.Deck
import app.mnemo.domain.model.DeckSettings
import app.mnemo.domain.model.MINUTE_MS
import app.mnemo.domain.model.Rating
import app.mnemo.domain.model.ReviewMode
import app.mnemo.domain.scheduler.Scheduled
import app.mnemo.domain.scheduler.Scheduler
import app.mnemo.domain.stats.nextLocalDayStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    /** When set, the session reviews exactly these cards (a weak-spot session) instead of what is due. */
    private val cardIds: List<Long>? = null,
) {
    private val queue = ArrayDeque<Card>()
    private val undoStack = ArrayDeque<UndoEntry>()
    private val settingsByDeck = HashMap<Long, DeckSettings>()
    private val schedulers = HashMap<Long, Scheduler>()
    private var deckOfNote = HashMap<Long, Long>()

    /** Everything an action did, so [undo] can take all of it back, not just the card row. */
    private sealed class UndoEntry {
        class Answer(
            val logId: Long,
            val rating: Rating,
            val before: Card,
            val after: Card,
            /** The answer added the `leech` tag (so undo removes it). */
            val leechTagged: Boolean,
            /** The answer suspended the card as a leech (so undo unsuspends it). */
            val leechSuspended: Boolean,
        ) : UndoEntry()

        class Suspended(
            val card: Card,
        ) : UndoEntry()

        class Buried(
            val card: Card,
        ) : UndoEntry()
    }

    /** What [undo] reverted. */
    sealed class Undone {
        /** A grade was taken back; the card is at the front of the queue again. */
        data class Answer(
            val rating: Rating,
        ) : Undone()

        data object Suspend : Undone()

        data object Bury : Undone()
    }

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
        decks.forEach { settingsByDeck[it.id] = it.settings }
        queue.clear()
        undoStack.clear()
        // Daily limits: the chosen deck's (or defaults for "all decks"), each sub-deck capping its share.
        val cards =
            if (cardIds != null) {
                cardIds.mapNotNull { graph.cards.byId(it) }.filter { !it.suspended }
            } else {
                gate(DailyQueue.cards(graph, deckId, now, decks), now)
            }
        queue.addAll(cards)
        total = cards.size
        done = 0
        return total
    }

    /**
     * Prerequisite gating: in decks that opt in, a new card linked to a concept
     * whose prerequisite concepts are below [GATE_MASTERY] waits until they improve.
     */
    private suspend fun gate(
        cards: List<Card>,
        now: Long,
    ): List<Card> {
        val newCards = cards.filter { it.state == CardState.New }
        if (newCards.isEmpty()) return cards
        val gatedDecks = settingsByDeck.filterValues { it.gatePrerequisites }.keys
        if (gatedDecks.isEmpty()) return cards
        val mastery = WeakSpots.conceptMastery(graph, now)
        val prereqs =
            graph.concepts
                .edges()
                .filter {
                    it.relation == app.mnemo.domain.model.Relation.PrerequisiteOf
                }.groupBy({ it.toId }, { it.fromId })
        val blocked = HashSet<Long>()
        for (card in newCards) {
            val view = graph.cards.view(card.id) ?: continue
            if (view.deckId !in gatedDecks) continue
            val concepts = graph.concepts.conceptsFor(card.id)
            val weakPrereq =
                concepts.any { c -> prereqs[c.id].orEmpty().any { pre -> (mastery[pre] ?: 1.0) < GATE_MASTERY } }
            if (weakPrereq) blocked += card.id
        }
        return cards.filter { it.id !in blocked }
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
        // Card state and its log entry land together or not at all; a crash between them
        // would otherwise leave a graded card with nothing to undo.
        val logId =
            withContext(Dispatchers.IO) {
                graph.db.transactionWithResult {
                    graph.cards.saveNow(scheduled.card)
                    graph.reviewLogs.logNow(before, rating, responseMs, mode, now, aiVerdict, aiScore, userOverrode)
                }
            }
        done += 1

        val becameLeech = rating == Rating.Again && before.state == CardState.Review && scheduled.card.lapses >= settings.leechThreshold
        var leechTagged = false
        var leechSuspended = false
        if (becameLeech) {
            leechTagged = "leech" !in view.tags
            if (leechTagged) graph.notes.addTag(before.noteId, "leech")
            if (settings.leechSuspend && !before.suspended) {
                graph.cards.setSuspended(before.id, true)
                leechSuspended = true
            }
        }
        undoStack.addLast(UndoEntry.Answer(logId, rating, before, scheduled.card, leechTagged, leechSuspended))

        if (scheduled.card.due <= now + LEARN_AHEAD_MS) {
            // Back into this session, after everything already queued.
            queue.addLast(scheduled.card)
            total += 1
        }
        return scheduled
    }

    /**
     * Reverts the last action (answer, suspend or bury): card state, log entry,
     * leech tag/suspension and queue position. Returns what was undone, or null
     * when there is nothing to undo.
     */
    suspend fun undo(): Undone? {
        val entry = undoStack.removeLastOrNull() ?: return null
        return when (entry) {
            is UndoEntry.Answer -> {
                withContext(Dispatchers.IO) {
                    graph.db.transaction {
                        graph.cards.saveNow(entry.before)
                        graph.reviewLogs.deleteNow(entry.logId)
                    }
                }
                if (entry.leechTagged) graph.notes.removeTag(entry.before.noteId, "leech")
                if (entry.leechSuspended) graph.cards.setSuspended(entry.before.id, false)
                val requeued = queue.indexOfFirst { it.id == entry.after.id }
                if (requeued >= 0) {
                    queue.removeAt(requeued)
                    total -= 1
                }
                queue.addFirst(entry.before)
                done -= 1
                Undone.Answer(entry.rating)
            }
            is UndoEntry.Suspended -> {
                graph.cards.setSuspended(entry.card.id, false)
                queue.addFirst(entry.card)
                total += 1
                Undone.Suspend
            }
            is UndoEntry.Buried -> {
                graph.cards.bury(entry.card.id, entry.card.buriedUntil)
                queue.addFirst(entry.card)
                total += 1
                Undone.Bury
            }
        }
    }

    suspend fun suspendCurrent(): Boolean {
        val card = queue.removeFirstOrNull() ?: return false
        graph.cards.setSuspended(card.id, true)
        undoStack.addLast(UndoEntry.Suspended(card))
        total -= 1
        return true
    }

    /** Buries the current card until the start of the next local day. */
    suspend fun buryCurrent(): Boolean {
        val card = queue.removeFirstOrNull() ?: return false
        graph.cards.bury(card.id, nextLocalDayStart(graph.clock.now()))
        undoStack.addLast(UndoEntry.Buried(card))
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
        const val GATE_MASTERY = 0.70
    }
}
