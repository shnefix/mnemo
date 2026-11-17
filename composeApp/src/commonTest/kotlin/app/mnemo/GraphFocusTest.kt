package app.mnemo

import app.mnemo.domain.graph.GraphFocus
import app.mnemo.domain.model.ConceptEdge
import app.mnemo.domain.model.Relation
import kotlin.test.Test
import kotlin.test.assertEquals

class GraphFocusTest {
    private val edges =
        listOf(
            ConceptEdge(1, 2, Relation.RelatedTo),
            ConceptEdge(3, 1, Relation.PrerequisiteOf),
            ConceptEdge(2, 4, Relation.PartOf),
            ConceptEdge(5, 6, Relation.RelatedTo),
        )

    @Test
    fun neighbourhoodFollowsEdgesBothWays() {
        assertEquals(mapOf(1L to 0, 2L to 1, 3L to 1), GraphFocus.neighborhood(edges, 1, 1))
        assertEquals(setOf(1L, 2L, 3L, 4L), GraphFocus.neighborhood(edges, 1, 2).keys)
        assertEquals(mapOf(7L to 0), GraphFocus.neighborhood(edges, 7, 2))
    }

    @Test
    fun radialLayoutCentresTheFocus() {
        val pos = GraphFocus.radialLayout(GraphFocus.neighborhood(edges, 1, 2))
        assertEquals(0.5f to 0.5f, pos[1L])
        pos.values.forEach { (x, y) -> check(x in 0f..1f && y in 0f..1f) }
    }
}
