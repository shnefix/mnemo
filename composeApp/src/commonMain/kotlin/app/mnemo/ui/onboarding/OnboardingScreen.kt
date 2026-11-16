package app.mnemo.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mnemo.LocalAppGraph
import app.mnemo.di.AppGraph
import app.mnemo.domain.model.DeckSettings
import app.mnemo.domain.model.NoteKind
import app.mnemo.platform.Prefs
import app.mnemo.ui.components.MnemoButton
import app.mnemo.ui.components.MnemoIcons
import app.mnemo.ui.components.MnemoTextField
import app.mnemo.ui.theme.MnemoColors
import kotlinx.coroutines.launch

/** A starter: a deck path, optional language code, and a few sample cards. */
data class Starter(
    val label: String,
    val deck: String,
    val language: String?,
    val cards: List<Triple<NoteKind, String, String>>,
)

val starters =
    listOf(
        Starter(
            "German",
            "German::Vocab",
            "de",
            listOf(
                Triple(NoteKind.Reversed, "der Zug", "the train"),
                Triple(NoteKind.Reversed, "die Zeitung", "the newspaper"),
                Triple(NoteKind.Reversed, "das Fahrrad", "the bicycle"),
                Triple(NoteKind.Cloze, "The preposition **mit** always takes the {{c1::dative}} case.", ""),
            ),
        ),
        Starter(
            "Spanish",
            "Spanish::Vocab",
            "es",
            listOf(
                Triple(NoteKind.Reversed, "el tren", "the train"),
                Triple(NoteKind.Reversed, "la ciudad", "the city"),
                Triple(NoteKind.Reversed, "el libro", "the book"),
                Triple(NoteKind.Cloze, "**Ser** is used for permanent traits, **{{c1::estar}}** for states and locations.", ""),
            ),
        ),
        Starter(
            "Japanese",
            "Japanese::Vocab",
            "ja",
            listOf(
                Triple(NoteKind.Reversed, "水 (みず)", "water"),
                Triple(NoteKind.Reversed, "電車 (でんしゃ)", "train"),
                Triple(NoteKind.Reversed, "本 (ほん)", "book"),
            ),
        ),
        Starter(
            "Medicine",
            "Medicine::Physiology",
            null,
            listOf(
                Triple(NoteKind.Basic, "Normal resting heart rate in adults?", "60–100 beats per minute"),
                Triple(NoteKind.Basic, "Which hormone lowers blood glucose?", "Insulin (from pancreatic β-cells)"),
                Triple(NoteKind.Cloze, "The {{c1::sinoatrial node}} is the heart's natural pacemaker.", ""),
            ),
        ),
        Starter(
            "Biochemistry",
            "Biochemistry::Enzymes",
            null,
            listOf(
                Triple(NoteKind.Basic, "What does a low Km tell you about an enzyme?", "High affinity for its substrate"),
                Triple(NoteKind.Cloze, "A competitive inhibitor raises the apparent {{c1::Km}} but leaves {{c2::Vmax}} unchanged.", ""),
            ),
        ),
        Starter(
            "Programming",
            "Programming::Fundamentals",
            null,
            listOf(
                Triple(NoteKind.Basic, "Time complexity of binary search?", "O(log n)"),
                Triple(
                    NoteKind.Basic,
                    "What does `git rebase` do?",
                    "Re-applies commits on top of another base commit, rewriting history.",
                ),
                Triple(NoteKind.Cloze, "In Kotlin, `{{c1::val}}` declares a read-only reference and `{{c2::var}}` a mutable one.", ""),
            ),
        ),
    )

suspend fun applyStarter(
    graph: AppGraph,
    starter: Starter?,
    customName: String?,
) {
    val name = starter?.deck ?: customName?.trim()?.takeIf { it.isNotEmpty() } ?: "My first deck"
    val deckId = graph.decks.createByPath(name, DeckSettings(language = starter?.language))
    starter?.cards?.forEach { (kind, front, back) ->
        val type = graph.notes.noteTypeOf(kind)
        val fields = if (kind == NoteKind.Cloze) mapOf("Text" to front, "Extra" to back) else mapOf("Front" to front, "Back" to back)
        graph.notes.create(deckId, type, fields, listOf("starter"))
    }
    Prefs.set(Prefs.ONBOARDED, "1")
}

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<Starter?>(null) }
    var custom by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(MnemoColors.background), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(MnemoColors.coral))
                Spacer(Modifier.size(12.dp))
                Text("mnemo", style = MaterialTheme.typography.headlineLarge, color = MnemoColors.text)
            }
            Spacer(Modifier.height(28.dp))
            Text("What are you learning?", style = MaterialTheme.typography.displaySmall, color = MnemoColors.text)
            Spacer(Modifier.height(6.dp))
            Text(
                "Pick one to get a starter deck with sensible settings. You can add decks or import from Anki any time.",
                style = MaterialTheme.typography.bodyLarge,
                color = MnemoColors.text2,
            )
            Spacer(Modifier.height(24.dp))
            starters.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { s ->
                        val on = picked == s
                        val tint = listOf(MnemoColors.sky, MnemoColors.lime, MnemoColors.lavender)[starters.indexOf(s) % 3]
                        Column(
                            Modifier
                                .weight(1f)
                                .height(96.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (on) MnemoColors.coral else tint)
                                .clickable(role = Role.RadioButton) {
                                    picked = s
                                    custom = ""
                                }.padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                s.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = MnemoColors.onLime,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${s.cards.size} starter cards",
                                style = MaterialTheme.typography.labelMedium,
                                color = MnemoColors.onLime.copy(alpha = 0.75f),
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(8.dp))
            MnemoTextField(
                value = custom,
                onValueChange = {
                    custom = it
                    if (it.isNotBlank()) picked = null
                },
                label = "Or name your own deck",
                placeholder = "e.g. Art history",
                singleLine = true,
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MnemoButton(
                    if (working) "Setting up…" else "Start learning",
                    primary = true,
                    icon = MnemoIcons.ArrowRight,
                    enabled = !working && (picked != null || custom.isNotBlank()),
                    height = 52.dp,
                    onClick = {
                        working = true
                        scope.launch {
                            applyStarter(graph, picked, custom)
                            onDone()
                        }
                    },
                )
                MnemoButton("Skip", height = 52.dp, onClick = {
                    Prefs.set(Prefs.ONBOARDED, "1")
                    onDone()
                })
            }
        }
    }
}
