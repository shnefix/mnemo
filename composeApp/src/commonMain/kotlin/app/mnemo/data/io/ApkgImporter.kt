package app.mnemo.data.io

import app.mnemo.data.db.encodeFields
import app.mnemo.data.db.encodeTags
import app.mnemo.data.db.json
import app.mnemo.di.AppGraph
import app.mnemo.domain.cards.ClozeParser
import app.mnemo.domain.model.CardState
import app.mnemo.domain.model.DAY_MS
import app.mnemo.domain.model.DeckSettings
import app.mnemo.domain.model.NoteKind
import app.mnemo.platform.MediaFiles
import app.mnemo.platform.RawSqlite
import app.mnemo.platform.bytes
import app.mnemo.platform.deleteFile
import app.mnemo.platform.long
import app.mnemo.platform.str
import app.mnemo.platform.unzip
import app.mnemo.platform.writeTempFile
import app.mnemo.platform.zstdDecompress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Imports an Anki package (`.apkg`): decks, note types, notes, cards with
 * their scheduling, review history and media. Handles the legacy
 * `collection.anki2` / `anki21` (schema 11, JSON in `col`) and the 2.1.50+
 * `collection.anki21b` (zstd, schema 18 with `notetypes`/`decks` tables).
 *
 * Anki's SM-2 numbers are mapped onto FSRS: stability ≈ interval (recall is
 * 90% at the interval by definition), difficulty from the ease factor.
 */
object ApkgImporter {
    data class Result(
        val decks: Int,
        val notes: Int,
        val cards: Int,
        val reviews: Int,
        val media: Int,
        val skippedNotes: Int,
        val warnings: List<String>,
    )

    private class AnkiModel(
        val id: Long,
        val name: String,
        val cloze: Boolean,
        val fields: List<String>,
        val templates: Int,
    )

    private class AnkiNote(
        val id: Long,
        val guid: String,
        val mid: Long,
        val fields: List<String>,
        val tags: List<String>,
    )

    private class AnkiCard(
        val id: Long,
        val nid: Long,
        val did: Long,
        val ord: Int,
        val type: Long,
        val queue: Long,
        val due: Long,
        val ivl: Long,
        val factor: Long,
        val reps: Long,
        val lapses: Long,
        val flags: Long = 0,
    )

    suspend fun import(
        graph: AppGraph,
        bytes: ByteArray,
        intoDeck: String? = null,
    ): Result =
        withContext(Dispatchers.IO) {
            val warnings = mutableListOf<String>()
            val entries = unzip(bytes)
            val collection =
                when {
                    entries.containsKey("collection.anki21b") -> zstdDecompress(entries.getValue("collection.anki21b"))
                    entries.containsKey("collection.anki21") -> entries.getValue("collection.anki21")
                    entries.containsKey("collection.anki2") -> entries.getValue("collection.anki2")
                    else -> throw IllegalArgumentException("Not an Anki package: no collection file inside")
                }
            val tmp = writeTempFile("mnemo-apkg", ".sqlite", collection)
            try {
                RawSqlite(tmp).use { db -> importCollection(graph, db, entries, intoDeck, warnings) }
            } finally {
                deleteFile(tmp)
            }
        }

