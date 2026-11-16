package app.mnemo.data.io

import app.mnemo.di.AppGraph
import app.mnemo.domain.cards.ClozeParser
import app.mnemo.domain.model.NoteKind

/**
 * Imports a folder of Markdown notes (an Obsidian vault, say) as cards.
 *
 * A file yields one card per question/answer pair, written in any of:
 *  - headings: `## Question` … `## Answer` … (also Q/A, Front/Back, Prompt/Response/Solution);
 *  - prefixed lines: `Q: …` / `A: …` (also `**Q:**`, `Question:`), the answer running to the next `Q:`;
 *  - the Spaced Repetition plugin layout: a block whose question and answer are separated by a line holding just `?`.
 * With [titleFallback], a file that has none of these becomes one card: its `# Title` (or file name) asks, the body answers.
 *
 * Tags come from `tags:` in the YAML frontmatter and from lines that hold only `#tags`.
 * Subfolders become subdecks of the chosen deck. A question whose front contains
 * `{{c1::…}}` becomes a cloze note. Re-importing the same folder skips cards already
 * imported (keyed by file path + question), so the vault can stay the source of truth.
 */
object MarkdownImporter {
    data class Doc(
        /** Path relative to the chosen folder, `/`-separated, e.g. `German/Cases/dativ.md`. */
        val path: String,
        val text: String,
    )

    data class Parsed(
        val question: String,
        val answer: String,
        val tags: List<String>,
    )

    data class Result(
        val notes: Int,
        val cards: Int,
        /** Cards already imported earlier (same file and question). */
        val skipped: Int,
        val files: Int,
        val decks: Int,
    )

