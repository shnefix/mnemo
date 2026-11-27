package app.mnemo.data.seed

import app.mnemo.data.seed.EnglishDeck.Section
import app.mnemo.data.seed.EnglishDeck.rows
import app.mnemo.data.seed.SeedInstaller.Card
import app.mnemo.di.AppGraph
import app.mnemo.domain.graph.DeckGraph
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.Relation
import app.mnemo.platform.Prefs
import kotlinx.coroutines.sync.withLock

/**
 * The built-in "AI & ML" deck, for learning AI engineering beyond interview questions:
 *  1. History: the milestones, when they happened and why they matter, plus "put in order" cards.
 *  2. People: who did what.
 *  3. Landmark papers: the one idea each introduced.
 *  4. Failures & ethics: real cases and their lessons.
 *  5. Confusable pairs: precision vs recall, L1 vs L2, encoder vs decoder…
 *  6. Formulas: cloze cards (softmax, cross-entropy, attention…).
 *  7. Diagrams: image occlusion on drawn diagrams (Transformer, confusion matrix, RAG…).
 *  8. What's wrong?: symptoms of a broken model or pipeline, their causes and fixes.
 *  9. Numbers to know: memory, compute, dataset sizes, defaults.
 *  10. Code: PyTorch, scikit-learn, NumPy and Hugging Face snippets with a blank.
 *  11. Tools: what each library or service is for.
 *  12. Writing prompts: techniques for writing a good prompt (added in version 2).
 * Cards carry a topic tag (`ml-<topic>`); [syncGraph] puts the topics on the knowledge graph as a
 * concept map, with the order to learn them in.
 *
 * Versioned like [EnglishDeck]: a newer app updates installed cards the user has not edited.
 */
object AiDeck {
    const val ROOT = "AI & ML"

    /** Bump when the content changes; older installs are updated on the next launch. */
    const val VERSION = 2

    val history = Section("1. History", 2, "history")
    val people = Section("2. People", 1, "people")
    val papers = Section("3. Landmark papers", 2, "paper")
    val failures = Section("4. Failures & ethics", 1, "ethics")
    val pairs = Section("5. Confusable pairs", 3, "pairs")
    val formulas = Section("6. Formulas", 2, "formula")
    val diagrams = Section("7. Diagrams", 2, "diagram")
    val debugging = Section("8. What's wrong?", 2, "debugging")
    val numbers = Section("9. Numbers to know", 1, "numbers")
    val code = Section("10. Code", 2, "code")
    val tools = Section("11. Tools", 1, "tools")
    val prompts = Section("12. Writing prompts", 2, "prompting")

    /** Prefix of the diagram pictures in the media folder. */
    internal const val MEDIA_PREFIX = "mnemo-ai-"

    /** Topic code (tag `ml-<code>`) → concept name on the graph, in learning order. */
    val topics =
        linkedMapOf(
            "linalg" to "Linear algebra",
            "prob" to "Probability & statistics",
            "calc" to "Calculus & gradients",
            "data" to "Data & features",
            "classic" to "Classic ML models",
            "eval" to "Evaluation & metrics",
            "optim" to "Optimisation & training",
            "regular" to "Overfitting & regularisation",
            "nn" to "Neural networks",
            "cnn" to "CNNs & vision",
            "seq" to "Sequence models (RNN, LSTM)",
            "transformer" to "Attention & Transformers",
            "embed" to "Embeddings & retrieval",
            "gen" to "Generative models",
            "rl" to "Reinforcement learning",
            "llmtrain" to "Training LLMs",
            "llmuse" to "Using LLMs (prompting, RAG, agents)",
            "mlops" to "MLOps & deployment",
        )

    /** Learn the first topic before the second, or the two are related. */
    private val topicEdges =
        listOf(
            Triple("linalg", "nn", Relation.PrerequisiteOf),
            Triple("calc", "optim", Relation.PrerequisiteOf),
            Triple("prob", "classic", Relation.PrerequisiteOf),
            Triple("prob", "eval", Relation.PrerequisiteOf),
            Triple("data", "classic", Relation.PrerequisiteOf),
            Triple("classic", "eval", Relation.PrerequisiteOf),
            Triple("classic", "regular", Relation.PrerequisiteOf),
            Triple("optim", "nn", Relation.PrerequisiteOf),
            Triple("nn", "cnn", Relation.PrerequisiteOf),
            Triple("nn", "seq", Relation.PrerequisiteOf),
            Triple("seq", "transformer", Relation.PrerequisiteOf),
            Triple("linalg", "embed", Relation.PrerequisiteOf),
            Triple("embed", "transformer", Relation.PrerequisiteOf),
            Triple("nn", "gen", Relation.PrerequisiteOf),
            Triple("prob", "gen", Relation.PrerequisiteOf),
            Triple("optim", "rl", Relation.PrerequisiteOf),
            Triple("transformer", "llmtrain", Relation.PrerequisiteOf),
            Triple("llmtrain", "llmuse", Relation.PrerequisiteOf),
            Triple("embed", "llmuse", Relation.PrerequisiteOf),
            Triple("eval", "mlops", Relation.PrerequisiteOf),
            Triple("regular", "optim", Relation.RelatedTo),
            Triple("rl", "llmtrain", Relation.RelatedTo),
            Triple("cnn", "gen", Relation.RelatedTo),
        )

