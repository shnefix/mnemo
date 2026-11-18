package app.mnemo.data.seed

import app.mnemo.data.repo.NoteRepository
import app.mnemo.di.AppGraph
import app.mnemo.domain.model.DeckSettings
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.ReviewMode

/**
 * Installs a built-in deck: creates its decks, collects new cards (only when [fresh]) and updates
 * cards from an older content version that the user has not edited, then writes them all in one
 * transaction each ([commit]). Cards are matched within their deck by their first field.
 */
internal class SeedInstaller(
    private val graph: AppGraph,
    private val root: String,
    private val fresh: Boolean,
    private val language: String = "en",
) {
    private val create = mutableListOf<NoteRepository.NewNote>()
    private val update = mutableListOf<NoteRepository.NoteUpdate>()

    /** One card's fields and its tags. */
    data class Card(
        val fields: Map<String, String>,
        val tags: List<String> = emptyList(),
    )

    suspend fun deck(
        path: String,
        newPerDay: Int,
        mode: ReviewMode = ReviewMode.Classic,
    ): Long {
        val full = if (path.isEmpty()) root else "$root::$path"
        return graph.decks.createByPath(full, DeckSettings(newPerDay = newPerDay, language = language, mode = mode))
    }

    /** Adds or updates [cards] in the deck at [path] (relative to the root). */
    suspend fun fill(
        deckId: Long,
        kind: NoteKind,
        cards: List<Card>,
        tags: List<String>,
    ) {
        val type = graph.notes.noteTypeOf(kind)
        val keyField = type.fields.first()
        val existing = graph.notes.byDeck(deckId).associateBy { it.fields[keyField].orEmpty() }
        cards.forEach { card ->
            val all = (tags + card.tags).distinct()
            val note = existing[card.fields[keyField].orEmpty()]
            when {
                note == null -> if (fresh) create += NoteRepository.NewNote(deckId, type, card.fields, all)
                // Edited by the user since it was installed: theirs wins.
                note.updatedAt != note.createdAt -> {}
                note.fields != card.fields || note.tags != all ->
                    update += NoteRepository.NoteUpdate(note.id, deckId, card.fields, all)
            }
        }
    }

    /**
     * A section split into small sub-decks of [packSize] cards ("Vocabulary 01", "Vocabulary 02", …),
     * so one pack can be studied on its own.
     */
    suspend fun packs(
        section: String,
        newPerDay: Int,
        label: String,
        cards: List<Card>,
        kind: NoteKind,
        tags: List<String>,
        packSize: Int = EnglishDeck.PACK_SIZE,
        mode: ReviewMode = ReviewMode.Classic,
    ) {
        deck(section, newPerDay, mode)
        cards.chunked(packSize).forEachIndexed { i, pack ->
            val name = "$label ${(i + 1).toString().padStart(2, '0')}"
            fill(deck("$section::$name", packSize, mode), kind, pack, tags)
        }
    }

    /** Writes everything collected; returns how many notes were created or changed. */
    suspend fun commit(): Int {
        // One transaction each: a second or so on a phone, and all-or-nothing.
        val changed = graph.notes.createMany(create) + graph.notes.updateMany(update)
        // Updates keep the note's time stamps equal, so later versions can still update it.
        graph.notes.markUnedited(update.map { it.noteId })
        return changed
    }
}
