package app.mnemo.data.seed

import app.mnemo.data.repo.NoteRepository
import app.mnemo.di.AppGraph
import app.mnemo.domain.graph.DeckGraph
import app.mnemo.domain.model.DeckSettings
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
    const val VERSION = 2

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
        if (installed == 0 || rootExists) install(graph, fresh = installed == 0)
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
    ): Int {
        val create = mutableListOf<NoteRepository.NewNote>()
        val update = mutableListOf<NoteRepository.NoteUpdate>()
        // Opening the whole English deck gives a small daily mix; opening one pack gives all of it.
        graph.decks.createByPath(ROOT, DeckSettings(newPerDay = 15, language = "en"))

        suspend fun packs(
            s: Section,
            label: String,
            cards: List<Card>,
            kind: NoteKind,
        ) {
            graph.decks.createByPath("$ROOT::${s.name}", DeckSettings(newPerDay = s.newPerDay, language = "en"))
            val type = graph.notes.noteTypeOf(kind)
            val keyField = type.fields.first()
            cards.chunked(PACK_SIZE).forEachIndexed { i, pack ->
                val name = "$label ${(i + 1).toString().padStart(2, '0')}"
                val deckId = graph.decks.createByPath("$ROOT::${s.name}::$name", DeckSettings(newPerDay = PACK_SIZE, language = "en"))
                val existing = graph.notes.byDeck(deckId).associateBy { it.fields[keyField].orEmpty() }
                pack.forEach { card ->
                    val tags = listOf("english", s.tag) + card.tags
                    val note = existing[card.fields[keyField].orEmpty()]
                    when {
                        note == null -> if (fresh) create += NoteRepository.NewNote(deckId, type, card.fields, tags)
                        // Edited by the user since it was installed: theirs wins.
                        note.updatedAt != note.createdAt -> {}
                        note.fields != card.fields || note.tags != tags ->
                            update += NoteRepository.NoteUpdate(note.id, deckId, card.fields, tags)
                    }
                }
            }
        }

        packs(vocabulary, "Vocabulary", vocabularyCards(), NoteKind.Vocab)
        packs(idioms, "Idioms", idiomCards(), NoteKind.Basic)
        packs(situations, "Situations", situationCards(), NoteKind.Basic)
        packs(
            pictures,
            "Things",
            pictureCards(EnglishDeckContent.PICTURE_THINGS, EnglishDeckExtras.PICTURE_THINGS, "What is this called?"),
            NoteKind.Basic,
        )
        packs(
            pictures,
            "Actions",
            pictureCards(EnglishDeckContent.PICTURE_ACTIONS, EnglishDeckExtras.PICTURE_ACTIONS, "What is this person doing?"),
            NoteKind.Basic,
        )
        packs(grammar, "Grammar", grammarCards(), NoteKind.Cloze)
        // One transaction each: a second or so on a phone, and all-or-nothing.
        val changed = graph.notes.createMany(create) + graph.notes.updateMany(update)
        // Updates keep the note's time stamps equal, so later versions can still update it.
        graph.notes.markUnedited(update.map { it.noteId })
        return changed
    }

    /** One card's fields and any extra tags (grammar topic). */
    internal data class Card(
        val fields: Map<String, String>,
        val tags: List<String> = emptyList(),
    )

    private fun quote(examples: List<String>) = examples.joinToString("\n\n") { "> $it" }

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
                .allViews()
                .filter { "english" in it.tags }
                .flatMap { v -> v.tags.mapNotNull { ids[it] }.map { v.card.id to it } }
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
