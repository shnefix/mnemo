package app.mnemo.platform

/**
 * Read-only access to an arbitrary SQLite file (an Anki collection, for
 * example). Rows come back as maps of column name → value (Long, Double,
 * String, ByteArray or null).
 */
expect class RawSqlite(
    path: String,
) : AutoCloseable {
    fun query(
        sql: String,
        vararg args: Any?,
    ): List<Map<String, Any?>>

    fun tableExists(name: String): Boolean

    override fun close()
}

fun Map<String, Any?>.long(key: String): Long =
    when (val v = this[key]) {
        is Long -> v
        is Int -> v.toLong()
        is Double -> v.toLong()
        is String -> v.toLongOrNull() ?: 0L
        else -> 0L
    }

fun Map<String, Any?>.str(key: String): String =
    when (val v = this[key]) {
        null -> ""
        is ByteArray -> v.decodeToString()
        else -> v.toString()
    }

fun Map<String, Any?>.bytes(key: String): ByteArray? =
    when (val v = this[key]) {
        is ByteArray -> v
        is String -> v.encodeToByteArray()
        else -> null
    }
