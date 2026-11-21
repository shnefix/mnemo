package app.mnemo.data.seed

import app.mnemo.data.seed.SeedInstaller.Card
import app.mnemo.di.AppGraph
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.ReviewMode
import app.mnemo.platform.Prefs

/**
 * The built-in "German (A0)" deck, for the very first German words, in three sections:
 *  1. Name it: a picture and the German word. Nouns come with their article (der / die / das)
 *     and plural, verbs with their "ich" form, plus colours and the numbers 1–10. The categories
 *     are numbered so the daily mix starts with the most useful ones.
 *  2. What to say: the first phrases for real situations (greetings, ordering, asking the way).
 *  3. Type it (articles): the same nouns, but you type the article (der / die / das).
 * Every card has a very simple example sentence with its translation.
 *
 * Versioned like [EnglishDeck]: a newer app updates installed cards the user has not edited.
 */
object GermanA0Deck {
    const val ROOT = "German (A0)"

    /** Bump when the content changes; older installs are updated on the next launch. */
    const val VERSION = 2

    /** Version in which the categories were nested and the other two sections were added. */
    private const val SECTIONS_SINCE = 2

    val nameIt = EnglishDeck.Section("1. Name it", 5, "name-it")
    val whatToSay = EnglishDeck.Section("2. What to say", 3, "situation")
    val articles = EnglishDeck.Section("3. Type it (articles)", 3, "article")

    /** The categories of [nameIt], in the order the daily mix takes them. */
    private class Category(
        val name: String,
        val label: String,
        val cards: () -> List<Card>,
    )

    private val categories =
        listOf(
            Category("1. Things", "Things") { nounCards(GermanA0Content.THINGS) },
            Category("2. People & places", "People & places") { nounCards(GermanA0Content.PEOPLE_PLACES) },
            Category("3. Colours & numbers", "Colours & numbers") { colourCards() },
            Category("4. Food & drink", "Food & drink") { nounCards(GermanA0Content.FOOD) },
            Category("5. Animals", "Animals") { nounCards(GermanA0Content.ANIMALS) },
            Category("6. Nature & body", "Nature & body") { nounCards(GermanA0Content.NATURE_BODY) },
            Category("7. Actions", "Actions") { verbCards() },
        )

    /** Adds the deck once, or brings an older install up to [VERSION]. A deleted deck stays deleted. */
    suspend fun installOnce(graph: AppGraph) {
        val installed = Prefs.get(Prefs.GERMAN_A0_DECK)?.toIntOrNull() ?: 0
        if (installed >= VERSION) return
        val rootExists = graph.decks.all().any { it.name == ROOT && it.parentId == null }
        if (installed == 0 || rootExists) install(graph, fresh = installed == 0, from = installed)
        Prefs.set(Prefs.GERMAN_A0_DECK, VERSION.toString())
    }

    suspend fun install(
        graph: AppGraph,
        fresh: Boolean = true,
        from: Int = 0,
    ): Int {
        val seed = SeedInstaller(graph, ROOT, fresh, language = "de")
        seed.deck("", newPerDay = 10)
        val tags = listOf("german", "a0")

        // Version 1 had the packs straight under "1. Name it": its cards move into the categories.
        val decks = graph.decks.all()
        val rootDeck = decks.firstOrNull { it.name == ROOT && it.parentId == null }
        val old = rootDeck?.let { r -> decks.firstOrNull { it.name == nameIt.name && it.parentId == r.id } }
        val oldIds = old?.let { graph.decks.subtreeIds(it.id) }.orEmpty()
        seed.adopt(oldIds.flatMap { graph.notes.byDeck(it) }, "Front")
        // Cards of the sections added in version 2 are new even on an install that is only updating.
        if (from < SECTIONS_SINCE) seed.alwaysCreate(situationCards() + articleCards())

        categories.forEach { c ->
            seed.packs("${nameIt.name}::${c.name}", nameIt.newPerDay, c.label, c.cards(), NoteKind.Basic, tags + nameIt.tag)
        }
        seed.packs(whatToSay.name, whatToSay.newPerDay, "Phrases", situationCards(), NoteKind.Basic, tags + whatToSay.tag)
        seed.packs(
            articles.name,
            articles.newPerDay,
            "Articles",
            articleCards(),
            NoteKind.TypeAnswer,
            tags + articles.tag,
            packSize = ARTICLE_PACK_SIZE,
            mode = ReviewMode.Type,
        )
        val changed = seed.commit()
        // Packs the move left empty.
        val all = graph.decks.all()
        oldIds.forEach { id ->
            val deck = all.firstOrNull { it.id == id } ?: return@forEach
            val isPack = deck.parentId == old?.id && deck.name.matches(Regex(".* \\d\\d"))
            if (isPack && all.none { it.parentId == id } && graph.notes.countByDeck(id) == 0L) graph.decks.delete(id)
        }
        return changed
    }

    private const val ARTICLE_PACK_SIZE = 20

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

    /** situation | what you say | translation | another way | translation */
    internal fun situationCards(): List<Card> =
        EnglishDeck.rows(GermanA0Content.SITUATIONS).map { r ->
            val (situation, say, translation, other, otherTranslation) = r
            Card(
                mapOf(
                    "Front" to "$situation\n\n*What do you say?*",
                    "Back" to "**$say**\n\n*$translation*\n\nAnother way:\n\n- $other — *$otherTranslation*",
                ),
            )
        }

    /** The article drill: the noun without its article; you type der, die or das. */
    internal fun articleCards(): List<Card> =
        nounSets.flatMap { text ->
            EnglishDeck.rows(text).map { r ->
                val (article, noun) = r[1].split(' ', limit = 2)
                Card(mapOf("Front" to "# ${r[0]}\n\n…… $noun\n\nder, die or das?", "Back" to article))
            }
        }

    private val nounSets =
        listOf(
            GermanA0Content.THINGS,
            GermanA0Content.PEOPLE_PLACES,
            GermanA0Content.FOOD,
            GermanA0Content.ANIMALS,
            GermanA0Content.NATURE_BODY,
        )

    /** Plural column of a noun that has none. */
    const val NO_PLURAL = "–"
}
