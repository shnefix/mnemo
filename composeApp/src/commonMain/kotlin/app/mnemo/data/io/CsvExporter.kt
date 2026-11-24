package app.mnemo.data.io

import app.mnemo.di.AppGraph
import app.mnemo.domain.model.NoteKind

/**
 * Notes as `front,back,tags` CSV, the same shape [CsvImporter] reads (a cloze note's text goes
 * in the front). Image-occlusion notes are left out: a picture doesn't fit in a cell.
 */
object CsvExporter {
    data class Result(
        val text: String,
        val notes: Int,
        val skipped: Int,
    )

    suspend fun export(
        graph: AppGraph,
        deckId: Long?,
    ): Result {
        val decks = graph.decks.all()
        val ids =
            if (deckId == null) {
                decks.map { it.id }
            } else {
                val children = decks.groupBy { it.parentId }
                val out = mutableListOf<Long>()

                fun walk(id: Long) {
                    out += id
                    children[id].orEmpty().forEach { walk(it.id) }
                }
                walk(deckId)
                out
            }
        val types = graph.notes.noteTypes().associateBy { it.id }
        val sb = StringBuilder("front,back,tags\n")
        var n = 0
        var skipped = 0
        ids.flatMap { graph.notes.byDeck(it) }.sortedBy { it.createdAt }.forEach { note ->
            val type = types[note.noteTypeId]
            val (front, back) =
                when (type?.kind) {
                    NoteKind.Occlusion -> {
                        skipped += 1
                        return@forEach
                    }
                    NoteKind.Cloze -> note.fields["Text"].orEmpty() to note.fields["Extra"].orEmpty()
                    NoteKind.Vocab -> note.fields["Word"].orEmpty() to note.fields["Translation"].orEmpty()
                    null ->
                        note.fields.values
                            .firstOrNull()
                            .orEmpty() to
                            note.fields.values
                                .drop(1)
                                .firstOrNull()
                                .orEmpty()
                    else -> {
                        val names = type.fields
                        note.fields[names.getOrNull(0)].orEmpty() to note.fields[names.getOrNull(1)].orEmpty()
                    }
                }
            sb
                .append(cell(front))
                .append(',')
                .append(cell(back))
                .append(',')
                .append(cell(note.tags.joinToString(" ")))
                .append('\n')
            n += 1
        }
        return Result(sb.toString(), n, skipped)
    }

    private fun cell(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + s.replace("\"", "\"\"") +
                "\""
        } else {
            s
        }
}
