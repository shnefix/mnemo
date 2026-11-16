package app.mnemo.domain.model

import kotlinx.serialization.Serializable

/** Milliseconds since the Unix epoch, UTC. */
typealias EpochMillis = Long

const val DAY_MS: Long = 86_400_000L
const val MINUTE_MS: Long = 60_000L

enum class CardState(
    val code: Long,
) {
    New(0),
    Learning(1),
    Review(2),
    Relearning(3),
    ;

    companion object {
        fun fromCode(code: Long): CardState = entries.first { it.code == code }
    }
}

enum class Rating(
    val code: Long,
    val label: String,
    val key: String,
) {
    Again(1, "Again", "1"),
    Hard(2, "Hard", "2"),
    Good(3, "Good", "3"),
    Easy(4, "Easy", "4"),
    ;

    companion object {
        fun fromCode(code: Long): Rating = entries.first { it.code == code }
    }
}

enum class NoteKind(
    val code: String,
    val displayName: String,
) {
    Basic("basic", "Basic"),
    Reversed("reversed", "Basic + Reversed"),
    Cloze("cloze", "Cloze"),
    TypeAnswer("type", "Type the answer"),
    Vocab("vocab", "Vocabulary"),
    ;

    companion object {
        fun fromCode(code: String): NoteKind = entries.first { it.code == code }
    }
}

enum class ReviewMode(
    val code: String,
    val label: String,
) {
    Classic("classic", "Classic"),
    Type("type", "Type"),
    Speak("speak", "Speak"),
    ;

    companion object {
        fun fromCode(code: String): ReviewMode = entries.firstOrNull { it.code == code } ?: Classic
    }
}

@Serializable
data class DeckSettings(
    val desiredRetention: Double = 0.90,
    val newPerDay: Int = 20,
    val reviewsPerDay: Int = 200,
    /** Learning steps in minutes. */
    val learningSteps: List<Int> = listOf(1, 10),
    /** Relearning steps in minutes. */
    val relearningSteps: List<Int> = listOf(10),
    val leechThreshold: Int = 8,
    val leechSuspend: Boolean = false,
    val mode: ReviewMode = ReviewMode.Classic,
    val language: String? = null,
    val varyQuestions: Boolean = false,
    /** Hold new cards whose concept prerequisites are below 70% mastery. */
    val gatePrerequisites: Boolean = false,
    val maximumIntervalDays: Int = 36_500,
)

data class Deck(
    val id: Long,
    val parentId: Long?,
    val name: String,
    val settings: DeckSettings,
    val createdAt: EpochMillis,
) {
    /** Full path like `German::Vocab::A1`, computed by the repository. */
    var path: String = name
}

@Serializable
data class CardTemplate(
    val name: String,
    val front: String,
    val back: String,
)

data class NoteType(
    val id: Long,
    val name: String,
    val kind: NoteKind,
    val fields: List<String>,
    val templates: List<CardTemplate>,
)

data class Note(
    val id: Long,
    val deckId: Long,
    val noteTypeId: Long,
    val fields: Map<String, String>,
    val tags: List<String>,
    val sourceId: Long?,
    val createdAt: EpochMillis,
    val updatedAt: EpochMillis,
)

data class Card(
    val id: Long,
    val noteId: Long,
    val templateIndex: Int,
    val state: CardState,
    val due: EpochMillis,
    val stability: Double?,
    val difficulty: Double?,
    val elapsedDays: Int,
    val scheduledDays: Int,
    val reps: Int,
    val lapses: Int,
    val lastReview: EpochMillis?,
    val suspended: Boolean,
    val buriedUntil: EpochMillis?,
    /** Index into learning/relearning steps while in a learning state. */
    val learningStep: Int?,
) {
    val isNew: Boolean get() = state == CardState.New

    companion object {
        fun fresh(
            id: Long,
            noteId: Long,
            templateIndex: Int,
            due: EpochMillis,
        ) = Card(
            id = id,
            noteId = noteId,
            templateIndex = templateIndex,
            state = CardState.New,
            due = due,
            stability = null,
            difficulty = null,
            elapsedDays = 0,
            scheduledDays = 0,
            reps = 0,
            lapses = 0,
            lastReview = null,
            suspended = false,
            buriedUntil = null,
            learningStep = null,
        )
    }
}

data class ReviewLog(
    val id: Long,
    val cardId: Long,
    val rating: Rating,
    val before: Card,
    val responseMs: Long,
    val mode: ReviewMode,
    val aiVerdict: String?,
    val aiScore: Double?,
    val userOverrode: Boolean,
    val reviewedAt: EpochMillis,
)

data class Concept(
    val id: Long,
    val name: String,
    val description: String,
)

enum class Relation(
    val code: String,
    val label: String,
) {
    RelatedTo("related_to", "related"),
    PrerequisiteOf("prerequisite_of", "prerequisite"),
    PartOf("part_of", "part of"),
    ;

    companion object {
        fun fromCode(code: String): Relation = entries.first { it.code == code }
    }
}

data class ConceptEdge(
    val fromId: Long,
    val toId: Long,
    val relation: Relation,
)

/** A card with the note/deck context the UI needs to render it. */
data class CardView(
    val card: Card,
    val deckId: Long,
    val deckName: String,
    val noteTypeId: Long,
    val noteKind: NoteKind,
    val fields: Map<String, String>,
    val tags: List<String>,
    val templates: List<CardTemplate>,
)
