package app.mnemo.data.io

import app.mnemo.di.AppGraph
import app.mnemo.domain.cards.ClozeParser
import app.mnemo.domain.model.NoteKind

/**
 * Imports `front,back[,tags]` CSV (comma, semicolon or tab separated, quotes
 * allowed). A row whose front contains `{{c1::` becomes a Cloze note.
 */
object CsvImporter {
    data class Result(
        val notes: Int,
        val cards: Int,
        val skipped: Int,
    )

    data class Row(
        val front: String,
        val back: String,
        val tags: List<String>,
    )

    fun parse(text: String): List<Row> {
        val lines = splitRecords(text)
        if (lines.isEmpty()) return emptyList()
        val sep = detectSeparator(lines.first())
        val rows = lines.map { parseRecord(it, sep) }.filter { it.any { cell -> cell.isNotBlank() } }
        if (rows.isEmpty()) return emptyList()
        // Skip a header row like "front,back"
        val body =
            if (rows
                    .first()
                    .getOrNull(0)
                    ?.trim()
                    ?.lowercase() in setOf("front", "question", "text") &&
                rows
                    .first()
                    .getOrNull(1)
                    ?.trim()
                    ?.lowercase() in setOf("back", "answer", "extra")
            ) {
                rows.drop(1)
            } else {
                rows
            }
        return body.mapNotNull { cells ->
            val front = cells.getOrNull(0)?.trim().orEmpty()
            if (front.isEmpty()) return@mapNotNull null
            val back = cells.getOrNull(1)?.trim().orEmpty()
            val tags =
                cells
                    .getOrNull(2)
                    ?.split(' ', ',')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
            Row(front, back, tags)
        }
    }

    suspend fun import(
        graph: AppGraph,
        deckId: Long,
        text: String,
        extraTags: List<String> = emptyList(),
    ): Result {
        val rows = parse(text)
        val basic = graph.notes.noteTypeOf(NoteKind.Basic)
        val cloze = graph.notes.noteTypeOf(NoteKind.Cloze)
        var notes = 0
        var cards = 0
        var skipped = 0
        for (row in rows) {
            val isCloze = ClozeParser.indices(row.front).isNotEmpty()
            if (!isCloze && row.back.isBlank()) {
                skipped += 1
                continue
            }
            val tags = (row.tags + extraTags).distinct()
            if (isCloze) {
                graph.notes.create(deckId, cloze, mapOf("Text" to row.front, "Extra" to row.back), tags)
                cards += ClozeParser.indices(row.front).size
            } else {
                graph.notes.create(deckId, basic, mapOf("Front" to row.front, "Back" to row.back), tags)
                cards += 1
            }
            notes += 1
        }
        return Result(notes, cards, skipped)
    }

    private fun detectSeparator(firstLine: String): Char {
        val counts = listOf('\t', ';', ',').associateWith { c -> firstLine.count { it == c } }
        return counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
    }

    /** Splits into records, keeping newlines inside quoted cells. */
    private fun splitRecords(text: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                ch == '"' -> {
                    inQuotes = !inQuotes
                    sb.append(ch)
                }
                (ch == '\n' || ch == '\r') && !inQuotes -> {
                    if (sb.isNotBlank()) out += sb.toString()
                    sb.clear()
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                }
                else -> sb.append(ch)
            }
            i++
        }
        if (sb.isNotBlank()) out += sb.toString()
        return out
    }

    private fun parseRecord(
        line: String,
        sep: Char,
    ): List<String> {
        val cells = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"')
                    i++
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == sep && !inQuotes -> {
                    cells += sb.toString()
                    sb.clear()
                }
                else -> sb.append(ch)
            }
            i++
        }
        cells += sb.toString()
        return cells
    }
}
