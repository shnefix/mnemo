package app.mnemo.domain.graph

import app.mnemo.di.AppGraph
import app.mnemo.domain.model.Concept
import app.mnemo.domain.model.Deck
import app.mnemo.domain.model.Relation
import app.mnemo.platform.Prefs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Puts every deck on the knowledge graph: each top-level deck and each of its direct sub-decks
 * becomes a concept (a sub-deck is "part of" its parent), and every card is linked to the
 * concepts of the decks it sits in. Deeper decks (packs like "Vocabulary 07") roll up into
 * their sub-deck, so the graph stays readable.
 *
 * Runs at launch and whenever the Graph opens, so new decks and new cards show up on their own.
 * A deck concept the user deletes stays deleted ([rememberDeleted]); renaming one keeps it.
 * Deleting a deck removes its concept.
 */
object DeckGraph {
    /**
     * Hidden marker in a deck concept's description; ties the concept to its deck. The creation
     * time keeps it unique if a deck id is used again (after a restore, say).
     */
    fun marker(deck: Deck) = "$DECK_PREFIX${deck.id}@${deck.createdAt}"

    /** How many levels of decks get a concept: top-level decks and their direct sub-decks. */
    private const val MAX_DEPTH = 1

    /** Serialises graph syncs (launch and the Graph screen can start one at the same time). */
    val lock = Mutex()

    suspend fun sync(graph: AppGraph) = lock.withLock { syncLocked(graph) }

    private suspend fun syncLocked(graph: AppGraph) {
        val decks = graph.decks.all()
        if (decks.isEmpty()) return
        val byId = decks.associateBy { it.id }

        fun depth(d: Deck): Int {
            var n = 0
            var p = d.parentId
            while (p != null) {
                n++
                p = byId[p]?.parentId
            }
            return n
        }
        val conceptDecks = decks.filter { depth(it) <= MAX_DEPTH }
        val hidden = hiddenMarkers()
        val all = graph.concepts.all()
        // A deck that was deleted takes its concept with it.
        all.filter { it.description.startsWith(DECK_PREFIX) }.forEach { c ->
            if (decks.none { marker(it) == c.description }) graph.concepts.delete(c.id)
        }
        val byMarker = all.associateBy { it.description }
        val conceptOf = HashMap<Long, Long>()
        for (d in conceptDecks) {
            val m = marker(d)
            val existing = byMarker[m]
            when {
                existing != null -> conceptOf[d.id] = existing.id
                m in hidden -> {} // the user deleted this deck's concept
                else -> conceptOf[d.id] = graph.concepts.create(d.name, m)
            }
        }
        // Sub-deck concepts are part of their parent deck's concept.
        for (d in conceptDecks) {
            val child = conceptOf[d.id] ?: continue
            val parent = d.parentId?.let { conceptOf[it] } ?: continue
            graph.concepts.addEdge(child, parent, Relation.PartOf)
        }
        // Each card belongs to the concepts of every deck above it that has one.
        val conceptsForDeck = HashMap<Long, List<Long>>()

        fun conceptsAbove(deckId: Long): List<Long> =
            conceptsForDeck.getOrPut(deckId) {
                val out = mutableListOf<Long>()
                var cur: Deck? = byId[deckId]
                while (cur != null) {
                    conceptOf[cur.id]?.let { out += it }
                    cur = cur.parentId?.let { byId[it] }
                }
                out
            }
        val known =
            graph.concepts
                .allLinks()
                .flatMap { (c, cards) -> cards.map { it to c } }
                .toHashSet()
        val links =
            graph.cards
                .decksAndTags()
                .flatMap { p -> conceptsAbove(p.deckId).map { p.cardId to it } }
                .filter { it !in known }
        graph.concepts.linkMany(links)
    }

    private const val DECK_PREFIX = "deck:"

    /** Markers of built-in concepts (decks, topics) the user deleted; syncs leave those out. */
    fun hiddenMarkers(): Set<String> =
        Prefs
            .get(Prefs.GRAPH_HIDDEN)
            .orEmpty()
            .split('\n')
            .filter { it.isNotBlank() }
            .toSet()

    /** Call when the user deletes [concept]: a deck or topic concept is then not made again. */
    fun rememberDeleted(concept: Concept) {
        if (concept.description.isEmpty() || !concept.description.matches(Regex("""[a-z-]+:\S+"""))) return
        Prefs.set(Prefs.GRAPH_HIDDEN, (hiddenMarkers() + concept.description).sorted().joinToString("\n"))
    }

    /** The concept made for [deck], if it still exists. */
    suspend fun conceptFor(
        graph: AppGraph,
        deck: Deck,
    ): Concept? = graph.concepts.all().firstOrNull { it.description == marker(deck) }
}
