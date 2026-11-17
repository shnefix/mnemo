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

    /** The English deck's shape: root + 5 sections + 17 topics. Nodes spread out instead of piling on the walls. */
    @Test
    fun forceLayoutSpreadsALargeGraph() {
        val n = 23
        val start =
            (1L..n).associateWith { i ->
                val a =
                    i * 2 * kotlin.math.PI / n
                (0.5f + 0.35f * kotlin.math.cos(a).toFloat()) to (0.5f + 0.35f * kotlin.math.sin(a).toFloat())
            }
        val edges = (2L..6L).map { it to 1L } + (7L..23L).map { it to 6L } + listOf(7L to 8L, 8L to 9L, 9L to 11L, 10L to 11L)
        val pos = GraphFocus.forceLayout(start, edges)
        assertEquals(n, pos.size)
        val pts = pos.values.toList()
        pts.forEach { (x, y) -> kotlin.test.assertTrue(x in 0.08f..0.92f && y in 0.1f..0.9f) }
        val minGap =
            pts.indices
                .flatMap { i ->
                    (i + 1 until n).map { j ->
                        kotlin.math.hypot(
                            (pts[i].first - pts[j].first).toDouble(),
                            (
                                pts[i].second -
                                    pts[j].second
                            ).toDouble(),
                        )
                    }
                }.min()
        kotlin.test.assertTrue(minGap > 0.05, "nodes overlap: min gap $minGap")
        val onWall = pts.count { (x, y) -> x <= 0.081f || x >= 0.919f || y <= 0.101f || y >= 0.899f }
        kotlin.test.assertTrue(onWall <= n / 3, "$onWall of $n nodes pushed to the edges")
        val cx = pts.map { it.first }.average()
        val cy = pts.map { it.second }.average()
        kotlin.test.assertTrue(cx in 0.35..0.65 && cy in 0.35..0.65, "centre ($cx, $cy)")
    }
}
