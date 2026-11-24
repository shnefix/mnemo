package app.mnemo.data.repo

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.mnemo.db.MnemoDatabase
import app.mnemo.domain.search.SearchQuery
import app.mnemo.domain.search.SortKey
import app.mnemo.domain.stats.startOfLocalDayPlus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Browse search done in SQL: a [SearchQuery] becomes a WHERE clause over the
 * card/note/deck join and only the matching (card id, note id) pairs come
 * back, already sorted. Views are then loaded a page at a time by id, so a
 * 20k-card collection never has its JSON parsed or Markdown rendered in full.
 */
class BrowseRepository(
    private val driver: SqlDriver,
    private val db: MnemoDatabase,
    private val decks: DeckRepository,
) {
    data class Match(
        val cardId: Long,
        val noteId: Long,
    )

    suspend fun matches(
        query: SearchQuery,
        sort: SortKey,
        ascending: Boolean,
        now: Long,
        leechThreshold: Int = 8,
    ): List<Match> =
        withContext(Dispatchers.IO) {
            val params = mutableListOf<Any>()
            val where = query.terms.map { term -> clause(term, now, leechThreshold, params) }
            val order =
                when (sort) {
                    SortKey.Due -> "card.due"
                    SortKey.Created -> "card.id"
                    SortKey.Deck -> "deck.name COLLATE NOCASE"
                    SortKey.State -> "card.state"
                    SortKey.Lapses -> "card.lapses"
                }
            val sql =
                buildString {
                    append("SELECT card.id, card.note_id FROM card ")
                    append("JOIN note ON note.id = card.note_id JOIN deck ON deck.id = note.deck_id ")
                    if (where.isNotEmpty()) append("WHERE ").append(where.joinToString(" AND ")).append(' ')
                    append("ORDER BY ").append(order).append(if (ascending) " ASC" else " DESC").append(", card.id")
                }
            driver
                .executeQuery(
                    identifier = null,
                    sql = sql,
                    mapper = { cursor ->
                        val out = mutableListOf<Match>()
                        while (cursor.next().value) out += Match(cursor.getLong(0)!!, cursor.getLong(1)!!)
                        QueryResult.Value(out)
                    },
                    parameters = params.size,
                ) {
                    params.forEachIndexed { i, p ->
                        when (p) {
                            is Long -> bindLong(i, p)
                            is String -> bindString(i, p)
                            else -> error("unsupported parameter $p")
                        }
                    }
                }.value
        }

    suspend fun countAll(): Long = withContext(Dispatchers.IO) { db.cardQueries.countAll().executeAsOne() }

    private suspend fun clause(
        term: SearchQuery.Term,
        now: Long,
        leechThreshold: Int,
        params: MutableList<Any>,
    ): String {
        val sql =
            when (term) {
                is SearchQuery.Term.Tag -> {
                    params += "% ${escapeLike(term.tag)} %"
                    "note.tags LIKE ? ESCAPE '\\'"
                }
                is SearchQuery.Term.Deck -> {
                    val ids = deckIdsMatching(term.deck)
                    if (ids.isEmpty()) "0" else "note.deck_id IN (${ids.joinToString(",")})"
                }
                is SearchQuery.Term.Is ->
                    when (term.what.lowercase()) {
                        "due" -> {
                            params += now
                            "(card.suspended = 0 AND card.due <= ?)"
                        }
                        "new" -> "card.state = 0"
                        "learn", "learning" -> "card.state IN (1, 3)"
                        "review" -> "card.state = 2"
                        "suspended" -> "card.suspended = 1"
                        "buried" -> {
                            params += now
                            "(card.buried_until IS NOT NULL AND card.buried_until > ?)"
                        }
                        "leech" -> {
                            params += leechThreshold.toLong()
                            "card.lapses >= ?"
                        }
                        else -> "0"
                    }
                is SearchQuery.Term.Text -> {
                    params += "%${escapeLike(term.text)}%"
                    "note.fields_json LIKE ? ESCAPE '\\'"
                }
                is SearchQuery.Term.Op -> opClause(term, now, params)
            }
        return if (term.negated) "NOT ($sql)" else sql
    }

    /** The SQL for `flag:`, `rated:`, `added:`, `edited:`, `introduced:`, `prop:`, `note:`, `card:`, `nid:`, `cid:`. */
    private fun opClause(
        term: SearchQuery.Term.Op,
        now: Long,
        params: MutableList<Any>,
    ): String {
        val v = term.value.trim()

        /** Start of the day `n` days back counting today as 1 (Anki's `rated:1` = today). */
        fun since(n: Int): Long = startOfLocalDayPlus(now, -(n.coerceIn(1, 36_500) - 1))
        return when (term.key) {
            "flag" -> v.toIntOrNull()?.let { "card.flag = $it" } ?: "0"
            "card" -> v.toIntOrNull()?.let { "card.template_index = ${it - 1}" } ?: "0"
            "nid" -> SearchQuery.ids(v).takeIf { it.isNotEmpty() }?.let { "note.id IN (${it.joinToString(",")})" } ?: "0"
            "cid" -> SearchQuery.ids(v).takeIf { it.isNotEmpty() }?.let { "card.id IN (${it.joinToString(",")})" } ?: "0"
            "rated" -> {
                val parts = v.split(':')
                val days = parts[0].toIntOrNull() ?: return "0"
                params += since(days)
                val ease = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..4 }
                "card.id IN (SELECT card_id FROM review_log WHERE reviewed_at >= ?" + (ease?.let { " AND rating = $it" } ?: "") + ")"
            }
            "added" -> {
                params += since(v.toIntOrNull() ?: return "0")
                "note.created_at >= ?"
            }
            "edited" -> {
                params += since(v.toIntOrNull() ?: return "0")
                "note.updated_at >= ?"
            }
            "introduced" -> {
                params += since(v.toIntOrNull() ?: return "0")
                "card.id IN (SELECT card_id FROM review_log GROUP BY card_id " +
                    "HAVING min(CASE WHEN state_before = 0 THEN reviewed_at END) >= ?)"
            }
            "note" -> {
                params += v.replace("*", "%").replace("_", " ")
                "note.note_type_id IN (SELECT id FROM note_type WHERE name LIKE ?)"
            }
            "prop" -> {
                val p = SearchQuery.Prop.parse(v) ?: return "0"
                val op = p.op
                when (p.name) {
                    "ivl" -> "(card.state != 0 AND card.scheduled_days $op ${p.value.toLong()})"
                    "reps" -> "card.reps $op ${p.value.toLong()}"
                    "lapses" -> "card.lapses $op ${p.value.toLong()}"
                    "s" -> "(card.stability IS NOT NULL AND card.stability $op ${p.value})"
                    "d" -> "(card.difficulty IS NOT NULL AND (card.difficulty - 1.0) / 9.0 $op ${p.value})"
                    "due" -> {
                        // Days from today: prop:due=0 is due today, prop:due<=-1 overdue, prop:due>3 later than in 3 days.
                        val d = p.value.toInt()
                        val start = startOfLocalDayPlus(now, d)
                        val end = startOfLocalDayPlus(now, d + 1)
                        val cmp =
                            when (p.op) {
                                "<" -> "card.due < $start"
                                "<=" -> "card.due < $end"
                                ">" -> "card.due >= $end"
                                ">=" -> "card.due >= $start"
                                "!=" -> "(card.due < $start OR card.due >= $end)"
                                else -> "(card.due >= $start AND card.due < $end)"
                            }
                        "(card.state != 0 AND $cmp)"
                    }
                    else -> "0"
                }
            }
            else -> "0"
        }
    }

    /** Ids of every deck whose path is [name] or lies below it (case-insensitive), like Anki's `deck:` term. */
    private suspend fun deckIdsMatching(name: String): List<Long> {
        val wanted = name.trim().lowercase()
        return decks
            .all()
            .filter { d ->
                val path = d.path.lowercase()
                path == wanted || path.startsWith("$wanted::")
            }.map { it.id }
    }

    private fun escapeLike(s: String): String = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