    /** Adds the deck once, or brings an older install up to [VERSION]. A deleted deck stays deleted. */
    suspend fun installOnce(graph: AppGraph) {
        val installed = Prefs.get(Prefs.AI_DECK)?.toIntOrNull() ?: 0
        if (installed >= VERSION) return
        val rootExists = graph.decks.all().any { it.name == ROOT && it.parentId == null }
        if (installed == 0 || rootExists) install(graph, fresh = installed == 0)
        Prefs.set(Prefs.AI_DECK, VERSION.toString())
    }

    suspend fun install(
        graph: AppGraph,
        fresh: Boolean = true,
    ): Int {
        SeedMedia.install("ai", AiDiagrams.all.map { it.image }, MEDIA_PREFIX)
        val seed = SeedInstaller(graph, ROOT, fresh)
        seed.deck("", newPerDay = 15)

        suspend fun packs(
            s: Section,
            label: String,
            cards: List<Card>,
            kind: NoteKind = NoteKind.Basic,
            packSize: Int = EnglishDeck.PACK_SIZE,
        ) = seed.packs(s.name, s.newPerDay, label, cards, kind, listOf("ai-ml", s.tag), packSize = packSize)

        packs(history, "History", historyCards())
        packs(people, "People", peopleCards())
        packs(papers, "Papers", paperCards())
        packs(failures, "Cases", failureCards())
        packs(pairs, "Pairs", pairCards())
        packs(formulas, "Formulas", formulaCards(), NoteKind.Cloze)
        // A diagram makes one card per label (6–9), so a pack holds two diagrams.
        packs(diagrams, "Diagrams", diagramCards(), NoteKind.Occlusion, packSize = DIAGRAMS_PER_PACK)
        packs(debugging, "Debugging", debuggingCards())
        packs(numbers, "Numbers", numberCards())
        packs(code, "Code", codeCards())
        packs(tools, "Tools", toolCards())
        // Added in version 2: created on older installs too.
        seed.alwaysCreate(promptCards())
        packs(prompts, "Prompts", promptCards())
        return seed.commit()
    }

    private const val DIAGRAMS_PER_PACK = 2

    /** The last column of a row, when it is a `#topic`, as a tag. */
    private fun topicTag(row: List<String>): List<String> =
        row
            .lastOrNull()
            ?.takeIf { it.startsWith("#") }
            ?.let { listOf("ml-" + it.drop(1)) }
            .orEmpty()

    /** The row without its `#topic` column. */
    private fun fieldsOf(row: List<String>) = if (row.lastOrNull()?.startsWith("#") == true) row.dropLast(1) else row

    internal fun historyCards(): List<Card> =
        rows(AiDeckContent.HISTORY).map { r ->
            val (event, year, why) = fieldsOf(r)
            Card(mapOf("Front" to "**$event**\n\n*When was it, and why does it matter?*", "Back" to "**$year**\n\n$why"), topicTag(r))
        } +
            rows(AiDeckContent.ORDER).map { (items, answer) ->
                Card(mapOf("Front" to "**$items**\n\n*Oldest first.*", "Back" to answer))
            }

    internal fun peopleCards(): List<Card> =
        rows(AiDeckContent.PEOPLE).map { (name, knownFor) ->
            Card(mapOf("Front" to "**$name**\n\n*What are they known for?*", "Back" to knownFor))
        }

    internal fun paperCards(): List<Card> =
        rows(AiDeckContent.PAPERS).map { r ->
            val (title, authors, idea) = fieldsOf(r)
            Card(mapOf("Front" to "**$title**\n\n$authors\n\n*What idea did it introduce?*", "Back" to idea), topicTag(r))
        }

    internal fun failureCards(): List<Card> =
        rows(AiDeckContent.FAILURES).map { (case, what, lesson) ->
            Card(mapOf("Front" to "**$case**\n\n*What went wrong, and what's the lesson?*", "Back" to "$what\n\n**Lesson:** $lesson"))
        }

