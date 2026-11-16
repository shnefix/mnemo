package app.mnemo.data.repo

import app.mnemo.data.db.toDomain
import app.mnemo.db.MnemoDatabase
import app.mnemo.domain.model.Card
import app.mnemo.domain.model.Rating
import app.mnemo.domain.model.ReviewLog
import app.mnemo.domain.model.ReviewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DailyReviews(
    val dayIndex: Long,
    val count: Int,
    val timeMs: Long,
)

data class Retention(
    val correct: Int,
    val total: Int,
) {
    val ratio: Double? get() = if (total == 0) null else correct.toDouble() / total
}

class ReviewLogRepository(
    private val db: MnemoDatabase,
) {
    private val q get() = db.reviewLogQueries

    /** Logs a review, storing the card as it was *before* grading so it can be undone. */
    suspend fun log(
        before: Card,
        rating: Rating,
        responseMs: Long,
        mode: ReviewMode,
        reviewedAt: Long,
        aiVerdict: String? = null,
        aiScore: Double? = null,
        userOverrode: Boolean = false,
    ): Long =
        withContext(Dispatchers.IO) {
            db.transactionWithResult {
                logNow(before, rating, responseMs, mode, reviewedAt, aiVerdict, aiScore, userOverrode)
            }
        }

    /** Same as [log] but blocking, for use inside an enclosing `db.transaction`. */
    fun logNow(
        before: Card,
        rating: Rating,
        responseMs: Long,
        mode: ReviewMode,
        reviewedAt: Long,
        aiVerdict: String? = null,
        aiScore: Double? = null,
        userOverrode: Boolean = false,
    ): Long {
        q.insert(
            before.id,
            rating.code,
            before.state.code,
            before.due,
            before.stability,
            before.difficulty,
            before.elapsedDays.toLong(),
            before.scheduledDays.toLong(),
            before.reps.toLong(),
            before.lapses.toLong(),
            before.lastReview,
            before.learningStep?.toLong(),
            responseMs,
            mode.code,
            aiVerdict,
            aiScore,
            if (userOverrode) 1L else 0L,
            reviewedAt,
        )
        return q.lastInsertId().executeAsOne()
    }

    /** Blocking delete for use inside an enclosing `db.transaction`. */
    fun deleteNow(id: Long) = q.delete(id)

    /** New cards first answered since [since] in [deckIds] (for the daily new-card limit). */
    fun newIntroducedSinceNow(
        deckIds: List<Long>,
        since: Long,
    ): Int = if (deckIds.isEmpty()) 0 else q.newIntroducedSince(deckIds, since).executeAsOne().toInt()

    /** Review-state answers since [since] in [deckIds] (for the daily review limit). */
    fun reviewsDoneSinceNow(
        deckIds: List<Long>,
        since: Long,
    ): Int = if (deckIds.isEmpty()) 0 else q.reviewsDoneSince(deckIds, since).executeAsOne().toInt()

    suspend fun newIntroducedSince(
        deckIds: List<Long>,
        since: Long,
    ): Int = withContext(Dispatchers.IO) { newIntroducedSinceNow(deckIds, since) }

    suspend fun reviewsDoneSince(
        deckIds: List<Long>,
        since: Long,
    ): Int = withContext(Dispatchers.IO) { reviewsDoneSinceNow(deckIds, since) }

    suspend fun latest(): ReviewLog? = withContext(Dispatchers.IO) { q.latest().executeAsOneOrNull()?.toDomain() }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { q.delete(id) }

    suspend fun since(time: Long): List<ReviewLog> = withContext(Dispatchers.IO) { q.since(time).executeAsList().map { it.toDomain() } }

    suspend fun byCard(cardId: Long): List<ReviewLog> =
        withContext(Dispatchers.IO) { q.byCard(cardId).executeAsList().map { it.toDomain() } }

    suspend fun countAll(): Long = withContext(Dispatchers.IO) { q.countAll().executeAsOne() }

    suspend fun dailyCounts(since: Long): List<DailyReviews> =
        withContext(Dispatchers.IO) {
            q.dailyCounts(since).executeAsList().map { DailyReviews(it.day, it.n.toInt(), it.ms ?: 0L) }
        }

    suspend fun retentionSince(since: Long): Retention =
        withContext(Dispatchers.IO) {
            val row = q.retentionSince(since).executeAsOne()
            Retention(correct = (row.correct ?: 0L).toInt(), total = row.total.toInt())
        }

    suspend fun timeSpentSince(since: Long): Long = withContext(Dispatchers.IO) { q.timeSpentSince(since).executeAsOne().sum ?: 0L }
}
