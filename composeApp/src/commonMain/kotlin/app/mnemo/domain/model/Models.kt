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
    Occlusion("occlusion", "Image occlusion"),

    /** A note type you made yourself: your own fields and `{{Field}}` card templates. */
    Custom("custom", "Custom"),
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
    /** The order new cards come in. */
    val newOrder: NewOrder = NewOrder.Added,
    /** Answering a card holds its note's other new cards until tomorrow (and only one comes per day). */
    val buryNewSiblings: Boolean = true,
    /** The same for review cards of the same note. */
    val buryReviewSiblings: Boolean = true,
    /** Picks the quietest day within an interval's fuzz range, so review days even out. */
    val loadBalance: Boolean = true,
    /** Review load per weekday, Monday first: 1 normal, 0.5 reduced, 0 as few as possible. */
    val easyDays: List<Double> = List(7) { 1.0 },
    /** The shared preset these settings belong to (decks with the same preset change together). */
    val preset: String? = null,
) {
    /** True when some weekday is set lighter than normal. */
    val hasEasyDays: Boolean get() = easyDays.size == 7 && easyDays.any { it < 1.0 }

    /**
     * These settings with the preset's scheduling options, keeping what belongs to this deck
     * alone (its language, review mode and graph gating).
     */
    fun withPreset(preset: DeckSettings): DeckSettings =
        preset.copy(language = language, mode = mode, varyQuestions = varyQuestions, gatePrerequisites = gatePrerequisites)
}

@Serializable
enum class NewOrder(
    val label: String,
) {
    /** In the order they were added (or as repositioned). */
    Added("In order added"),

    /** Shuffled; the order stays the same for the whole day. */
    Random("Random"),
}

/** Anki's card flags, by number (0 = none). */
enum class Flag(
    val number: Int,
    val label: String,
) {
    Red(1, "Red"),
    Orange(2, "Orange"),
    Green(3, "Green"),
    Blue(4, "Blue"),
    Pink(5, "Pink"),
    Turquoise(6, "Turquoise"),
    Purple(7, "Purple"),
    ;

    companion object {
        fun of(number: Int): Flag? = entries.firstOrNull { it.number == number }
    }
}

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
    /** Anki flag colour, 0 = none (see [Flag]). */
    val flag: Int = 0,
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
