package app.mnemo.data.io

import app.mnemo.di.AppGraph
import app.mnemo.domain.cards.Occlusions
import app.mnemo.domain.model.Card
import app.mnemo.domain.model.CardState
import app.mnemo.domain.model.DAY_MS
import app.mnemo.domain.model.Deck
import app.mnemo.domain.model.Note
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.NoteType
import app.mnemo.domain.model.ReviewLog
import app.mnemo.domain.stats.startOfLocalDay
import app.mnemo.domain.stats.startOfLocalDayPlus
import app.mnemo.platform.MediaFiles
import app.mnemo.platform.SqliteWriter
import app.mnemo.platform.deleteFile
import app.mnemo.platform.readFileBytes
import app.mnemo.platform.sha1
import app.mnemo.platform.writeTempFile
import app.mnemo.platform.zip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.math.roundToInt

/**
 * Writes decks as an Anki package (`.apkg`, the `collection.anki2` format every Anki version
 * imports): decks, note types with their templates, notes (Markdown turned into HTML), cards
 * with their scheduling, flags and FSRS memory state, the review history and the media the
 * notes use. Image-occlusion notes become Anki image-occlusion notes.
 */
object ApkgExporter {
    data class Result(
        val bytes: ByteArray,
        val notes: Int,
        val cards: Int,
        val reviews: Int,
        val media: Int,
    )

    /** How far back the exported collection "started": review due days count from here. */
    private const val CRT_DAYS_BACK = 3650

    /** Offsets that keep exported ids clear of Anki's own (deck 1 is "Default"). */
    private const val DECK_ID_BASE = 1_700_000_000_000L
    private const val MODEL_ID_BASE = 1_600_000_000_000L

