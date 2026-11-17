package app.mnemo.data.io

import app.mnemo.data.db.decodeFields
import app.mnemo.data.db.decodeTags
import app.mnemo.data.db.encodeFields
import app.mnemo.data.db.encodeTags
import app.mnemo.data.db.json
import app.mnemo.di.AppGraph
import app.mnemo.domain.model.DeckSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * Full backup of everything the user owns, as one JSON document. Restore
 * rebuilds ids, so a backup can be loaded into an empty database on any device.
 */
object JsonBackup {
    @Serializable
    data class Backup(
        val version: Int = 1,
        val exportedAt: Long,
        val decks: List<BDeck>,
        val noteTypes: List<BNoteType>,
        val notes: List<BNote>,
        val cards: List<BCard>,
        val reviewLogs: List<BLog>,
        val concepts: List<BConcept>,
        val cardConcepts: List<BCardConcept>,
        val conceptEdges: List<BEdge>,
        val deletedNotes: List<BTombstone> = emptyList(),
    )

    @Serializable data class BTombstone(
        val uid: String,
        val deletedAt: Long,
    )

    @Serializable data class BDeck(
        val id: Long,
        val parentId: Long?,
        val name: String,
        val settings: DeckSettings,
        val createdAt: Long,
    )

    @Serializable data class BNoteType(
        val id: Long,
        val name: String,
        val kind: String,
        val fieldsJson: String,
        val templatesJson: String,
    )

    @Serializable data class BNote(
        val id: Long,
        val deckId: Long,
        val noteTypeId: Long,
        val fields: Map<String, String>,
        val tags: List<String>,
        val createdAt: Long,
        val updatedAt: Long,
        val uid: String? = null,
    )

    @Serializable data class BCard(
        val id: Long,
        val noteId: Long,
        val templateIndex: Long,
        val state: Long,
        val due: Long,
        val stability: Double?,
        val difficulty: Double?,
        val elapsedDays: Long,
        val scheduledDays: Long,
        val reps: Long,
        val lapses: Long,
        val lastReview: Long?,
        val suspended: Long,
        val buriedUntil: Long?,
        val learningStep: Long?,
    )

    @Serializable data class BLog(
        val cardId: Long,
        val rating: Long,
        val stateBefore: Long,
        val dueBefore: Long,
        val stabilityBefore: Double?,
        val difficultyBefore: Double?,
        val elapsedDaysBefore: Long,
        val scheduledDaysBefore: Long,
        val repsBefore: Long,
        val lapsesBefore: Long,
        val lastReviewBefore: Long?,
        val learningStepBefore: Long?,
        val responseMs: Long,
        val mode: String,
        val userOverrode: Long,
        val reviewedAt: Long,
    )

    @Serializable data class BConcept(
        val id: Long,
        val name: String,
        val description: String,
    )

    @Serializable data class BCardConcept(
        val cardId: Long,
        val conceptId: Long,
    )

    @Serializable data class BEdge(
        val fromId: Long,
        val toId: Long,
        val relation: String,
    )

    suspend fun export(graph: AppGraph): String =
        withContext(Dispatchers.IO) {
            val db = graph.db
            val backup =
                Backup(
                    exportedAt = graph.clock.now(),
                    decks =
                        db.deckQueries.selectAll().executeAsList().map {
                            BDeck(
                                it.id,
                                it.parent_id,
                                it.name,
                                runCatching {
                                    json.decodeFromString<DeckSettings>(it.settings_json)
                                }.getOrDefault(DeckSettings()),
                                it.created_at,
                            )
                        },
                    noteTypes =
                        db.noteTypeQueries.selectAll().executeAsList().map {
                            BNoteType(
                                it.id,
                                it.name,
                                it.kind,
                                it.fields_json,
                                it.templates_json,
                            )
                        },
                    notes =
                        db.noteQueries.selectAll().executeAsList().map {
                            BNote(
                                it.id,
                                it.deck_id,
                                it.note_type_id,
                                decodeFields(it.fields_json),
                                decodeTags(it.tags),
                                it.created_at,
                                it.updated_at,
                                it.uid,
                            )
                        },
                    cards =
                        db.cardQueries.allCardViews().executeAsList().map {
                            BCard(
                                it.id,
                                it.note_id,
                                it.template_index,
                                it.state,
                                it.due,
                                it.stability,
                                it.difficulty,
                                it.elapsed_days,
                                it.scheduled_days,
                                it.reps,
                                it.lapses,
                                it.last_review,
                                it.suspended,
                                it.buried_until,
                                it.learning_step,
                            )
                        },
                    reviewLogs =
                        db.reviewLogQueries.since(0).executeAsList().map {
                            BLog(
                                it.card_id,
                                it.rating,
                                it.state_before,
                                it.due_before,
                                it.stability_before,
                                it.difficulty_before,
                                it.elapsed_days_before,
                                it.scheduled_days_before,
                                it.reps_before,
                                it.lapses_before,
                                it.last_review_before,
                                it.learning_step_before,
                                it.response_ms,
                                it.mode,
                                it.user_overrode,
                                it.reviewed_at,
                            )
                        },
                    concepts =
                        db.conceptQueries
                            .selectAll()
                            .executeAsList()
                            .map { BConcept(it.id, it.name, it.description) },
                    cardConcepts =
                        db.conceptQueries
                            .allLinks()
                            .executeAsList()
                            .map { BCardConcept(it.card_id, it.concept_id) },
                    conceptEdges =
                        db.conceptQueries
                            .allEdges()
                            .executeAsList()
                            .map { BEdge(it.from_id, it.to_id, it.relation) },
                    deletedNotes =
                        db.noteQueries
                            .allTombstones()
                            .executeAsList()
                            .map { BTombstone(it.uid, it.deleted_at) },
                )
            json.encodeToString(backup)
        }