    internal fun pairCards(): List<Card> =
        rows(AiDeckContent.PAIRS).map { r ->
            val (pair, first, second, matters) = fieldsOf(r)
            Card(mapOf("Front" to "**$pair**\n\n*What's the difference?*", "Back" to "$first\n\n$second\n\n> $matters"), topicTag(r))
        }

    internal fun formulaCards(): List<Card> =
        rows(AiDeckContent.FORMULAS).map { r ->
            val (text, extra) = fieldsOf(r)
            Card(mapOf("Text" to text, "Extra" to extra), topicTag(r))
        }

    internal fun diagramCards(): List<Card> =
        AiDiagrams.all.map { d ->
            Card(
                mapOf(
                    "Image" to MEDIA_PREFIX + d.image,
                    "Occlusions" to d.occlusions,
                    "Header" to d.header,
                    "Back Extra" to d.extra,
                ),
            )
        }

    internal fun debuggingCards(): List<Card> =
        rows(AiDeckContent.WRONG).map { r ->
            val (symptom, causes, fix) = fieldsOf(r)
            Card(
                mapOf(
                    "Front" to "**$symptom**\n\n*What's likely wrong, and what do you do?*",
                    "Back" to "**Likely:** $causes\n\n**Do:** $fix",
                ),
                topicTag(r),
            )
        }

    internal fun numberCards(): List<Card> =
        rows(AiDeckContent.NUMBERS).map { r ->
            val (question, answer, why) = fieldsOf(r)
            Card(mapOf("Front" to "**$question**", "Back" to "**$answer**\n\n$why"), topicTag(r))
        }

    internal fun codeCards(): List<Card> =
        rows(AiDeckContent.CODE).map { r ->
            val (question, blank, full, why) = fieldsOf(r).map { it.replace("\\n", "\n") }
            Card(
                mapOf(
                    "Front" to "**$question**\n\n```python\n$blank\n```",
                    "Back" to "```python\n$full\n```\n\n$why",
                ),
                topicTag(r),
            )
        }

    internal fun toolCards(): List<Card> =
        rows(AiDeckContent.TOOLS).map { r ->
            val (tool, what) = fieldsOf(r)
            Card(mapOf("Front" to "**$tool**\n\n*What is it for?*", "Back" to what), topicTag(r))
        }

    internal fun promptCards(): List<Card> =
        rows(AiDeckContent.PROMPTS).map { r ->
            val (technique, what, example) = fieldsOf(r)
            Card(
                mapOf(
                    "Front" to "**$technique**\n\n*What is it, and why does it work?*",
                    "Back" to "$what\n\n> **In the AI history prompt:** $example",
                ),
                topicTag(r),
            )
        }

    /**
     * The concept map: one concept per topic, linked to its cards, "part of" the deck's own concept,
     * with the order to learn them in. Safe to run again; topics the user deleted stay deleted.
     */
    suspend fun syncGraph(graph: AppGraph) = DeckGraph.lock.withLock { syncGraphLocked(graph) }

    private suspend fun syncGraphLocked(graph: AppGraph) {
        val root = graph.decks.all().firstOrNull { it.name == ROOT && it.parentId == null }
        if (root == null) {
            // The deck is gone: so are its topics.
            graph.concepts
                .all()
                .filter { it.description.startsWith(TOPIC_PREFIX) }
                .forEach { graph.concepts.delete(it.id) }
            return
        }
        val rootConcept = DeckGraph.conceptFor(graph, root)
        val hidden = DeckGraph.hiddenMarkers()
        val concepts = graph.concepts.all().associateBy { it.description }
        val ids = HashMap<String, Long>()
        topics.forEach { (code, name) ->
            val marker = "$TOPIC_PREFIX$code"
            val existing = concepts[marker]
            when {
                existing != null -> ids[code] = existing.id
                marker !in hidden -> ids[code] = graph.concepts.create(name, marker)
            }
        }
        if (ids.isEmpty()) return
        val links =
            graph.cards
                .decksAndTags()
                .filter { "ai-ml" in it.tags }
                .flatMap { p ->
                    p.tags
                        .filter { it.startsWith("ml-") }
                        .mapNotNull { ids[it.removePrefix("ml-")] }
                        .map { p.cardId to it }
                }
        graph.concepts.linkMany(links)
        topicEdges.forEach { (from, to, relation) ->
            val a = ids[from] ?: return@forEach
            val b = ids[to] ?: return@forEach
            graph.concepts.addEdge(a, b, relation)
        }
        if (rootConcept != null) ids.values.forEach { graph.concepts.addEdge(it, rootConcept.id, Relation.PartOf) }
    }

    private const val TOPIC_PREFIX = "ai-topic:"
}