    suspend fun export(
        graph: AppGraph,
        deckId: Long?,
        includeScheduling: Boolean = true,
    ): Result =
        withContext(Dispatchers.IO) {
            val now = graph.clock.now()
            val allDecks = graph.decks.all()
            val decks = subtree(allDecks, deckId)
            val rootPrefix =
                deckId?.let { id -> allDecks.firstOrNull { it.id == id } }?.path?.let { path ->
                    path.substringBeforeLast("::", "").let { if (it.isEmpty()) "" else "$it::" }
                } ?: ""
            val types = graph.notes.noteTypes().associateBy { it.id }
            val notes = decks.flatMap { graph.notes.byDeck(it.id) }.sortedBy { it.createdAt }
            val cardsByNote = graph.cards.all().groupBy { it.noteId }
            val logsByCard = if (includeScheduling) graph.reviewLogs.since(0).groupBy { it.cardId } else emptyMap()

            val path = writeTempFile("mnemo-export", ".anki2", ByteArray(0))
            deleteFile(path)
            val media = LinkedHashSet<String>()
            var cardCount = 0
            var reviewCount = 0
            try {
                SqliteWriter(path).use { db ->
                    db.transaction {
                        SCHEMA.forEach { db.exec(it) }
                        val crtSec = startOfLocalDayPlus(now, -CRT_DAYS_BACK) / 1000
                        val todayStart = startOfLocalDay(now)
                        val usedTypes = notes.mapNotNull { types[it.noteTypeId] }.distinctBy { it.id }
                        val firstDeck = decks.firstOrNull()?.let { DECK_ID_BASE + it.id } ?: 1L
                        db.exec(
                            "INSERT INTO col VALUES (1, ?, ?, ?, 11, 0, 0, 0, ?, ?, ?, ?, '{}')",
                            crtSec,
                            now,
                            now,
                            conf(usedTypes.firstOrNull()?.let { modelId(it) } ?: 0L).toString(),
                            JsonObject(usedTypes.associate { modelId(it).toString() to modelJson(it, firstDeck, now) }).toString(),
                            decksJson(decks, rootPrefix, now).toString(),
                            DCONF,
                        )

                        val ids = UniqueIds()
                        var newPosition = 0L
                        val newOrder =
                            notes
                                .flatMap { cardsByNote[it.id].orEmpty() }
                                .filter { it.state == CardState.New || !includeScheduling }
                                .sortedWith(compareBy({ it.due }, { it.id }))
                                .associate { it.id to newPosition++ }
                        for (note in notes) {
                            val type = types[note.noteTypeId] ?: continue
                            val (fieldValues, cardOrd) = ankiFields(type, note.fields)
                            fieldValues.forEach { media += mediaIn(it) }
                            val noteId = ids.next(note.createdAt)
                            val first = MarkdownHtml.plain(fieldValues.firstOrNull().orEmpty())
                            db.exec(
                                "INSERT INTO notes VALUES (?, ?, ?, ?, 0, ?, ?, ?, ?, 0, '')",
                                noteId,
                                note.fields["_guid"] ?: guidOf(note),
                                modelId(type),
                                note.updatedAt / 1000,
                                if (note.tags.isEmpty()) "" else note.tags.joinToString(" ", " ", " "),
                                fieldValues.joinToString("\u001f"),
                                first,
                                checksum(first),
                            )
                            val deck = DECK_ID_BASE + note.deckId
                            for (card in cardsByNote[note.id].orEmpty().sortedBy { it.templateIndex }) {
                                val ord = cardOrd(card.templateIndex) ?: continue
                                val cardId = ids.next(note.createdAt + card.templateIndex + 1)
                                val s =
                                    if (includeScheduling && card.state != CardState.New) {
                                        scheduling(card, now, todayStart)
                                    } else {
                                        AnkiSched(0, 0, newOrder[card.id] ?: 0, 0, 0)
                                    }
                                val queue =
                                    when {
                                        !includeScheduling -> s.queue
                                        card.suspended -> -1
                                        (card.buriedUntil ?: 0) > now -> -3
                                        else -> s.queue
                                    }
                                val data =
                                    if (includeScheduling && card.stability != null && card.difficulty != null) {
                                        buildJsonObject {
                                            put("s", round4(card.stability))
                                            put("d", round4(card.difficulty))
                                        }.toString()
                                    } else {
                                        ""
                                    }
                                db.exec(
                                    "INSERT INTO cards VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?)",
                                    cardId,
                                    noteId,
                                    deck,
                                    ord.toLong(),
                                    now / 1000,
                                    s.type,
                                    queue,
                                    s.due,
                                    s.ivl,
                                    if (s.type == 0L) 0L else 2500L,
                                    if (includeScheduling) card.reps.toLong() else 0L,
                                    if (includeScheduling) card.lapses.toLong() else 0L,
                                    if (s.type == 1L || s.type == 3L) 1001L else 0L,
                                    if (includeScheduling) card.flag.toLong() else 0L,
                                    data,
                                )
                                cardCount += 1
                                val logs = logsByCard[card.id].orEmpty().sortedBy { it.reviewedAt }
                                logs.forEachIndexed { i, log ->
                                    db.exec(
                                        "INSERT INTO revlog VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?)",
                                        ids.next(log.reviewedAt),
                                        cardId,
                                        log.rating.code,
                                        intervalAfter(logs, i, card),
                                        log.before.scheduledDays.toLong(),
                                        if (log.before.state == CardState.New) 0L else 2500L,
                                        log.responseMs.coerceIn(0, 60_000),
                                        revlogType(log),
                                    )
                                    reviewCount += 1
                                }
                            }
                        }
                    }
                }
                val entries = mutableListOf<Pair<String, ByteArray>>("collection.anki2" to readFileBytes(path))
                val mediaMap = LinkedHashMap<String, String>()
                media.filter { MediaFiles.exists(it) }.forEachIndexed { i, name ->
                    MediaFiles.read(name)?.let {
                        mediaMap[i.toString()] = name
                        entries += i.toString() to it
                    }
                }
                entries += "media" to JsonObject(mediaMap.mapValues { JsonPrimitive(it.value) }).toString().encodeToByteArray()
                Result(zip(entries), notes.size, cardCount, reviewCount, mediaMap.size)
            } finally {
                deleteFile(path)
            }
        }

    private fun subtree(
        all: List<Deck>,
        deckId: Long?,
    ): List<Deck> {
        if (deckId == null) return all
        val children = all.groupBy { it.parentId }
        val out = mutableListOf<Deck>()

        fun walk(d: Deck) {
            out += d
            children[d.id].orEmpty().forEach { walk(it) }
        }
        all.firstOrNull { it.id == deckId }?.let { walk(it) }
        return out
    }

    private fun modelId(t: NoteType): Long = MODEL_ID_BASE + t.id

