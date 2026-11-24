package app.mnemo.domain.search

import app.mnemo.domain.model.CardState
import app.mnemo.domain.model.CardView
import app.mnemo.domain.model.EpochMillis

/**
 * Anki-like search: `tag:x deck:y is:due is:new is:learn is:review is:suspended
 * is:leech "free text"`, plus `flag:1`, `rated:7[:1]`, `added:3`, `edited:3`,
 * `introduced:7`, `prop:ivl>=10` (ivl, due, reps, lapses, s, d), `note:Name`,
 * `card:2`, `nid:1,2` and `cid:1,2`. Terms are ANDed; a leading `-` negates a term.
 */
data class SearchQuery(
    val terms: List<Term>,
) {
    sealed interface Term {
        val negated: Boolean

        data class Tag(
            val tag: String,
            override val negated: Boolean,
        ) : Term

        data class Deck(
            val deck: String,
            override val negated: Boolean,
        ) : Term

        data class Is(
            val what: String,
            override val negated: Boolean,
        ) : Term

        data class Text(
            val text: String,
            override val negated: Boolean,
        ) : Term

        /** `flag:`, `rated:`, `added:`, `edited:`, `introduced:`, `prop:`, `note:`, `card:`, `nid:`, `cid:`. */
        data class Op(
            val key: String,
            val value: String,
            override val negated: Boolean,
        ) : Term
    }

    /** A `prop:` comparison such as `ivl>=10`. */
    data class Prop(
        val name: String,
        val op: String,
        val value: Double,
    ) {
        fun test(x: Double): Boolean =
            when (op) {
                "<" -> x < value
                "<=" -> x <= value
                ">" -> x > value
                ">=" -> x >= value
                "!=" -> x != value
                else -> x == value
            }

        companion object {
            private val pattern = Regex("^([a-z]+)(<=|>=|!=|=|<|>)(-?[0-9.]+)$")

            fun parse(s: String): Prop? {
                val m = pattern.find(s.lowercase()) ?: return null
                val v = m.groupValues[3].toDoubleOrNull() ?: return null
                return Prop(m.groupValues[1], m.groupValues[2], v)
            }
        }
    }

    fun matches(
        card: CardView,
        now: EpochMillis,
        leechThreshold: Int = 8,
    ): Boolean = terms.all { term -> termMatches(term, card, now, leechThreshold) != term.negated }

    private fun termMatches(
        term: Term,
        card: CardView,
        now: EpochMillis,
        leechThreshold: Int,
    ): Boolean =
        when (term) {
            is Term.Tag -> card.tags.any { it.equals(term.tag, ignoreCase = true) }
            is Term.Deck ->
                card.deckName.equals(term.deck, ignoreCase = true) ||
                    card.deckName.startsWith("${term.deck}::", ignoreCase = true)
            is Term.Is ->
                when (term.what.lowercase()) {
                    "due" -> !card.card.suspended && card.card.due <= now
                    "new" -> card.card.state == CardState.New
                    "learn", "learning" -> card.card.state == CardState.Learning || card.card.state == CardState.Relearning
                    "review" -> card.card.state == CardState.Review
                    "suspended" -> card.card.suspended
                    "buried" -> card.card.buriedUntil?.let { it > now } ?: false
                    "leech" -> card.card.lapses >= leechThreshold
                    else -> false
                }
            is Term.Text -> card.fields.values.any { it.contains(term.text, ignoreCase = true) }
            // In memory only what the card itself knows; dates and history need the database (Browse).
            is Term.Op ->
                when (term.key) {
                    "flag" -> term.value.toIntOrNull() == card.card.flag
                    "card" -> term.value.toIntOrNull() == card.card.templateIndex + 1
                    "cid" -> ids(term.value).contains(card.card.id)
                    "nid" -> ids(term.value).contains(card.card.noteId)
                    "prop" -> {
                        val p = Prop.parse(term.value)
                        val c = card.card
                        when (p?.name) {
                            "ivl" -> c.state != CardState.New && p.test(c.scheduledDays.toDouble())
                            "reps" -> p.test(c.reps.toDouble())
                            "lapses" -> p.test(c.lapses.toDouble())
                            "s" -> c.stability != null && p.test(c.stability)
                            "d" -> c.difficulty != null && p.test((c.difficulty - 1.0) / 9.0)
                            else -> false
                        }
                    }
                    else -> false
                }
        }

    companion object {
        val EMPTY = SearchQuery(emptyList())

        private val OPS = setOf("flag", "rated", "added", "edited", "introduced", "prop", "note", "card", "nid", "cid")

        /** `1,2,3` → ids. */
        fun ids(value: String): Set<Long> = value.split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()

        fun parse(input: String): SearchQuery {
            val terms = mutableListOf<Term>()
            for (raw in tokenize(input)) {
                var token = raw
                val negated = token.startsWith("-")
                if (negated) token = token.drop(1)
                if (token.isEmpty()) continue
                val colon = token.indexOf(':')
                val term =
                    if (colon > 0) {
                        val key = token.substring(0, colon).lowercase()
                        val value = token.substring(colon + 1).trim('"')
                        when (key) {
                            "tag" -> Term.Tag(value, negated)
                            "deck" -> Term.Deck(value, negated)
                            "is" -> Term.Is(value, negated)
                            in OPS -> Term.Op(key, value, negated)
                            else -> Term.Text(token.trim('"'), negated)
                        }
                    } else {
                        Term.Text(token.trim('"'), negated)
                    }
                terms += term
            }
            return SearchQuery(terms)
        }

        /** Splits on whitespace, keeping quoted phrases together. */
        private fun tokenize(input: String): List<String> {
            val out = mutableListOf<String>()
            val sb = StringBuilder()
            var inQuotes = false
            for (ch in input) {
                when {
                    ch == '"' -> {
                        inQuotes = !inQuotes
                        sb.append(ch)
                    }
                    ch.isWhitespace() && !inQuotes -> {
                        if (sb.isNotEmpty()) out += sb.toString()
                        sb.clear()
                    }
                    else -> sb.append(ch)
                }
            }
            if (sb.isNotEmpty()) out += sb.toString()
            return out
        }
    }
}
