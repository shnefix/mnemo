package app.mnemo.domain.cards

/**
 * Anki-style card templates for note types you make yourself:
 *
 * - `{{Field}}` the field's text
 * - `{{#Field}}…{{/Field}}` only when the field has text, `{{^Field}}…{{/Field}}` only when it is empty
 * - `{{FrontSide}}` (on the back) the rendered front
 * - `{{type:Field}}` a box to type the field into; the back shows the field
 * - `{{hint:Field}}` the field, marked as a hint
 * - `{{text:Field}}` the field without HTML tags
 *
 * Other filters (`{{cloze:…}}`, `{{furigana:…}}`) just show the field.
 */
object Templates {
    data class Result(
        val text: String,
        /** The field named in `{{type:…}}`, when the template has one. */
        val typeField: String?,
    )

    private val section = Regex("""\{\{([#^])\s*([^}]+?)\s*\}\}((?:(?!\{\{[#^]).)*?)\{\{/\s*\2\s*\}\}""", RegexOption.DOT_MATCHES_ALL)
    private val tag = Regex("""\{\{\s*([^}]+?)\s*\}\}""")
    private val html = Regex("<[^>]+>")

    fun render(
        template: String,
        fields: Map<String, String>,
        question: Boolean,
        frontSide: String = "",
    ): Result {
        var text = template
        // Innermost sections first, until none are left (sections may nest).
        repeat(20) {
            val next =
                section.replace(text) { m ->
                    val filled = fields[m.groupValues[2].trim()].orEmpty().isNotBlank()
                    val show = if (m.groupValues[1] == "#") filled else !filled
                    if (show) Regex.escapeReplacement(m.groupValues[3]) else ""
                }
            if (next == text) return@repeat
            text = next
        }
        var typeField: String? = null
        text =
            tag.replace(text) { m ->
                val inner = m.groupValues[1]
                val value =
                    when {
                        inner == "FrontSide" -> frontSide
                        inner.startsWith("type:") -> {
                            val name = inner.substringAfterLast(':').trim()
                            if (typeField == null) typeField = name
                            if (question) "" else fields[name].orEmpty()
                        }
                        inner.startsWith("hint:") ->
                            fields[
                                inner
                                    .substringAfterLast(
                                        ':',
                                    ).trim(),
                            ].orEmpty().takeIf { it.isNotBlank() }?.let { "*Hint:* $it" }.orEmpty()
                        inner.startsWith("text:") -> fields[inner.substringAfterLast(':').trim()].orEmpty().replace(html, "")
                        inner.startsWith("#") || inner.startsWith("^") || inner.startsWith("/") -> ""
                        else -> fields[inner.substringAfterLast(':').trim()].orEmpty()
                    }
                Regex.escapeReplacement(value)
            }
        return Result(text.trim(), typeField)
    }

    /**
     * Whether a template makes a card for these fields: Anki makes one only when the front shows
     * something that comes from a field.
     */
    fun generates(
        front: String,
        fields: Map<String, String>,
    ): Boolean {
        val filled = render(front, fields, question = true).text
        if (filled.isBlank()) return false
        val empty = render(front, fields.mapValues { "" }, question = true).text
        return filled != empty
    }

    /** Field names a template refers to (for checks in the note-type editor). */
    fun fieldsUsed(template: String): Set<String> =
        tag
            .findAll(template)
            .map {
                it.groupValues[1]
                    .trim()
                    .trimStart('#', '^', '/')
                    .substringAfterLast(':')
                    .trim()
            }.filter { it != "FrontSide" }
            .toSet()
}