    /** Restores into the database, remapping ids. Existing data is kept; decks with the same path are merged. */
    suspend fun restore(
        graph: AppGraph,
        text: String,
    ): Int = restoreOrMerge(graph, text, merge = false)

    /**
     * Sync with another device's backup: notes that already exist here (same deck and
     * creation time, which restores preserve) are updated instead of duplicated. The newer
     * edit wins for fields, the more recently reviewed side wins for scheduling, and review
     * history is unioned. Returns how many notes were added or changed.
     */
    suspend fun merge(
        graph: AppGraph,
        text: String,
    ): Int = restoreOrMerge(graph, text, merge = true)

    private suspend fun restoreOrMerge(
        graph: AppGraph,
        text: String,
        merge: Boolean,
    ): Int =
        withContext(Dispatchers.IO) {
            val backup = json.decodeFromString<Backup>(text)
            val db = graph.db
            var imported = 0
            db.transaction {
                // Note types: match by kind.
                val existingTypes = db.noteTypeQueries.selectAll().executeAsList()
                val typeMap = HashMap<Long, Long>()
                backup.noteTypes.forEach { t ->
                    val match = existingTypes.firstOrNull { it.kind == t.kind }
                    typeMap[t.id] =
                        match?.id ?: run {
                            db.noteTypeQueries.insert(t.name, t.kind, t.fieldsJson, t.templatesJson)
                            db.noteTypeQueries.lastInsertId().executeAsOne()
                        }
                }
                // Decks: parents first (by order in backup, then re-pass until all resolved).
                val deckMap = HashMap<Long, Long>()
                var pending = backup.decks
                var guard = 0
                while (pending.isNotEmpty() && guard++ < 50) {
                    val next = mutableListOf<BDeck>()
                    pending.forEach { d ->
                        val parentNew = d.parentId?.let { deckMap[it] }
                        if (d.parentId != null && parentNew == null) {
                            next += d
                        } else {
                            val existing = db.deckQueries.selectByName(d.name, parentNew).executeAsOneOrNull()
                            deckMap[d.id] =
                                existing?.id ?: run {
                                    db.deckQueries.insert(parentNew, d.name, json.encodeToString(d.settings), d.createdAt)
                                    db.deckQueries.lastInsertId().executeAsOne()
                                }
                        }
                    }
                    pending = next
                }
                val noteMap = HashMap<Long, Long>()
                val mergedNotes = HashSet<Long>()
                val localNotes = if (merge) db.noteQueries.selectAll().executeAsList() else emptyList()
                val byUid = localNotes.filter { it.uid != null }.associateBy { it.uid!! }.toMutableMap()
                // Backups from before note uids existed: match on deck + creation time, and on the
                // fields too when several notes share that time (an .apkg import stamps one time).
                val byLegacy = localNotes.groupBy { it.deck_id to it.created_at }
                val tombstones = HashMap<String, Long>()
                db.noteQueries
                    .allTombstones()
                    .executeAsList()
                    .forEach { tombstones[it.uid] = it.deleted_at }
                if (merge) {
                    // The other device deleted these: drop them here unless edited here since.
                    backup.deletedNotes.forEach { t ->
                        val local = byUid[t.uid]
                        if (local != null && local.updated_at <= t.deletedAt) {
                            db.noteQueries.delete(local.id)
                            byUid.remove(t.uid)
                            imported += 1
                        } else if (local == null) {
                            db.noteQueries.addTombstone(t.uid, maxOf(t.deletedAt, tombstones[t.uid] ?: 0))
                        }
                        tombstones[t.uid] = maxOf(t.deletedAt, tombstones[t.uid] ?: 0)
                    }
                }
                val claimed = HashSet<Long>()
                backup.notes.forEach { n ->
                    val deck = deckMap[n.deckId] ?: return@forEach
                    val type = typeMap[n.noteTypeId] ?: return@forEach
                    val fieldsJson = encodeFields(n.fields)
                    if (merge) {
                        // Deleted here after its last edit there: keep it deleted.
                        val gone = n.uid?.let { tombstones[it] }
                        if (gone != null && gone >= n.updatedAt) return@forEach
                        val same =
                            n.uid?.let { byUid[it] }?.takeIf { it.id !in claimed }
                                ?: byLegacy[deck to n.createdAt]
                                    ?.filter { it.note_type_id == type && it.id !in claimed }
                                    ?.let { c -> c.firstOrNull { it.fields_json == fieldsJson } ?: c.singleOrNull() }
                        if (same != null) {
                            claimed += same.id
                            noteMap[n.id] = same.id
                            mergedNotes += same.id
                            if (n.uid != null && same.uid != n.uid) {
                                // Adopt one uid on both sides; the lower one wins so devices converge.
                                val keep = listOfNotNull(same.uid, n.uid).min()
                                if (keep != same.uid) db.noteQueries.setUid(keep, same.id)
                            }
                            if (n.updatedAt > same.updated_at) {
                                db.noteQueries.update(deck, fieldsJson, encodeTags(n.tags), n.updatedAt, same.id)
                                imported += 1
                            }
                            return@forEach
                        }
                    }
                    db.noteQueries.insert(deck, type, fieldsJson, encodeTags(n.tags), null, n.createdAt, n.updatedAt)
                    val id = db.noteQueries.lastInsertId().executeAsOne()
                    if (n.uid != null &&
                        db.noteQueries
                            .selectByUid(n.uid)
                            .executeAsList()
                            .isEmpty()
                    ) {
                        db.noteQueries.setUid(n.uid, id)
                    }
                    noteMap[n.id] = id
                    claimed += id
                    imported += 1
                }
                val cardMap = HashMap<Long, Long>()
                val knownLogs = HashSet<Pair<Long, Long>>()
                backup.cards.forEach { c ->
                    val note = noteMap[c.noteId] ?: return@forEach
                    if (note in mergedNotes) {
                        val local =
                            db.cardQueries
                                .selectByNote(note)
                                .executeAsList()
                                .firstOrNull { it.template_index == c.templateIndex }
                        if (local != null) {
                            cardMap[c.id] = local.id
                            db.reviewLogQueries
                                .byCard(local.id)
                                .executeAsList()
                                .forEach { knownLogs += local.id to it.reviewed_at }
                            if ((c.lastReview ?: 0) > (local.last_review ?: 0)) {
                                db.cardQueries.updateScheduling(
                                    c.state,
                                    c.due,
                                    c.stability,
                                    c.difficulty,
                                    c.elapsedDays,
                                    c.scheduledDays,
                                    c.reps,
                                    c.lapses,
                                    c.lastReview,
                                    c.learningStep,
                                    local.id,
                                )
                                db.cardQueries.setSuspended(c.suspended, local.id)
                                db.cardQueries.setBuried(c.buriedUntil, local.id)
                            }
                            return@forEach
                        }
                    }
                    db.cardQueries.insert(note, c.templateIndex, c.due)
                    val id = db.cardQueries.lastInsertId().executeAsOne()
                    cardMap[c.id] = id
                    db.cardQueries.updateScheduling(
                        c.state,
                        c.due,
                        c.stability,
                        c.difficulty,
                        c.elapsedDays,
                        c.scheduledDays,
                        c.reps,
                        c.lapses,
                        c.lastReview,
                        c.learningStep,
                        id,
                    )
                    if (c.suspended != 0L) db.cardQueries.setSuspended(1L, id)
                    if (c.buriedUntil != null) db.cardQueries.setBuried(c.buriedUntil, id)
                }
                backup.reviewLogs.forEach { l ->
                    val card = cardMap[l.cardId] ?: return@forEach
                    if (!knownLogs.add(card to l.reviewedAt)) return@forEach
                    db.reviewLogQueries.insert(
                        card,
                        l.rating,
                        l.stateBefore,
                        l.dueBefore,
                        l.stabilityBefore,
                        l.difficultyBefore,
                        l.elapsedDaysBefore,
                        l.scheduledDaysBefore,
                        l.repsBefore,
                        l.lapsesBefore,
                        l.lastReviewBefore,
                        l.learningStepBefore,
                        l.responseMs,
                        l.mode,
                        null,
                        null,
                        l.userOverrode,
                        l.reviewedAt,
                    )
                }
                val conceptMap = HashMap<Long, Long>()
                // Concepts are only copied on a full restore; merging would duplicate them.
                if (!merge) {
                    backup.concepts.forEach { c ->
                        db.conceptQueries.insert(c.name, c.description)
                        conceptMap[c.id] = db.conceptQueries.lastInsertId().executeAsOne()
                    }
                }
                backup.cardConcepts.forEach { cc ->
                    val card = cardMap[cc.cardId] ?: return@forEach
                    val concept = conceptMap[cc.conceptId] ?: return@forEach
                    db.conceptQueries.link(card, concept)
                }
                backup.conceptEdges.forEach { e ->
                    val from = conceptMap[e.fromId] ?: return@forEach
                    val to = conceptMap[e.toId] ?: return@forEach
                    db.conceptQueries.addEdge(from, to, e.relation)
                }
            }
            imported
        }
}
