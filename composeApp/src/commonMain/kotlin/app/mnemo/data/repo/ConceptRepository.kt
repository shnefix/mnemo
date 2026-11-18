package app.mnemo.data.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.mnemo.data.db.toDomain
import app.mnemo.db.MnemoDatabase
import app.mnemo.domain.model.Card
import app.mnemo.domain.model.Concept
import app.mnemo.domain.model.ConceptEdge
import app.mnemo.domain.model.Relation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ConceptRepository(
    private val db: MnemoDatabase,
) {
    private val q get() = db.conceptQueries

    fun observeAll(): Flow<List<Concept>> =
        q
            .selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    suspend fun all(): List<Concept> = withContext(Dispatchers.IO) { q.selectAll().executeAsList().map { it.toDomain() } }

    suspend fun byId(id: Long): Concept? = withContext(Dispatchers.IO) { q.selectById(id).executeAsOneOrNull()?.toDomain() }

    suspend fun create(
        name: String,
        description: String = "",
    ): Long =
        withContext(Dispatchers.IO) {
            var id = -1L
            db.transaction {
                q.insert(name, description)
                id = q.lastInsertId().executeAsOne()
            }
            id
        }

    suspend fun update(concept: Concept) = withContext(Dispatchers.IO) { q.update(concept.name, concept.description, concept.id) }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { q.delete(id) }

    suspend fun link(
        cardId: Long,
        conceptId: Long,
    ) = withContext(Dispatchers.IO) { q.link(cardId, conceptId) }

    /** Links many (card id, concept id) pairs in one transaction; existing links are kept. */
    suspend fun linkMany(pairs: Collection<Pair<Long, Long>>) =
        withContext(Dispatchers.IO) {
            if (pairs.isEmpty()) return@withContext
            // Skip links that already exist, so the launch-time syncs write nothing (and hold no
            // write lock) on an ordinary launch.
            val known = q.allLinks().executeAsList().mapTo(HashSet()) { it.card_id to it.concept_id }
            val missing = pairs.filter { it !in known }
            if (missing.isEmpty()) return@withContext
            db.transaction { missing.forEach { (card, concept) -> q.link(card, concept) } }
        }

    suspend fun unlink(
        cardId: Long,
        conceptId: Long,
    ) = withContext(Dispatchers.IO) { q.unlink(cardId, conceptId) }

    suspend fun cardsFor(conceptId: Long): List<Card> =
        withContext(Dispatchers.IO) {
            q.cardsForConcept(conceptId).executeAsList().map { it.toDomain() }
        }

    suspend fun conceptsFor(cardId: Long): List<Concept> =
        withContext(Dispatchers.IO) {
            q.conceptsForCard(cardId).executeAsList().map { it.toDomain() }
        }

    /** Map of concept id → linked card ids. */
    suspend fun allLinks(): Map<Long, List<Long>> =
        withContext(Dispatchers.IO) {
            q.allLinks().executeAsList().groupBy({ it.concept_id }, { it.card_id })
        }

    suspend fun addEdge(
        fromId: Long,
        toId: Long,
        relation: Relation,
    ) = withContext(Dispatchers.IO) { q.addEdge(fromId, toId, relation.code) }

    suspend fun removeEdge(edge: ConceptEdge) = withContext(Dispatchers.IO) { q.removeEdge(edge.fromId, edge.toId, edge.relation.code) }

    suspend fun edges(): List<ConceptEdge> = withContext(Dispatchers.IO) { q.allEdges().executeAsList().map { it.toDomain() } }
}
