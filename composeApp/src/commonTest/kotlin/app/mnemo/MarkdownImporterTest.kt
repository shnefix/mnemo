package app.mnemo

import app.mnemo.data.io.MarkdownImporter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownImporterTest {
    @Test
    fun headingSectionsWithFrontmatterTags() {
        val text =
            """
            ---
            title: Dative prepositions
            tags: [german, cases]
            ---
            # Dative

            ## Question
            Which case follows **mit**?

            ## Answer
            Dative, always.

            ### Question
            And *nach*?
            ### Answer
            Dative too.
            """.trimIndent()
        val cards = MarkdownImporter.parse(text)
        assertEquals(2, cards.size)
        assertEquals("Which case follows **mit**?", cards[0].question)
        assertEquals("Dative, always.", cards[0].answer)
        assertEquals("And *nach*?", cards[1].question)
        assertEquals(listOf("german", "cases"), cards[0].tags)
    }

    @Test
    fun prefixedLinesAndTagLines() {
        val text =
            """
            #biology #enzymes

            Q: What does Km measure?
            A: The substrate concentration at half Vmax.
            It reflects affinity.

            **Q:** Lower Km means?
            **A:** Higher affinity.
            """.trimIndent()
        val cards = MarkdownImporter.parse(text)
        assertEquals(2, cards.size)
        assertEquals("What does Km measure?", cards[0].question)
        assertEquals("The substrate concentration at half Vmax.\nIt reflects affinity.", cards[0].answer)
        assertEquals("Higher affinity.", cards[1].answer)
        assertEquals(listOf("biology", "enzymes"), cards[1].tags)
    }

    @Test
    fun questionMarkSeparatorBlocksAndListTags() {
        val text =
            """
            ---
            tags:
              - spanish
              - verbs
            ---
            ser
            ?
            to be (permanent)

            estar
            ?
            to be (temporary)

            Just a paragraph with no card in it.
            """.trimIndent()
        val cards = MarkdownImporter.parse(text)
        assertEquals(listOf("ser", "estar"), cards.map { it.question })
        assertEquals("to be (temporary)", cards[1].answer)
        assertEquals(listOf("spanish", "verbs"), cards[0].tags)
    }

    @Test
    fun titleFallbackOnlyWhenAsked() {
        val text = "# Photosynthesis\n\nConverts light into chemical energy."
        assertTrue(MarkdownImporter.parse(text).isEmpty())
        val fallback = MarkdownImporter.parse(text, fileName = "photosynthesis.md", titleFallback = true)
        assertEquals(1, fallback.size)
        assertEquals("Photosynthesis", fallback[0].question)
        assertEquals("Converts light into chemical energy.", fallback[0].answer)
        val noTitle = MarkdownImporter.parse("Just a body.", fileName = "Krebs cycle.md", titleFallback = true)
        assertEquals("Krebs cycle", noTitle[0].question)
    }

    @Test
    fun clozeQuestionsNeedNoAnswerAndSourceIdsAreStable() {
        val cards = MarkdownImporter.parse("Q: The capital of France is {{c1::Paris}}.\nA:")
        assertEquals(1, cards.size)
        assertEquals(MarkdownImporter.sourceId("a/b.md", "q"), MarkdownImporter.sourceId("a/b.md", " q "))
        assertTrue(MarkdownImporter.sourceId("a/b.md", "q") != MarkdownImporter.sourceId("a/c.md", "q"))
        assertTrue(MarkdownImporter.sourceId("x", "y") >= 0)
    }
}
