package app.mnemo.ui.graph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mnemo.data.seed.EnglishDeck
import app.mnemo.di.AppGraph
import app.mnemo.domain.cards.CardGenerator
import app.mnemo.domain.graph.ConceptSuggester
import app.mnemo.domain.graph.DeckGraph
import app.mnemo.domain.model.CardView
import app.mnemo.domain.model.Concept
import app.mnemo.domain.model.ConceptEdge
import app.mnemo.domain.model.DeckSettings
import app.mnemo.domain.model.Relation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

data class GraphNode(
    val concept: Concept,
    val mastery: Double,
    val cardCount: Int,
    val dueCount: Int,
    var x: Float,
    var y: Float,
)

data class LinkedCard(
    val view: CardView,
    val question: String,
    val retrievability: Double,
)

data class GraphUiState(
    val loading: Boolean = true,
    val nodes: List<GraphNode> = emptyList(),
    val edges: List<ConceptEdge> = emptyList(),
    val selectedId: Long? = null,
    val selectedCards: List<LinkedCard> = emptyList(),
    val allCards: List<Pair<CardView, String>> = emptyList(),
    val suggestions: List<ConceptSuggester.Suggestion> = emptyList(),
    val suggested: Boolean = false,
    /** Hops shown around the selected concept; 0 shows the whole graph. */
    val focusHops: Int = 0,
) {
    val selected: GraphNode? get() = nodes.firstOrNull { it.concept.id == selectedId }

    /** Nodes to draw: everything, or the selected concept's neighbourhood laid out in rings. */
    val visibleNodes: List<GraphNode>
        get() {
            val center = selectedId
            if (focusHops == 0 || center == null) return nodes
            val dist =
                app.mnemo.domain.graph.GraphFocus
                    .neighborhood(edges, center, focusHops)
            val pos =
                app.mnemo.domain.graph.GraphFocus
                    .radialLayout(dist)
            return nodes.filter { it.concept.id in dist }.map { n ->
                val (x, y) = pos.getValue(n.concept.id)
                n.copy(x = x, y = y)
            }
        }
}

