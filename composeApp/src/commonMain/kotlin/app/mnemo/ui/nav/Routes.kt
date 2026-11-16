package app.mnemo.ui.nav

import kotlinx.serialization.Serializable

/** Type-safe navigation routes. */
sealed interface Route {
    @Serializable data object Today : Route

    @Serializable data object Decks : Route

    @Serializable data class Review(
        val deckId: Long? = null,
        /** Review the cards of the weakest concepts instead of what is due. */
        val weak: Boolean = false,
    ) : Route

    @Serializable data object Browse : Route

    @Serializable data class Editor(
        val noteId: Long? = null,
        val deckId: Long? = null,
    ) : Route

    @Serializable data object Graph : Route

    @Serializable data object Stats : Route

    @Serializable data object Settings : Route

    @Serializable data class DeckSettings(
        val deckId: Long,
    ) : Route
}

/** Entries of the main navigation (bottom bar on phones, sidebar on desktop). */
enum class NavSection(
    val label: String,
    val route: Route,
) {
    Today("Today", Route.Today),
    Decks("Decks", Route.Decks),
    Browse("Browse", Route.Browse),
    Graph("Graph", Route.Graph),
    Stats("Stats", Route.Stats),
}
