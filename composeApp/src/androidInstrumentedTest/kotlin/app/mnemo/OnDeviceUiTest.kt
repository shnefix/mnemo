package app.mnemo

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mnemo.data.seed.EnglishDeck
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the app end to end on the phone: launch, (skip onboarding on a fresh
 * install), create a deck from the editor, add a card, review it, then delete
 * the deck again. Runs in the test install (`app.mnemo.debug`), which has its
 * own database, never in the user's real `app.mnemo`.
 */
@RunWith(AndroidJUnit4::class)
class OnDeviceUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val deckName = "UiTest ${System.currentTimeMillis() % 100000}"

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    /** The deck list is lazy: a test deck may sit below the fold (the English deck tree is above it), and rows load async. */
    private fun scrollDecksTo(name: String) {
        rule.waitUntil(15_000) {
            runCatching { rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(name)) }.isSuccess
        }
        waitFor(hasText(name))
    }

    /** Waits for [matcher]; on timeout the failure message carries the whole semantics tree. */
    private fun waitFor(
        matcher: SemanticsMatcher,
        timeoutMs: Long = 15_000,
    ) {
        try {
            rule.waitUntil(timeoutMs) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: ComposeTimeoutException) {
            val tree = runCatching { rule.onRoot().printToString() }.getOrElse { "(no tree: $it)" }
            throw AssertionError("Timed out waiting for ${matcher.description}. Screen was:\n$tree", e)
        }
    }

    private fun exists(matcher: SemanticsMatcher) = rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun launchCreateDeckAddCardReviewAndCleanUp() {
        // 1. Launch: either the first-run screen or the main shell must appear.
        rule.waitUntil(20_000) {
            exists(hasText("What are you learning?")) || exists(hasContentDescription("Create cards"))
        }
        if (exists(hasText("What are you learning?"))) {
            rule.onNodeWithText("Skip").performClick()
        }
        waitFor(hasContentDescription("Create cards"))
        rule.onNodeWithContentDescription("Create cards").assertIsDisplayed()

        // 2. Editor: create a deck and a Basic card.
        rule.onNodeWithContentDescription("Create cards").performClick()
        waitFor(hasText("Add cards"))
        rule.onNodeWithText("New deck").performClick()
        waitFor(hasText("Create"))
        rule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput(deckName)
        rule.onNodeWithText("Create").performClick()
        waitFor(hasText(deckName))

        val fields = rule.onAllNodes(hasSetTextAction() and !hasAnyAncestor(isDialog()))
        fields[0].performTextInput("Capital of France?")
        fields[1].performTextInput("Paris")
        waitFor(hasText("Preview · 1 card", ignoreCase = true)) // the eyebrow renders in capitals
        rule.onNodeWithText("Save & close").performClick()

        // 3. Decks tab shows the new deck with one due card; tapping its name opens its page, which starts a review.
        waitFor(hasContentDescription("Create cards"))
        rule.onNode(hasText("Decks") and hasRole(Role.Tab)).performClick()
        scrollDecksTo(deckName)
        // The name button merges its texts, so match the count on this run's deck rather than on any card.
        rule.onNode(hasText(deckName) and hasText("1 cards · not started")).assertIsDisplayed()
        rule.onNodeWithContentDescription("Study 1 cards in $deckName").assertIsDisplayed()
        rule.onNode(hasText(deckName) and hasRole(Role.Button)).performClick()
        waitFor(hasText("Study $deckName") and hasRole(Role.Button))
        rule.onNode(hasText("Study $deckName") and hasRole(Role.Button)).performClick()

        // 4. Review: reveal, grade Good, session ends.
        waitFor(hasText("Capital of France?"))
        rule.onNodeWithText("Tap the card to see the answer").performClick() // the whole card is the button
        waitFor(hasText("Paris"))
        rule.onNode(hasText("Good") and hasRole(Role.Button)).performClick()
        rule.waitUntil(15_000) { exists(hasText("Session done")) || exists(hasText("Capital of France?")) }
        rule.onNodeWithContentDescription("End session").performClick()

        // 5. Clean up: delete this run's deck (and any left behind by earlier failed runs) from each deck's own page.
        // Ending the session may land on Today or the deck page: go to the deck list first.
        waitFor(hasText("Decks") and hasRole(Role.Tab))
        rule.onNode(hasText("Decks") and hasRole(Role.Tab)).performClick()
        scrollDecksTo(deckName)
        // Only the name button of a "UiTest" deck card, and only a dialog that names a "UiTest" deck: the
        // deck name also shows elsewhere for a moment (the "deleted" snackbar), never act on that.
        val testDeckCard = hasText("UiTest ", substring = true) and hasText(" cards · ", substring = true) and hasRole(Role.Button)
        waitFor(testDeckCard)
        var rounds = 0
        while (rounds++ < 5 && exists(testDeckCard)) {
            rule.waitForIdle()
            // The list may still be settling after the last delete: read the deck's name, then tap that card.
            val node = rule.onAllNodes(testDeckCard).fetchSemanticsNodes().firstOrNull() ?: break
            val texts: List<String> = node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { t -> t.text }
            val name = texts.firstOrNull { t -> t.startsWith("UiTest ") } ?: break
            val card = hasText(name) and hasText(" cards · ", substring = true) and hasRole(Role.Button)
            rule.onNode(card).performScrollTo().performClick()
            waitFor(hasContentDescription("Rename or delete deck"))
            rule.onNodeWithContentDescription("Rename or delete deck").performClick()
            waitFor(hasText("Delete"))
            rule.onNodeWithText("Delete").performClick()
            waitFor(hasText("Delete “UiTest ", substring = true) and hasAnyAncestor(isDialog()))
            rule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
            rule.waitUntil(15_000) { !exists(isDialog()) }
            // The page closes itself once its deck is gone; then look for another left-over test deck.
            rule.waitUntil(15_000) { !exists(hasContentDescription("Rename or delete deck")) }
            rule.waitUntil(15_000) { !exists(card) }
            runCatching { rule.onNode(hasScrollToNodeAction()).performScrollToNode(testDeckCard) }
        }
        rule.waitUntil(15_000) { !exists(hasText(deckName) and hasText(" cards · ", substring = true) and hasRole(Role.Button)) }
    }

    /** The built-in English deck is added on launch and shows in the deck list. */
    @Test
    fun builtInEnglishDeckAppearsInDeckList() {
        rule.waitUntil(20_000) {
            exists(hasText("What are you learning?")) || exists(hasContentDescription("Create cards"))
        }
        if (exists(hasText("What are you learning?"))) {
            rule.onNodeWithText("Skip").performClick()
        }
        waitFor(hasContentDescription("Create cards"))
        rule.onNode(hasText("Decks") and hasRole(Role.Tab)).performClick()
        // Installing ~500 notes takes a moment on a cold first launch.
        rule.waitUntil(60_000) {
            runCatching { rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(EnglishDeck.ROOT)) }.isSuccess
        }
        waitFor(hasText(EnglishDeck.ROOT))
    }
}
