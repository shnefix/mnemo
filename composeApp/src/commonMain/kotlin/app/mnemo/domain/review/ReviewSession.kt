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
import app.mnemo.domain.scheduler.LoadBalancer
import app.mnemo.domain.scheduler.Scheduled
import app.mnemo.domain.scheduler.Scheduler
import app.mnemo.domain.stats.localDate
import app.mnemo.domain.stats.nextLocalDayStart
import app.mnemo.domain.stats.plusDays
import app.mnemo.domain.stats.startOfLocalDay
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
    /** When set, only due cards in these states are studied (a Today "layer": review, learning or new). */
    private val states: Set<CardState>? = null,
    /**
     * False for a preview (custom study without rescheduling): answers change nothing and log
     * nothing; Again shows the card once more at the end.
     */
    val reschedule: Boolean = true,
) {
    private val queue = ArrayDeque<Card>()
    private val undoStack = ArrayDeque<UndoEntry>()
    private val settingsByDeck = HashMap<Long, DeckSettings>()
    private val schedulers = HashMap<Long, Scheduler>()
    private var deckOfNote = HashMap<Long, Long>()
    private var load: DayLoad? = null

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
            /** Siblings the answer buried until tomorrow, and whether each was waiting in this session. */
            val buriedSiblings: List<Pair<Card, Boolean>> = emptyList(),
        ) : UndoEntry()

        /** A preview answer: nothing was saved; Again put the card back at the end. */
        class Previewed(
            val card: Card,
            val rating: Rating,
            val requeued: Boolean,
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
                    .filter { states == null || it.state in states }
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

    /** Intervals each grade would give the current card, for the buttons (none in a preview). */
    suspend fun preview(view: CardView): Map<Rating, Scheduled> =
        if (reschedule) schedulerFor(view.deckId).preview(view.card, graph.clock.now()) else emptyMap()

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
        if (!reschedule) {
            done += 1
            val again = rating == Rating.Again
            if (again) {
                queue.addLast(before)
                total += 1
            }
            undoStack.addLast(UndoEntry.Previewed(before, rating, again))
            return null
        }
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
        if (scheduled.card.state == CardState.Review) load?.recordDue(scheduled.card.due)
        val buried = burySiblings(before, settings, now)
        undoStack.addLast(UndoEntry.Answer(logId, rating, before, scheduled.card, leechTagged, leechSuspended, buried))

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
                entry.buriedSiblings.forEach { (sib, wasQueued) ->
                    graph.cards.bury(sib.id, sib.buriedUntil)
                    if (wasQueued) {
                        queue.addLast(sib)
                        total += 1
                    }
                }
                Undone.Answer(entry.rating)
            }
            is UndoEntry.Previewed -> {
                if (entry.requeued) {
                    val i = queue.indexOfLast { it.id == entry.card.id }
                    if (i >= 0) {
                        queue.removeAt(i)
                        total -= 1
                    }
                }
                queue.addFirst(entry.card)
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

    /**
     * Anki's sibling burying: once a card is answered, its note's other new and review cards
     * (as the deck's settings say) wait until tomorrow and leave this session.
     */
    private suspend fun burySiblings(
        answered: Card,
        settings: DeckSettings,
        now: Long,
    ): List<Pair<Card, Boolean>> {
        if (!settings.buryNewSiblings && !settings.buryReviewSiblings) return emptyList()
        val until = nextLocalDayStart(now)
        val out = mutableListOf<Pair<Card, Boolean>>()
        for (sib in graph.cards.siblings(answered.noteId, answered.id)) {
            val bury =
                !sib.suspended &&
                    (
                        (sib.state == CardState.New && settings.buryNewSiblings) ||
                            (sib.state == CardState.Review && settings.buryReviewSiblings)
                    )
            if (!bury || (sib.buriedUntil ?: 0) >= until) continue
            graph.cards.bury(sib.id, until)
            val i = queue.indexOfFirst { it.id == sib.id }
            if (i >= 0) {
                queue.removeAt(i)
                total -= 1
            }
            out += sib to (i >= 0)
        }
        return out
    }

    /** Toggles [flag] on the current card (the same flag again removes it). Returns the new flag. */
    suspend fun flagCurrent(flag: Int): Int? {
        val card = queue.firstOrNull() ?: return null
        val current = graph.cards.byId(card.id)?.flag ?: 0
        val next = if (current == flag) 0 else flag
        graph.cards.setFlag(listOf(card.id), next)
        return next
    }

    /** Re-reads the current card after an edit so the displayed text is fresh. */
    fun skipCurrent() {
        queue.removeFirstOrNull()?.let { queue.addLast(it) }
    }

    private suspend fun schedulerFor(deckId: Long): Scheduler {
        schedulers[deckId]?.let { return it }
        val settings = settingsByDeck[deckId] ?: DeckSettings()
        val balancer =
            if (settings.loadBalance || settings.hasEasyDays) {
                load ?: DayLoad(graph.clock.now(), graph.cards.scheduledDues()).also { load = it }
            } else {
                null
            }
        return graph.scheduler(settings, balancer).also { schedulers[deckId] = it }
    }

    companion object {
        const val LEARN_AHEAD_MS: Long = 20 * MINUTE_MS
        const val GATE_MASTERY = 0.70
    }
}

/**
 * Reviews due per coming study day, counted once from every scheduled card's due time and kept
 * up to date as this session schedules more, for [LoadBalancer].
 */
internal class DayLoad(
    private val now: Long,
    dues: List<Long>,
) : LoadBalancer {
    private val today = startOfLocalDay(now)
    private val counts = HashMap<Int, Int>()

    init {
        dues.forEach { record(offsetOf(it)) }
    }

    /** Study-day offset of [due] from today; overdue cards count for today. */
    private fun offsetOf(due: Long): Int = if (due <= today) 0 else ((due - today) / app.mnemo.domain.model.DAY_MS).toInt()

    fun recordDue(due: Long) = record(offsetOf(due))

    override fun reviewsOn(dayOffset: Int): Int = counts[dayOffset] ?: 0

    override fun weekday(dayOffset: Int): Int = localDate(now).plusDays(dayOffset).dayOfWeek.ordinal

    override fun record(dayOffset: Int) {
        counts[dayOffset] = (counts[dayOffset] ?: 0) + 1
    }
}
