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
        /** Study only one layer of what is due: "review", "learning" or "new" (null = all of it). */
        val layer: String? = null,
        /** A custom-study session (`CustomStudy.encode()`). */
        val custom: String? = null,
    ) : Route

    @Serializable data class Browse(
        /** A search to start with, e.g. `deck:"German"` from a deck page. */
        val query: String? = null,
    ) : Route

    /** One deck's own page. */
    @Serializable data class Deck(
        val deckId: Long,
    ) : Route

    @Serializable data class Editor(
        val noteId: Long? = null,
        val deckId: Long? = null,
    ) : Route

    @Serializable data object Quiz : Route

    @Serializable data object Stats : Route

    @Serializable data object Settings : Route

    /** Your note types: fields and card templates. */
    @Serializable data object NoteTypes : Route

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
    Browse("Browse", Route.Browse()),
    Stats("Stats", Route.Stats),
}