    /** Anki field values in the model's field order, and card ord for a Mnemo template index (null = skip). */
    private fun ankiFields(
        type: NoteType,
        fields: Map<String, String>,
    ): Pair<List<String>, (Int) -> Int?> {
        fun html(name: String) = MarkdownHtml.toHtml(fields[name].orEmpty())
        return when (type.kind) {
            NoteKind.Cloze -> listOf(html("Text"), html("Extra")) to { i: Int -> i }
            NoteKind.Occlusion -> {
                val occ = Occlusions.parse(fields["Occlusions"])
                val image = fields["Image"].orEmpty().trim()
                listOf(
                    Occlusions.toAnki(occ),
                    if (image.isEmpty()) "" else "<img src=\"$image\">",
                    html("Header"),
                    html("Back Extra"),
                    "",
                ) to { i: Int -> occ.groups.getOrNull(i)?.let { it - 1 } }
            }
            else -> type.fields.map { html(it) } to { i: Int -> i.takeIf { it < type.templates.size.coerceAtLeast(1) } }
        }
    }

    private fun modelJson(
        t: NoteType,
        deck: Long,
        now: Long,
    ): JsonObject {
        val cloze = t.kind == NoteKind.Cloze || t.kind == NoteKind.Occlusion
        val fields: List<String>
        val templates: List<Triple<String, String, String>>
        when (t.kind) {
            NoteKind.Cloze -> {
                fields = listOf("Text", "Back Extra")
                templates = listOf(Triple("Cloze", "{{cloze:Text}}", "{{cloze:Text}}<br>\n{{Back Extra}}"))
            }
            NoteKind.Occlusion -> {
                fields = listOf("Occlusion", "Image", "Header", "Back Extra", "Comments")
                templates = listOf(Triple("Image Occlusion", IO_FRONT, IO_BACK))
            }
            NoteKind.TypeAnswer -> {
                fields = t.fields
                templates = listOf(Triple("Card 1", "{{Front}}\n\n{{type:Back}}", "{{Front}}\n\n<hr id=answer>\n\n{{type:Back}}"))
            }
            NoteKind.Vocab -> {
                fields = t.fields
                val details =
                    "{{#Gender}}<br>{{Gender}}{{/Gender}}{{#Plural}}<br>Plural: {{Plural}}{{/Plural}}{{#IPA}}<br>{{IPA}}{{/IPA}}" +
                        "{{#Example}}<br><i>{{Example}}</i>{{/Example}}{{#Example translation}}<br>{{Example translation}}{{/Example translation}}"
                templates =
                    listOf(
                        Triple(
                            t.templates.getOrNull(0)?.name ?: "Card 1",
                            "{{Word}}",
                            "{{FrontSide}}\n\n<hr id=answer>\n\n<b>{{Translation}}</b>$details",
                        ),
                        Triple(
                            t.templates.getOrNull(1)?.name ?: "Card 2",
                            "{{Translation}}",
                            "{{FrontSide}}\n\n<hr id=answer>\n\n<b>{{Word}}</b>$details",
                        ),
                    )
            }
            else -> {
                fields = t.fields
                templates =
                    t.templates.map { tmpl ->
                        val back = MarkdownHtml.toHtml(tmpl.back)
                        Triple(
                            tmpl.name,
                            MarkdownHtml.toHtml(tmpl.front),
                            if ("{{FrontSide}}" in tmpl.back) back else "{{FrontSide}}\n\n<hr id=answer>\n\n$back",
                        )
                    }
            }
        }
        return buildJsonObject {
            put("id", modelId(t))
            put("name", if (t.kind == NoteKind.Occlusion) "Image Occlusion" else t.name)
            put("type", if (cloze) 1 else 0)
            if (t.kind == NoteKind.Occlusion) put("originalStockKind", 6)
            put("mod", now / 1000)
            put("usn", 0)
            put("sortf", if (t.kind == NoteKind.Occlusion) 2 else 0)
            put("did", deck)
            putJsonArray("tmpls") {
                templates.forEachIndexed { i, (name, q, a) ->
                    add(
                        buildJsonObject {
                            put("name", name)
                            put("ord", i)
                            put("qfmt", q)
                            put("afmt", a)
                            put("bqfmt", "")
                            put("bafmt", "")
                            put("did", JsonNull)
                            put("bfont", "")
                            put("bsize", 0)
                        },
                    )
                }
            }
            putJsonArray("flds") {
                fields.forEachIndexed { i, name ->
                    add(
                        buildJsonObject {
                            put("name", name)
                            put("ord", i)
                            put("sticky", false)
                            put("rtl", false)
                            put("font", "Arial")
                            put("size", 20)
                            putJsonArray("media") {}
                        },
                    )
                }
            }
            put("css", CSS)
            put("latexPre", LATEX_PRE)
            put("latexPost", "\\end{document}")
            put("latexsvg", false)
            if (!cloze) {
                putJsonArray("req") {
                    templates.indices.forEach { i ->
                        addJsonArray {
                            add(i)
                            add("any")
                            addJsonArray { add(0) }
                        }
                    }
                }
            }
            putJsonArray("tags") {}
            putJsonArray("vers") {}
        }
    }

