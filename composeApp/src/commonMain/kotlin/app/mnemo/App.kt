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
import app.mnemo.di.AppGraph
import app.mnemo.platform.Prefs
import app.mnemo.ui.browse.BrowseScreen
import app.mnemo.ui.decks.DeckListScreen
import app.mnemo.ui.decks.DeckSettingsScreen
import app.mnemo.ui.editor.NoteEditorScreen
import app.mnemo.ui.graph.GraphScreen
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
                // Daily safety net; never blocks the UI and never fails the launch.
                runCatching { AutoBackup.runIfDue(graph) }
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
                val isFocus = routeName.contains("Route.Review") || routeName.contains("Route.Editor")

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
                            )
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
                        composable<Route.Graph> {
                            GraphScreen(
                                onEditCard = { nav.navigate(Route.Editor(noteId = it)) },
                                onWeakSpots = { nav.navigate(Route.Review(weak = true)) },
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
