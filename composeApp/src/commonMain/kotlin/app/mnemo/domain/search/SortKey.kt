package app.mnemo.domain.search

/** Browse sort orders; each maps to an indexed or cheap SQL column. */
enum class SortKey(
    val label: String,
) {
    Due("Due"),
    Created("Created"),
    Deck("Deck"),
    State("State"),
    Lapses("Lapses"),
}
