package app.mnemo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import app.mnemo.data.db.DriverFactory
import app.mnemo.di.AppGraph
import app.mnemo.domain.model.DAY_MS
import app.mnemo.domain.model.MINUTE_MS
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.Rating
import app.mnemo.domain.model.Relation
import app.mnemo.domain.review.ReviewSession
import app.mnemo.platform.Clock
import app.mnemo.ui.browse.BrowseScreen
import app.mnemo.ui.decks.DeckListScreen
import app.mnemo.ui.editor.NoteEditorScreen
import app.mnemo.ui.nav.AdaptiveScaffold
import app.mnemo.ui.nav.NavSection
import app.mnemo.ui.nav.WindowSizeProvider
import app.mnemo.ui.onboarding.OnboardingScreen
import app.mnemo.ui.review.ReviewScreen
import app.mnemo.ui.settings.SettingsScreen
import app.mnemo.ui.stats.StatsScreen
import app.mnemo.ui.theme.MnemoTheme
import app.mnemo.ui.theme.ThemeMode
import app.mnemo.ui.theme.ThemeState
import app.mnemo.ui.today.TodayScreen
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Renders every screen off-screen to build/screens/ (PNG) so the UI can be
 * inspected without a display. Run with:
 * ./gradlew :composeApp:desktopTest --tests app.mnemo.ScreenshotTest
 */
@OptIn(ExperimentalComposeUiApi::class)
class ScreenshotTest {
    private class MutableClock(
        var time: Long,
    ) : Clock {
        override fun now(): Long = time
    }

    private val outDir = File(System.getProperty("mnemo.screens.dir") ?: "build/screens").apply { mkdirs() }

    private fun seeded(): Pair<AppGraph, MutableClock> =
        runBlocking {
            val start = System.currentTimeMillis() - 40 * DAY_MS
            val clock = MutableClock(start)
            val g = AppGraph.create(DriverFactory(), null, clock, fuzz = null)
            g.seed()
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            val reversed = g.notes.noteTypeOf(NoteKind.Reversed)
            val cloze = g.notes.noteTypeOf(NoteKind.Cloze)
            val a1 = g.decks.createByPath("German::Vocab::A1")
            val grammar = g.decks.createByPath("German::Grammar")
            val bio = g.decks.createByPath("Biochemistry::Enzymes")
            val vocab =
                listOf(
                    "der Zug" to "the train",
                    "die Zeitung" to "the newspaper",
                    "das Fahrrad" to "the bicycle",
                    "der Hund" to "the dog",
                    "die Katze" to "the cat",
                    "das Haus" to "the house",
                    "der Apfel" to "the apple",
                    "die Straße" to "the street",
                    "das Mädchen" to "the girl",
                    "der Frühling" to "spring",
                    "die Verspätung" to "the delay",
                    "das Fenster" to "the window",
                )
            vocab.forEach { (de, en) -> g.notes.create(a1, reversed, mapOf("Front" to de, "Back" to en), listOf("a1", "noun")) }
            g.notes.create(
                grammar,
                basic,
                mapOf("Front" to "Which case follows **mit**?", "Back" to "Dative — *mit dem Zug*"),
                listOf("cases"),
            )
            g.notes.create(
                grammar,
                cloze,
                mapOf(
                    "Text" to "The two-way prepositions take the {{c1::accusative}} for motion and the {{c2::dative}} for location.",
                    "Extra" to "",
                ),
                listOf("cases"),
            )
            g.notes.create(
                bio,
                basic,
                mapOf(
                    "Front" to "What does a low Km tell you about an enzyme?",
                    "Back" to "High affinity for its substrate: half of Vmax is reached at a low concentration.",
                ),
                listOf("kinetics"),
            )
            g.notes.create(
                bio,
                basic,
                mapOf(
                    "Front" to "Which three enzymes catalyse the irreversible steps of glycolysis?",
                    "Back" to "Hexokinase, PFK-1, pyruvate kinase",
                ),
                listOf("glycolysis"),
            )
            g.notes.create(
                bio,
                cloze,
                mapOf(
                    "Text" to "A competitive inhibitor raises the apparent {{c1::Km}} but leaves {{c2::Vmax}} unchanged.",
                    "Extra" to "Can be overcome with excess substrate.",
                ),
                listOf("kinetics"),
            )

            // Simulate 40 days of reviews so stats have data.
            val ratings = listOf(Rating.Good, Rating.Good, Rating.Easy, Rating.Hard, Rating.Good, Rating.Again, Rating.Good)
            var i = 0
            repeat(40) { day ->
                clock.time = start + day * DAY_MS + 9 * 60 * MINUTE_MS
                if (day % 6 == 5) return@repeat
                val session = ReviewSession(g, null)
                session.load()
                var guard = 0
                while (!session.isFinished && guard++ < 60) {
                    session.answer(ratings[i++ % ratings.size], 4_000 + (i % 5) * 900L)
                    clock.time += 12 * MINUTE_MS
                }
            }
            clock.time = System.currentTimeMillis()
            val c1 = g.concepts.create("German cases")
            val c2 = g.concepts.create("Two-way prepositions")
            val c3 = g.concepts.create("Enzyme kinetics")
            val c4 = g.concepts.create("Glycolysis regulation")
            g.concepts.addEdge(c1, c2, Relation.PrerequisiteOf)
            g.concepts.addEdge(c3, c4, Relation.PrerequisiteOf)
            g.concepts.addEdge(c2, c1, Relation.PartOf)
            val cards = g.cards.allViews()
            cards.filter { it.deckName == "Grammar" }.forEach {
                g.concepts.link(it.card.id, c1)
                g.concepts.link(it.card.id, c2)
            }
            cards.filter { it.deckName == "Enzymes" }.forEachIndexed { idx, v -> g.concepts.link(v.card.id, if (idx < 2) c3 else c4) }
            // Cards added after the reviews stay new: one concept not started, one partly studied.
            val idioms = g.decks.createByPath("English::Idioms")
            listOf("break the ice", "a piece of cake", "call it a day", "on the fence").forEach {
                g.notes.create(idioms, basic, mapOf("Front" to it, "Back" to "…"), listOf("idiom"))
            }
            val c5 = g.concepts.create("Idioms")
            val c6 = g.concepts.create("Vocab & idioms")
            g.cards.allViews().forEach { v ->
                if (v.deckName == "Idioms") g.concepts.link(v.card.id, c5)
                if (v.deckName == "Idioms" || v.deckName == "A1") g.concepts.link(v.card.id, c6)
            }
            g to clock
        }

