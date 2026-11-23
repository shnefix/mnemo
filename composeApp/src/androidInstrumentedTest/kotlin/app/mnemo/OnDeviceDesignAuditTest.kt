package app.mnemo

import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Walks every screen reachable on a phone and lints the real rendered text:
 * words broken mid-word, text squeezed into a sliver, clipped text, anything
 * running off the screen. Also saves a screenshot per screen to the app's
 * external files dir (…/files/shots) for visual review.
 */
@RunWith(AndroidJUnit4::class)
class OnDeviceDesignAuditTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val problems = mutableListOf<String>()
    private val shotDir by lazy {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        File(ctx.getExternalFilesDir(null), "shots").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private fun exists(m: SemanticsMatcher) = rule.onAllNodes(m).fetchSemanticsNodes().isNotEmpty()

    private fun settle() {
        rule.waitForIdle()
        Thread.sleep(700)
        rule.waitForIdle()
    }

    private fun shot(name: String) {
        val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(shotDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun isLetter(c: Char) = c.isLetterOrDigit()

    private fun lint(screen: String) {
        settle()
        shot(screen)
        val root = rule.onAllNodes(SemanticsMatcher("root") { true }).fetchSemanticsNodes().first()
        val screenW = root.boundsInRoot.right.coerceAtLeast(1f)
        val all = mutableListOf<SemanticsNode>()

        fun walk(n: SemanticsNode) {
            all += n
            n.children.forEach(::walk)
        }
        rule.onAllNodes(isRoot()).fetchSemanticsNodes().forEach(::walk)
        for (n in all) {
            val texts = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: continue
            val label = texts.take(40).replace("\n", " ")
            val b = n.boundsInRoot
            if (b.width <= 0f || b.height <= 0f) continue
            if (b.left < -1f ||
                b.right > screenW + 1f
            ) {
                problems += "[$screen] '$label' runs off screen (l=${b.left}, r=${b.right}, screen=$screenW)"
            }
            val results = mutableListOf<TextLayoutResult>()
            n.config
                .getOrNull(SemanticsActions.GetTextLayoutResult)
                ?.action
                ?.invoke(results)
            val r = results.firstOrNull() ?: continue
            val text = r.layoutInput.text.text
            for (line in 0 until r.lineCount - 1) {
                val end = r.getLineEnd(line, visibleEnd = false)
                if (end in 1 until text.length && isLetter(text[end - 1]) && isLetter(text[end])) {
                    problems +=
                        "[$screen] '$label' breaks a word across lines ('${text.substring(
                            (end - 4).coerceAtLeast(0),
                            end,
                        )}|${text.substring(end, (end + 3).coerceAtMost(text.length))}')"
                    break
                }
            }
            if (text.length >= 4 && r.lineCount >= 3 && r.size.width < r.size.height / 2
            ) {
                problems += "[$screen] '$label' squeezed into ${r.size.width}x${r.size.height}px"
            }
        }
    }

    private fun tap(text: String) {
        rule.onAllNodes(hasText(text)).onLast().performClick()
        settle()
    }

    @Test
    fun auditEveryPhoneScreen() {
        rule.waitUntil(20_000) { exists(hasText("What are you learning?")) || exists(hasContentDescription("Create cards")) }
        if (exists(hasText("What are you learning?"))) {
            lint("00-onboarding")
            rule.onNodeWithText("Skip").performClick()
        }
        rule.waitUntil(15_000) { exists(hasContentDescription("Create cards")) }
        lint("01-today")
        for ((i, tab) in listOf("Decks", "Browse", "Stats").withIndex()) {
            tap(tab)
            lint("0${i + 2}-${tab.lowercase()}")
        }
        // Editor via the + button.
        rule.onNodeWithContentDescription("Create cards").performClick()
        settle()
        lint("07-editor")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle()
        // Settings is reached from the Decks header on phones.
        tap("Decks")
        rule.onNodeWithContentDescription("Settings").performClick()
        settle()
        lint("09-settings")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle()
        // A deck's own page (tap a deck's name), then its settings from there.
        val deckCard = hasText(" cards · ", substring = true) and hasRole(Role.Button)
        if (exists(deckCard)) {
            rule.onAllNodes(deckCard).onFirst().performClick()
            settle()
            lint("10-deck-page")
            rule.onNodeWithText("Deck settings").performScrollTo().performClick()
            settle()
            lint("10-deck-settings")
        }
        if (problems.isNotEmpty()) fail("Design audit found ${problems.size} problem(s):\n" + problems.distinct().joinToString("\n"))
    }

    /** Creates a deck with long-text cards, audits Decks, Deck settings and Review, then deletes the deck. */
    @Test
    fun auditScreensThatNeedData() {
        val deck = "UiAudit Biochemistry and Programming"
        rule.waitUntil(20_000) { exists(hasText("What are you learning?")) || exists(hasContentDescription("Create cards")) }
        if (exists(hasText("What are you learning?"))) rule.onNodeWithText("Skip").performClick()
        rule.waitUntil(15_000) { exists(hasContentDescription("Create cards")) }
        try {
            rule.onNodeWithContentDescription("Create cards").performClick()
            settle()
            rule.onNodeWithText("New deck").performClick()
            settle()
            rule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput(deck)
            rule.onNodeWithText("Create").performClick()
            settle()
            val fields = rule.onAllNodes(hasSetTextAction() and !hasAnyAncestor(isDialog()))
            fields[0].performTextInput("What is the process by which a cell converts glucose into ATP, called in biochemistry?")
            fields[1].performTextInput(
                "Glycolysis, then the citric acid cycle and oxidative phosphorylation, supercalifragilisticexpialidocious",
            )
            rule.onNodeWithText("Save & close").performScrollTo().performClick()
            rule.waitUntil(15_000) { exists(hasContentDescription("Create cards")) }

            tap("Decks")
            rule.waitUntil(15_000) {
                runCatching { rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(deck)) }.isSuccess
            }
            rule.waitUntil(15_000) { exists(hasText(deck)) }
            lint("11-decks-with-data")

            // The deck's own page (tap its name), then Deck settings from there.
            // The deck list loads in the background: wait for this deck's card before tapping it.
            rule.waitUntil(15_000) { exists(hasText(deck) and hasRole(Role.Button)) }
            rule.onNode(hasText(deck) and hasRole(Role.Button)).performScrollTo().performClick()
            rule.waitUntil(15_000) { exists(hasText("Study $deck") and hasRole(Role.Button)) }
            lint("12-deck-page")
            rule.onNodeWithText("Deck settings").performScrollTo().performClick()
            settle()
            lint("12-deck-settings")
            rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            settle()

            // Review from the deck page: question, answer, then end the session.
            rule.onNode(hasText("Study $deck") and hasRole(Role.Button)).performClick()
            rule.waitUntil(15_000) { exists(hasText("Show answer")) }
            lint("13-review-question")
            rule.onNodeWithText("Show answer").performClick()
            rule.waitUntil(15_000) { exists(hasText("Good")) }
            lint("14-review-answer")
            rule.onNodeWithContentDescription("End session").performClick()
            settle()
        } finally {
            runCatching { deleteDeck(deck) }
        }
        if (problems.isNotEmpty()) fail("Design audit found ${problems.size} problem(s):\n" + problems.distinct().joinToString("\n"))
    }

    /**
     * Deletes every deck called [name] (this run's and any left by an earlier failed run) from that
     * deck's own page: tap its name in the list, then ⋮ › Delete.
     */
    private fun deleteDeck(name: String) {
        rule.waitUntil(15_000) { exists(hasContentDescription("Create cards")) }
        tap("Decks")
        val card = hasText(name) and hasText(" cards · ", substring = true) and hasRole(Role.Button)
        var rounds = 0
        while (rounds++ < 5) {
            val found =
                runCatching {
                    rule.waitUntil(15_000) {
                        runCatching { rule.onNode(hasScrollToNodeAction()).performScrollToNode(card) }.isSuccess
                    }
                }.isSuccess
            if (!found) break
            rule.onAllNodes(card).onFirst().performClick()
            rule.waitUntil(15_000) { exists(hasContentDescription("Rename or delete deck")) }
            rule.onNodeWithContentDescription("Rename or delete deck").performClick()
            rule.waitUntil(15_000) { exists(hasText("Delete")) }
            rule.onNodeWithText("Delete").performClick()
            rule.waitUntil(15_000) { exists(hasText("Delete “$name", substring = true) and hasAnyAncestor(isDialog())) }
            rule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
            rule.waitUntil(15_000) { !exists(isDialog()) }
            // The page closes itself once its deck is gone.
            rule.waitUntil(15_000) { !exists(hasContentDescription("Rename or delete deck")) }
        }
    }
}
