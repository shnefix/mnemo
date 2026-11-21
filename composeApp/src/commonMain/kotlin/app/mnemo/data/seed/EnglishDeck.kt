package app.mnemo.data.seed

import app.mnemo.data.seed.SeedInstaller.Card
import app.mnemo.di.AppGraph
import app.mnemo.domain.graph.DeckGraph
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.Relation
import app.mnemo.platform.Prefs
import kotlinx.coroutines.sync.withLock

/**
 * The built-in English (B1–B2) deck every install gets: vocabulary, idioms and phrasal verbs,
 * what to say in real situations, naming things and actions from pictures, and grammar. Every
 * card shows three examples. New cards per day are split across the sub-decks, so studying the
 * whole deck gives a daily mix of about 15 new cards.
 *
 * The content is versioned ([VERSION]): a newer app updates the cards it installed earlier in
 * place (keeping their review history) unless the user edited them.
 */
object EnglishDeck {
    const val ROOT = "English (B1–B2)"

    /** Bump when the content changes; older installs are updated on the next launch. */
    const val VERSION = 4

    data class Section(
        val name: String,
        val newPerDay: Int,
        val tag: String,
    )

    val vocabulary = Section("1. Vocabulary", 5, "vocabulary")
    val idioms = Section("2. Idioms & phrasal verbs", 3, "idiom")
    val situations = Section("3. What to say", 3, "situation")
    val pictures = Section("4. Things to know", 3, "picture")
    val grammar = Section("5. Grammar", 2, "grammar")
    val numbers = Section("6. Numbers, time & money", 2, "numbers")

    /** Version in which [numbers] was added: older installs get it on update. */
    private const val NUMBERS_SINCE = 4

    /** What [pictures] was called before it was cut down to the useful things (version 2). */
    private const val OLD_PICTURES = "4. Name it (pictures)"

    /** Grammar topics: tag on the cards → concept name on the graph. */
    val topics =
        linkedMapOf(
            "past-tenses" to "Past tenses",
            "present-perfect" to "Present perfect",
            "past-perfect" to "Past perfect",
            "future" to "Future forms",
            "conditionals" to "Conditionals",
            "wishes" to "Wishes & regrets",
            "passive" to "Passive voice",
            "reported-speech" to "Reported speech",
            "modals" to "Modal verbs",
            "verb-patterns" to "Verb patterns (-ing / to)",
            "relative-clauses" to "Relative clauses",
            "comparisons" to "Comparisons",
            "quantifiers" to "Quantifiers",
            "articles" to "Articles",
            "prepositions" to "Prepositions",
            "linking" to "Linking words",
            "questions" to "Questions & short answers",
        )

    /** Learn the first topic before the second ([Relation.PrerequisiteOf]), or related topics. */
    private val topicEdges =
        listOf(
            Triple("past-tenses", "present-perfect", Relation.PrerequisiteOf),
            Triple("present-perfect", "past-perfect", Relation.PrerequisiteOf),
            Triple("past-tenses", "past-perfect", Relation.PrerequisiteOf),
            Triple("past-tenses", "conditionals", Relation.PrerequisiteOf),
            Triple("past-perfect", "conditionals", Relation.PrerequisiteOf),
            Triple("future", "conditionals", Relation.PrerequisiteOf),
            Triple("conditionals", "wishes", Relation.PrerequisiteOf),
            Triple("past-perfect", "wishes", Relation.PrerequisiteOf),
            Triple("past-tenses", "reported-speech", Relation.PrerequisiteOf),
            Triple("past-perfect", "reported-speech", Relation.PrerequisiteOf),
            Triple("past-tenses", "passive", Relation.PrerequisiteOf),
            Triple("present-perfect", "modals", Relation.RelatedTo),
            Triple("comparisons", "quantifiers", Relation.RelatedTo),
            Triple("articles", "quantifiers", Relation.RelatedTo),
            Triple("prepositions", "verb-patterns", Relation.RelatedTo),
        )

    /**
     * Adds the deck on a fresh install, or brings an older one up to [VERSION]. A deck the user
     * deleted is not brought back.
     */
    suspend fun installOnce(graph: AppGraph) {
        val installed = Prefs.get(Prefs.ENGLISH_DECK)?.toIntOrNull() ?: 0
        if (installed >= VERSION) return
        val rootExists = graph.decks.all().any { it.name == ROOT && it.parentId == null }
        if (installed == 0 || rootExists) install(graph, fresh = installed == 0, from = installed)
        Prefs.set(Prefs.ENGLISH_DECK, VERSION.toString())
    }

    /** Cards per pack: each section is split into small sub-decks (Vocabulary 01, 02, …). */
    const val PACK_SIZE = 10

