package app.mnemo.data.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import app.mnemo.data.db.toDomain
import app.mnemo.db.MnemoDatabase
import app.mnemo.domain.model.Card
import app.mnemo.domain.model.CardState
import app.mnemo.domain.model.CardView
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
            val new = if (newLeft > 0) q.newCards(deckIds, now, newLeft.toLong()).executeAsList().map { it.toDomain() } else emptyList()
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
}
