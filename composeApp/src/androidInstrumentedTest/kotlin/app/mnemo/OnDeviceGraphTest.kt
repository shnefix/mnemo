package app.mnemo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mnemo.data.seed.EnglishDeck
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opens the Graph tab on the phone and checks the header text is laid out at a readable width. */
@RunWith(AndroidJUnit4::class)
class OnDeviceGraphTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun exists(m: androidx.compose.ui.test.SemanticsMatcher) = rule.onAllNodes(m).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun graphHeaderIsNotSqueezed() {
        rule.waitUntil(20_000) { exists(hasText("What are you learning?")) || exists(hasContentDescription("Create cards")) }
        if (exists(hasText("What are you learning?"))) rule.onNodeWithText("Skip").performClick()
        rule.waitUntil(15_000) { exists(hasText("Graph")) }
        rule.onNodeWithText("Graph").performClick()
        rule.waitUntil(15_000) { exists(hasText("Knowledge graph")) }
        rule.onNodeWithText("Knowledge graph").assertIsDisplayed()
        // A title squeezed to one character per line is far narrower than it is tall.
        val title = rule.onNodeWithText("Knowledge graph").fetchSemanticsNode().size
        assertTrue("Title wrapped to ${title.width}x${title.height}px", title.width > title.height * 3)
        val sub = rule.onNode(hasText("nodes colored by mastery", substring = true)).fetchSemanticsNode().size
        assertTrue("Subtitle wrapped to ${sub.width}x${sub.height}px", sub.width > sub.height * 3)
        rule.onNodeWithText("New concept").assertExists()
    }

    /** Every deck is on the graph, and tapping a concept with hundreds of cards opens its panel (it used to crash). */
    @Test
    fun selectingADeckConceptShowsItsCards() {
        rule.waitUntil(20_000) { exists(hasText("What are you learning?")) || exists(hasContentDescription("Create cards")) }
        if (exists(hasText("What are you learning?"))) rule.onNodeWithText("Skip").performClick()
        rule.waitUntil(15_000) { exists(hasText("Graph")) }
        rule.onNodeWithText("Graph").performClick()
        val english = hasContentDescription(EnglishDeck.ROOT, substring = true)
        rule.waitUntil(30_000) { exists(english) }
        rule.onNode(english).performClick()
        rule.waitUntil(15_000) { exists(hasText("Cards · weakest first", substring = true, ignoreCase = true)) }
        // Hundreds of linked cards: the panel lists a few and offers the rest.
        rule.waitUntil(15_000) { exists(hasText("Show all", substring = true)) }
        rule.onNode(hasText("Show all", substring = true)).performScrollTo().performClick()
        rule.waitForIdle()
        assertTrue(exists(hasText("Knowledge graph")))
    }
}
