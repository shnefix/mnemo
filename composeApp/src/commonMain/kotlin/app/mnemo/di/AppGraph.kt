package app.mnemo.di

import app.mnemo.data.db.DriverFactory
import app.mnemo.data.repo.CardRepository
import app.mnemo.data.repo.ConceptRepository
import app.mnemo.data.repo.DeckRepository
import app.mnemo.data.repo.NoteRepository
import app.mnemo.data.repo.ReviewLogRepository
import app.mnemo.db.MnemoDatabase
import app.mnemo.domain.model.DeckSettings
import app.mnemo.domain.scheduler.Fsrs
import app.mnemo.domain.scheduler.Scheduler
import app.mnemo.platform.Clock
import app.mnemo.platform.SystemClock
import kotlin.random.Random

/**
 * Hand-wired object graph. One instance per process (or per test).
 */
class AppGraph(
    val db: MnemoDatabase,
    val clock: Clock = SystemClock,
    private val fuzz: Random? = Random.Default,
) {
    val decks = DeckRepository(db, clock)
    val notes = NoteRepository(db, clock)
    val cards = CardRepository(db)
    val reviewLogs = ReviewLogRepository(db)
    val concepts = ConceptRepository(db)
    val fsrs = Fsrs()

    fun scheduler(settings: DeckSettings): Scheduler = Scheduler(fsrs, settings, fuzz)

    suspend fun seed() {
        notes.ensureBuiltInTypes()
    }

    companion object {
        fun create(
            driverFactory: DriverFactory,
            dbPath: String?,
            clock: Clock = SystemClock,
            fuzz: Random? = Random.Default,
        ): AppGraph = AppGraph(MnemoDatabase(driverFactory.create(dbPath)), clock, fuzz)
    }
}
