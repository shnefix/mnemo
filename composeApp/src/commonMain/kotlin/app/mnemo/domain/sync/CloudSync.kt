package app.mnemo.domain.sync

import app.mnemo.data.io.JsonBackup
import app.mnemo.di.AppGraph
import app.mnemo.domain.stats.localDate
import app.mnemo.platform.Cloud
import app.mnemo.platform.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.number

/**
 * Keeps every phone signed in to the same account on one collection. A sync pulls the
 * cloud snapshot, merges it in (newer edits and the latest review win, history is
 * unioned; see [JsonBackup.merge]) and pushes the result back when it changed.
 */
object CloudSync {
    sealed interface Status {
        data object Off : Status

        data object Idle : Status

        data object Syncing : Status

        data class Done(
            val at: Long,
            val changed: Int,
        ) : Status

        data class Failed(
            val message: String,
        ) : Status
    }

    private val mutex = Mutex()
    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private const val LAST_HASH = "sync.cloud.hash"
    private const val LAST_FINGERPRINT = "sync.cloud.fingerprint"
    private const val HISTORY_DAY = "sync.cloud.historyDay"
    const val HISTORY_KEEP = 7
    private val exportedAt = Regex("\"exportedAt\"\\s*:\\s*\\d+")

    val signedIn: Boolean get() = Cloud.supported && Cloud.userEmail() != null

    /** Content hash that ignores the export timestamp, so an unchanged collection is not re-uploaded. */
    private fun contentHash(json: String) = Cloud.hash(exportedAt.replace(json, ""))

    /** Safe to call any time (start, resume, leaving the app); does nothing when signed out. */
    suspend fun sync(graph: AppGraph): Int =
        // Exporting, hashing and merging the whole collection takes a while on a phone; on the
        // main thread it froze the app at every launch and every return to it.
        withContext(Dispatchers.Default) { syncOffMain(graph) }

    private suspend fun syncOffMain(graph: AppGraph): Int {
        if (!signedIn) {
            _status.value = Status.Off
            return 0
        }
        if (mutex.isLocked) return 0
        return mutex.withLock {
            _status.value = Status.Syncing
            runCatching {
                val last = Prefs.get(LAST_HASH)
                var changed = 0
                val remote = Cloud.remoteHash()
                // Nothing new in the cloud and nothing changed here since the last sync: skip the
                // export, hash and upload (most opens and closes of the app).
                val unchanged = remote != null && remote == last && fingerprint(graph) == Prefs.get(LAST_FINGERPRINT)
                if (!unchanged) {
                    if (remote != null && remote != last) {
                        Cloud.downloadSnapshot()?.let { (json, _) -> changed = JsonBackup.merge(graph, json) }
                    }
                    val local = JsonBackup.export(graph)
                    val hash = contentHash(local)
                    if (hash != remote) Cloud.uploadSnapshot(local, hash)
                    Prefs.set(LAST_HASH, hash)
                    Prefs.set(LAST_FINGERPRINT, fingerprint(graph))
                    keepDailyCopy(graph, local)
                } else {
                    keepDailyCopy(graph, null)
                }
                changed
            }.fold(
                onSuccess = {
                    _status.value = Status.Done(graph.clock.now(), it)
                    it
                },
                onFailure = {
                    _status.value = Status.Failed(it.message ?: it::class.simpleName.orEmpty())
                    0
                },
            )
        }
    }

    internal suspend fun fingerprint(graph: AppGraph): String =
        withContext(Dispatchers.IO) {
            graph.db.cardQueries
                .syncFingerprint()
                .executeAsOne()
                .toString()
        }

    /** One cloud copy per day, the newest [HISTORY_KEEP] kept: a way back if a deletion synced everywhere. */
    private suspend fun keepDailyCopy(
        graph: AppGraph,
        export: String?,
    ) {
        val day = dayKey(graph.clock.now())
        if (Prefs.get(HISTORY_DAY) == day) return
        runCatching {
            Cloud.saveHistory(day, export ?: JsonBackup.export(graph))
            Prefs.set(HISTORY_DAY, day)
            Cloud.listHistory().drop(HISTORY_KEEP).forEach { Cloud.deleteHistory(it) }
        }
    }

    private fun dayKey(epochMillis: Long): String {
        val d = localDate(epochMillis)
        return "${d.year}${d.month.number.toString().padStart(2, '0')}${d.day.toString().padStart(2, '0')}"
    }

    /** The daily cloud copies, newest first ("yyyymmdd"). */
    suspend fun history(): List<String> = withContext(Dispatchers.Default) { if (signedIn) Cloud.listHistory() else emptyList() }

    /** Brings back what the cloud copy of [day] has and this phone lost, without duplicating anything. */
    suspend fun restoreFromHistory(
        graph: AppGraph,
        day: String,
    ): Int =
        withContext(Dispatchers.Default) {
            val text = Cloud.loadHistory(day) ?: error("No cloud copy for $day")
            JsonBackup.restoreDeleted(graph, text)
        }

    suspend fun signIn(graph: AppGraph): Result<String> {
        val r = Cloud.signIn()
        if (r.isSuccess) {
            // A new account (or a fresh phone) must merge whatever the cloud has first.
            Prefs.set(LAST_HASH, "")
            sync(graph)
        }
        return r
    }

    suspend fun signOut() {
        Cloud.signOut()
        Prefs.set(LAST_HASH, "")
        _status.value = Status.Off
    }
}
