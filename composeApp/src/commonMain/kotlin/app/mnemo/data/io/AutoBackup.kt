package app.mnemo.data.io

import app.mnemo.di.AppGraph
import app.mnemo.platform.appFileSize
import app.mnemo.platform.deleteAppFile
import app.mnemo.platform.listAppFiles
import app.mnemo.platform.readAppFile
import app.mnemo.platform.writeAppFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

/**
 * Safety net for a local-first app: a JSON backup written on launch at most
 * once a day into `<app data>/backups`, keeping the newest [KEEP]. Restoring
 * merges the backup into the current data (see [JsonBackup.restore]).
 */
object AutoBackup {
    const val DIR = "backups"
    const val KEEP = 5
    private const val PREFIX = "mnemo-auto-"
    private const val MIN_GAP_MS = 20 * 60 * 60 * 1000L

    data class Entry(
        val name: String,
        val bytes: Long,
    ) {
        /** "16 Nov 2026 13:05" style label parsed from the file name. */
        val label: String
            get() {
                val stamp = name.removePrefix(PREFIX).removeSuffix(".json")
                return runCatching {
                    val (d, t) = stamp.split("-")
                    "${d.substring(6, 8)}.${d.substring(4, 6)}.${d.substring(0, 4)} ${t.substring(0, 2)}:${t.substring(2, 4)}"
                }.getOrDefault(stamp)
            }
    }

    /** Newest first. */
    fun list(): List<Entry> =
        listAppFiles(DIR)
            .filter { it.startsWith(PREFIX) && it.endsWith(".json") }
            .sortedDescending()
            .map { Entry(it, appFileSize("$DIR/$it")) }

    /**
     * Writes a backup unless one was written in the last 20 hours or there is
     * nothing to back up. Returns the new file name, or null when skipped.
     */
    suspend fun runIfDue(
        graph: AppGraph,
        force: Boolean = false,
    ): String? {
        val now = graph.clock.now()
        if (graph.notes.countAll() == 0L) return null
        val newest = list().firstOrNull()
        if (!force && newest != null && now - timestampOf(newest.name) < MIN_GAP_MS) return null
        val text = JsonBackup.export(graph)
        val name = "$PREFIX${stamp(now)}.json"
        withContext(Dispatchers.IO) {
            writeAppFile("$DIR/$name", text)
            list().drop(KEEP).forEach { deleteAppFile("$DIR/${it.name}") }
        }
        return name
    }

    suspend fun restore(
        graph: AppGraph,
        name: String,
    ): Int {
        val text = withContext(Dispatchers.IO) { readAppFile("$DIR/$name") } ?: error("Backup $name not found")
        return JsonBackup.restore(graph, text)
    }

    @OptIn(ExperimentalTime::class)
    private fun stamp(epochMillis: Long): String {
        val t =
            kotlin.time.Instant
                .fromEpochMilliseconds(epochMillis)
                .toLocalDateTime(TimeZone.currentSystemDefault())

        fun two(n: Int) = n.toString().padStart(2, '0')
        return "${t.year}${two(t.monthNumber)}${two(t.dayOfMonth)}-${two(t.hour)}${two(t.minute)}"
    }

    /** Epoch millis encoded in a backup file name (0 when unparseable, which counts as "old"). */
    @OptIn(ExperimentalTime::class)
    fun timestampOf(name: String): Long =
        runCatching {
            val (d, t) = name.removePrefix(PREFIX).removeSuffix(".json").split("-")
            LocalDateTime(
                d.substring(0, 4).toInt(),
                d.substring(4, 6).toInt(),
                d.substring(6, 8).toInt(),
                t.substring(0, 2).toInt(),
                t.substring(2, 4).toInt(),
            ).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        }.getOrDefault(0L)
}
