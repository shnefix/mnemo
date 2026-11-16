package app.mnemo.domain.cards

/**
 * Anki-style cloze deletions: `{{c1::answer}}` or `{{c1::answer::hint}}`.
 * One card is generated per distinct cloze number.
 */
object ClozeParser {
    private val pattern = Regex("""\{\{c(\d+)::(.+?)(?:::(.+?))?\}\}""")

    data class Cloze(
        val index: Int,
        val answer: String,
        val hint: String?,
    )

    fun find(text: String): List<Cloze> =
        pattern
            .findAll(text)
            .map { m ->
                Cloze(m.groupValues[1].toInt(), m.groupValues[2], m.groupValues[3].ifEmpty { null })
            }.toList()

    /** Distinct cloze numbers in ascending order; empty when the text has no clozes. */
    fun indices(text: String): List<Int> = find(text).map { it.index }.distinct().sorted()

    /**
     * Renders the question side for cloze [index]: that cloze becomes `[...]`
     * (or `[hint]`), every other cloze shows its answer.
     */
    fun renderQuestion(
        text: String,
        index: Int,
    ): String =
        pattern.replace(text) { m ->
            val idx = m.groupValues[1].toInt()
            val answer = m.groupValues[2]
            val hint = m.groupValues[3]
            if (idx == index) {
                if (hint.isNotEmpty()) "[$hint]" else "[...]"
            } else {
                answer
            }
        }

    /** Renders the answer side: every cloze shows its answer, the active one marked with `**`. */
    fun renderAnswer(
        text: String,
        index: Int,
    ): String =
        pattern.replace(text) { m ->
            val idx = m.groupValues[1].toInt()
            val answer = m.groupValues[2]
            if (idx == index) "**$answer**" else answer
        }

    /** The expected answer for cloze [index] (several deletions with the same number joined with ", "). */
    fun answerFor(
        text: String,
        index: Int,
    ): String = find(text).filter { it.index == index }.joinToString(", ") { it.answer }

    /** Wraps [selection] as the next cloze number in [text]. */
    fun wrapAsNextCloze(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
    ): String {
        if (selectionStart >= selectionEnd) return text
        val next = (indices(text).maxOrNull() ?: 0) + 1
        val selected = text.substring(selectionStart, selectionEnd)
        return text.substring(0, selectionStart) + "{{c$next::$selected}}" + text.substring(selectionEnd)
    }
}
