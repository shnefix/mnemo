package app.mnemo

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.mnemo.data.io.AutoBackup
import app.mnemo.data.seed.EnglishB2Deck
import app.mnemo.data.seed.EnglishDeck
import app.mnemo.data.seed.GermanA0Deck
import app.mnemo.data.seed.GermanA1Deck
import app.mnemo.di.AppGraph
import app.mnemo.domain.graph.DeckGraph
import app.mnemo.domain.sync.CloudSync
import app.mnemo.platform.Prefs
import app.mnemo.platform.Speech
import app.mnemo.ui.browse.BrowseScreen
import app.mnemo.ui.decks.DeckListScreen
import app.mnemo.ui.decks.DeckSettingsScreen
import app.mnemo.ui.editor.NoteEditorScreen
import app.mnemo.ui.nav.AdaptiveScaffold
import app.mnemo.ui.nav.NavSection
import app.mnemo.ui.nav.Route
import app.mnemo.ui.nav.WindowSizeProvider
import app.mnemo.ui.onboarding.OnboardingScreen
import app.mnemo.ui.review.ReviewScreen
import app.mnemo.ui.settings.SettingsScreen
import app.mnemo.ui.stats.StatsScreen
import app.mnemo.ui.theme.MnemoTheme
import app.mnemo.ui.theme.ThemeState
import app.mnemo.ui.today.TodayScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How long launch housekeeping waits, so the first screen gets the database to itself. */
private const val HOUSEKEEPING_DELAY_MS = 1_500L

val LocalAppGraph = staticCompositionLocalOf<AppGraph> { error("AppGraph not provided") }

