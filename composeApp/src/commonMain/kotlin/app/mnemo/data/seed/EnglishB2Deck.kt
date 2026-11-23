package app.mnemo.data.seed

import app.mnemo.data.io.DeckTrash
import app.mnemo.data.seed.SeedInstaller.Card
import app.mnemo.di.AppGraph
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.ReviewMode
import app.mnemo.platform.Prefs

/**
 * The built-in "English B2 practice" deck, for getting from B1 to a confident B2: your own
 * mistakes, phrasal verbs by topic, word families, linking words and dictation. It sits next to [EnglishDeck] with its own
 * daily mix, so its sections are not crowded out by the B1–B2 deck's.
 *
 * Versioned like [EnglishDeck]: a newer app updates installed cards the user has not edited.
 */
object EnglishB2Deck {
    const val ROOT = "English B2 practice"

    /** Bump when the content changes; older installs are updated on the next launch. */
    const val VERSION = 2

    data class Section(
        val name: String,
        val newPerDay: Int,
        val tag: String,
    )

    // "My mistakes" comes first: the daily mix takes sub-decks in name order, and your own
    // mistakes are the most valuable cards you'll ever have.
    val mistakes = Section("1. My mistakes", 5, "my-mistake")
    val phrasalVerbs = Section("3. Phrasal verbs by topic", 3, "phrasal-verb")
    val wordFamilies = Section("4. Word families", 2, "word-family")

    /** Tagged "linking" so the cards join the grammar deck's "Linking words" topic on the graph. */
    val linking = Section("5. Linking words", 2, "linking")
    val listening = Section("7. Listening", 2, "listening")

    val sections = listOf(mistakes, phrasalVerbs, wordFamilies, linking, listening)

    /** Version 2 took out "2. Collocations" and "6. Say it another way"; older installs trash them on update. */
    private const val REMOVED_SINCE = 2
    private val removed = listOf("2. Collocations", "6. Say it another way")

    /** Phrasal-verb topic codes in the content → sub-deck names. */
    val phrasalTopics =
        linkedMapOf(
            "work" to "Work",
            "travel" to "Travel",
            "relationships" to "Relationships",
            "home" to "Home & daily life",
            "health" to "Feelings & health",
            "money" to "Money & shopping",
            "talk" to "Talking & ideas",
            "problems" to "Problems & plans",
        )

    /** Adds the deck once, or brings an older install up to [VERSION]. A deleted deck stays deleted. */
    suspend fun installOnce(graph: AppGraph) {
        val installed = Prefs.get(Prefs.ENGLISH_B2_DECK)?.toIntOrNull() ?: 0
        if (installed >= VERSION) return
        val rootExists = graph.decks.all().any { it.name == ROOT && it.parentId == null }
        if (installed == 0 || rootExists) install(graph, fresh = installed == 0, from = installed)
        Prefs.set(Prefs.ENGLISH_B2_DECK, VERSION.toString())
    }

    suspend fun install(
        graph: AppGraph,
        fresh: Boolean = true,
        from: Int = 0,
    ): Int {
        val seed = SeedInstaller(graph, ROOT, fresh)
        seed.deck("", newPerDay = 15)

        fun tags(s: Section) = listOf("english", "b2", s.tag)

        // Empty on purpose: add the mistakes you make when you write or speak.
        seed.deck(mistakes.name, mistakes.newPerDay)
        seed.deck(phrasalVerbs.name, phrasalVerbs.newPerDay)
        phrasalVerbCards().forEach { (topic, cards) ->
            seed.fill(seed.deck("${phrasalVerbs.name}::${phrasalTopics.getValue(topic)}", 10), NoteKind.Basic, cards, tags(phrasalVerbs))
        }
        // Packs of 9 = three families, so a family is never split across packs.
        seed.packs(
            wordFamilies.name,
            wordFamilies.newPerDay,
            "Families",
            wordFamilyCards(),
            NoteKind.Cloze,
            tags(wordFamilies),
            packSize = 9,
        )
        seed.packs(linking.name, linking.newPerDay, "Linking", linkingCards(), NoteKind.Basic, tags(linking))
        seed.packs(
            listening.name,
            listening.newPerDay,
            "Dictation",
            listeningCards(),
            NoteKind.TypeAnswer,
            tags(listening),
            mode = ReviewMode.Type,
        )
        val changed = seed.commit()
        if (from in 1 until REMOVED_SINCE) {
            val all = graph.decks.all()
            val root = all.firstOrNull { it.name == ROOT && it.parentId == null }
            all.filter { d -> root != null && d.parentId == root.id && d.name in removed }.forEach { DeckTrash.delete(graph, it.id) }
        }
        return changed
    }

    private val gap = Regex("""\[([^\]]+)]""")

    /** Phrasal verbs grouped by topic code, in [phrasalTopics] order. */
    internal fun phrasalVerbCards(): List<Pair<String, List<Card>>> {
        val rows = EnglishDeck.rows(EnglishB2Content.PHRASAL_VERBS)
        return phrasalTopics.keys.map { topic ->
            topic to
                rows.filter { it[0] == topic }.map { r ->
                    val (_, verb, meaning) = r
                    Card(
                        mapOf(
                            "Front" to "**$verb**\n\nWhat does it mean?",
                            "Back" to "$meaning\n\n${EnglishDeck.quote(r.drop(3))}",
                        ),
                    )
                }
        }
    }

    /**
     * Use-of-English style gap fills: one card per sentence, the word in capitals to change.
     * Every card shows the whole family on the back.
     */
    internal fun wordFamilyCards(): List<Card> =
        EnglishDeck.rows(EnglishB2Content.WORD_FAMILIES).flatMap { r ->
            val base = r[0]
            val family = r[1]
            r.drop(2).map { sentence ->
                val text = sentence.replace(gap) { "{{c1::${it.groupValues[1]}}}" } + " (${base.uppercase()})"
                Card(mapOf("Text" to text, "Extra" to "Word family: $family"))
            }
        }

    internal fun linkingCards(): List<Card> =
        EnglishDeck.rows(EnglishB2Content.LINKING).map { r ->
            val (word, use, how) = r
            Card(
                mapOf(
                    "Front" to "**$word**\n\nWhat is it for, and how do you use it?",
                    "Back" to "$use\n\n*$how*\n\n${EnglishDeck.quote(r.drop(3))}",
                ),
            )
        }

    /** Dictation: the app reads the sentence aloud and you type it. */
    internal fun listeningCards(): List<Card> =
        EnglishDeck.rows(EnglishB2Content.LISTENING).map { (sentence) ->
            Card(mapOf("Front" to "[listen: $sentence]", "Back" to sentence))
        }
}