    /**
     * Adds missing cards (only when [fresh]) and updates cards from an older version that the
     * user has not edited. Returns how many notes were created or changed.
     */
    suspend fun install(
        graph: AppGraph,
        fresh: Boolean = true,
        from: Int = 0,
    ): Int {
        val seed = SeedInstaller(graph, ROOT, fresh)
        // Opening the whole English deck gives a small daily mix; opening one pack gives all of it.
        seed.deck("", newPerDay = 15)

        suspend fun packs(
            s: Section,
            label: String,
            cards: List<Card>,
            kind: NoteKind,
        ) = seed.packs(s.name, s.newPerDay, label, cards, kind, listOf("english", s.tag))

        packs(vocabulary, "Vocabulary", vocabularyCards(), NoteKind.Vocab)
        packs(idioms, "Idioms", idiomCards(), NoteKind.Basic)
        packs(situations, "Situations", situationCards(), NoteKind.Basic)
        // Version 3 cut the pictures down to the useful ones: the section is renamed in place, its
        // cards move to the right pack, cards nobody uses are removed and new useful ones added.
        // A section the user deleted is not brought back.
        val decks = graph.decks.all()
        val rootDeck = decks.firstOrNull { it.name == ROOT && it.parentId == null }

        fun sectionOf(name: String) = rootDeck?.let { r -> decks.firstOrNull { it.name == name && it.parentId == r.id } }
        val old = sectionOf(OLD_PICTURES)
        val oldIds = old?.let { graph.decks.subtreeIds(it.id) }.orEmpty()
        val pool = oldIds.flatMap { graph.notes.byDeck(it) }
        val things = thingCards()
        val actions = actionCards()
        if (old != null) {
            graph.decks.rename(old.id, pictures.name)
            // Its concept on the knowledge graph follows the new name.
            graph.concepts
                .all()
                .firstOrNull { it.name == OLD_PICTURES && it.description.startsWith("deck:") }
                ?.let { graph.concepts.update(it.copy(name = pictures.name)) }
            seed.adopt(pool, "Front")
            // Cards added in version 3 are new even on an install that is only being updated.
            seed.alwaysCreate(thingCards(onlyNew = true) + actionCards(onlyNew = true))
        }
        if (fresh || old != null || sectionOf(pictures.name) != null) {
            packs(pictures, "Things", things, NoteKind.Basic)
            packs(pictures, "Actions", actions, NoteKind.Basic)
        }
        // Seed cards that are no longer in the deck (the rare ones), if you never edited them.
        val kept = (things + actions).map { it.fields.getValue("Front") }.toSet()
        val retired =
            pool.filter { n ->
                n.fields["Front"] !in kept && n.updatedAt == n.createdAt && n.tags == listOf("english", pictures.tag)
            }
        packs(grammar, "Grammar", grammarCards(), NoteKind.Cloze)
        // Added in version 4: a new section, so it is created even when only updating.
        if (from < NUMBERS_SINCE) seed.alwaysCreate(numberCards())
        packs(numbers, "Numbers", numberCards(), NoteKind.Basic)
        val changed = seed.commit() + retired.size
        retired.forEach { graph.notes.delete(it.id) }
        // Packs the move left empty.
        val all = graph.decks.all()
        oldIds.forEach { id ->
            val deck = all.firstOrNull { it.id == id } ?: return@forEach
            val isPack = deck.parentId != null && deck.name.matches(PACK_NAME)
            if (isPack && all.none { it.parentId == id } && graph.notes.countByDeck(id) == 0L) graph.decks.delete(id)
        }
        return changed
    }

    private val PACK_NAME = Regex(".* \\d\\d")

    internal fun quote(examples: List<String>) = examples.joinToString("\n\n") { "> $it" }

    internal fun vocabularyCards(): List<Card> {
        val more = extras(EnglishDeckExtras.VOCABULARY)
        return rows(EnglishDeckContent.VOCABULARY).map { (word, meaning, example) ->
            val examples = listOf(example) + more[word].orEmpty()
            Card(mapOf("Word" to word, "Translation" to meaning, "Example" to examples.joinToString("\n")))
        }
    }

    internal fun idiomCards(): List<Card> {
        val more = extras(EnglishDeckExtras.IDIOMS)
        return rows(EnglishDeckContent.IDIOMS).map { (phrase, meaning, example) ->
            val examples = listOf(example) + more[phrase].orEmpty()
            Card(mapOf("Front" to "**$phrase**\n\nWhat does it mean?", "Back" to "$meaning\n\n${quote(examples)}"))
        }
    }

    internal fun situationCards(): List<Card> {
        val more = extras(EnglishDeckExtras.SITUATIONS)
        return rows(EnglishDeckContent.SITUATIONS).map { (situation, say, other) ->
            val ways = listOf(other) + more[situation].orEmpty()
            Card(
                mapOf(
                    "Front" to "$situation\n\n*What do you say?*",
                    "Back" to "**$say**\n\nOther ways to say it:\n\n" + ways.joinToString("\n") { "- $it" },
                ),
            )
        }
    }

    private const val THINGS_QUESTION = "What is this called?"
    private const val ACTIONS_QUESTION = "What is this person doing?"

