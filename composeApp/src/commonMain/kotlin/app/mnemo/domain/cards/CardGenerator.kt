package app.mnemo.domain.cards

import app.mnemo.domain.model.CardTemplate
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.NoteType

/** What one card shows: a question side, an answer side, and the typed answer when applicable. */
data class RenderedCard(
    val question: String,
    val answer: String,
    /** The text a typed answer is compared against (Type-the-answer and Cloze). */
    val expected: String?,
)

/**
 * Turns a note's fields into cards. Basic/Reversed/Type use templates with
 * `{{Field}}` holes; Cloze makes one card per cloze number.
 */
object CardGenerator {
    /** Template indices the note should have cards for. */
    fun templateIndices(
        noteType: NoteType,
        fields: Map<String, String>,
    ): List<Int> =
        when (noteType.kind) {
            NoteKind.Cloze -> ClozeParser.indices(fields["Text"].orEmpty()).map { it - 1 }
            else -> noteType.templates.indices.toList()
        }

    fun render(
        noteType: NoteType,
        fields: Map<String, String>,
        templateIndex: Int,
    ): RenderedCard =
        when (noteType.kind) {
            NoteKind.Cloze -> {
                val text = fields["Text"].orEmpty()
                val extra = fields["Extra"].orEmpty()
                val cloze = templateIndex + 1
                val answer = ClozeParser.renderAnswer(text, cloze) + if (extra.isNotBlank()) "\n\n$extra" else ""
                RenderedCard(
                    question = ClozeParser.renderQuestion(text, cloze),
                    answer = answer,
                    expected = ClozeParser.answerFor(text, cloze),
                )
            }
            NoteKind.Vocab -> {
                val word = fields["Word"].orEmpty().trim()
                val translation = fields["Translation"].orEmpty().trim()
                val gender = fields["Gender"].orEmpty().trim()
                val plural = fields["Plural"].orEmpty().trim()
                val ipa = fields["IPA"].orEmpty().trim()
                val example = fields["Example"].orEmpty().trim()
                val exampleT = fields["Example translation"].orEmpty().trim()
                val headline = if (gender.isNotEmpty() && !word.lowercase().startsWith(gender.lowercase() + " ")) "$gender $word" else word
                val details =
                    buildString {
                        if (plural.isNotEmpty()) append("Plural: *$plural*  \n")
                        if (ipa.isNotEmpty()) append("$ipa  \n")
                        // Several examples, one per line, each shown as its own quote.
                        if (example.isNotEmpty()) {
                            append(
                                "\n" + example.lines().filter { it.isNotBlank() }.joinToString("\n\n") { "> ${it.trim()}" },
                            )
                        }
                        if (exampleT.isNotEmpty()) append("  \n> *$exampleT*")
                    }.trim()
                if (templateIndex == 0) {
                    RenderedCard(
                        question = word,
                        answer =
                            listOf("**$translation**", details)
                                .filter {
                                    it.isNotEmpty()
                                }.joinToString("\n\n"),
                        expected = translation,
                    )
                } else {
                    RenderedCard(
                        question = translation,
                        answer =
                            listOf("**$headline**", details)
                                .filter {
                                    it.isNotEmpty()
                                }.joinToString("\n\n"),
                        expected = word,
                    )
                }
            }
            else -> {
                val template = noteType.templates.getOrElse(templateIndex) { noteType.templates.first() }
                val question = fill(template.front, fields)
                val answer = fill(template.back, fields)
                RenderedCard(
                    question = question,
                    answer = answer,
                    expected = if (noteType.kind == NoteKind.TypeAnswer) answer else null,
                )
            }
        }

    private fun fill(
        template: String,
        fields: Map<String, String>,
    ): String =
        Regex("""\{\{([^}]+)\}\}""").replace(template) { m ->
            fields[m.groupValues[1].trim()].orEmpty()
        }

    /** The built-in note types; created on first launch. */
    fun builtInTypes(): List<NoteType> =
        listOf(
            NoteType(
                id = 0,
                name = "Basic",
                kind = NoteKind.Basic,
                fields = listOf("Front", "Back"),
                templates = listOf(CardTemplate("Card 1", "{{Front}}", "{{Back}}")),
            ),
            NoteType(
                id = 0,
                name = "Basic + Reversed",
                kind = NoteKind.Reversed,
                fields = listOf("Front", "Back"),
                templates =
                    listOf(
                        CardTemplate("Card 1", "{{Front}}", "{{Back}}"),
                        CardTemplate("Card 2", "{{Back}}", "{{Front}}"),
                    ),
            ),
            NoteType(
                id = 0,
                name = "Cloze",
                kind = NoteKind.Cloze,
                fields = listOf("Text", "Extra"),
                templates = listOf(CardTemplate("Cloze", "{{cloze:Text}}", "{{cloze:Text}}\n{{Extra}}")),
            ),
            NoteType(
                id = 0,
                name = "Type the answer",
                kind = NoteKind.TypeAnswer,
                fields = listOf("Front", "Back"),
                templates = listOf(CardTemplate("Card 1", "{{Front}}", "{{Back}}")),
            ),
            NoteType(
                id = 0,
                name = "Vocabulary",
                kind = NoteKind.Vocab,
                fields = listOf("Word", "Translation", "Gender", "Plural", "IPA", "Example", "Example translation"),
                templates =
                    listOf(
                        CardTemplate("Word → Translation", "{{Word}}", "{{Translation}}"),
                        CardTemplate("Translation → Word", "{{Translation}}", "{{Word}}"),
                    ),
            ),
        )
}
