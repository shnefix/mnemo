package app.mnemo

import app.mnemo.domain.cards.CardGenerator
import app.mnemo.domain.cards.ClozeParser
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.review.AnswerMatcher
import kotlin.test.Test
import kotlin.test.assertEquals

class ClozeParserTest {
    private val text = "The {{c1::mitochondria}} is the {{c2::powerhouse::energy}} of the {{c1::cell}}."

    @Test
    fun findsDistinctIndices() {
        assertEquals(listOf(1, 2), ClozeParser.indices(text))
        assertEquals(emptyList(), ClozeParser.indices("no clozes here"))
    }

    @Test
    fun rendersQuestionHidingOnlyTheActiveCloze() {
        assertEquals("The [...] is the powerhouse of the [...].", ClozeParser.renderQuestion(text, 1))
        assertEquals("The mitochondria is the [energy] of the cell.", ClozeParser.renderQuestion(text, 2))
    }

    @Test
    fun rendersAnswerMarkingTheActiveCloze() {
        assertEquals("The mitochondria is the **powerhouse** of the cell.", ClozeParser.renderAnswer(text, 2))
    }

    @Test
    fun expectedAnswerJoinsSameIndex() {
        assertEquals("mitochondria, cell", ClozeParser.answerFor(text, 1))
        assertEquals("powerhouse", ClozeParser.answerFor(text, 2))
    }

    @Test
    fun wrapsSelectionAsNextCloze() {
        val out = ClozeParser.wrapAsNextCloze("der Zug fährt", 4, 7)
        assertEquals("der {{c1::Zug}} fährt", out)
        val out2 = ClozeParser.wrapAsNextCloze(out, 0, 3)
        assertEquals("{{c2::der}} {{c1::Zug}} fährt", out2)
    }
}

class CardGeneratorTest {
    private val types = CardGenerator.builtInTypes().associateBy { it.kind }

    @Test
    fun basicMakesOneCard() {
        val t = types.getValue(NoteKind.Basic)
        val fields = mapOf("Front" to "Hund", "Back" to "dog")
        assertEquals(listOf(0), CardGenerator.templateIndices(t, fields))
        val card = CardGenerator.render(t, fields, 0)
        assertEquals("Hund", card.question)
        assertEquals("dog", card.answer)
        assertEquals(null, card.expected)
    }

    @Test
    fun reversedMakesTwoCards() {
        val t = types.getValue(NoteKind.Reversed)
        val fields = mapOf("Front" to "Hund", "Back" to "dog")
        assertEquals(listOf(0, 1), CardGenerator.templateIndices(t, fields))
        assertEquals("dog", CardGenerator.render(t, fields, 1).question)
        assertEquals("Hund", CardGenerator.render(t, fields, 1).answer)
    }

    @Test
    fun clozeMakesOneCardPerDeletion() {
        val t = types.getValue(NoteKind.Cloze)
        val fields = mapOf("Text" to "{{c1::Berlin}} is the capital of {{c2::Germany}}", "Extra" to "")
        assertEquals(listOf(0, 1), CardGenerator.templateIndices(t, fields))
        val c2 = CardGenerator.render(t, fields, 1)
        assertEquals("Berlin is the capital of [...]", c2.question)
        assertEquals("Germany", c2.expected)
    }

    @Test
    fun typeAnswerExposesExpected() {
        val t = types.getValue(NoteKind.TypeAnswer)
        val card = CardGenerator.render(t, mapOf("Front" to "capital of France", "Back" to "Paris"), 0)
        assertEquals("Paris", card.expected)
    }
}

class AnswerMatcherTest {
    @Test
    fun normalisesCaseAccentsAndPunctuation() {
        assertEquals(AnswerMatcher.Verdict.Correct, AnswerMatcher.check("  Die Zeitung! ", "die zeitung"))
        assertEquals(AnswerMatcher.Verdict.Correct, AnswerMatcher.check("fahrrad", "Fahrräd"))
        assertEquals(AnswerMatcher.Verdict.Wrong, AnswerMatcher.check("die Zeit", "die Zeitung"))
    }

    @Test
    fun unicodeFoldingTreatsEquivalentSpellingsAsCorrect() {
        assertEquals(AnswerMatcher.Verdict.Correct, AnswerMatcher.check("Strasse", "Straße"))
        assertEquals(AnswerMatcher.Verdict.Correct, AnswerMatcher.check("garcon", "garçon"))
        // Decomposed (e + combining acute) equals precomposed é.
        assertEquals(AnswerMatcher.Verdict.Correct, AnswerMatcher.check("cafe\u0301", "café"))
    }

    @Test
    fun oneTypoIsCloseButNotForShortWords() {
        val r = AnswerMatcher.compare("Zeitnug", "Zeitung")
        assertEquals(AnswerMatcher.Verdict.Close, r.verdict)
        assertEquals(1, r.distance, "a swap of two letters is one edit")
        assertEquals(AnswerMatcher.Verdict.Close, AnswerMatcher.check("recieve", "receive"))
        assertEquals(AnswerMatcher.Verdict.Wrong, AnswerMatcher.check("dog", "dot"))
        assertEquals(AnswerMatcher.Verdict.Wrong, AnswerMatcher.check("", "Zeitung"))
    }

    @Test
    fun diffMarksMissingAndExtraCharacters() {
        val d = AnswerMatcher.diff("Zeitnug", "Zeitung")
        assertEquals("Zeitung", d.filter { it.kind != AnswerMatcher.Kind.Extra }.joinToString("") { it.text })
        assertEquals("Zeitnug", d.filter { it.kind != AnswerMatcher.Kind.Missing }.joinToString("") { it.text })
        assertEquals(setOf(AnswerMatcher.Kind.Same, AnswerMatcher.Kind.Missing, AnswerMatcher.Kind.Extra), d.map { it.kind }.toSet())
        assertEquals("Zeit", d.first().text)
        // Same segments show the expected spelling even when the typed letter only differs by an accent.
        val accent = AnswerMatcher.diff("cafe", "café")
        assertEquals(listOf(AnswerMatcher.Segment("café", AnswerMatcher.Kind.Same)), accent)
        val wrong = AnswerMatcher.compare("", "ab")
        assertEquals(listOf(AnswerMatcher.Segment("ab", AnswerMatcher.Kind.Missing)), wrong.diff)
    }
}