    /** The pictures of things worth knowing; [onlyNew] = just those added in version 3. */
    internal fun thingCards(onlyNew: Boolean = false): List<Card> {
        val v3 = pictureCards(EnglishDeckContent.PICTURE_THINGS_V3, EnglishDeckExtras.PICTURE_THINGS, THINGS_QUESTION)
        return if (onlyNew) v3 else pictureCards(EnglishDeckContent.PICTURE_THINGS, EnglishDeckExtras.PICTURE_THINGS, THINGS_QUESTION) + v3
    }

    internal fun actionCards(onlyNew: Boolean = false): List<Card> {
        val v3 = pictureCards(EnglishDeckContent.PICTURE_ACTIONS_V3, EnglishDeckExtras.PICTURE_ACTIONS, ACTIONS_QUESTION)
        return if (onlyNew) {
            v3
        } else {
            pictureCards(EnglishDeckContent.PICTURE_ACTIONS, EnglishDeckExtras.PICTURE_ACTIONS, ACTIONS_QUESTION) +
                v3
        }
    }

    internal fun pictureCards(
        content: String,
        extrasText: String,
        question: String,
    ): List<Card> {
        val more = extras(extrasText)
        return rows(content).map { (pic, name, hint) ->
            val examples = more[name].orEmpty()
            Card(mapOf("Front" to "# $pic\n\n$question", "Back" to "**$name**\n\n$hint\n\n${quote(examples)}".trim()))
        }
    }

    /** written | how you say it | another way | three examples */
    internal fun numberCards(): List<Card> =
        rows(EnglishNumbersContent.NUMBERS).map { r ->
            Card(
                mapOf(
                    "Front" to "**${r[0]}**\n\n*How do you say it?*",
                    "Back" to "**${r[1]}**\n\nAnother way: ${r[2]}\n\n${quote(r.drop(3))}",
                ),
            )
        }

    internal fun grammarCards(): List<Card> {
        val more = extras(EnglishDeckExtras.GRAMMAR)
        return rows(EnglishDeckContent.GRAMMAR).map { (text, extra) ->
            val m = more[text].orEmpty()
            val topic = m.firstOrNull()
            val examples = m.drop(1)
            val back = if (examples.isEmpty()) extra else "$extra\n\nMore examples:\n\n" + examples.joinToString("\n") { "- $it" }
            Card(mapOf("Text" to text, "Extra" to back), listOfNotNull(topic))
        }
    }

    /**
     * Grammar topics on the knowledge graph: one concept per topic, linked to its cards, "part
     * of" the Grammar deck's concept, with the order to learn them in. Safe to run again.
     */
    suspend fun syncGraph(graph: AppGraph) = DeckGraph.lock.withLock { syncGraphLocked(graph) }

    private suspend fun syncGraphLocked(graph: AppGraph) {
        val decks = graph.decks.all()
        val root = decks.firstOrNull { it.name == ROOT && it.parentId == null }
        val grammarDeck = root?.let { r -> decks.firstOrNull { it.name == grammar.name && it.parentId == r.id } }
        if (grammarDeck == null) {
            // The deck is gone: so are its topics.
            graph.concepts
                .all()
                .filter { it.description.startsWith(TOPIC_PREFIX) }
                .forEach { graph.concepts.delete(it.id) }
            return
        }
        val grammarConcept = DeckGraph.conceptFor(graph, grammarDeck)
        val hidden = DeckGraph.hiddenMarkers()
        val concepts = graph.concepts.all().associateBy { it.description }
        val ids = HashMap<String, Long>()
        topics.forEach { (tag, name) ->
            val marker = "$TOPIC_PREFIX$tag"
            val existing = concepts[marker]
            when {
                existing != null -> ids[tag] = existing.id
                marker !in hidden -> ids[tag] = graph.concepts.create(name, marker)
            }
        }
        if (ids.isEmpty()) return
        val links =
            graph.cards
                .decksAndTags()
                .filter { "english" in it.tags }
                .flatMap { p -> p.tags.mapNotNull { ids[it] }.map { p.cardId to it } }
        graph.concepts.linkMany(links)
        topicEdges.forEach { (from, to, relation) ->
            val a = ids[from] ?: return@forEach
            val b = ids[to] ?: return@forEach
            graph.concepts.addEdge(a, b, relation)
        }
        if (grammarConcept != null) ids.values.forEach { graph.concepts.addEdge(it, grammarConcept.id, Relation.PartOf) }
    }

    private const val TOPIC_PREFIX = "english-topic:"

    /** Splits the `|`-separated content into rows; blank lines are skipped. */
    internal fun rows(text: String): List<List<String>> =
        text
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line -> line.split('|').map { it.trim() } }

    /** Extra-content rows keyed by their first column. */
    internal fun extras(text: String): Map<String, List<String>> = rows(text).associate { it.first() to it.drop(1) }
}