@Composable
fun App(
    graph: AppGraph,
    themeState: ThemeState? = null,
    reviewRequests: Int = 0,
) {
    CompositionLocalProvider(LocalAppGraph provides graph) {
        MnemoTheme(themeState = themeState) {
            // First launch: ask what the user is learning before showing the app.
            var onboarded by remember { mutableStateOf(Prefs.get(Prefs.ONBOARDED) == "1") }
            var checked by remember { mutableStateOf(onboarded) }
            LaunchedEffect(Unit) {
                if (!onboarded && graph.decks.count() > 0L) {
                    Prefs.set(Prefs.ONBOARDED, "1")
                    onboarded = true
                }
                checked = true
            }
            // Launch housekeeping waits until the first screen has loaded and then runs off the
            // main thread, so it never competes with what you are looking at.
            LaunchedEffect(onboarded) {
                if (!onboarded) return@LaunchedEffect
                delay(HOUSEKEEPING_DELAY_MS)
                withContext(Dispatchers.Default) {
                    // Everyone gets the built-in English decks once.
                    runCatching { EnglishDeck.installOnce(graph) }
                    runCatching { EnglishB2Deck.installOnce(graph) }
                    runCatching { GermanA0Deck.installOnce(graph) }
                    runCatching { GermanA1Deck.installOnce(graph) }
                    // Every deck on the knowledge graph, plus the English grammar topics.
                    runCatching { DeckGraph.sync(graph) }
                    runCatching { EnglishDeck.syncGraph(graph) }
                    // Same decks and progress as the other phones on this account.
                    runCatching { CloudSync.sync(graph) }
                    // Daily safety net; never fails the launch.
                    runCatching { AutoBackup.runIfDue(graph) }
                    runCatching { graph.notes.pruneTombstones() }
                }
                runCatching { Speech.warmUp() }
            }
            // A newer version on GitHub: offer it once per launch.
            var update by remember { mutableStateOf<app.mnemo.platform.AppUpdate?>(null) }
            var updateError by remember { mutableStateOf<String?>(null) }
            val updateScope = androidx.compose.runtime.rememberCoroutineScope()
            LaunchedEffect(Unit) {
                if (app.mnemo.platform.Updater.supported) {
                    delay(HOUSEKEEPING_DELAY_MS)
                    update =
                        app.mnemo.platform.Updater
                            .check()
                }
            }
            update?.let { u ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { update = null },
                    title = { androidx.compose.material3.Text("Update available") },
                    text = {
                        androidx.compose.material3.Text(
                            "Mnemo ${u.versionName} is ready." + (updateError?.let { "\n\n$it" } ?: "") +
                                (u.notes.takeIf { it.isNotBlank() }?.let { "\n\n$it" } ?: ""),
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            updateScope.launch {
                                app.mnemo.platform.Updater
                                    .install(u)
                                    .onSuccess { update = null }
                                    .onFailure { updateError = it.message }
                            }
                        }) { androidx.compose.material3.Text("Install") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { update = null }) { androidx.compose.material3.Text("Later") }
                    },
                )
            }
            if (!checked) return@MnemoTheme
            if (!onboarded) {
                OnboardingScreen(onDone = { onboarded = true })
                return@MnemoTheme
            }
            WindowSizeProvider {
                val nav = rememberNavController()
                val backStack by nav.currentBackStackEntryAsState()
                val routeName = backStack?.destination?.route.orEmpty()
                val currentSection =
                    NavSection.entries.firstOrNull { section ->
                        routeName.substringBefore('/').substringBefore('?').endsWith(section.route::class.simpleName ?: "~")
                    }
                val isFocus = routeName.contains("Route.Review") || routeName.contains("Route.Editor") || routeName.contains("Route.Quiz")

                fun goHome() {
                    nav.navigate(Route.Today) {
                        popUpTo(Route.Today) { inclusive = true }
                        launchSingleTop = true
                    }
                }

                // The home-screen widget's "Start review" (each tap bumps the counter).
                LaunchedEffect(reviewRequests) {
                    if (reviewRequests > 0) nav.navigate(Route.Review()) { launchSingleTop = true }
                }

                AdaptiveScaffold(
                    current = currentSection,
                    showChrome = !isFocus,
                    onNavigate = { section ->
                        nav.navigate(section.route) {
                            popUpTo(Route.Today) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onCreate = { nav.navigate(Route.Editor()) },
                    onSettings = { nav.navigate(Route.Settings) },
                ) { padding ->
                    NavHost(
                        navController = nav,
                        startDestination = Route.Today,
                        modifier = Modifier.padding(padding),
                        // The default 700ms crossfade made every screen switch feel sluggish.
                        enterTransition = { fadeIn(tween(120)) },
                        exitTransition = { fadeOut(tween(90)) },
                        popEnterTransition = { fadeIn(tween(120)) },
                        popExitTransition = { fadeOut(tween(90)) },
                    ) {
                        composable<Route.Today> {
                            TodayScreen(
                                onReview = { nav.navigate(Route.Review(it)) },
                                onAddCards = { nav.navigate(Route.Editor()) },
                                onDecks = { nav.navigate(Route.Decks) { launchSingleTop = true } },
                                onWeakSpots = { nav.navigate(Route.Review(weak = true)) },
                                onQuiz = { nav.navigate(Route.Quiz) },
                            )
                        }
                        composable<Route.Quiz> {
                            app.mnemo.ui.quiz
                                .QuizScreen(onExit = { if (!nav.popBackStack()) goHome() })
                        }
                        composable<Route.Decks> {
                            DeckListScreen(
                                onReview = { nav.navigate(Route.Review(it)) },
                                onAddCards = { nav.navigate(Route.Editor(deckId = it)) },
                                onSettings = { nav.navigate(Route.DeckSettings(it)) },
                                onBrowse = { nav.navigate(Route.Browse) },
                                onAppSettings = { nav.navigate(Route.Settings) },
                            )
                        }
                        composable<Route.Browse> {
                            BrowseScreen(
                                onEdit = { nav.navigate(Route.Editor(noteId = it)) },
                                onAdd = { nav.navigate(Route.Editor()) },
                            )
                        }
                        composable<Route.Stats> { StatsScreen(onEdit = { nav.navigate(Route.Editor(noteId = it)) }) }
                        composable<Route.Settings> { SettingsScreen() }
                        composable<Route.Review> { entry ->
                            val route = entry.toRoute<Route.Review>()
                            ReviewScreen(
                                deckId = route.deckId,
                                weak = route.weak,
                                onExit = { if (!nav.popBackStack()) goHome() },
                                onEdit = { nav.navigate(Route.Editor(noteId = it)) },
                                onSearch = { nav.navigate(Route.Browse) },
                            )
                        }
                        composable<Route.Editor> { entry ->
                            val route = entry.toRoute<Route.Editor>()
                            NoteEditorScreen(
                                noteId = route.noteId,
                                deckId = route.deckId,
                                onClose = { if (!nav.popBackStack()) goHome() },
                            )
                        }
                        composable<Route.DeckSettings> { entry ->
                            val route = entry.toRoute<Route.DeckSettings>()
                            DeckSettingsScreen(deckId = route.deckId, onBack = { if (!nav.popBackStack()) goHome() })
                        }
                    }
                }
            }
        }
    }
}
