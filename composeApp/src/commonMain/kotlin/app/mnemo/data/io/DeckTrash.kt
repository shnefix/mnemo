package app.mnemo.data.io

import app.mnemo.data.db.json
import app.mnemo.di.AppGraph
import app.mnemo.domain.model.DAY_MS
import app.mnemo.platform.deleteAppFile
import app.mnemo.platform.listAppFiles
import app.mnemo.platform.readAppFile
import app.mnemo.platform.writeAppFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Deleting a deck first copies it (sub-decks, notes, cards, review history) into
 * `<app data>/trash`, so it can be brought back: right away with Undo, or from
 * Settings for [KEEP_DAYS] days.
 */
object DeckTrash {
    const val DIR = "trash"
    const val KEEP_DAYS = 30

    data class Entry(
        /** File name inside [DIR]: `<deleted at>-<deck id>.json`. */
        val name: String,
        val deckPath: String,
        val deletedAt: Long,
        val cards: Int,
    )

    /** Copies [deckId] and its sub-decks into the trash, then deletes them. */
    suspend fun delete(
        graph: AppGraph,
        deckId: Long,
    ): Entry? {
        val deck = graph.decks.byId(deckId) ?: return null
        val ids = graph.decks.subtreeIds(deckId).toSet()
        val text = JsonBackup.exportDecks(graph, ids)
        val now = graph.clock.now()
        val name = "$now-$deckId.json"
        withContext(Dispatchers.IO) { writeAppFile("$DIR/$name", text) }
        graph.decks.delete(deckId)
        prune(now)
        return entry(name, text) ?: Entry(name, deck.path, now, 0)
    }

    /** Puts a deleted deck back and removes it from the trash. Returns how many notes came back. */
    suspend fun restore(
        graph: AppGraph,
        entry: Entry,
    ): Int {
        val text = withContext(Dispatchers.IO) { readAppFile("$DIR/${entry.name}") } ?: error("${entry.deckPath} is no longer in the trash")
        val n = JsonBackup.restoreDeleted(graph, text)
        withContext(Dispatchers.IO) { deleteAppFile("$DIR/${entry.name}") }
        return n
    }

    /** What is in the trash, most recently deleted first. */
    suspend fun list(now: Long): List<Entry> =
        withContext(Dispatchers.IO) {
            prune(now)
            listAppFiles(DIR)
                .filter { it.endsWith(".json") }
                .mapNotNull { name -> readAppFile("$DIR/$name")?.let { entry(name, it) } }
                .sortedByDescending { it.deletedAt }
        }

    private fun prune(now: Long) {
        listAppFiles(DIR)
            .filter { deletedAt(it)?.let { t -> now - t > KEEP_DAYS * DAY_MS } ?: false }
            .forEach { deleteAppFile("$DIR/$it") }
    }

    private fun deletedAt(name: String): Long? = name.substringBefore('-').toLongOrNull()

    private fun entry(
        name: String,
        text: String,
    ): Entry? =
        runCatching {
            val b = json.decodeFromString<JsonBackup.Backup>(text)
            val byId = b.decks.associateBy { it.id }
            val deckId = name.substringAfter('-').substringBefore('.').toLongOrNull()
            val deck = byId[deckId] ?: b.decks.firstOrNull { it.parentId == null } ?: return null
            val path = generateSequence(deck) { d -> d.parentId?.let { byId[it] } }.map { it.name }.toList().reversed()
            Entry(name, path.joinToString("::"), deletedAt(name) ?: b.exportedAt, b.cards.size)
        }.getOrNull()
}
