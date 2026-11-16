package app.mnemo.domain.graph

import app.mnemo.domain.language.Tokenizer
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Suggests concepts without an AI model: cards are grouped by shared tags and
 * by TF-IDF similarity of their question text, and each group is named after
 * its most distinctive terms. The user accepts, renames or discards each one.
 */
object ConceptSuggester {
    data class CardText(
        val cardId: Long,
        val text: String,
        val tags: List<String>,
        val deck: String,
    )

    data class Suggestion(
        val name: String,
        val cardIds: List<Long>,
        val reason: String,
    )

    private val stop =
        setOf(
            "the",
            "a",
            "an",
            "of",
            "to",
            "in",
            "is",
            "are",
            "was",
            "what",
            "which",
            "who",
            "how",
            "why",
            "does",
            "do",
            "and",
            "or",
            "for",
            "on",
            "with",
            "that",
            "this",
            "it",
            "its",
            "by",
            "as",
            "at",
            "from",
            "be",
            "has",
            "have",
            "when",
            "where",
            "you",
            "your",
            "der",
            "die",
            "das",
            "ein",
            "eine",
            "und",
            "ist",
            "el",
            "la",
            "los",
            "las",
            "un",
            "una",
            "le",
            "les",
            "de",
            "du",
            "not",
            "no",
            "than",
            "into",
            "can",
            "will",
            "would",
            "should",
            "there",
            "their",
            "they",
            "them",
            "then",
            "between",
        )

    fun suggest(
        cards: List<CardText>,
        existingConceptNames: Set<String> = emptySet(),
        minCards: Int = 3,
        similarity: Double = 0.28,
    ): List<Suggestion> {
        if (cards.size < minCards) return emptyList()
        val out = mutableListOf<Suggestion>()
        val taken = HashSet<Long>()
        val existing = existingConceptNames.map { it.lowercase() }.toSet()

        // 1. Tags shared by enough cards become concepts straight away.
        cards
            .flatMap { c -> c.tags.map { it to c.cardId } }
            .groupBy({ it.first }, { it.second })
            .filter { (tag, ids) -> ids.size >= minCards && tag.lowercase() !in setOf("leech", "starter", "marked") }
            .toList()
            .sortedByDescending { it.second.size }
            .forEach { (tag, ids) ->
                val name = tag.replace('-', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }
                if (name.lowercase() !in existing) {
                    out += Suggestion(name, ids, "${ids.size} cards share the tag “$tag”")
                    taken += ids
                }
            }

        // 2. A distinctive term shared by enough cards (but not by most of them)
        //    is a concept in its own right, the same way a shared tag is.
        val rest = cards.filter { it.cardId !in taken }
        if (rest.size >= minCards) {
            val docs = rest.map { c -> c to terms(c.text) }
            val df = HashMap<String, Int>()
            docs.forEach { (_, t) -> t.toSet().forEach { df[it] = (df[it] ?: 0) + 1 } }
            val cap = maxOf(minCards, docs.size / 2)
            df.entries
                .filter { (_, count) -> count in minCards..cap }
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .forEach { (term, _) ->
                    val ids = docs.filter { (c, t) -> c.cardId !in taken && term in t }.map { it.first.cardId }
                    if (ids.size < minCards) return@forEach
                    val name = term.replaceFirstChar { it.uppercase() }
                    if (name.lowercase() !in existing) {
                        out += Suggestion(name, ids, "${ids.size} cards mention “$term”")
                        taken += ids
                    }
                }
        }

        // 3. Remaining cards: TF-IDF vectors over question terms, greedy clustering.
        val rest2 = cards.filter { it.cardId !in taken }
        if (rest2.size >= minCards) {
            val docs = rest2.map { c -> c to terms(c.text) }
            val df = HashMap<String, Int>()
            docs.forEach { (_, t) -> t.toSet().forEach { df[it] = (df[it] ?: 0) + 1 } }
            val n = docs.size.toDouble()
            val vectors =
                docs.map { (c, t) ->
                    val counts = t.groupingBy { it }.eachCount()
                    val v = counts.mapValues { (term, cnt) -> (1 + ln(cnt.toDouble())) * ln((n + 1) / ((df[term] ?: 1) + 1)) }
                    val norm = sqrt(v.values.sumOf { it * it }).takeIf { it > 0 } ?: 1.0
                    c to v.mapValues { it.value / norm }
                }
            val used = HashSet<Long>()
            for ((seed, sv) in vectors) {
                if (seed.cardId in used || sv.isEmpty()) continue
                val members = vectors.filter { (c, v) -> c.cardId !in used && cosine(sv, v) >= similarity }
                if (members.size >= minCards) {
                    val ids = members.map { it.first.cardId }
                    used += ids
                    val centroid = HashMap<String, Double>()
                    members.forEach { (_, v) -> v.forEach { (k, x) -> centroid[k] = (centroid[k] ?: 0.0) + x } }
                    val top =
                        centroid.entries
                            .sortedByDescending { it.value }
                            .take(2)
                            .map { it.key }
                    val name = top.joinToString(" & ") { it.replaceFirstChar { ch -> ch.uppercase() } }
                    if (name.isNotBlank() && name.lowercase() !in existing) {
                        out += Suggestion(name, ids, "${ids.size} cards mention ${top.joinToString(", ") { "“$it”" }}")
                    }
                }
            }
        }
        return out
    }

    private fun terms(text: String): List<String> =
        Tokenizer
            .tokenize(text.replace(Regex("""\{\{c\d+::([^}:]+)(?:::[^}]*)?\}\}"""), "$1"))
            .map { Tokenizer.key(it.text) }
            .filter { it.length >= 2 && it !in stop && !it.all { ch -> ch.isDigit() } }

    private fun cosine(
        a: Map<String, Double>,
        b: Map<String, Double>,
    ): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val (small, large) = if (a.size <= b.size) a to b else b to a
        var dot = 0.0
        for ((k, v) in small) large[k]?.let { dot += v * it }
        return dot
    }
}