class GraphViewModel(
    private val graph: AppGraph,
) : ViewModel() {
    private val _state = MutableStateFlow(GraphUiState())
    val state: StateFlow<GraphUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            // New decks and cards join the graph on their own.
            runCatching {
                DeckGraph.sync(graph)
                EnglishDeck.syncGraph(graph)
            }
            val now = graph.clock.now()
            val concepts = graph.concepts.all()
            val links = graph.concepts.allLinks()
            val edges = graph.concepts.edges()
            val types = graph.notes.noteTypes().associateBy { it.id }
            val views = graph.cards.allViews().associateBy { it.card.id }
            val sched = graph.scheduler(DeckSettings())
            val old = _state.value.nodes.associateBy { it.concept.id }
            val nodes =
                concepts.mapIndexed { i, c ->
                    val ids = links[c.id].orEmpty()
                    val cards = ids.mapNotNull { views[it] }
                    val mastery = if (cards.isEmpty()) 0.0 else cards.map { sched.retrievability(it.card, now) }.average()
                    val angle = (i.toFloat() / concepts.size.coerceAtLeast(1)) * 2f * 3.14159f
                    GraphNode(
                        concept = c,
                        mastery = mastery,
                        cardCount = cards.size,
                        dueCount = cards.count { it.card.due <= now && !it.card.suspended },
                        x = old[c.id]?.x ?: (0.5f + 0.35f * cos(angle)),
                        y = old[c.id]?.y ?: (0.5f + 0.35f * sin(angle)),
                    )
                }
            layout(nodes, edges)
            val all =
                views.values
                    .map { v ->
                        v to
                            (
                                types[v.noteTypeId]?.let {
                                    CardGenerator
                                        .render(
                                            it,
                                            v.fields,
                                            v.card.templateIndex,
                                        ).question
                                        .lineSequence()
                                        .first()
                                }
                                    ?: ""
                            )
                    }.sortedBy { it.second.lowercase() }
            _state.update { it.copy(loading = false, nodes = nodes, edges = edges, allCards = all) }
            _state.value.selectedId?.let { select(it) }
        }
    }

    /** Positions the nodes with [GraphFocus.forceLayout] (stable for any graph size). */
    private fun layout(
        nodes: List<GraphNode>,
        edges: List<ConceptEdge>,
    ) {
        if (nodes.size < 2) return
        val pos =
            app.mnemo.domain.graph.GraphFocus.forceLayout(
                nodes.associate { it.concept.id to (it.x to it.y) },
                edges.map { it.fromId to it.toId },
            )
        nodes.forEach { n ->
            pos[n.concept.id]?.let { (x, y) ->
                n.x = x
                n.y = y
            }
        }
    }

    fun setFocus(hops: Int) = _state.update { it.copy(focusHops = hops) }

    fun select(id: Long?) {
        _state.update { it.copy(selectedId = id, focusHops = if (id == null) 0 else it.focusHops) }
        if (id == null) return
        viewModelScope.launch {
            val now = graph.clock.now()
            val types = graph.notes.noteTypes().associateBy { it.id }
            val sched = graph.scheduler(DeckSettings())
            val cards =
                graph.concepts
                    .cardsFor(id)
                    .mapNotNull { c ->
                        val v = graph.cards.view(c.id) ?: return@mapNotNull null
                        val q =
                            types[v.noteTypeId]?.let {
                                CardGenerator
                                    .render(
                                        it,
                                        v.fields,
                                        v.card.templateIndex,
                                    ).question
                                    .lineSequence()
                                    .first()
                            }
                                ?: ""
                        LinkedCard(v, q, sched.retrievability(v.card, now))
                    }.sortedBy { it.retrievability }
            _state.update { it.copy(selectedCards = cards) }
        }
    }

    /** Groups cards by tags and by text similarity into concept proposals (no AI involved). */
    fun suggest() {
        viewModelScope.launch {
            val s = _state.value
            val texts =
                s.allCards.map { (v, q) ->
                    ConceptSuggester.CardText(v.card.id, q + " " + v.fields.values.joinToString(" "), v.tags, v.deckName)
                }
            val existing = s.nodes.map { it.concept.name }.toSet()
            _state.update { it.copy(suggestions = ConceptSuggester.suggest(texts, existing), suggested = true) }
        }
    }

    fun acceptSuggestion(sg: ConceptSuggester.Suggestion) {
        viewModelScope.launch {
            val id = graph.concepts.create(sg.name, sg.reason)
            sg.cardIds.forEach { graph.concepts.link(it, id) }
            _state.update { it.copy(suggestions = it.suggestions - sg) }
            reload()
            select(id)
        }
    }

    fun dismissSuggestion(sg: ConceptSuggester.Suggestion) = _state.update { it.copy(suggestions = it.suggestions - sg) }

    fun createConcept(
        name: String,
        description: String = "",
    ) {
        viewModelScope.launch {
            val id = graph.concepts.create(name, description)
            reload()
            select(id)
        }
    }

    fun rename(
        id: Long,
        name: String,
    ) {
        viewModelScope.launch {
            graph.concepts.byId(id)?.let { graph.concepts.update(it.copy(name = name)) }
            reload()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            graph.concepts.byId(id)?.let { DeckGraph.rememberDeleted(it) }
            graph.concepts.delete(id)
            _state.update { it.copy(selectedId = null, selectedCards = emptyList()) }
            reload()
        }
    }

    fun link(
        cardId: Long,
        conceptId: Long,
    ) {
        viewModelScope.launch {
            graph.concepts.link(cardId, conceptId)
            reload()
        }
    }

    fun unlink(
        cardId: Long,
        conceptId: Long,
    ) {
        viewModelScope.launch {
            graph.concepts.unlink(cardId, conceptId)
            reload()
        }
    }

    fun addEdge(
        from: Long,
        to: Long,
        relation: Relation,
    ) {
        viewModelScope.launch {
            graph.concepts.addEdge(from, to, relation)
            reload()
        }
    }

    fun removeEdge(edge: ConceptEdge) {
        viewModelScope.launch {
            graph.concepts.removeEdge(edge)
            reload()
        }
    }
}