    private suspend fun importCollection(
        graph: AppGraph,
        db: RawSqlite,
        entries: Map<String, ByteArray>,
        intoDeck: String?,
        warnings: MutableList<String>,
    ): Result {
        val col = db.query("SELECT * FROM col LIMIT 1").firstOrNull() ?: throw IllegalArgumentException("Collection has no col row")
        val crtMs = col.long("crt") * 1000L
        val now = graph.clock.now()

        // --- decks ---
        val deckNames = LinkedHashMap<Long, String>()
        val decksJson = col.str("decks")
        if (decksJson.length > 2) {
            json.parseToJsonElement(decksJson).jsonObject.forEach { (id, v) ->
                deckNames[id.toLong()] = v.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: "Deck $id"
            }
        } else if (db.tableExists("decks")) {
            db.query("SELECT id, name FROM decks").forEach { r -> deckNames[r.long("id")] = r.str("name").replace("\u001f", "::") }
        }
        deckNames.replaceAll { _, name -> name.replace("\u001f", "::") }

        // --- note types ---
        val models = HashMap<Long, AnkiModel>()
        val modelsJson = col.str("models")
        if (modelsJson.length > 2) {
            json.parseToJsonElement(modelsJson).jsonObject.forEach { (id, v) ->
                val o = v.jsonObject
                models[id.toLong()] =
                    AnkiModel(
                        id = id.toLong(),
                        name = o["name"]?.jsonPrimitive?.contentOrNull ?: "Model",
                        cloze = (o["type"]?.jsonPrimitive?.longOrNull ?: 0L) == 1L,
                        fields =
                            o["flds"]
                                ?.jsonArray
                                ?.sortedBy { it.jsonObject["ord"]?.jsonPrimitive?.longOrNull ?: 0 }
                                ?.map {
                                    it.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                                        ?: "Field"
                                }.orEmpty(),
                        templates = o["tmpls"]?.jsonArray?.size ?: 1,
                    )
            }
        } else if (db.tableExists("notetypes")) {
            val fields = db.query("SELECT ntid, ord, name FROM fields ORDER BY ntid, ord").groupBy { it.long("ntid") }
            val templates = db.query("SELECT ntid, ord FROM templates").groupBy { it.long("ntid") }
            db.query("SELECT id, name, config FROM notetypes").forEach { r ->
                val id = r.long("id")
                val config = r.bytes("config")
                // protobuf: field 1 (kind) varint; 0x08 0x01 == cloze
                val cloze = config != null && config.size >= 2 && config[0] == 0x08.toByte() && config[1] == 0x01.toByte()
                models[id] =
                    AnkiModel(
                        id = id,
                        name = r.str("name"),
                        cloze = cloze,
                        fields = fields[id].orEmpty().map { it.str("name") },
                        templates = templates[id]?.size ?: 1,
                    )
            }
        }

        // --- notes & cards ---
        val notes =
            db.query("SELECT id, guid, mid, tags, flds FROM notes").map { r ->
                AnkiNote(
                    id = r.long("id"),
                    guid = r.str("guid"),
                    mid = r.long("mid"),
                    fields = r.str("flds").split('\u001f'),
                    tags =
                        r
                            .str("tags")
                            .split(' ')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() },
                )
            }
        val cards =
            db
                .query("SELECT id, nid, did, ord, type, queue, due, ivl, factor, reps, lapses, flags FROM cards")
                .map { r ->
                    AnkiCard(
                        r.long("id"),
                        r.long("nid"),
                        r.long("did"),
                        r.long("ord").toInt(),
                        r.long("type"),
                        r.long("queue"),
                        r.long("due"),
                        r.long("ivl"),
                        r.long("factor"),
                        r.long("reps"),
                        r.long("lapses"),
                        r.long("flags") and 7L,
                    )
                }.groupBy { it.nid }
        val revlog =
            if (db.tableExists("revlog")) {
                db.query("SELECT id, cid, ease, ivl, lastIvl, factor, time, type FROM revlog ORDER BY id")
            } else {
                emptyList()
            }

        // --- media ---
        var mediaCount = 0
        entries["media"]?.let { mediaBytes ->
            val mapping = parseMediaMapping(mediaBytes, warnings)
            mapping.forEach { (index, name) ->
                entries[index]?.let {
                    MediaFiles.write(name, it)
                    mediaCount += 1
                }
            }
        }

        // --- write into Mnemo ---
        val mnemoTypes = graph.notes.noteTypes().associateBy { it.kind }
        val existingGuids =
            graph.cards
                .allViews()
                .mapNotNull { it.fields["_guid"] }
                .toHashSet()
        var noteCount = 0
        var cardCount = 0
        var reviewCount = 0
        var skipped = 0
        val deckIds = HashMap<Long, Long>()
        val prefix = intoDeck?.takeIf { it.isNotBlank() }?.let { "$it::" } ?: ""

