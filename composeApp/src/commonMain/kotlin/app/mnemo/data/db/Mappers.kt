package app.mnemo.data.db

import app.mnemo.domain.model.Card
import app.mnemo.domain.model.CardState
import app.mnemo.domain.model.CardTemplate
import app.mnemo.domain.model.CardView
import app.mnemo.domain.model.Concept
import app.mnemo.domain.model.ConceptEdge
import app.mnemo.domain.model.Deck
import app.mnemo.domain.model.DeckSettings
import app.mnemo.domain.model.Note
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.NoteType
import app.mnemo.domain.model.Rating
import app.mnemo.domain.model.Relation
import app.mnemo.domain.model.ReviewLog
import app.mnemo.domain.model.ReviewMode
import kotlinx.serialization.json.Json
import app.mnemo.db.AllCardViews as DbAllCardView
import app.mnemo.db.Card as DbCard
import app.mnemo.db.CardView as DbCardView
import app.mnemo.db.CardViewsByIds as DbCardViewsByIds
import app.mnemo.db.Concept as DbConcept
import app.mnemo.db.Concept_edge as DbConceptEdge
import app.mnemo.db.Deck as DbDeck
import app.mnemo.db.Note as DbNote
import app.mnemo.db.Note_type as DbNoteType
import app.mnemo.db.Review_log as DbReviewLog

val json: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

/** Tags are stored as " a b " so `LIKE '% tag %'` works and splitting is trivial. */
fun encodeTags(tags: List<String>): String =
    if (tags.isEmpty()) {
        ""
    } else {
        tags
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" ", " ", " ")
    }

fun decodeTags(raw: String): List<String> = raw.split(' ').map { it.trim() }.filter { it.isNotEmpty() }

fun encodeFields(fields: Map<String, String>): String = json.encodeToString(fields)

fun decodeFields(raw: String): Map<String, String> =
    runCatching {
        json.decodeFromString<Map<String, String>>(raw)
    }.getOrDefault(emptyMap())

fun DbDeck.toDomain(): Deck =
    Deck(
        id = id,
        parentId = parent_id,
        name = name,
        settings = runCatching { json.decodeFromString<DeckSettings>(settings_json) }.getOrDefault(DeckSettings()),
        createdAt = created_at,
    )

fun DbNoteType.toDomain(): NoteType =
    NoteType(
        id = id,
        name = name,
        kind = NoteKind.fromCode(kind),
        fields = json.decodeFromString(fields_json),
        templates = json.decodeFromString(templates_json),
    )

fun DbNote.toDomain(): Note =
    Note(
        id = id,
        deckId = deck_id,
        noteTypeId = note_type_id,
        fields = decodeFields(fields_json),
        tags = decodeTags(tags),
        sourceId = source_id,
        createdAt = created_at,
        updatedAt = updated_at,
    )

fun DbCard.toDomain(): Card =
    Card(
        id = id,
        noteId = note_id,
        templateIndex = template_index.toInt(),
        state = CardState.fromCode(state),
        due = due,
        stability = stability,
        difficulty = difficulty,
        elapsedDays = elapsed_days.toInt(),
        scheduledDays = scheduled_days.toInt(),
        reps = reps.toInt(),
        lapses = lapses.toInt(),
        lastReview = last_review,
        suspended = suspended != 0L,
        buriedUntil = buried_until,
        learningStep = learning_step?.toInt(),
        flag = flag.toInt(),
    )

private fun cardOf(
    id: Long,
    noteId: Long,
    templateIndex: Long,
    state: Long,
    due: Long,
    stability: Double?,
    difficulty: Double?,
    elapsedDays: Long,
    scheduledDays: Long,
    reps: Long,
    lapses: Long,
    lastReview: Long?,
    suspended: Long,
    buriedUntil: Long?,
    learningStep: Long?,
    flag: Long,
) = Card(
    id = id,
    noteId = noteId,
    templateIndex = templateIndex.toInt(),
    state = CardState.fromCode(state),
    due = due,
    stability = stability,
    difficulty = difficulty,
    elapsedDays = elapsedDays.toInt(),
    scheduledDays = scheduledDays.toInt(),
    reps = reps.toInt(),
    lapses = lapses.toInt(),
    lastReview = lastReview,
    suspended = suspended != 0L,
    buriedUntil = buriedUntil,
    learningStep = learningStep?.toInt(),
    flag = flag.toInt(),
)

fun DbCardView.toDomain(): CardView =
    CardView(
        card =
            cardOf(
                id,
                note_id,
                template_index,
                state,
                due,
                stability,
                difficulty,
                elapsed_days,
                scheduled_days,
                reps,
                lapses,
                last_review,
                suspended,
                buried_until,
                learning_step,
                flag,
            ),
        deckId = deck_id,
        deckName = deck_name,
        noteTypeId = note_type_id,
        noteKind = NoteKind.fromCode(note_kind),
        fields = decodeFields(fields_json),
        tags = decodeTags(tags),
        templates = json.decodeFromString<List<CardTemplate>>(templates_json),
    )

fun DbAllCardView.toDomain(): CardView =
    CardView(
        card =
            cardOf(
                id,
                note_id,
                template_index,
                state,
                due,
                stability,
                difficulty,
                elapsed_days,
                scheduled_days,
                reps,
                lapses,
                last_review,
                suspended,
                buried_until,
                learning_step,
                flag,
            ),
        deckId = deck_id,
        deckName = deck_name,
        noteTypeId = note_type_id,
        noteKind = NoteKind.fromCode(note_kind),
        fields = decodeFields(fields_json),
        tags = decodeTags(tags),
        templates = json.decodeFromString<List<CardTemplate>>(templates_json),
    )

fun DbCardViewsByIds.toDomain(): CardView =
    CardView(
        card =
            cardOf(
                id,
                note_id,
                template_index,
                state,
                due,
                stability,
                difficulty,
                elapsed_days,
                scheduled_days,
                reps,
                lapses,
                last_review,
                suspended,
                buried_until,
                learning_step,
                flag,
            ),
        deckId = deck_id,
        deckName = deck_name,
        noteTypeId = note_type_id,
        noteKind = NoteKind.fromCode(note_kind),
        fields = decodeFields(fields_json),
        tags = decodeTags(tags),
        templates = json.decodeFromString<List<CardTemplate>>(templates_json),
    )

fun DbReviewLog.toDomain(): ReviewLog =
    ReviewLog(
        id = id,
        cardId = card_id,
        rating = Rating.fromCode(rating),
        before =
            Card(
                id = card_id,
                noteId = 0,
                templateIndex = 0,
                state = CardState.fromCode(state_before),
                due = due_before,
                stability = stability_before,
                difficulty = difficulty_before,
                elapsedDays = elapsed_days_before.toInt(),
                scheduledDays = scheduled_days_before.toInt(),
                reps = reps_before.toInt(),
                lapses = lapses_before.toInt(),
                lastReview = last_review_before,
                suspended = false,
                buriedUntil = null,
                learningStep = learning_step_before?.toInt(),
            ),
        responseMs = response_ms,
        mode = ReviewMode.fromCode(mode),
        aiVerdict = ai_verdict,
        aiScore = ai_score,
        userOverrode = user_overrode != 0L,
        reviewedAt = reviewed_at,
    )

fun DbConcept.toDomain(): Concept = Concept(id = id, name = name, description = description)

fun DbConceptEdge.toDomain(): ConceptEdge = ConceptEdge(fromId = from_id, toId = to_id, relation = Relation.fromCode(relation))
