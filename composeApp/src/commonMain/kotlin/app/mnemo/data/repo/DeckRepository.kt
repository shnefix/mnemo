package app.mnemo.data.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.mnemo.data.db.json
import app.mnemo.data.db.toDomain
import app.mnemo.db.MnemoDatabase
import app.mnemo.domain.model.Deck
import app.mnemo.domain.model.DeckSettings
import app.mnemo.platform.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Decks form a tree via `parent_id`. Names like `German::Vocab::A1` are
 * accepted when creating: each segment becomes a nested deck.
 */
class DeckRepository(
    private val db: MnemoDatabase,
    private val clock: Clock,
) {
    private val q get() = db.deckQueries

    /** All decks with their full `A::B::C` path filled in, sorted by path. */
    fun observeAll(): Flow<List<Deck>> =
        q.selectAll().asFlow().mapToList(Dispatchers.IO).map { rows ->
            withPaths(rows.map { it.toDomain() })
        }

    suspend fun all(): List<Deck> = withContext(Dispatchers.IO) { withPaths(q.selectAll().executeAsList().map { it.toDomain() }) }

    suspend fun byId(id: Long): Deck? =
        withContext(Dispatchers.IO) {
            val row = q.selectById(id).executeAsOneOrNull()?.toDomain() ?: return@withContext null
            withPaths(q.selectAll().executeAsList().map { it.toDomain() }).firstOrNull { it.id == row.id }
        }

    /** Ids of [deckId] and every deck below it. */
    suspend fun subtreeIds(deckId: Long): List<Long> =
        withContext(Dispatchers.IO) {
            val all = q.selectAll().executeAsList()
            val byParent = all.groupBy { it.parent_id }
            val out = mutableListOf<Long>()

            fun walk(id: Long) {
                out += id
                byParent[id]?.forEach { walk(it.id) }
            }
            walk(deckId)
            out
        }

    /** Creates (or finds) a deck by `A::B::C` path. Returns the leaf deck id. */
    suspend fun createByPath(
        path: String,
        settings: DeckSettings = DeckSettings(),
    ): Long =
        withContext(Dispatchers.IO) {
            var parent: Long? = null
            var id = -1L
            db.transaction {
                for (segment in path.split("::").map { it.trim() }.filter { it.isNotEmpty() }) {
                    val existing = q.selectByName(segment, parent).executeAsOneOrNull()
                    id =
                        if (existing != null) {
                            existing.id
                        } else {
                            q.insert(parent, segment, json.encodeToString(settings), clock.now())
                            q.lastInsertId().executeAsOne()
                        }
                    parent = id
                }
            }
            id
        }

    suspend fun rename(
        id: Long,
        name: String,
    ) = withContext(Dispatchers.IO) { q.rename(name, id) }

    suspend fun updateSettings(
        id: Long,
        settings: DeckSettings,
    ) = withContext(Dispatchers.IO) { q.updateSettings(json.encodeToString(settings), id) }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { q.delete(id) }

    suspend fun count(): Long = withContext(Dispatchers.IO) { q.count().executeAsOne() }

    private fun withPaths(decks: List<Deck>): List<Deck> {
        val byId = decks.associateBy { it.id }

        fun pathOf(d: Deck): String = d.parentId?.let { byId[it] }?.let { pathOf(it) + "::" + d.name } ?: d.name
        decks.forEach { it.path = pathOf(it) }
        return decks.sortedBy { it.path.lowercase() }
    }
}
