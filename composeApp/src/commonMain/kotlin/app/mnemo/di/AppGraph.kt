package app.mnemo.di

import app.cash.sqldelight.db.SqlDriver
import app.mnemo.data.db.DriverFactory
import app.mnemo.data.repo.BrowseRepository
import app.mnemo.data.repo.CardRepository
import app.mnemo.data.repo.ConceptRepository
import app.mnemo.data.repo.DeckRepository
import app.mnemo.data.repo.NoteRepository
import app.mnemo.data.repo.ReviewLogRepository
import app.mnemo.db.MnemoDatabase
import app.mnemo.domain.model.DeckSettings
import app.mnemo.domain.scheduler.Fsrs
import app.mnemo.domain.scheduler.FsrsParameters
import app.mnemo.domain.scheduler.LoadBalancer
import app.mnemo.domain.scheduler.Scheduler
import app.mnemo.platform.Clock
import app.mnemo.platform.Prefs
import app.mnemo.platform.SystemClock
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * Hand-wired object graph. One instance per process (or per test).
 */
class AppGraph(
    val driver: SqlDriver,
    val clock: Clock = SystemClock,
    private val fuzz: Random? = Random.Default,
    initialParameters: FsrsParameters? = null,
) {
    val db: MnemoDatabase = MnemoDatabase(driver)
    val decks = DeckRepository(db, clock)
    val notes = NoteRepository(db, clock)
    val cards = CardRepository(db)
    val browse = BrowseRepository(driver, db, decks)
    val reviewLogs = ReviewLogRepository(db)
    val concepts = ConceptRepository(db)

    /** The scheduler weights in use: the published defaults until the optimizer fits your own. */
    var fsrs: Fsrs = Fsrs(initialParameters ?: loadParameters())
        private set

    val usesCustomParameters: Boolean get() = !fsrs.parameters.w.contentEquals(FsrsParameters.DEFAULT.w)

    fun scheduler(
        settings: DeckSettings,
        balancer: LoadBalancer? = null,
    ): Scheduler = Scheduler(fsrs, settings, fuzz, balancer)

    fun setParameters(parameters: FsrsParameters?) {
        fsrs = Fsrs(parameters ?: FsrsParameters.DEFAULT)
        if (parameters ==
            null
        ) {
            Prefs.set(Prefs.FSRS_PARAMS, "")
        } else {
            Prefs.set(Prefs.FSRS_PARAMS, Json.encodeToString(parameters.w.toList()))
        }
    }

    suspend fun seed() {
        notes.ensureBuiltInTypes()
    }

    companion object {
        fun create(
            driverFactory: DriverFactory,
            dbPath: String?,
            clock: Clock = SystemClock,
            fuzz: Random? = Random.Default,
        ): AppGraph = AppGraph(driverFactory.create(dbPath), clock, fuzz)

        private fun loadParameters(): FsrsParameters =
            runCatching {
                val raw = Prefs.get(Prefs.FSRS_PARAMS)?.takeIf { it.isNotBlank() } ?: return FsrsParameters.DEFAULT
                FsrsParameters(Json.decodeFromString<List<Double>>(raw).toDoubleArray())
            }.getOrDefault(FsrsParameters.DEFAULT)
    }
}
