package app.mnemo.data.seed

import app.mnemo.data.seed.SeedInstaller.Card
import app.mnemo.di.AppGraph
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.ReviewMode
import app.mnemo.platform.Prefs

/**
 * The built-in "English B2 practice" deck, for getting from B1 to a confident B2: your own
 * mistakes, collocations, phrasal verbs by topic, word families, linking words, "say it another
 * way" (key word transformations) and dictation. It sits next to [EnglishDeck] with its own
 * daily mix, so its sections are not crowded out by the B1–B2 deck's.
 *
 * Versioned like [EnglishDeck]: a newer app updates installed cards the user has not edited.
 */
object EnglishB2Deck {
    const val ROOT = "English B2 practice"

    /** Bump when the content changes; older installs are updated on the next launch. */
    const val VERSION = 1

    data class Section(
        val name: String,
        val newPerDay: Int,
        val tag: String,
    )

    // "My mistakes" comes first: the daily mix takes sub-decks in name order, and your own
    // mistakes are the most valuable cards you'll ever have.
    val mistakes = Section("1. My mistakes", 5, "my-mistake")
    val collocations = Section("2. Collocations", 3, "collocation")
    val phrasalVerbs = Section("3. Phrasal verbs by topic", 3, "phrasal-verb")
    val wordFamilies = Section("4. Word families", 2, "word-family")

    /** Tagged "linking" so the cards join the grammar deck's "Linking words" topic on the graph. */
    val linking = Section("5. Linking words", 2, "linking")
    val rewriting = Section("6. Say it another way", 2, "rewriting")
    val listening = Section("7. Listening", 2, "listening")

    val sections = listOf(mistakes, collocations, phrasalVerbs, wordFamilies, linking, rewriting, listening)

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
        if (installed == 0 || rootExists) install(graph, fresh = installed == 0)
        Prefs.set(Prefs.ENGLISH_B2_DECK, VERSION.toString())
    }

    suspend fun install(
        graph: AppGraph,
        fresh: Boolean = true,
    ): Int {
        val seed = SeedInstaller(graph, ROOT, fresh)
        seed.deck("", newPerDay = 15)

        fun tags(s: Section) = listOf("english", "b2", s.tag)

        // Empty on purpose: add the mistakes you make when you write or speak.
        seed.deck(mistakes.name, mistakes.newPerDay)
        seed.packs(collocations.name, collocations.newPerDay, "Collocations", collocationCards(), NoteKind.Basic, tags(collocations))
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
        seed.packs(rewriting.name, rewriting.newPerDay, "Rewrite", rewritingCards(), NoteKind.Basic, tags(rewriting))
        seed.packs(
            listening.name,
            listening.newPerDay,
            "Dictation",
            listeningCards(),
            NoteKind.TypeAnswer,
            tags(listening),
            mode = ReviewMode.Type,
        )
        return seed.commit()
    }

    /** A gap to fill; not underscores, which Markdown reads as emphasis. */
    private const val BLANK = "……"

    private val gap = Regex("""\[([^\]]+)]""")

    /** `[make] a decision` → front "…… a decision", back "**make** a decision". */
    internal fun collocationCards(): List<Card> =
        EnglishDeck.rows(EnglishB2Content.COLLOCATIONS).map { r ->
            val (phrase, meaning, wrong) = r
            val examples = r.drop(3)
            val front = phrase.replace(gap, BLANK)
            val bold = phrase.replace(gap) { "**${it.groupValues[1]}**" }
            val not = if (wrong == "-") "" else "\n\nNot: ~~${phrase.replace(gap, wrong)}~~"
            Card(
                mapOf(
                    "Front" to "**$front**\n\n*$meaning*\n\nWhich word goes in the gap?",
                    "Back" to "$bold$not\n\n${EnglishDeck.quote(examples)}",
                ),
            )
        }

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

    /** Key word transformations: same meaning, 2–5 words including the word in capitals. */
    internal fun rewritingCards(): List<Card> =
        EnglishDeck.rows(EnglishB2Content.REWRITING).map { (original, key, gapped, answer, tip) ->
            Card(
                mapOf(
                    "Front" to
                        "$original\n\nSay it another way with **$key** (2–5 words, don't change $key):\n\n" +
                        gapped.replace("___", BLANK),
                    "Back" to "${gapped.replace("___", "**$answer**")}\n\n*$tip*",
                ),
            )
        }

    /** Dictation: the app reads the sentence aloud and you type it. */
    internal fun listeningCards(): List<Card> =
        EnglishDeck.rows(EnglishB2Content.LISTENING).map { (sentence) ->
            Card(mapOf("Front" to "[listen: $sentence]", "Back" to sentence))
        }
}
