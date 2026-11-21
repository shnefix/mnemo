package app.mnemo.data.seed

import app.mnemo.data.seed.SeedInstaller.Card
import app.mnemo.di.AppGraph
import app.mnemo.domain.model.NoteKind
import app.mnemo.platform.Prefs

/**
 * The built-in "German (A0)" deck, for the very first German words. Its one section, "Name it",
 * shows a picture and asks for the German word: nouns come with their article (der / die / das)
 * and plural, verbs with their "ich" form, plus colours and the numbers 1–10. Every card has a
 * very simple example sentence with its translation.
 *
 * Versioned like [EnglishDeck]: a newer app updates installed cards the user has not edited.
 */
object GermanA0Deck {
    const val ROOT = "German (A0)"

    /** Bump when the content changes; older installs are updated on the next launch. */
    const val VERSION = 1

    val nameIt = EnglishDeck.Section("1. Name it", 5, "name-it")

    /** Adds the deck once, or brings an older install up to [VERSION]. A deleted deck stays deleted. */
    suspend fun installOnce(graph: AppGraph) {
        val installed = Prefs.get(Prefs.GERMAN_A0_DECK)?.toIntOrNull() ?: 0
        if (installed >= VERSION) return
        val rootExists = graph.decks.all().any { it.name == ROOT && it.parentId == null }
        if (installed == 0 || rootExists) install(graph, fresh = installed == 0)
        Prefs.set(Prefs.GERMAN_A0_DECK, VERSION.toString())
    }

    suspend fun install(
        graph: AppGraph,
        fresh: Boolean = true,
    ): Int {
        val seed = SeedInstaller(graph, ROOT, fresh, language = "de")
        seed.deck("", newPerDay = 10)
        val tags = listOf("german", "a0", nameIt.tag)

        suspend fun packs(
            label: String,
            cards: List<Card>,
        ) = seed.packs(nameIt.name, nameIt.newPerDay, label, cards, NoteKind.Basic, tags)
        packs("Things", nounCards(GermanA0Content.THINGS))
        packs("Food & drink", nounCards(GermanA0Content.FOOD))
        packs("Animals", nounCards(GermanA0Content.ANIMALS))
        packs("Nature & body", nounCards(GermanA0Content.NATURE_BODY))
        packs("People & places", nounCards(GermanA0Content.PEOPLE_PLACES))
        packs("Actions", verbCards())
        packs("Colours & numbers", colourCards())
        return seed.commit()
    }

    /** picture | word with article | plural | English | example | translation */
    internal fun nounCards(content: String): List<Card> =
        EnglishDeck.rows(content).map { r ->
            val (pic, word, plural, english, example) = r
            val translation = r[5]
            val pluralLine = if (plural == NO_PLURAL) "No plural" else "Plural: *$plural*"
            Card(
                mapOf(
                    "Front" to "# $pic\n\nWhat is this called in German? (with der / die / das)",
                    "Back" to "**$word**\n\n$english\n\n$pluralLine\n\n> $example  \n> *$translation*",
                ),
            )
        }

    /** picture | verb | ich-form | English | example | translation */
    internal fun verbCards(): List<Card> =
        EnglishDeck.rows(GermanA0Content.ACTIONS).map { r ->
            val (pic, verb, ich, english, example) = r
            val translation = r[5]
            Card(
                mapOf(
                    "Front" to "# $pic\n\nWhat is this person doing? (in German)",
                    "Back" to "**$verb**\n\n$english\n\n*$ich*\n\n> $example  \n> *$translation*",
                ),
            )
        }

    /** picture | word | English | example | translation */
    internal fun colourCards(): List<Card> =
        EnglishDeck.rows(GermanA0Content.COLOURS_NUMBERS).map { (pic, word, english, example, translation) ->
            Card(
                mapOf(
                    "Front" to "# $pic\n\nWhat is this in German?",
                    "Back" to "**$word**\n\n$english\n\n> $example  \n> *$translation*",
                ),
            )
        }

    /** Plural column of a noun that has none. */
    const val NO_PLURAL = "–"
}
