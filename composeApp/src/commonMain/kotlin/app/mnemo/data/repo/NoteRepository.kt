package app.mnemo.data.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.mnemo.data.db.decodeTags
import app.mnemo.data.db.encodeFields
import app.mnemo.data.db.encodeTags
import app.mnemo.data.db.json
import app.mnemo.data.db.toDomain
import app.mnemo.db.MnemoDatabase
import app.mnemo.domain.cards.CardGenerator
import app.mnemo.domain.model.Note
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.NoteType
import app.mnemo.platform.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Notes and their note types. Creating or editing a note keeps its cards in sync. */
class NoteRepository(
    private val db: MnemoDatabase,
    private val clock: Clock,
) {
    private val notes get() = db.noteQueries
    private val types get() = db.noteTypeQueries
    private val cards get() = db.cardQueries

    /** Inserts any built-in note type that is missing (first launch, or after an upgrade adds one). */
    suspend fun ensureBuiltInTypes() =
        withContext(Dispatchers.IO) {
            val existing =
                types
                    .selectAll()
                    .executeAsList()
                    .map { it.kind }
                    .toSet()
            db.transaction {
                CardGenerator.builtInTypes().filter { it.kind.code !in existing }.forEach { t ->
                    types.insert(t.name, t.kind.code, json.encodeToString(t.fields), json.encodeToString(t.templates))
                }
            }
        }

    suspend fun noteTypes(): List<NoteType> = withContext(Dispatchers.IO) { types.selectAll().executeAsList().map { it.toDomain() } }

    suspend fun noteType(id: Long): NoteType? = withContext(Dispatchers.IO) { types.selectById(id).executeAsOneOrNull()?.toDomain() }

    suspend fun noteTypeOf(kind: NoteKind): NoteType =
        withContext(Dispatchers.IO) { types.selectByKind(kind.code).executeAsOne().toDomain() }

    suspend fun byId(id: Long): Note? = withContext(Dispatchers.IO) { notes.selectById(id).executeAsOneOrNull()?.toDomain() }

    fun observeAll(): Flow<List<Note>> =
        notes
            .selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    /** Creates a note and one card per template (or per cloze). Returns the note id. */
    suspend fun create(
        deckId: Long,
        noteType: NoteType,
        fields: Map<String, String>,
        tags: List<String>,
        sourceId: Long? = null,
    ): Long =
        withContext(Dispatchers.IO) {
            val now = clock.now()
            var noteId = -1L
            db.transaction {
                notes.insert(deckId, noteType.id, encodeFields(fields), encodeTags(tags), sourceId, now, now)
                noteId = notes.lastInsertId().executeAsOne()
                CardGenerator.templateIndices(noteType, fields).forEach { idx ->
                    cards.insert(noteId, idx.toLong(), now)
                }
            }
            noteId
        }

    /** A note to add with [createMany]. */
    data class NewNote(
        val deckId: Long,
        val noteType: NoteType,
        val fields: Map<String, String>,
        val tags: List<String>,
    )

    /**
     * Creates many notes (and their cards) in one transaction: much faster than [create] in a
     * loop, the database observers fire once, and an interrupted batch leaves nothing behind.
     */
    suspend fun createMany(items: List<NewNote>): Int =
        withContext(Dispatchers.IO) {
            if (items.isEmpty()) return@withContext 0
            val now = clock.now()
            db.transaction {
                items.forEach { n ->
                    notes.insert(n.deckId, n.noteType.id, encodeFields(n.fields), encodeTags(n.tags), null, now, now)
                    val noteId = notes.lastInsertId().executeAsOne()
                    CardGenerator.templateIndices(n.noteType, n.fields).forEach { idx -> cards.insert(noteId, idx.toLong(), now) }
                }
            }
            items.size
        }

    /** Updates fields/deck/tags and adds cards for new cloze numbers (never deletes reviewed cards). */
    suspend fun update(
        noteId: Long,
        deckId: Long,
        fields: Map<String, String>,
        tags: List<String>,
    ) = withContext(Dispatchers.IO) {
        val now = clock.now()
        db.transaction { updateBlocking(noteId, deckId, fields, tags, now) }
    }

    /** A change for [updateMany]. */
    data class NoteUpdate(
        val noteId: Long,
        val deckId: Long,
        val fields: Map<String, String>,
        val tags: List<String>,
    )

    /** [update] for many notes in one transaction (scheduling is kept). */
    suspend fun updateMany(updates: List<NoteUpdate>): Int =
        withContext(Dispatchers.IO) {
            if (updates.isEmpty()) return@withContext 0
            val now = clock.now()
            db.transaction { updates.forEach { updateBlocking(it.noteId, it.deckId, it.fields, it.tags, now) } }
            updates.size
        }

    private fun updateBlocking(
        noteId: Long,
        deckId: Long,
        fields: Map<String, String>,
        tags: List<String>,
        now: Long,
    ) {
        val row = notes.selectById(noteId).executeAsOne()
        val type = types.selectById(row.note_type_id).executeAsOne().toDomain()
        notes.update(deckId, encodeFields(fields), encodeTags(tags), now, noteId)
        val existing =
            cards
                .selectByNote(noteId)
                .executeAsList()
                .map { it.template_index.toInt() }
                .toSet()
        CardGenerator.templateIndices(type, fields).filter { it !in existing }.forEach { idx ->
            cards.insert(noteId, idx.toLong(), now)
        }
    }

    /** Sets updated time back to creation time (built-in content updates, not user edits). */
    suspend fun markUnedited(ids: List<Long>) =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext
            db.transaction { ids.chunked(500).forEach { notes.markUnedited(it) } }
        }

    /** Notes whose deck is exactly [deckId] (not its sub-decks). */
    suspend fun byDeck(deckId: Long): List<Note> =
        withContext(Dispatchers.IO) { notes.selectByDeck(deckId).executeAsList().map { it.toDomain() } }

    suspend fun setTags(
        noteId: Long,
        tags: List<String>,
    ) = withContext(Dispatchers.IO) { notes.updateTags(encodeTags(tags), clock.now(), noteId) }

    suspend fun addTag(
        noteId: Long,
        tag: String,
    ) = withContext(Dispatchers.IO) {
        val row = notes.selectById(noteId).executeAsOne()
        val current = decodeTags(row.tags)
        if (tag !in current) notes.updateTags(encodeTags(current + tag), clock.now(), noteId)
    }

    suspend fun removeTag(
        noteId: Long,
        tag: String,
    ) = withContext(Dispatchers.IO) {
        val row = notes.selectById(noteId).executeAsOne()
        notes.updateTags(encodeTags(decodeTags(row.tags) - tag), clock.now(), noteId)
    }

    suspend fun moveToDeck(
        noteId: Long,
        deckId: Long,
    ) = withContext(Dispatchers.IO) { notes.moveToDeck(deckId, clock.now(), noteId) }

    suspend fun delete(noteId: Long) = withContext(Dispatchers.IO) { notes.delete(noteId) }

    suspend fun allTags(): List<String> =
        withContext(Dispatchers.IO) {
            notes
                .allTags()
                .executeAsList()
                .flatMap { decodeTags(it) }
                .distinct()
                .sorted()
        }

    suspend fun countAll(): Long = withContext(Dispatchers.IO) { notes.countAll().executeAsOne() }

    /**
     * Forgets deletions older than [maxAgeMs] (default 180 days). Syncs carry tombstones so a
     * deleted note stays deleted; a device last synced longer ago than this could bring one back.
     */
    suspend fun pruneTombstones(maxAgeMs: Long = TOMBSTONE_MAX_AGE_MS): Long =
        withContext(Dispatchers.IO) { notes.pruneTombstones(clock.now() - maxAgeMs).value }

    suspend fun countByDeck(deckId: Long): Long = withContext(Dispatchers.IO) { notes.countByDeck(deckId).executeAsOne() }

    /** Every `source_id` in use (importers set one to recognise notes they created). */
    suspend fun existingSourceIds(): List<Long> = withContext(Dispatchers.IO) { notes.sourceIds().executeAsList().filterNotNull() }
}

/** How long deletions are remembered for sync (see [NoteRepository.pruneTombstones]). */
const val TOMBSTONE_MAX_AGE_MS = 180L * 24 * 60 * 60 * 1000
