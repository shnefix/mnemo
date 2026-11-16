package app.mnemo.domain.review

/**
 * Phase 1 typed-answer check: exact after normalising case, accents,
 * punctuation and whitespace. (The AI grader for partial answers is Phase 4.)
 */
object AnswerMatcher {
    enum class Verdict { Correct, Wrong }

    fun check(
        typed: String,
        expected: String,
    ): Verdict = if (normalize(typed) == normalize(expected)) Verdict.Correct else Verdict.Wrong

    fun normalize(s: String): String =
        s
            .lowercase()
            .map { foldAccent(it) }
            .joinToString("")
            .replace(Regex("""[\p{P}\p{S}]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun foldAccent(c: Char): Char =
        when (c) {
            'à', 'á', 'â', 'ã', 'å' -> 'a'
            'ä' -> 'a'
            'è', 'é', 'ê', 'ë' -> 'e'
            'ì', 'í', 'î', 'ï' -> 'i'
            'ò', 'ó', 'ô', 'õ' -> 'o'
            'ö' -> 'o'
            'ù', 'ú', 'û' -> 'u'
            'ü' -> 'u'
            'ç' -> 'c'
            'ñ' -> 'n'
            'ß' -> 's'
            else -> c
        }
}
