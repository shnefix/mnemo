package app.mnemo.ui.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.mnemo.LocalAppGraph
import app.mnemo.domain.quiz.DailyQuiz
import app.mnemo.ui.components.Eyebrow
import app.mnemo.ui.components.MnemoButton
import app.mnemo.ui.components.MnemoTextField
import app.mnemo.ui.components.Panel
import app.mnemo.ui.theme.MnemoColors

/** One question at a time; choice and short answers are marked at once from Claude's key. */
@Composable
fun QuizScreen(onExit: () -> Unit) {
    val graph = LocalAppGraph.current
    var loading by remember { mutableStateOf(true) }
    var quiz by remember { mutableStateOf<DailyQuiz.Quiz?>(null) }
    var done by remember { mutableStateOf<DailyQuiz.Answers?>(null) }
    val answers = remember { mutableStateListOf<DailyQuiz.Answer>() }
    var index by remember { mutableStateOf(0) }
    var input by remember { mutableStateOf(TextFieldValue()) }
    var checked by remember { mutableStateOf<DailyQuiz.Answer?>(null) }

    LaunchedEffect(Unit) {
        DailyQuiz.latest()?.let { (q, a) ->
            quiz = q
            done = a
        }
        loading = false
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Today's quiz",
                    style = MaterialTheme.typography.displaySmall,
                    color = MnemoColors.text,
                    modifier = Modifier.weight(1f),
                )
                MnemoButton("Close", onClick = onExit, height = 36.dp)
            }
            Spacer(Modifier.height(16.dp))
            val q = quiz
            when {
                loading -> Text("Loading…", color = MnemoColors.text2)
                q == null ->
                    Text(
                        "No quiz yet. Claude writes one each evening from the cards you reviewed that day " +
                            "(you need to be signed in under Settings → Account & sync).",
                        color = MnemoColors.text2,
                    )
                done != null -> Results(q, done!!)
                index >= q.questions.size -> {
                    Text("All done — saving…", color = MnemoColors.text2)
                    LaunchedEffect(Unit) {
                        DailyQuiz.submit(graph, q, answers.toList())
                        done = DailyQuiz.Answers(q.date, answers.toList(), graph.clock.now())
                    }
                }
                else -> {
                    if (index == 0 && checked == null) {
                        if (q.feedback.isNotBlank()) {
                            Panel(color = MnemoColors.lavender) {
                                Eyebrow("Feedback on yesterday", color = MnemoColors.onLavender)
                                Spacer(Modifier.height(6.dp))
                                Text(q.feedback, color = MnemoColors.onLavender)
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        if (q.intro.isNotBlank()) {
                            Text(q.intro, color = MnemoColors.text2)
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    val question = q.questions[index]
                    Eyebrow("Question ${index + 1} of ${q.questions.size}")
                    Spacer(Modifier.height(8.dp))
                    Text(question.prompt, style = MaterialTheme.typography.titleLarge, color = MnemoColors.text)
                    Spacer(Modifier.height(16.dp))

                    fun answer(given: String) {
                        val a = DailyQuiz.Answer(question.id, given, DailyQuiz.check(question, given))
                        answers += a
                        checked = a
                    }
                    val c = checked
                    if (c == null) {
                        if (question.type == "choice") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                question.choices.forEach { choice ->
                                    MnemoButton(choice, onClick = { answer(choice) }, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        } else {
                            MnemoTextField(
                                value = input,
                                onValueChange = { input = it },
                                placeholder = if (question.type == "write") "Write your answer…" else "Your answer",
                                singleLine = question.type != "write",
                                minLines = if (question.type == "write") 3 else 1,
                                onSubmit = { if (input.text.isNotBlank()) answer(input.text) },
                            )
                            Spacer(Modifier.height(10.dp))
                            MnemoButton("Check", primary = true, enabled = input.text.isNotBlank(), onClick = { answer(input.text) })
                        }
                    } else {
                        val (color, onColor, title) =
                            when (c.correct) {
                                true -> Triple(MnemoColors.lime, MnemoColors.onLime, "Correct")
                                false -> Triple(MnemoColors.coral, MnemoColors.onCoral, "Not quite — answer: ${question.answer}")
                                null -> Triple(MnemoColors.sky, MnemoColors.onSky, "Saved — Claude will give feedback tomorrow")
                            }
                        Panel(color = color, padding = PaddingValues(16.dp)) {
                            Text(title, style = MaterialTheme.typography.titleMedium, color = onColor)
                            if (question.explain.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(question.explain, color = onColor)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        MnemoButton(
                            if (index + 1 < q.questions.size) "Next" else "Finish",
                            primary = true,
                            onClick = {
                                index++
                                checked = null
                                input = TextFieldValue()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Results(
    quiz: DailyQuiz.Quiz,
    done: DailyQuiz.Answers,
) {
    val marked = done.answers.filter { it.correct != null }
    val right = marked.count { it.correct == true }
    Panel(color = MnemoColors.lime) {
        Eyebrow("Quiz for ${quiz.date} done", color = MnemoColors.onLime)
        Spacer(Modifier.height(6.dp))
        Text(
            if (marked.isEmpty()) "Answers saved." else "$right of ${marked.size} right.",
            style = MaterialTheme.typography.headlineSmall,
            color = MnemoColors.onLime,
        )
        if (done.answers.any { it.correct == null }) {
            Spacer(Modifier.height(4.dp))
            Text("Claude will comment on your written answers in tomorrow's quiz.", color = MnemoColors.onLime)
        }
        if (marked.any { it.correct == false }) {
            Spacer(Modifier.height(4.dp))
            Text("The cards behind the questions you missed are back in today's reviews.", color = MnemoColors.onLime)
        }
    }
    Spacer(Modifier.height(16.dp))
    quiz.questions.forEach { q ->
        val a = done.answers.firstOrNull { it.id == q.id }
        Text(q.prompt, style = MaterialTheme.typography.titleSmall, color = MnemoColors.text)
        Text(
            "You: ${a?.given ?: "—"}" + (if (q.type != "write") "   ·   Answer: ${q.answer}" else ""),
            style = MaterialTheme.typography.bodySmall,
            color = if (a?.correct == false) MnemoColors.coral else MnemoColors.text2,
        )
        Spacer(Modifier.height(10.dp))
    }
}