    private fun decksJson(
        decks: List<Deck>,
        prefix: String,
        now: Long,
    ): JsonObject {
        fun deck(
            id: Long,
            name: String,
        ) = buildJsonObject {
            put("id", id)
            put("name", name)
            put("mod", now / 1000)
            put("usn", 0)
            listOf("lrnToday", "revToday", "newToday", "timeToday").forEach { k ->
                putJsonArray(k) {
                    add(0)
                    add(0)
                }
            }
            put("collapsed", false)
            put("browserCollapsed", false)
            put("desc", "")
            put("dyn", 0)
            put("conf", 1)
            put("extendNew", 0)
            put("extendRev", 0)
        }
        val map = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
        map["1"] = deck(1, "Default")
        decks.forEach { d -> map[(DECK_ID_BASE + d.id).toString()] = deck(DECK_ID_BASE + d.id, d.path.removePrefix(prefix)) }
        return JsonObject(map)
    }

    private fun conf(currentModel: Long) =
        buildJsonObject {
            put("nextPos", 1)
            put("estTimes", true)
            putJsonArray("activeDecks") { add(1) }
            put("sortType", "noteFld")
            put("timeLim", 0)
            put("sortBackwards", false)
            put("addToCur", true)
            put("curDeck", 1)
            put("newSpread", 0)
            put("dueCounts", true)
            put("curModel", currentModel)
            put("collapseTime", 1200)
        }

    private class AnkiSched(
        val type: Long,
        val queue: Long,
        val due: Long,
        val ivl: Long,
        val left: Long,
    )

    /** Anki card type/queue/due/ivl for a scheduled Mnemo card. */
    private fun scheduling(
        card: Card,
        now: Long,
        todayStart: Long,
    ): AnkiSched =
        when (card.state) {
            CardState.Learning -> AnkiSched(1, 1, card.due / 1000, 0, 1001)
            CardState.Relearning -> AnkiSched(3, 1, card.due / 1000, card.scheduledDays.toLong().coerceAtLeast(1), 1001)
            else -> {
                val dayOffset = kotlin.math.floor((card.due - todayStart).toDouble() / DAY_MS).toLong()
                AnkiSched(2, 2, CRT_DAYS_BACK + dayOffset, card.scheduledDays.toLong().coerceAtLeast(1), 0)
            }
        }

    /** The interval a review gave: the next review's "before" interval, or the card's current one. */
    private fun intervalAfter(
        logs: List<ReviewLog>,
        i: Int,
        card: Card,
    ): Long {
        val next = logs.getOrNull(i + 1)
        val days = next?.before?.scheduledDays ?: card.scheduledDays
        val learning = (next?.before?.state ?: card.state).let { it == CardState.Learning || it == CardState.Relearning }
        return if (learning && days == 0) -600L else days.toLong()
    }

    private fun revlogType(log: ReviewLog): Long =
        when (log.before.state) {
            CardState.New, CardState.Learning -> 0L
            CardState.Review -> 1L
            CardState.Relearning -> 2L
        }

    private fun guidOf(note: Note): String = "mnemo-" + note.id.toString(36) + "-" + note.createdAt.toString(36)

    /** Anki's note checksum: the first 8 hex digits of the SHA-1 of the first field's text. */
    private fun checksum(text: String): Long {
        val d = sha1(text.encodeToByteArray())
        var v = 0L
        for (i in 0 until 4) v = (v shl 8) or (d[i].toLong() and 0xff)
        return v
    }

    private val mediaRef = Regex("""!\[[^\]]*]\(([^)\s]+)\)|<img[^>]+src="([^"]+)"|\[sound:([^\]]+)]""")

