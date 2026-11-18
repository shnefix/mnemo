package app.mnemo.platform

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Reads/writes a small text file in the app data folder (used for preferences). */
expect fun readAppFile(name: String): String?

expect fun writeAppFile(
    name: String,
    text: String,
)

/** Names of the files directly inside the app-data subfolder [dir] (empty when it does not exist). */
expect fun listAppFiles(dir: String): List<String>

expect fun deleteAppFile(name: String)

/** Size in bytes of an app-data file, or 0 when missing. */
expect fun appFileSize(name: String): Long

/** Tiny key/value preferences stored as `prefs.json` in the app data folder. */
object Prefs {
    private const val FILE = "prefs.json"
    private var cache: MutableMap<String, String>? = null

    private fun load(): MutableMap<String, String> =
        cache ?: run {
            val map =
                runCatching {
                    readAppFile(FILE)?.let { Json.parseToJsonElement(it).jsonObject.mapValues { (_, v) -> v.jsonPrimitive.content } }
                }.getOrNull()?.toMutableMap() ?: mutableMapOf()
            cache = map
            map
        }

    fun get(key: String): String? = load()[key]

    fun set(
        key: String,
        value: String,
    ) {
        val map = load()
        map[key] = value
        runCatching {
            writeAppFile(
                FILE,
                Json.encodeToString(kotlinx.serialization.json.JsonObject(map.mapValues { JsonPrimitive(it.value) })),
            )
        }
    }

    const val THEME = "theme"
    const val TEXT_SIZE = "text.size"
    const val ONBOARDED = "onboarded"
    const val ENGLISH_DECK = "seed.english"
    const val ENGLISH_B2_DECK = "seed.english.b2"
    const val DECKS_EXPANDED = "decks.expanded"
    const val GRAPH_HIDDEN = "graph.hidden"
    const val FSRS_PARAMS = "fsrs.params"
    const val DAY_START_HOUR = "day.start.hour"
    const val REMINDER_HOUR = "reminder.hour"
}
