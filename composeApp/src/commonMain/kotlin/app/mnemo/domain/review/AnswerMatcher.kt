package app.mnemo.domain.review

import app.mnemo.platform.foldDiacritics
import kotlin.math.max
import kotlin.math.min

/**
 * Typed-answer check. Both sides are normalised (case, diacritics, punctuation,
 * whitespace); an exact match is [Verdict.Correct], a small edit distance is
 * [Verdict.Close], anything else [Verdict.Wrong]. [compare] also returns a
 * character diff of what was typed against the expected answer, in the style
 * Anki shows after "type the answer".
 */
object AnswerMatcher {
    enum class Verdict { Correct, Close, Wrong }

    enum class Kind {
        /** Present in both. */
        Same,

        /** In the expected answer but not typed. */
        Missing,

        /** Typed but not in the expected answer. */
        Extra,
    }

    data class Segment(
        val text: String,
        val kind: Kind,
    )

    data class Result(
        val verdict: Verdict,
        /** Edit distance between the normalised strings. */
        val distance: Int,
        val diff: List<Segment>,
    )

    private const val MAX_DIFF_CHARS = 400

    fun check(
        typed: String,
        expected: String,
    ): Verdict = compare(typed, expected).verdict

    fun compare(
        typed: String,
        expected: String,
    ): Result {
        val t = normalize(typed)
        val e = normalize(expected)
        val distance = levenshtein(t, e)
        val verdict =
            when {
                distance == 0 -> Verdict.Correct
                distance <= closeThreshold(e.length) -> Verdict.Close
                else -> Verdict.Wrong
            }
        val diff =
            if (verdict == Verdict.Correct) {
                listOf(Segment(expected.trim(), Kind.Same))
            } else {
                diff(typed.trim(), expected.trim())
            }
        return Result(verdict, distance, diff)
    }

    /** How many edits still count as "close": one for short words, roughly one per eight characters after that. */
    fun closeThreshold(normalisedLength: Int): Int =
        when {
            normalisedLength <= 3 -> 0
            normalisedLength <= 9 -> 1
            normalisedLength <= 15 -> 2
            else -> min(5, normalisedLength / 8)
        }

    fun normalize(s: String): String =
        foldDiacritics(s.lowercase())
            .replace(Regex("""[\p{P}\p{S}]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    /** Edit distance where a swap of two adjacent characters counts as one edit (optimal string alignment). */
    fun levenshtein(
        a: String,
        b: String,
    ): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = min(min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    d[i][j] = min(d[i][j], d[i - 2][j - 2] + 1)
                }
            }
        }
        return d[a.length][b.length]
    }

    /**
     * Character diff of [typed] against [expected]. Characters are compared
     * after case/diacritic folding, so `e` lines up with `é`; the expected
     * spelling is what shows in [Kind.Same] segments.
     */
    fun diff(
        typed: String,
        expected: String,
    ): List<Segment> {
        val a = typed.take(MAX_DIFF_CHARS)
        val b = expected.take(MAX_DIFF_CHARS)
        val ka = a.map { key(it) }
        val kb = b.map { key(it) }
        // lcs[i][j] = length of the longest common subsequence of a[i..] and b[j..]
        val lcs = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in a.length - 1 downTo 0) {
            for (j in b.length - 1 downTo 0) {
                lcs[i][j] = if (ka[i] == kb[j]) lcs[i + 1][j + 1] + 1 else max(lcs[i + 1][j], lcs[i][j + 1])
            }
        }
        val out = mutableListOf<Segment>()

        fun add(
            kind: Kind,
            ch: Char,
        ) {
            val last = out.lastOrNull()
            if (last != null && last.kind == kind) {
                out[out.lastIndex] = Segment(last.text + ch, kind)
            } else {
                out += Segment(ch.toString(), kind)
            }
        }
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            when {
                ka[i] == kb[j] -> {
                    add(Kind.Same, b[j])
                    i++
                    j++
                }
                lcs[i + 1][j] >= lcs[i][j + 1] -> {
                    add(Kind.Extra, a[i])
                    i++
                }
                else -> {
                    add(Kind.Missing, b[j])
                    j++
                }
            }
        }
        while (i < a.length) add(Kind.Extra, a[i++])
        while (j < b.length) add(Kind.Missing, b[j++])
        return out
    }

    private fun key(c: Char): String = foldDiacritics(c.toString()).lowercase()
}