    private fun render(
        name: String,
        width: Int,
        height: Int,
        graph: AppGraph,
        mode: ThemeMode = ThemeMode.Dark,
        content: @Composable () -> Unit,
    ) {
        val scene =
            ImageComposeScene(width = width, height = height, density = Density(1f)) {
                CompositionLocalProvider(LocalAppGraph provides graph) {
                    MnemoTheme(themeState = ThemeState(mode)) { WindowSizeProvider(content) }
                }
            }
        try {
            var t = 0L
            repeat(12) {
                scene.render(t)
                Thread.sleep(120)
                t += 16_000_000L
            }
            val image = scene.render(t)
            val bytes = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(outDir, "$name.png").writeBytes(bytes)
        } finally {
            // Compose can throw "LayoutNode not found" while tearing down lazy lists; the PNG is already written.
            runCatching { scene.close() }
        }
    }

    @Composable
    private fun Shell(
        section: NavSection?,
        content: @Composable () -> Unit,
    ) {
        AdaptiveScaffold(current = section, showChrome = true, onNavigate = {}, onCreate = {}, onSettings = {}) { content() }
    }

    @Test
    fun renderAllScreens() {
        val (g, _) = seeded()
        val desktop = 1280 to 860
        val phone = 390 to 844
        for ((w, h) in listOf(desktop, phone)) {
            val tag = if (w > 600) "desktop" else "phone"
            render("today-$tag", w, h, g) { Shell(NavSection.Today) { TodayScreen({}, {}, {}, {}) } }
            render("decks-$tag", w, h, g) { Shell(NavSection.Decks) { DeckListScreen({}, {}, {}) } }
            render("browse-$tag", w, h, g) { Shell(NavSection.Browse) { BrowseScreen({}, {}) } }
            render("stats-$tag", w, h, g) { Shell(NavSection.Stats) { StatsScreen({}) } }
            render("settings-$tag", w, h, g) { Shell(null) { SettingsScreen() } }
            render("review-$tag", w, h, g) { ReviewScreen(null, onExit = {}, onEdit = {}) }
            render("editor-$tag", w, h, g) { NoteEditorScreen(null, null, {}) }
            render("onboarding-$tag", w, h, g) { OnboardingScreen({}) }
        }
        render("today-light", desktop.first, desktop.second, g, ThemeMode.Light) { Shell(NavSection.Today) { TodayScreen({}, {}, {}, {}) } }
        render("review-light", phone.first, phone.second, g, ThemeMode.Light) { ReviewScreen(null, onExit = {}, onEdit = {}) }
    }
}
