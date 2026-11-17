package app.mnemo.domain.graph

import app.mnemo.domain.model.ConceptEdge
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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

    /**
     * Force-directed layout (Fruchterman–Reingold) in unit coordinates: linked nodes pull
     * together, all nodes push apart, and each step is capped by a falling "temperature" so the
     * result settles instead of flinging nodes against the walls, however many there are.
     */
    fun forceLayout(
        start: Map<Long, Pair<Float, Float>>,
        edges: List<Pair<Long, Long>>,
        iterations: Int = 300,
    ): Map<Long, Pair<Float, Float>> {
        val ids = start.keys.toList()
        val n = ids.size
        if (n < 2) return start
        val index = ids.withIndex().associate { (i, id) -> id to i }
        val x = FloatArray(n) { start.getValue(ids[it]).first }
        val y = FloatArray(n) { start.getValue(ids[it]).second }
        val links = edges.mapNotNull { (a, b) -> index[a]?.let { i -> index[b]?.let { j -> if (i != j) i to j else null } } }
        val k = 0.75f * sqrt(1f / n) // ideal distance between nodes
        val gravity = 2f * maxOf(1f, n / 20f)
        var temp = 0.12f
        val dx = FloatArray(n)
        val dy = FloatArray(n)
        repeat(iterations) {
            dx.fill(0f)
            dy.fill(0f)
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    var ux = x[i] - x[j]
                    var uy = y[i] - y[j]
                    var d = sqrt(ux * ux + uy * uy)
                    if (d < 1e-3f) {
                        // Same spot: nudge apart in a fixed direction per pair.
                        ux = 0.001f * (1 + (i % 3))
                        uy = 0.001f * (1 + (j % 3))
                        d = sqrt(ux * ux + uy * uy)
                    }
                    val f = k * k / d
                    dx[i] += ux / d * f
                    dy[i] += uy / d * f
                    dx[j] -= ux / d * f
                    dy[j] -= uy / d * f
                }
            }
            for ((i, j) in links) {
                val ux = x[i] - x[j]
                val uy = y[i] - y[j]
                val d = sqrt(ux * ux + uy * uy).coerceAtLeast(1e-3f)
                val f = d * d / k
                dx[i] -= ux / d * f
                dy[i] -= uy / d * f
                dx[j] += ux / d * f
                dy[j] += uy / d * f
            }
            for (i in 0 until n) {
                // Gravity towards the centre keeps nodes off the edges (tuned for 3–40 nodes).
                dx[i] += (0.5f - x[i]) * gravity
                dy[i] += (0.5f - y[i]) * gravity
                val len = sqrt(dx[i] * dx[i] + dy[i] * dy[i])
                if (len > 0f) {
                    val step = minOf(len, temp)
                    x[i] = (x[i] + dx[i] / len * step).coerceIn(0.08f, 0.92f)
                    y[i] = (y[i] + dy[i] / len * step).coerceIn(0.1f, 0.9f)
                }
            }
            temp = (temp * 0.985f).coerceAtLeast(0.002f)
        }
        return ids.withIndex().associate { (i, id) -> id to (x[i] to y[i]) }
    }
}
