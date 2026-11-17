package app.mnemo.domain.graph

import app.mnemo.domain.model.ConceptEdge
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Neighbourhood view for large graphs: one concept and what is within a few links of it. */
object GraphFocus {
    /** Concept ids within [hops] links of [center] (edges count in both directions), by distance. */
    fun neighborhood(
        edges: List<ConceptEdge>,
        center: Long,
        hops: Int,
    ): Map<Long, Int> {
        val adj = HashMap<Long, MutableSet<Long>>()
        edges.forEach { e ->
            adj.getOrPut(e.fromId) { mutableSetOf() } += e.toId
            adj.getOrPut(e.toId) { mutableSetOf() } += e.fromId
        }
        val dist = linkedMapOf(center to 0)
        var frontier = listOf(center)
        for (d in 1..hops) {
            frontier = frontier.flatMap { adj[it].orEmpty() }.filter { it !in dist }.distinct()
            frontier.forEach { dist[it] = d }
        }
        return dist
    }

    /** Radial positions in unit coordinates: the centre in the middle, each hop on its own ring. */
    fun radialLayout(dist: Map<Long, Int>): Map<Long, Pair<Float, Float>> {
        val rings = dist.entries.groupBy({ it.value }, { it.key })
        val maxHop = rings.keys.maxOrNull() ?: 0
        val out = HashMap<Long, Pair<Float, Float>>()
        rings.forEach { (hop, ids) ->
            if (hop == 0) {
                ids.forEach { out[it] = 0.5f to 0.5f }
                return@forEach
            }
            val r = 0.38f * hop / maxHop.coerceAtLeast(1)
            ids.sorted().forEachIndexed { i, id ->
                val a = 2 * PI * i / ids.size + hop * 0.4
                out[id] = (0.5f + r * cos(a).toFloat()) to (0.5f + r * sin(a).toFloat())
            }
        }
        return out
    }
}