        suspend fun mnemoDeck(did: Long): Long =
            deckIds.getOrPut(did) {
                val name = deckNames[did] ?: "Imported"
                graph.decks.createByPath(prefix + name, DeckSettings())
            }

        for (note in notes) {
            if (note.guid in existingGuids) {
                skipped += 1
                continue
            }
            val model = models[note.mid]
            val noteCards = cards[note.id].orEmpty().sortedBy { it.ord }
            if (noteCards.isEmpty()) {
                skipped += 1
                continue
            }
            val occlusionField = note.fields.firstOrNull { "image-occlusion:" in it }
            if (occlusionField != null) {
                if (importOcclusion(graph, note, occlusionField, model, noteCards, mnemoTypes, ::mnemoDeck, now, crtMs)) {
                    noteCount += 1
                    cardCount += noteCards.size
                } else {
                    skipped += 1
                }
                continue
            }
            val converted = note.fields.map { AnkiHtml.toMarkdown(it) }
            val isCloze = model?.cloze == true || ClozeParser.indices(converted.firstOrNull().orEmpty()).isNotEmpty()
            val kind =
                when {
                    isCloze -> NoteKind.Cloze
                    (model?.templates ?: 1) >= 2 && noteCards.any { it.ord == 1 } -> NoteKind.Reversed
                    else -> NoteKind.Basic
                }
            val type = mnemoTypes[kind] ?: continue
            val fieldNames = model?.fields ?: converted.indices.map { "Field ${it + 1}" }
            val fields = LinkedHashMap<String, String>()
            if (kind == NoteKind.Cloze) {
                fields["Text"] = converted.firstOrNull().orEmpty()
                fields["Extra"] = converted.drop(1).filter { it.isNotBlank() }.joinToString("\n\n")
            } else {
                fields["Front"] = converted.firstOrNull().orEmpty()
                fields["Back"] =
                    if (converted.size <= 2) {
                        converted.getOrNull(1).orEmpty()
                    } else {
                        converted
                            .drop(1)
                            .mapIndexedNotNull { i, v ->
                                if (v.isBlank()) null else "**${fieldNames.getOrNull(i + 1) ?: "Field"}:** $v"
                            }.joinToString("\n\n")
                    }
            }
            fields["_guid"] = note.guid
            val deckId = mnemoDeck(noteCards.first().did)
            val tags = note.tags.map { it.replace("::", "-") }

            val dbx = graph.db
            val nowMs = now
            dbx.transaction {
                dbx.noteQueries.insert(deckId, type.id, encodeFields(fields), encodeTags(tags), null, nowMs, nowMs)
                val noteId = dbx.noteQueries.lastInsertId().executeAsOne()
                noteCount += 1
                val validOrds =
                    when (kind) {
                        NoteKind.Cloze -> ClozeParser.indices(fields["Text"].orEmpty()).map { it - 1 }.toSet()
                        NoteKind.Reversed -> setOf(0, 1)
                        else -> setOf(0)
                    }
                for (c in noteCards) {
                    if (c.ord !in validOrds) continue
                    dbx.cardQueries.insert(noteId, c.ord.toLong(), nowMs)
                    val cardId = dbx.cardQueries.lastInsertId().executeAsOne()
                    cardCount += 1
                    val s = convertScheduling(c, crtMs, nowMs)
                    dbx.cardQueries.updateScheduling(
                        s.state.code,
                        s.due,
                        s.stability,
                        s.difficulty,
                        s.elapsed,
                        s.scheduled,
                        c.reps,
                        c.lapses,
                        s.lastReview,
                        s.step,
                        cardId,
                    )
                    if (c.queue == -1L) dbx.cardQueries.setSuspended(1L, cardId)
                    if (c.flags != 0L) dbx.cardQueries.setFlag(c.flags, cardId)
                    if (c.queue == -2L || c.queue == -3L) dbx.cardQueries.setBuried(nowMs + DAY_MS, cardId)
                    revlog.filter { it.long("cid") == c.id }.forEach { r ->
                        val type = r.long("type")
                        val stateBefore =
                            when (type) {
                                0L -> CardState.Learning
                                2L -> CardState.Relearning
                                else -> CardState.Review
                            }
                        val lastIvl = r.long("lastIvl")
                        dbx.reviewLogQueries.insert(
                            cardId,
                            r.long("ease").coerceIn(1, 4),
                            stateBefore.code,
                            r.long("id"),
                            if (lastIvl > 0) lastIvl.toDouble() else null,
                            factorToDifficulty(r.long("factor")),
                            0L,
                            if (lastIvl > 0) lastIvl else 0L,
                            0L,
                            0L,
                            null,
                            null,
                            r.long("time"),
                            "classic",
                            null,
                            null,
                            0L,
                            r.long("id"),
                        )
                        reviewCount += 1
                    }
                }
            }
        }
        if (models.isEmpty()) warnings += "No note type information found; fields were mapped by position."
        return Result(
            decks = deckIds.size,
            notes = noteCount,
            cards = cardCount,
            reviews = reviewCount,
            media = mediaCount,
            skippedNotes = skipped,
            warnings = warnings,
        )
    }

    /**
     * An Anki image-occlusion note → a Mnemo one: the boxes, the picture and the header and
     * back text, with each card's scheduling. False when it has no picture or no boxes.
     */
    private suspend fun importOcclusion(
        graph: AppGraph,
        note: AnkiNote,
        occlusionField: String,
        model: AnkiModel?,
        noteCards: List<AnkiCard>,
        types: Map<NoteKind, app.mnemo.domain.model.NoteType>,
        deckOf: suspend (Long) -> Long,
        now: Long,
        crtMs: Long,
    ): Boolean {
        val type = types[NoteKind.Occlusion] ?: return false
        val names = model?.fields.orEmpty()

        fun field(vararg wanted: String): String =
            wanted
                .firstNotNullOfOrNull { w -> names.indexOfFirst { it.equals(w, ignoreCase = true) }.takeIf { it >= 0 } }
                ?.let { note.fields.getOrNull(it) }
                .orEmpty()
        val imageHtml = field("Image").ifEmpty { note.fields.firstOrNull { "<img" in it }.orEmpty() }
        val image = Regex("""<img[^>]+src=["']([^"']+)["']""").find(imageHtml)?.groupValues?.get(1) ?: return false
        val size =
            MediaFiles.read(image)?.let { app.mnemo.platform.decodeImage(it) }?.let { it.width to it.height }
        val occ =
            app.mnemo.domain.cards.Occlusions
                .fromAnki(occlusionField, size)
        if (occ.masks.isEmpty()) return false
        val fields =
            linkedMapOf(
                "Image" to image,
                "Occlusions" to occ.encode(),
                "Header" to AnkiHtml.toMarkdown(field("Header")),
                "Back Extra" to
                    listOf(field("Back Extra"), field("Comments"))
                        .map { AnkiHtml.toMarkdown(it) }
                        .filter { it.isNotBlank() }
                        .joinToString("\n\n"),
                "_guid" to note.guid,
            )
        val noteId =
            graph.notes.create(deckOf(noteCards.first().did), type, fields, note.tags.map { it.replace("::", "-") })
        val cards = graph.cards.byNote(noteId)
        for (c in noteCards) {
            val index = occ.groups.indexOf(c.ord + 1)
            val card = cards.firstOrNull { it.templateIndex == index } ?: continue
            val s = convertScheduling(c, crtMs, now)
            graph.cards.save(
                card.copy(
                    state = s.state,
                    due = s.due,
                    stability = s.stability,
                    difficulty = s.difficulty,
                    elapsedDays = s.elapsed.toInt(),
                    scheduledDays = s.scheduled.toInt(),
                    reps = c.reps.toInt(),
                    lapses = c.lapses.toInt(),
                    lastReview = s.lastReview,
                    learningStep = s.step?.toInt(),
                ),
            )
            if (c.queue == -1L) graph.cards.setSuspended(card.id, true)
            if (c.flags != 0L) graph.cards.setFlag(listOf(card.id), c.flags.toInt())
        }
        return true
    }

    private class Sched(
        val state: CardState,
        val due: Long,
        val stability: Double?,
        val difficulty: Double?,
        val elapsed: Long,
        val scheduled: Long,
        val lastReview: Long?,
        val step: Long?,
    )

    /** Anki card → Mnemo/FSRS state. */
    private fun convertScheduling(
        c: AnkiCard,
        crtMs: Long,
        now: Long,
    ): Sched =
        when (c.type) {
            2L -> {
                val ivl = c.ivl.coerceAtLeast(1)
                val due = if (c.queue == 2L) crtMs + c.due * DAY_MS else now
                Sched(
                    state = CardState.Review,
                    due = due,
                    stability = ivl.toDouble(),
                    difficulty = factorToDifficulty(c.factor),
                    elapsed = 0,
                    scheduled = ivl,
                    lastReview = due - ivl * DAY_MS,
                    step = null,
                )
            }
            1L, 3L ->
                Sched(
                    state = if (c.type == 1L) CardState.Learning else CardState.Relearning,
                    due = now,
                    stability = if (c.ivl > 0) c.ivl.toDouble() else 2.3065,
                    difficulty = factorToDifficulty(c.factor),
                    elapsed = 0,
                    scheduled = 0,
                    lastReview = now - DAY_MS,
                    step = 0,
                )
            else -> Sched(CardState.New, now, null, null, 0, 0, null, null)
        }

    /** Ease factor (permille, 1300..∞, default 2500) → FSRS difficulty 1..10. */
    private fun factorToDifficulty(factor: Long): Double {
        if (factor <= 0) return 5.0
        val ease = factor / 1000.0
        // 1.3 → 10, 2.5 → 5, 3.7 → 1
        return (10.0 - (ease - 1.3) / 1.2 * 5.0).coerceIn(1.0, 10.0)
    }

    /** `media` is JSON `{"0":"name"}` in legacy packages, a zstd protobuf list in new ones. */
    private fun parseMediaMapping(
        bytes: ByteArray,
        warnings: MutableList<String>,
    ): Map<String, String> {
        val text = runCatching { bytes.decodeToString() }.getOrNull()
        if (text != null && text.trimStart().startsWith("{")) {
            return runCatching {
                json.parseToJsonElement(text).jsonObject.mapValues { (_, v) -> v.jsonPrimitive.content }
            }.getOrElse {
                warnings += "Media list could not be read"
                emptyMap()
            }
        }
        // zstd-compressed protobuf: MediaEntries { repeated MediaEntry entries = 1 }; MediaEntry { string name = 1; ... }
        return runCatching {
            val raw = zstdDecompress(bytes)
            val out = LinkedHashMap<String, String>()
            var pos = 0
            var index = 0

            fun varint(): Long {
                var shift = 0
                var result = 0L
                while (true) {
                    val b = raw[pos++].toInt() and 0xFF
                    result = result or ((b and 0x7F).toLong() shl shift)
                    if (b and 0x80 == 0) return result
                    shift += 7
                }
            }
            while (pos < raw.size) {
                val tag = varint()
                val wire = (tag and 7).toInt()
                if (wire != 2) break
                val len = varint().toInt()
                val entry = raw.copyOfRange(pos, pos + len)
                pos += len
                // inside entry: field 1 string name
                var p = 0
                var name: String? = null
                while (p < entry.size) {
                    val t = entry[p++].toInt() and 0xFF
                    val w = t and 7
                    val f = t shr 3
                    when (w) {
                        0 -> while (entry[p++].toInt() and 0x80 != 0) { /* skip varint */ }
                        2 -> {
                            var l = 0
                            var sh = 0
                            while (true) {
                                val b = entry[p++].toInt() and 0xFF
                                l = l or ((b and 0x7F) shl sh)
                                if (b and 0x80 == 0) break
                                sh += 7
                            }
                            if (f == 1) name = entry.copyOfRange(p, p + l).decodeToString()
                            p += l
                        }
                        else -> p = entry.size
                    }
                }
                if (name != null) out[index.toString()] = name
                index += 1
            }
            out
        }.getOrElse {
            warnings += "Media list could not be read (unsupported format)"
            emptyMap()
        }
    }
}
