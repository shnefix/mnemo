package app.mnemo.domain.sync

import app.mnemo.data.io.JsonBackup
import app.mnemo.di.AppGraph
import app.mnemo.platform.Cloud
import app.mnemo.platform.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val exportedAt = Regex("\"exportedAt\"\\s*:\\s*\\d+")

    val signedIn: Boolean get() = Cloud.supported && Cloud.userEmail() != null

    /** Content hash that ignores the export timestamp, so an unchanged collection is not re-uploaded. */
    private fun contentHash(json: String) = Cloud.hash(exportedAt.replace(json, ""))

    /** Safe to call any time (start, resume, leaving the app); does nothing when signed out. */
    suspend fun sync(graph: AppGraph): Int {
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
                if (remote != null && remote != last) {
                    Cloud.downloadSnapshot()?.let { (json, _) -> changed = JsonBackup.merge(graph, json) }
                }
                val local = JsonBackup.export(graph)
                val hash = contentHash(local)
                if (hash != remote) Cloud.uploadSnapshot(local, hash)
                Prefs.set(LAST_HASH, hash)
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
