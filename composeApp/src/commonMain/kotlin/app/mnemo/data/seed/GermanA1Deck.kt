package app.mnemo.data.seed

import app.mnemo.data.seed.SeedInstaller.Card
import app.mnemo.di.AppGraph
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.ReviewMode
import app.mnemo.platform.Prefs

/**
 * The built-in "German (A1)" deck, the step after [GermanA0Deck]:
 *  1. Verbs: the present tense of the common verbs (ich, du, er/sie), with an example.
 *  2. Word order: put the words in the right order (verb second, questions, "weil", modals).
 *  3. Questions: how to ask for the things you need in daily life, with a typical answer.
 *  4. Type it (du-form): you type the "du" form of a verb.
 *
 * Versioned like [EnglishDeck]: a newer app updates installed cards the user has not edited.
 */
object GermanA1Deck {
    const val ROOT = "German (A1)"

    /** Bump when the content changes; older installs are updated on the next launch. */
    const val VERSION = 1

    val verbs = EnglishDeck.Section("1. Verbs", 4, "verb")
    val wordOrder = EnglishDeck.Section("2. Word order", 3, "word-order")
    val questions = EnglishDeck.Section("3. Questions", 3, "question")
    val typeIt = EnglishDeck.Section("4. Type it (du-form)", 3, "du-form")

    /** Adds the deck once, or brings an older install up to [VERSION]. A deleted deck stays deleted. */
    suspend fun installOnce(graph: AppGraph) {
        val installed = Prefs.get(Prefs.GERMAN_A1_DECK)?.toIntOrNull() ?: 0
        if (installed >= VERSION) return
        val rootExists = graph.decks.all().any { it.name == ROOT && it.parentId == null }
        if (installed == 0 || rootExists) install(graph, fresh = installed == 0)
        Prefs.set(Prefs.GERMAN_A1_DECK, VERSION.toString())
    }

    suspend fun install(
        graph: AppGraph,
        fresh: Boolean = true,
    ): Int {
        val seed = SeedInstaller(graph, ROOT, fresh, language = "de")
        seed.deck("", newPerDay = 10)
        val tags = listOf("german", "a1")
        seed.packs(verbs.name, verbs.newPerDay, "Verbs", verbCards(), NoteKind.Basic, tags + verbs.tag)
        seed.packs(wordOrder.name, wordOrder.newPerDay, "Word order", wordOrderCards(), NoteKind.Basic, tags + wordOrder.tag)
        seed.packs(questions.name, questions.newPerDay, "Questions", questionCards(), NoteKind.Basic, tags + questions.tag)
        seed.packs(
            typeIt.name,
            typeIt.newPerDay,
            "du-form",
            duFormCards(),
            NoteKind.TypeAnswer,
            tags + typeIt.tag,
            packSize = TYPE_PACK_SIZE,
            mode = ReviewMode.Type,
        )
        return seed.commit()
    }

    private const val TYPE_PACK_SIZE = 20

    /** infinitive | English | ich | du | er/sie | example | translation */
    internal fun verbCards(): List<Card> =
        EnglishDeck.rows(GermanA1Content.VERBS).map { r ->
            val (verb, english, ich, du, er) = r
            val example = r[5]
            val translation = r[6]
            Card(
                mapOf(
                    "Front" to "**$verb** — $english\n\nich …… · du …… · er/sie ……",
                    "Back" to "ich **$ich**\n\ndu **$du**\n\ner/sie **$er**\n\n> $example  \n> *$translation*",
                ),
            )
        }

    /** words (the first one starts the sentence) | sentence | rule | translation */
    internal fun wordOrderCards(): List<Card> =
        EnglishDeck.rows(GermanA1Content.WORD_ORDER).map { (words, sentence, rule, translation) ->
            val parts = words.split(" / ")
            val shown = (listOf("**${parts.first()}**") + parts.drop(1)).joinToString(" / ")
            Card(
                mapOf(
                    "Front" to "$shown\n\n*Start with the bold part and put the rest in order.*",
                    "Back" to "**$sentence**\n\n*$translation*\n\n$rule",
                ),
            )
        }

    /** situation | the question | note | example answer | translation */
    internal fun questionCards(): List<Card> =
        EnglishDeck.rows(GermanA1Content.QUESTIONS).map { r ->
            val (situation, question, note, answer, translation) = r
            Card(
                mapOf(
                    "Front" to "$situation\n\n*How do you ask?*",
                    "Back" to "**$question**\n\n$note\n\n> $answer  \n> *$translation*",
                ),
            )
        }

    /** The "du" form of every verb that is not separable; you type it. */
    internal fun duFormCards(): List<Card> =
        EnglishDeck
            .rows(GermanA1Content.VERBS)
            .filter { "…" !in it[3] }
            .map { r -> Card(mapOf("Front" to "**${r[0]}** — ${r[1]}\n\ndu ……", "Back" to r[3])) }
}