    private fun mediaIn(html: String): List<String> =
        mediaRef
            .findAll(html)
            .map { m -> m.groupValues.drop(1).first { it.isNotEmpty() } }
            .filter { "://" !in it }
            .toList()

    private fun round4(d: Double): Double = (d * 10_000).roundToInt() / 10_000.0

    /** Millisecond ids that never repeat within one export. */
    private class UniqueIds {
        private val used = HashSet<Long>()

        fun next(want: Long): Long {
            var id = want
            while (!used.add(id)) id += 1
            return id
        }
    }

    private val SCHEMA =
        listOf(
            "CREATE TABLE col (id integer primary key, crt integer not null, mod integer not null, scm " +
                "integer not null, ver integer not null, dty integer not null, usn integer not null, ls integer " +
                "not null, conf text not null, models text not null, decks text not null, " +
                "dconf text not null, tags text not null)",
            "CREATE TABLE notes (id integer primary key, guid text not null, mid integer not null, mod " +
                "integer not null, usn integer not null, " +
                "tags text not null, flds text not null, sfld integer not null, csum integer not null, flags integer not null, " +
                "data text not null)",
            "CREATE TABLE cards (id integer primary key, nid integer not null, did integer not null, ord " +
                "integer not null, mod integer not null, usn integer not null, type integer not null, queue " +
                "integer not null, due integer not null, ivl integer not null, factor integer not null, reps " +
                "integer not null, lapses integer not null, left integer not null, odue integer not null, " +
                "odid integer not null, flags integer not null, data text not null)",
            "CREATE TABLE revlog (id integer primary key, cid integer not null, usn integer not null, ease " +
                "integer not null, ivl integer not null, " +
                "lastIvl integer not null, factor integer not null, time integer not null, type integer not null)",
            "CREATE TABLE graves (usn integer not null, oid integer not null, type integer not null)",
            "CREATE INDEX ix_notes_usn on notes (usn)",
            "CREATE INDEX ix_cards_usn on cards (usn)",
            "CREATE INDEX ix_revlog_usn on revlog (usn)",
            "CREATE INDEX ix_cards_nid on cards (nid)",
            "CREATE INDEX ix_cards_sched on cards (did, queue, due)",
            "CREATE INDEX ix_revlog_cid on revlog (cid)",
            "CREATE INDEX ix_notes_csum on notes (csum)",
        )

    private val DCONF =
        """{"1":{"id":1,"name":"Default","mod":0,"usn":0,"maxTaken":60,"autoplay":true,"timer":0,"replayq":true,"dyn":false,""" +
            """"new":{"bury":false,"delays":[1.0,10.0],"initialFactor":2500,"ints":[1,4,0],"order":1,"perDay":20},""" +
            """"rev":{"bury":false,"ease4":1.3,"ivlFct":1.0,"maxIvl":36500,"perDay":200,"hardFactor":1.2},""" +
            """"lapse":{"delays":[10.0],"leechAction":1,"leechFails":8,"minInt":1,"mult":0.0}}}"""

    private const val CSS =
        ".card {\n font-family: arial;\n font-size: 20px;\n text-align: center;\n color: black;\n background-color: white;\n}\n"

    private const val LATEX_PRE =
        "\\documentclass[12pt]{article}\n\\special{papersize=3in,5in}\n\\usepackage[utf8]{inputenc}\n" +
            "\\usepackage{amssymb,amsmath}\n\\pagestyle{empty}\n\\setlength{\\parindent}{0in}\n\\begin{document}\n"

    private const val IO_SETUP =
        "<div style=\"display: none\">{{cloze:Occlusion}}</div>\n<div id=\"err\"></div>\n" +
            "<div id=\"image-occlusion-container\">\n    {{Image}}\n    <canvas id=\"image-occlusion-canvas\"></canvas>\n</div>\n" +
            "<script>\ntry {\n    anki.imageOcclusion.setup();\n} catch (exc) {\n" +
            "    document.getElementById(\"err\").innerHTML = `Error loading image occlusion. Is your Anki version up to date?<br><br>\${exc}`;\n}\n</script>\n"

    private const val IO_FRONT = "{{#Header}}<div>{{Header}}</div>{{/Header}}\n$IO_SETUP"

    private const val IO_BACK =
        "$IO_FRONT\n<div><button id=\"toggle\">Toggle Masks</button></div>\n{{#Back Extra}}<div>{{Back Extra}}</div>{{/Back Extra}}"
}
