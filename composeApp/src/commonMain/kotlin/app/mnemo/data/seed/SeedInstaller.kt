package app.mnemo.data.seed

import app.mnemo.data.repo.NoteRepository
import app.mnemo.di.AppGraph
import app.mnemo.domain.model.DeckSettings
import app.mnemo.domain.model.Note
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

    /** Updated notes that still count as "never edited by the user". */
    private val unedited = mutableListOf<Long>()

    /** Notes of an older layout that [fill] may move into their new deck, by first field. */
    private var adopted = emptyMap<String, Note>()

    /** First fields of cards that are created even when this is only an update ([fresh] is false). */
    private var always = emptySet<String>()

    fun alwaysCreate(cards: List<Card>) {
        always = always + cards.mapNotNull { it.fields.values.firstOrNull() }
    }

    /** Lets [fill] find (and move) these notes from an older layout instead of adding copies. */
    fun adopt(
        notes: List<Note>,
        keyField: String,
    ) {
        adopted = notes.associateBy { it.fields[keyField].orEmpty() }
    }

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
            val key = card.fields[keyField].orEmpty()
            val note = existing[key] ?: adopted[key]
            when {
                note == null -> if (fresh || key in always) create += NoteRepository.NewNote(deckId, type, card.fields, all)
                // Edited by the user since it was installed: theirs wins (it only moves, if it must).
                note.updatedAt != note.createdAt ->
                    if (note.deckId != deckId) update += NoteRepository.NoteUpdate(note.id, deckId, note.fields, note.tags)
                note.fields != card.fields || note.tags != all || note.deckId != deckId -> {
                    update += NoteRepository.NoteUpdate(note.id, deckId, card.fields, all)
                    unedited += note.id
                }
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
        graph.notes.markUnedited(unedited)
        return changed
    }
}
