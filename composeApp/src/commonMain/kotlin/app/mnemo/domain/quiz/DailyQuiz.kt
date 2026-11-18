package app.mnemo.domain.quiz

import app.mnemo.di.AppGraph
import app.mnemo.domain.sync.CloudSync
import app.mnemo.platform.Cloud
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The daily quiz Claude writes from the day's reviews. A scheduled Claude task on the
 * computer (`tools/quiz/`) reads the synced collection, writes `users/{uid}/quiz/latest`,
 * and the next day reads `users/{uid}/answers/{date}` to give feedback on written answers.
 */
object DailyQuiz {
    @Serializable
    data class Quiz(
        val date: String,
        val intro: String = "",
        val questions: List<Question>,
        /** Claude's feedback on the previous quiz's written answers. */
        val feedback: String = "",
    )

    @Serializable
    data class Question(
        val id: String,
        /** "choice", "short" (checked against [answer]/[accept]) or "write" (Claude checks it later). */
        val type: String,
        val prompt: String,
        val choices: List<String> = emptyList(),
        val answer: String = "",
        val accept: List<String> = emptyList(),
        val explain: String = "",
        /** Uid of the note this question is about; a wrong answer makes its cards due again. */
        val noteUid: String? = null,
    )

    @Serializable
    data class Answer(
        val id: String,
        val given: String,
        /** null for written answers, which Claude marks later. */
        val correct: Boolean?,
    )

    @Serializable
    data class Answers(
        val date: String,
        val answers: List<Answer>,
        val finishedAt: Long,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** The newest quiz and, if already taken, its answers. Null when signed out or none exists. */
    suspend fun latest(): Pair<Quiz, Answers?>? {
        if (!CloudSync.signedIn) return null
        return runCatching {
            val quiz = Cloud.readDoc("quiz", "latest")?.let { json.decodeFromString<Quiz>(it) } ?: return null
            val answers = Cloud.readDoc("answers", quiz.date)?.let { json.decodeFromString<Answers>(it) }
            quiz to answers
        }.getOrNull()
    }

    fun normalize(s: String) =
        s
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}' ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Instant marking for choice/short questions; null for written ones. */
    fun check(
        q: Question,
        given: String,
    ): Boolean? =
        when (q.type) {
            "write" -> null
            else -> (listOf(q.answer) + q.accept).any { normalize(it) == normalize(given) }
        }

    suspend fun submit(
        graph: AppGraph,
        quiz: Quiz,
        answers: List<Answer>,
    ) {
        val result = Answers(quiz.date, answers, graph.clock.now())
        Cloud.writeDoc("answers", quiz.date, json.encodeToString(Answers.serializer(), result))
        // Missed questions send their cards back into today's reviews.
        val missed =
            quiz.questions
                .filter { q -> answers.any { it.id == q.id && it.correct == false } }
                .mapNotNull { it.noteUid }
                .toSet()
        if (missed.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val now = graph.clock.now()
                graph.db.noteQueries
                    .selectAll()
                    .executeAsList()
                    .filter { it.uid in missed }
                    .forEach { note ->
                        graph.cards.byNote(note.id).forEach { c -> if (c.due > now) graph.cards.save(c.copy(due = now)) }
                    }
            }
            CloudSync.sync(graph)
        }
    }
}
