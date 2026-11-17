package app.mnemo.data.seed

import app.mnemo.data.repo.NoteRepository
import app.mnemo.di.AppGraph
import app.mnemo.domain.model.DeckSettings
import app.mnemo.domain.model.NoteKind
import app.mnemo.platform.Prefs

/**
 * The built-in English (B1–B2) deck every install gets once: vocabulary, idioms and phrasal
 * verbs, what to say in real situations, naming things and actions from pictures, and grammar.
 * New cards per day are split across the sub-decks so day one is about 15 new cards.
 */
object EnglishDeck {
    const val ROOT = "English (B1–B2)"

    data class Section(
        val name: String,
        val newPerDay: Int,
        val tag: String,
    )

    val vocabulary = Section("1. Vocabulary", 5, "vocabulary")
    val idioms = Section("2. Idioms & phrasal verbs", 3, "idiom")
    val situations = Section("3. What to say", 3, "situation")
    val pictures = Section("4. Name it (pictures)", 3, "picture")
    val grammar = Section("5. Grammar", 2, "grammar")

    /** Adds the deck once per install; later launches (and deleting the deck) leave it alone. */
    suspend fun installOnce(graph: AppGraph) {
        if (Prefs.get(Prefs.ENGLISH_DECK) == "1") return
        install(graph)
        Prefs.set(Prefs.ENGLISH_DECK, "1")
    }

    /** Cards per pack: each section is split into small sub-decks (Vocabulary 01, 02, …). */
    const val PACK_SIZE = 10

    /** Adds every card; returns how many notes were created. */
    suspend fun install(graph: AppGraph): Int {
        val batch = mutableListOf<NoteRepository.NewNote>()
        // Opening the whole English deck gives a small daily mix; opening one pack gives all of it.
        graph.decks.createByPath(ROOT, DeckSettings(newPerDay = 15, language = "en"))

        suspend fun packs(
            s: Section,
            label: String,
            text: String,
            kind: NoteKind,
            fields: (List<String>) -> Map<String, String>,
        ) {
            graph.decks.createByPath("$ROOT::${s.name}", DeckSettings(newPerDay = s.newPerDay, language = "en"))
            val type = graph.notes.noteTypeOf(kind)
            rows(text).chunked(PACK_SIZE).forEachIndexed { i, pack ->
                val name = "$label ${(i + 1).toString().padStart(2, '0')}"
                val deckId = graph.decks.createByPath("$ROOT::${s.name}::$name", DeckSettings(newPerDay = PACK_SIZE, language = "en"))
                // Resumable: a pack that already has its cards (an earlier run whose flag was not
                // saved) is skipped instead of filled twice.
                val done = graph.notes.countByDeck(deckId).toInt()
                pack.drop(done).forEach { row ->
                    batch += NoteRepository.NewNote(deckId, type, fields(row), listOf("english", s.tag))
                }
            }
        }

        packs(vocabulary, "Vocabulary", EnglishDeckContent.VOCABULARY, NoteKind.Vocab) { (word, meaning, example) ->
            mapOf("Word" to word, "Translation" to meaning, "Example" to example)
        }
        packs(idioms, "Idioms", EnglishDeckContent.IDIOMS, NoteKind.Basic) { (phrase, meaning, example) ->
            mapOf("Front" to "**$phrase**\n\nWhat does it mean?", "Back" to "$meaning\n\n> $example")
        }
        packs(situations, "Situations", EnglishDeckContent.SITUATIONS, NoteKind.Basic) { (situation, say, other) ->
            mapOf("Front" to "$situation\n\n*What do you say?*", "Back" to "**$say**\n\n$other")
        }
        packs(pictures, "Things", EnglishDeckContent.PICTURE_THINGS, NoteKind.Basic) { (pic, name, hint) ->
            mapOf("Front" to "# $pic\n\nWhat is this called?", "Back" to "**$name**\n\n$hint")
        }
        packs(pictures, "Actions", EnglishDeckContent.PICTURE_ACTIONS, NoteKind.Basic) { (pic, action, hint) ->
            mapOf("Front" to "# $pic\n\nWhat is this person doing?", "Back" to "**$action**\n\n$hint")
        }
        packs(grammar, "Grammar", EnglishDeckContent.GRAMMAR, NoteKind.Cloze) { (text, extra) ->
            mapOf("Text" to text, "Extra" to extra)
        }
        // All cards go in as one transaction: a second or so on a phone, and all-or-nothing.
        return graph.notes.createMany(batch)
    }

    /** Splits the `|`-separated content into rows; blank lines are skipped. */
    internal fun rows(text: String): List<List<String>> =
        text
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line -> line.split('|').map { it.trim() } }
}
