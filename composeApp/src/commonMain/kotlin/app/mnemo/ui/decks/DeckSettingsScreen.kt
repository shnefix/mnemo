package app.mnemo.ui.decks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.mnemo.LocalAppGraph
import app.mnemo.domain.model.Deck
import app.mnemo.domain.model.DeckSettings
import app.mnemo.domain.model.ReviewMode
import app.mnemo.ui.components.Eyebrow
import app.mnemo.ui.components.IconActionButton
import app.mnemo.ui.components.MnemoButton
import app.mnemo.ui.components.MnemoIcons
import app.mnemo.ui.components.MnemoTextField
import app.mnemo.ui.components.Panel
import app.mnemo.ui.components.Segmented
import app.mnemo.ui.nav.LocalWindowSize
import app.mnemo.ui.nav.WindowSize
import app.mnemo.ui.theme.MnemoColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun DeckSettingsScreen(
    deckId: Long,
    onBack: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var deck by remember { mutableStateOf<Deck?>(null) }
    var s by remember { mutableStateOf(DeckSettings()) }
    var saved by remember { mutableStateOf(false) }
    val compact = LocalWindowSize.current == WindowSize.Compact

    LaunchedEffect(deckId) {
        deck = graph.decks.byId(deckId)
        deck?.let { s = it.settings }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (compact) 20.dp else 40.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconActionButton(MnemoIcons.Back, "Back", onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Deck settings", style = MaterialTheme.typography.labelLarge, color = MnemoColors.text2)
                Text(
                    deck?.path ?: "",
                    style =
                        if (LocalWindowSize.current ==
                            WindowSize.Compact
                        ) {
                            MaterialTheme.typography.headlineSmall
                        } else {
                            MaterialTheme.typography.headlineLarge
                        },
                    color = MnemoColors.text,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Panel {
                Eyebrow("Scheduling · FSRS")
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Desired retention",
                        style = MaterialTheme.typography.titleSmall,
                        color = MnemoColors.text,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${(s.desiredRetention * 100).roundToInt()}%",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MnemoColors.text,
                    )
                }
                Slider(
                    value = s.desiredRetention.toFloat(),
                    onValueChange = { s = s.copy(desiredRetention = ((it * 100).roundToInt() / 100.0)) },
                    valueRange = 0.80f..0.97f,
                    colors =
                        SliderDefaults.colors(
                            thumbColor = MnemoColors.coral,
                            activeTrackColor = MnemoColors.coral,
                            inactiveTrackColor = MnemoColors.surface3,
                        ),
                )
                Text(
                    "Higher retention means shorter intervals and more reviews per day. 90% is a good default.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MnemoColors.text2,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField("New cards / day", s.newPerDay, Modifier.weight(1f)) { s = s.copy(newPerDay = it) }
                    NumberField("Max reviews / day", s.reviewsPerDay, Modifier.weight(1f)) { s = s.copy(reviewsPerDay = it) }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepsField("Learning steps (min)", s.learningSteps, Modifier.weight(1f)) { s = s.copy(learningSteps = it) }
                    StepsField("Relearning steps (min)", s.relearningSteps, Modifier.weight(1f)) { s = s.copy(relearningSteps = it) }
                }
            }
            Panel {
                Eyebrow("Leeches")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    NumberField("Lapses before tagging “leech”", s.leechThreshold, Modifier.weight(1f)) { s = s.copy(leechThreshold = it) }
                    Row(Modifier.weight(1f).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = s.leechSuspend,
                            onCheckedChange = { s = s.copy(leechSuspend = it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = MnemoColors.onCoral, checkedTrackColor = MnemoColors.coral),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Also suspend leeches", style = MaterialTheme.typography.bodyMedium, color = MnemoColors.text)
                    }
                }
            }
            Panel {
                Eyebrow("Knowledge graph")
                Spacer(Modifier.height(12.dp))
                Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = s.gatePrerequisites,
                        onCheckedChange = { s = s.copy(gatePrerequisites = it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = MnemoColors.onCoral, checkedTrackColor = MnemoColors.coral),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Hold new cards until their prerequisite concepts reach 70%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MnemoColors.text,
                    )
                }
                Text(
                    "Uses the prerequisite links you set on the Graph page. Cards not linked to any concept are never held.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MnemoColors.text2,
                )
            }
            Panel {
                Eyebrow("Review mode")
                Spacer(Modifier.height(12.dp))
                Segmented(
                    options = listOf("Classic", "Type", "Speak"),
                    selected = s.mode.ordinal,
                    onSelect = { s = s.copy(mode = ReviewMode.entries[it]) },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Type: you type the answer and Mnemo checks it. Speak: a mock interview — the question is read out, you answer out loud, then compare with the model answer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MnemoColors.text2,
                )
                Spacer(Modifier.height(12.dp))
                MnemoTextField(
                    value = s.language.orEmpty(),
                    onValueChange = { s = s.copy(language = it.ifBlank { null }) },
                    label = "Language code (de, en, fr …) — enables language drills in Phase 6",
                    singleLine = true,
                    placeholder = "none",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MnemoButton(
                    "Save settings",
                    primary = true,
                    onClick = {
                        scope.launch {
                            graph.decks.updateSettings(deckId, s)
                            saved = true
                        }
                    },
                )
                MnemoButton("Back", onClick = onBack)
                if (saved) Text("Saved", style = MaterialTheme.typography.labelLarge, color = MnemoColors.success)
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    MnemoTextField(
        value = value.toString(),
        onValueChange = { t -> t.filter { it.isDigit() }.toIntOrNull()?.let(onChange) },
        label = label,
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun StepsField(
    label: String,
    value: List<Int>,
    modifier: Modifier = Modifier,
    onChange: (List<Int>) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.joinToString(" ")) }
    MnemoTextField(
        value = text,
        onValueChange = { t ->
            text = t
            val steps = t.split(' ', ',').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }
            onChange(steps)
        },
        label = label,
        placeholder = "e.g. 1 10",
        singleLine = true,
        modifier = modifier,
    )
}
