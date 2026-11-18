package app.mnemo.ui.decks

/** "Vocabulary 07": a label and a number of at least two digits. */
private val PACK = Regex("""^(.+) (\d{2,})$""")

/**
 * Small numbered sub-decks ("Vocabulary 01", "Vocabulary 02", …) shown as tiles inside their
 * parent instead of as rows of their own: leaf decks named "<label> NN" that share the label
 * with at least one sibling. Keyed by parent deck id, in name order. They stay real decks, so
 * sync and review work as before; only the list shows them grouped.
 */
internal fun packsByParent(rows: List<DeckRow>): Map<Long, List<DeckRow>> {
    val hasChildren = rows.mapNotNullTo(HashSet()) { it.deck.parentId }
    return rows
        .filter { it.deck.parentId != null && it.deck.id !in hasChildren && PACK.matches(it.deck.name) }
        .groupBy { it.deck.parentId!! to PACK.find(it.deck.name)!!.groupValues[1] }
        .filterValues { it.size >= 2 }
        .entries
        .groupBy({ it.key.first }, { it.value })
        .mapValues { (_, groups) -> groups.flatten().sortedBy { it.deck.name } }
}

/** The number of a pack ("07"), or its whole name when it has none. */
internal fun packNumber(name: String): String = PACK.find(name)?.groupValues?.get(2) ?: name
