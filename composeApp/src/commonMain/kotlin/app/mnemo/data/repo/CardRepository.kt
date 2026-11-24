package app.mnemo.data.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import app.mnemo.data.db.decodeTags
import app.mnemo.data.db.toDomain
import app.mnemo.db.MnemoDatabase
import app.mnemo.domain.model.Card
import app.mnemo.domain.model.CardState
import app.mnemo.domain.model.CardView
import app.mnemo.domain.model.DAY_MS
import app.mnemo.domain.model.NewOrder
import app.mnemo.domain.stats.startOfLocalDay
import app.mnemo.domain.stats.startOfLocalDayPlus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Counts of what is due right now, split by state. */
data class DueCounts(
    val new: Int,
    val learning: Int,
    val review: Int,
) {
    val total: Int get() = new + learning + review
}

class CardRepository(
    private val db: MnemoDatabase,
) {
    private val q get() = db.cardQueries

    suspend fun byId(id: Long): Card? = withContext(Dispatchers.IO) { q.selectById(id).executeAsOneOrNull()?.toDomain() }

    suspend fun view(id: Long): CardView? = withContext(Dispatchers.IO) { q.cardView(id).executeAsOneOrNull()?.toDomain() }

    suspend fun allViews(): List<CardView> = withContext(Dispatchers.IO) { q.allCardViews().executeAsList().map { it.toDomain() } }

    /** Views for [ids], in the order given (missing ids are skipped). */
    suspend fun viewsByIds(ids: List<Long>): List<CardView> =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext emptyList()
            val byId = q.cardViewsByIds(ids).executeAsList().associateBy { it.id }
            ids.mapNotNull { byId[it]?.toDomain() }
        }

    /**
     * Emits whenever a card, note or deck changes, without loading anything:
     * what Browse and Today re-query on. The value itself is meaningless.
     */
    fun observeChanges(): Flow<Long> = q.changeStamp().asFlow().mapToOne(Dispatchers.IO)

    suspend fun byNote(noteId: Long): List<Card> =
        withContext(Dispatchers.IO) { q.selectByNote(noteId).executeAsList().map { it.toDomain() } }

    /**
     * New, unsuspended, unburied cards whose notes sit directly in [deckIds], in [order]
     * (a shuffled order stays the same all day). With [onePerNote] only the first card of
     * each note comes, so siblings wait for another day.
     */
    suspend fun newCards(
        deckIds: List<Long>,
        now: Long,
        limit: Int,
        order: NewOrder = NewOrder.Added,
        onePerNote: Boolean = false,
    ): List<Card> =
        withContext(Dispatchers.IO) {
            if (deckIds.isEmpty() || limit <= 0) return@withContext emptyList()
            newCardsNow(deckIds, now, limit, order, onePerNote)
        }

    private fun newCardsNow(
        deckIds: List<Long>,
        now: Long,
        limit: Int,
        order: NewOrder,
        onePerNote: Boolean,
    ): List<Card> =
        q
            .newCards(
                deckIds,
                now,
                if (onePerNote) 1L else 0L,
                if (order == NewOrder.Random) 1L else 0L,
                startOfLocalDay(now) / DAY_MS,
                limit.toLong(),
            ).executeAsList()
            .map { it.toDomain() }

    /** Cards in [deckIds] seen for the first time since the start of the local day. */
    suspend fun newIntroducedToday(
        deckIds: List<Long>,
        now: Long,
    ): Int =
        withContext(Dispatchers.IO) {
            if (deckIds.isEmpty()) return@withContext 0
            db.reviewLogQueries
                .newIntroducedSince(deckIds, startOfLocalDay(now))
                .executeAsOne()
                .toInt()
        }

    /** What is left of today's limits once the cards already introduced/reviewed today are subtracted. */
    private fun remainingToday(
        deckIds: List<Long>,
        now: Long,
        newLimit: Int,
        reviewLimit: Int,
    ): Pair<Int, Int> {
        val dayStart = startOfLocalDay(now)
        val logs = db.reviewLogQueries
        val newDone = logs.newIntroducedSince(deckIds, dayStart).executeAsOne().toInt()
        val reviewsDone = logs.reviewsDoneSince(deckIds, dayStart).executeAsOne().toInt()
        return (newLimit - newDone).coerceAtLeast(0) to (reviewLimit - reviewsDone).coerceAtLeast(0)
    }

    /**
     * Cards due now in [deckIds] (learning first, then review), plus new cards.
     * [newLimit] and [reviewLimit] are *daily* limits: cards already introduced
     * or reviewed since the start of the local day count against them, so
     * reopening Review does not hand out another batch.
     */
    suspend fun dueQueue(
        deckIds: List<Long>,
        now: Long,
        newLimit: Int,
        reviewLimit: Int,
    ): List<Card> =
        withContext(Dispatchers.IO) {
            if (deckIds.isEmpty()) return@withContext emptyList()
            val (newLeft, reviewLeft) = remainingToday(deckIds, now, newLimit, reviewLimit)
            val due = q.dueCards(deckIds, now).executeAsList().map { it.toDomain() }
            val learning = due.filter { it.state == CardState.Learning || it.state == CardState.Relearning }
            val review = due.filter { it.state == CardState.Review }.take(reviewLeft)
            val new = if (newLeft > 0) newCardsNow(deckIds, now, newLeft, NewOrder.Added, false) else emptyList()
            learning + review + new
        }

    /** Due counts under today's remaining limits (see [dueQueue]). */
    suspend fun dueCounts(
        deckIds: List<Long>,
        now: Long,
        newLimit: Int,
        reviewLimit: Int,
    ): DueCounts =
        withContext(Dispatchers.IO) {
            if (deckIds.isEmpty()) return@withContext DueCounts(0, 0, 0)
            val (newLeft, reviewLeft) = remainingToday(deckIds, now, newLimit, reviewLimit)
            val rows = q.countByStateDue(deckIds, now).executeAsList()
            var learning = 0
            var review = 0
            rows.forEach { r ->
                when (CardState.fromCode(r.state)) {
                    CardState.Learning, CardState.Relearning -> learning += r.count.toInt()
                    CardState.Review -> review += r.count.toInt()
                    CardState.New -> {}
                }
            }
            val new = q.countNew(deckIds).executeAsOne().toInt()
            DueCounts(new = minOf(new, newLeft), learning = learning, review = minOf(review, reviewLeft))
        }

    /** Review cards due per local day for the next [days] days (index 0 = today, including overdue). */
    suspend fun forecast(
        deckIds: List<Long>,
        now: Long,
        days: Int,
    ): List<Int> =
        withContext(Dispatchers.IO) {
            if (deckIds.isEmpty()) return@withContext List(days) { 0 }
            (0 until days).map { d ->
                val from = if (d == 0) Long.MIN_VALUE else startOfLocalDayPlus(now, d)
                val to = startOfLocalDayPlus(now, d + 1)
                q.countDueBetween(deckIds, from, to).executeAsOne().toInt()
            }
        }

    suspend fun reviewCards(deckIds: List<Long>): List<Card> =
        withContext(Dispatchers.IO) {
            if (deckIds.isEmpty()) emptyList() else q.reviewCardsInDecks(deckIds).executeAsList().map { it.toDomain() }
        }

    suspend fun save(card: Card) = withContext(Dispatchers.IO) { saveNow(card) }

    /** Blocking [save], for use inside an enclosing `db.transaction`. */
    fun saveNow(card: Card) {
        q.updateScheduling(
            card.state.code,
            card.due,
            card.stability,
            card.difficulty,
            card.elapsedDays.toLong(),
            card.scheduledDays.toLong(),
            card.reps.toLong(),
            card.lapses.toLong(),
            card.lastReview,
            card.learningStep?.toLong(),
            card.id,
        )
    }

    /** Sets the Anki flag (0 = none, 1–7) on [ids]. */
    suspend fun setFlag(
        ids: Collection<Long>,
        flag: Int,
    ) = withContext(Dispatchers.IO) {
        db.transaction { ids.forEach { q.setFlag(flag.coerceIn(0, 7).toLong(), it) } }
    }

    /** Where repositioned new cards go. */
    enum class Reposition { Front, End, Shuffle }

    /**
     * Moves the new cards among [ids] to the front or end of the new-card queue (keeping their
     * order among themselves), or shuffles their places. Other cards are left alone.
     */
    suspend fun reposition(
        ids: Collection<Long>,
        how: Reposition,
        random: kotlin.random.Random = kotlin.random.Random.Default,
    ): Int =
        withContext(Dispatchers.IO) {
            db.transactionWithResult {
                val cards =
                    ids
                        .mapNotNull { q.selectById(it).executeAsOneOrNull()?.toDomain() }
                        .filter { it.state == CardState.New }
                        .sortedWith(compareBy({ it.due }, { it.id }))
                if (cards.isEmpty()) return@transactionWithResult 0
                val range = q.newPositionRange().executeAsOne()
                val lo = range.lo ?: 0L
                val hi = range.hi ?: 0L
                val positions =
                    when (how) {
                        Reposition.Front -> cards.indices.map { lo - cards.size + it }
                        Reposition.End -> cards.indices.map { hi + 1 + it }
                        Reposition.Shuffle -> cards.map { it.due }.shuffled(random)
                    }
                cards.zip(positions).forEach { (c, pos) -> q.setPosition(pos, c.id) }
                cards.size
            }
        }

    /**
     * Anki's "Set due date": [spec] is a number of days ("0" = today), a range ("3-7", a day picked
     * at random for each card), and a trailing "!" also makes that the card's interval. New and
     * learning cards become review cards (a new card starts with a stability of the interval).
     * Returns the cards changed, or null when [spec] can't be read.
     */
    suspend fun setDueDate(
        ids: Collection<Long>,
        spec: String,
        now: Long,
        random: kotlin.random.Random = kotlin.random.Random.Default,
    ): Int? {
        val parsed = parseDueSpec(spec) ?: return null
        return withContext(Dispatchers.IO) {
            db.transactionWithResult {
                var n = 0
                ids.forEach { id ->
                    val c = q.selectById(id).executeAsOneOrNull()?.toDomain() ?: return@forEach
                    val days = if (parsed.first == parsed.second) parsed.first else random.nextInt(parsed.first, parsed.second + 1)
                    val due = if (days == 0) now else startOfLocalDayPlus(now, days)
                    val updated =
                        if (c.state == CardState.Review && !parsed.third) {
                            c.copy(due = due)
                        } else {
                            c.copy(
                                state = CardState.Review,
                                due = due,
                                stability = c.stability ?: days.coerceAtLeast(1).toDouble(),
                                difficulty = c.difficulty ?: 5.0,
                                scheduledDays = days,
                                lastReview = c.lastReview ?: now,
                                learningStep = null,
                            )
                        }
                    saveNow(updated)
                    n += 1
                }
                n
            }
        }
    }

    /** The deck of each card in [ids] (its note's deck). */
    suspend fun deckOfCards(ids: List<Long>): Map<Long, Long> =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext emptyMap()
            ids.chunked(500).flatMap { chunk -> q.deckOfCards(chunk).executeAsList().map { it.id to it.deck_id } }.toMap()
        }

    /** Siblings: the other cards of [noteId]. */
    suspend fun siblings(
        noteId: Long,
        cardId: Long,
    ): List<Card> = byNote(noteId).filter { it.id != cardId }

    /** Due times of every scheduled, unsuspended card (load balancing counts these per day). */
    suspend fun scheduledDues(): List<Long> = withContext(Dispatchers.IO) { q.scheduledDues().executeAsList() }

    suspend fun setSuspended(
        id: Long,
        suspended: Boolean,
    ) = withContext(Dispatchers.IO) { q.setSuspended(if (suspended) 1L else 0L, id) }

    suspend fun bury(
        id: Long,
        until: Long?,
    ) = withContext(Dispatchers.IO) { q.setBuried(until, id) }

    suspend fun reset(
        id: Long,
        now: Long,
    ) = withContext(Dispatchers.IO) { q.resetCard(now, id) }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { q.delete(id) }

    suspend fun leeches(threshold: Int): List<Card> =
        withContext(Dispatchers.IO) {
            q.leeches(threshold.toLong()).executeAsList().map { it.toDomain() }
        }

    suspend fun countAll(): Long = withContext(Dispatchers.IO) { q.countAll().executeAsOne() }

    /** Every card, without note text: one query instead of one per card. */
    suspend fun all(): List<Card> = withContext(Dispatchers.IO) { q.selectAll().executeAsList().map { it.toDomain() } }

    /** The deck and tags of every card, without loading the note fields. */
    suspend fun decksAndTags(): List<CardPlacement> =
        withContext(Dispatchers.IO) {
            q.cardDecks().executeAsList().map { CardPlacement(it.id, it.deck_id, decodeTags(it.tags)) }
        }

    /** New cards available now, by deck: (cards, notes they belong to). */
    suspend fun newCountByDeck(now: Long): Map<Long, Pair<Int, Int>> =
        withContext(Dispatchers.IO) {
            q.newCountByDeck(now).executeAsList().associate { it.deck_id to (it.n.toInt() to it.notes.toInt()) }
        }

    /** Learning and review cards due now in one deck; [reviewNotes] counts one review card per note. */
    data class DueByDeck(
        val learning: Int,
        val review: Int,
        val reviewNotes: Int,
    )

    /** Learning and review cards due now, by deck. */
    suspend fun dueCountByDeck(now: Long): Map<Long, DueByDeck> =
        withContext(Dispatchers.IO) {
            val out = HashMap<Long, DueByDeck>()
            q.dueCountByDeck(now).executeAsList().forEach { r ->
                val d = out[r.deck_id] ?: DueByDeck(0, 0, 0)
                out[r.deck_id] =
                    when (CardState.fromCode(r.state)) {
                        CardState.Learning, CardState.Relearning -> d.copy(learning = d.learning + r.n.toInt())
                        CardState.Review -> d.copy(review = d.review + r.n.toInt(), reviewNotes = d.reviewNotes + r.notes.toInt())
                        CardState.New -> d
                    }
            }
            out
        }

    /** Cards first seen since [since], by deck. */
    suspend fun newIntroducedByDeck(since: Long): Map<Long, Int> =
        withContext(Dispatchers.IO) {
            db.reviewLogQueries
                .newIntroducedByDeck(since)
                .executeAsList()
                .associate { it.deck_id to it.n.toInt() }
        }

    /** Review-card answers since [since], by deck. */
    suspend fun reviewsDoneByDeck(since: Long): Map<Long, Int> =
        withContext(Dispatchers.IO) {
            db.reviewLogQueries
                .reviewsDoneByDeck(since)
                .executeAsList()
                .associate { it.deck_id to it.n.toInt() }
        }
}

/**
 * Reads a "Set due date" spec: `0`, `5`, `3-7`, with an optional trailing `!`.
 * Returns (from, to, setInterval), or null when it isn't one.
 */
fun parseDueSpec(spec: String): Triple<Int, Int, Boolean>? {
    val t = spec.trim().replace(" ", "")
    val bang = t.endsWith("!")
    val body = t.removeSuffix("!")
    val parts = body.split('-')
    val a = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val b = if (parts.size == 2) parts[1].toIntOrNull() ?: return null else a
    if (parts.size > 2 || a < 0 || b < a || b > 36_500) return null
    return Triple(a, b, bang)
}

/** Where a card sits: its deck and its note's tags. */
data class CardPlacement(
    val cardId: Long,
    val deckId: Long,
    val tags: List<String>,
)