    private val heading = Regex("""^(#{1,6})\s+(.+?)\s*#*\s*$""")
    private val questionTitle = Regex("""^(q|question|front|prompt)(\b.*)?$""", RegexOption.IGNORE_CASE)
    private val answerTitle = Regex("""^(a|answer|back|response|solution)(\b.*)?$""", RegexOption.IGNORE_CASE)
    private val questionLine = Regex("""^\**\s*(?:q|question)\s*:\**\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val answerLine = Regex("""^\**\s*(?:a|answer)\s*:\**\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val tagToken = Regex("""^#([\p{L}\p{N}_/-]+)$""")

    fun parse(
        text: String,
        fileName: String = "",
        titleFallback: Boolean = false,
    ): List<Parsed> {
        val (meta, rawBody) = splitFrontmatter(text)
        val tags = (metaTags(meta) + inlineTags(rawBody)).distinct()
        val body = rawBody.lines().filterNot { isTagLine(it) }.joinToString("\n")

        val pairs =
            headingPairs(body).ifEmpty { prefixedPairs(body) }.ifEmpty { separatorPairs(body) }.ifEmpty {
                if (titleFallback) titlePair(body, fileName) else emptyList()
            }
        return pairs
            .map { (q, a) -> Parsed(q.trim(), a.trim(), tags) }
            .filter { it.question.isNotBlank() && (it.answer.isNotBlank() || ClozeParser.indices(it.question).isNotEmpty()) }
    }

    suspend fun import(
        graph: AppGraph,
        rootDeckId: Long,
        docs: List<Doc>,
        titleFallback: Boolean = false,
    ): Result {
        val root = graph.decks.byId(rootDeckId) ?: error("Deck $rootDeckId not found")
        val basic = graph.notes.noteTypeOf(NoteKind.Basic)
        val cloze = graph.notes.noteTypeOf(NoteKind.Cloze)
        val existing = graph.notes.existingSourceIds().toHashSet()
        val deckIds = HashMap<String, Long>()
        var notes = 0
        var cards = 0
        var skipped = 0
        var files = 0
        for (doc in docs) {
            val parsed = parse(doc.text, fileName = doc.path.substringAfterLast('/'), titleFallback = titleFallback)
            if (parsed.isEmpty()) continue
            files += 1
            val folders =
                doc.path
                    .split('/')
                    .dropLast(1)
                    .filter { it.isNotBlank() }
            val deckPath = (listOf(root.path) + folders).joinToString("::")
            val deckId = deckIds.getOrPut(deckPath) { graph.decks.createByPath(deckPath) }
            for (card in parsed) {
                val source = sourceId(doc.path, card.question)
                if (source in existing) {
                    skipped += 1
                    continue
                }
                existing += source
                val clozes = ClozeParser.indices(card.question)
                if (clozes.isNotEmpty()) {
                    graph.notes.create(deckId, cloze, mapOf("Text" to card.question, "Extra" to card.answer), card.tags, source)
                    cards += clozes.size
                } else {
                    graph.notes.create(deckId, basic, mapOf("Front" to card.question, "Back" to card.answer), card.tags, source)
                    cards += 1
                }
                notes += 1
            }
        }
        return Result(notes, cards, skipped, files, deckIds.size)
    }

    /** Stable id for a card from a vault: FNV-1a over the file path and question, kept positive. */
    fun sourceId(
        path: String,
        question: String,
    ): Long {
        var h = -3750763034362895579L // FNV offset basis 0xcbf29ce484222325
        for (b in "md|$path|${question.trim()}".encodeToByteArray()) {
            h = h xor (b.toLong() and 0xff)
            h *= 1099511628211L
        }
        return h and Long.MAX_VALUE
    }

    // --- frontmatter and tags ---

    /** Splits a leading `---` YAML block off; returns its lines (possibly empty) and the body. */
    fun splitFrontmatter(text: String): Pair<List<String>, String> {
        val lines = text.lines()
        if (lines.firstOrNull()?.trim() != "---") return emptyList<String>() to text
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
        if (end < 0) return emptyList<String>() to text
        return lines.subList(1, end + 1) to lines.drop(end + 2).joinToString("\n")
    }

    private fun metaTags(meta: List<String>): List<String> {
        val out = mutableListOf<String>()
        var inTags = false
        for (line in meta) {
            val trimmed = line.trim()
            if (inTags && trimmed.startsWith("- ")) {
                out += cleanTag(trimmed.removePrefix("- "))
                continue
            }
            inTags = false
            val key = trimmed.substringBefore(':', "").trim().lowercase()
            if (key != "tags" && key != "tag") continue
            val value = trimmed.substringAfter(':').trim()
            if (value.isEmpty()) {
                inTags = true
            } else {
                out += value.trim('[', ']').split(',', ' ').map { cleanTag(it) }
            }
        }
        return out.filter { it.isNotEmpty() }
    }

    private fun cleanTag(s: String): String =
        s
            .trim()
            .trim('"', '\'')
            .removePrefix("#")
            .trim()

    private fun isTagLine(line: String): Boolean {
        val tokens = line.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        return tokens.isNotEmpty() && tokens.all { tagToken.matches(it) }
    }

    private fun inlineTags(body: String): List<String> =
        body
            .lines()
            .filter { isTagLine(it) }
            .flatMap { line -> line.trim().split(Regex("""\s+""")).map { it.removePrefix("#") } }

    // --- card layouts ---

    private fun headingPairs(body: String): List<Pair<String, String>> {
        data class Section(
            val title: String,
            val content: StringBuilder = StringBuilder(),
        )
        val sections = mutableListOf<Section>()
        var current: Section? = null
        for (line in body.lines()) {
            val m = heading.matchEntire(line)
            if (m != null) {
                current = Section(m.groupValues[2].trim()).also { sections += it }
            } else {
                current?.content?.append(line)?.append('\n')
            }
        }
        val out = mutableListOf<Pair<String, String>>()
        var i = 0
        while (i < sections.size - 1) {
            val q = sections[i]
            val a = sections[i + 1]
            if (questionTitle.matches(q.title) && answerTitle.matches(a.title)) {
                out += q.content.toString() to a.content.toString()
                i += 2
            } else {
                i += 1
            }
        }
        return out
    }

    private fun prefixedPairs(body: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        var q: StringBuilder? = null
        var a: StringBuilder? = null
        for (line in body.lines()) {
            val qm = questionLine.matchEntire(line)
            val am = answerLine.matchEntire(line)
            val answer = a
            val question = q
            when {
                qm != null -> {
                    if (question != null && answer != null) out += question.toString() to answer.toString()
                    q = StringBuilder(qm.groupValues[1]).append('\n')
                    a = null
                }
                am != null && question != null -> a = StringBuilder(am.groupValues[1]).append('\n')
                answer != null -> answer.append(line).append('\n')
                question != null -> question.append(line).append('\n')
            }
        }
        val question = q
        val answer = a
        if (question != null && answer != null) out += question.toString() to answer.toString()
        return out
    }

    /** Blocks separated by blank lines; within a block a line that is only `?` splits question from answer. */
    private fun separatorPairs(body: String): List<Pair<String, String>> =
        body
            .split(Regex("""\n\s*\n"""))
            .mapNotNull { block ->
                val lines = block.lines()
                val sep = lines.indexOfFirst { it.trim() == "?" }
                if (sep <= 0 || sep == lines.lastIndex) {
                    null
                } else {
                    lines.subList(0, sep).joinToString("\n") to lines.subList(sep + 1, lines.size).joinToString("\n")
                }
            }

    private fun titlePair(
        body: String,
        fileName: String,
    ): List<Pair<String, String>> {
        val lines = body.lines()
        val h1 = lines.indexOfFirst { heading.matchEntire(it)?.groupValues?.get(1) == "#" }
        val title =
            if (h1 >= 0) {
                heading.matchEntire(lines[h1])!!.groupValues[2].trim()
            } else {
                fileName
                    .substringAfterLast('/')
                    .removeSuffix(".md")
                    .removeSuffix(".markdown")
                    .trim()
            }
        val rest = lines.filterIndexed { i, _ -> i != h1 }.joinToString("\n").trim()
        return if (title.isBlank() || rest.isBlank()) emptyList() else listOf(title to rest)
    }
}
