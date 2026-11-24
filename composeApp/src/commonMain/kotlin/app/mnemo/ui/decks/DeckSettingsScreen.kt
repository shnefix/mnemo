package app.mnemo.ui.decks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import app.mnemo.domain.model.NewOrder
import app.mnemo.domain.model.ReviewMode
import app.mnemo.ui.components.Eyebrow
import app.mnemo.ui.components.IconActionButton
import app.mnemo.ui.components.MnemoButton
import app.mnemo.ui.components.MnemoIcons
import app.mnemo.ui.components.MnemoSelect
import app.mnemo.ui.components.MnemoTextField
import app.mnemo.ui.components.Panel
import app.mnemo.ui.components.Segmented
import app.mnemo.ui.nav.LocalWindowSize
import app.mnemo.ui.nav.WindowSize
import app.mnemo.ui.theme.MnemoColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    var allDecks by remember { mutableStateOf<List<Deck>>(emptyList()) }
    var newPreset by remember { mutableStateOf(false) }
    val compact = LocalWindowSize.current == WindowSize.Compact

    LaunchedEffect(deckId) {
        deck = graph.decks.byId(deckId)
        deck?.let { s = it.settings }
        allDecks = graph.decks.all()
    }
    // Presets: the names decks share; a preset's options are those of any deck that uses it.
    val presets =
        allDecks
            .mapNotNull { d ->
                d.settings.preset?.let { it to d.settings }
            }.distinctBy { it.first }
            .sortedBy { it.first.lowercase() }
    val sharing = s.preset?.let { p -> allDecks.count { it.id != deckId && it.settings.preset == p } } ?: 0

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
                Eyebrow("Preset")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Decks with the same preset share their scheduling options: change one and the others follow. " +
                        "Language, review mode and graph gating stay per deck.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MnemoColors.text2,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                    MnemoSelect(
                        label = null,
                        options = listOf<String?>(null) + presets.map { it.first },
                        selected = s.preset,
                        display = { it ?: "This deck only" },
                        onSelect = { name ->
                            s =
                                if (name == null) {
                                    s.copy(preset = null)
                                } else {
                                    s.withPreset(presets.first { it.first == name }.second).copy(preset = name)
                                }
                        },
                        placeholder = "This deck only",
                        modifier = Modifier.weight(1f),
                    )
                    MnemoButton("New preset", onClick = { newPreset = true })
                }
                if (s.preset != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (sharing ==
                            0
                        ) {
                            "Only this deck uses it so far."
                        } else {
                            "Also used by $sharing other deck${if (sharing == 1) "" else "s"}."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MnemoColors.text2,
                    )
                }
            }
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
                Spacer(Modifier.height(12.dp))
                NumberField("Maximum interval (days)", s.maximumIntervalDays, Modifier.fillMaxWidth()) {
                    s = s.copy(maximumIntervalDays = it.coerceIn(1, 36_500))
                }
            }
            Panel {
                Eyebrow("New cards")
                Spacer(Modifier.height(10.dp))
                Text("Order", style = MaterialTheme.typography.labelLarge, color = MnemoColors.text2)
                Spacer(Modifier.height(6.dp))
                Segmented(
                    options = NewOrder.entries.map { it.label },
                    selected = s.newOrder.ordinal,
                    onSelect = { s = s.copy(newOrder = NewOrder.entries[it]) },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Random keeps one shuffled order for the whole day. To move particular cards up or down, select them in Browse and use Reposition.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MnemoColors.text2,
                )
            }
            Panel {
                Eyebrow("Burying")
                Spacer(Modifier.height(4.dp))
                Text(
                    "Cards from the same note (both sides of a reversed card, the clozes of one sentence) give each other away. " +
                        "Burying shows only one of them a day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MnemoColors.text2,
                )
                SwitchRow("Bury new siblings until tomorrow", s.buryNewSiblings) { s = s.copy(buryNewSiblings = it) }
                SwitchRow("Bury review siblings until tomorrow", s.buryReviewSiblings) { s = s.copy(buryReviewSiblings = it) }
            }
            Panel {
                Eyebrow("Spreading reviews")
                SwitchRow("Load balancing", s.loadBalance) { s = s.copy(loadBalance = it) }
                Text(
                    "When a card can go on one of several days, it goes on a quieter one, so no day piles up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MnemoColors.text2,
                )
                Spacer(Modifier.height(14.dp))
                Text("Easy days", style = MaterialTheme.typography.labelLarge, color = MnemoColors.text2)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tap a day to make it lighter: Reduced gets fewer reviews, Minimum as few as possible.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MnemoColors.text2,
                )
                Spacer(Modifier.height(8.dp))
                val days = if (s.easyDays.size == 7) s.easyDays else List(7) { 1.0 }
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEachIndexed { i, name ->
                        val v = days[i]
                        val label =
                            when {
                                v >= 1.0 -> "Normal"
                                v > 0.0 -> "Reduced"
                                else -> "Minimum"
                            }
                        MnemoButton(
                            "$name · $label",
                            height = 36.dp,
                            primary = v < 1.0,
                            onClick = {
                                val next =
                                    when {
                                        v >= 1.0 -> 0.5
                                        v > 0.0 -> 0.0
                                        else -> 1.0
                                    }
                                s = s.copy(easyDays = days.toMutableList().also { it[i] = next })
                            },
                        )
                    }
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
                            // Everything else on the same preset takes the new scheduling options.
                            s.preset?.let { p ->
                                graph.decks.all().filter { it.id != deckId && it.settings.preset == p }.forEach { d ->
                                    graph.decks.updateSettings(d.id, d.settings.withPreset(s).copy(preset = p))
                                }
                            }
                            allDecks = graph.decks.all()
                            saved = true
                        }
                    },
                )
                MnemoButton("Back", onClick = onBack)
                if (saved) Text("Saved", style = MaterialTheme.typography.labelLarge, color = MnemoColors.success)
            }
        }
    }
    if (newPreset) {
        NameDialog(
            title = "New preset",
            hint = "This deck's options become the preset's",
            initial = deck?.name.orEmpty(),
            confirm = "Create",
            onDismiss = { newPreset = false },
            onConfirm = { name ->
                newPreset = false
                if (name.isNotBlank()) s = s.copy(preset = name.trim())
            },
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = MnemoColors.onCoral, checkedTrackColor = MnemoColors.coral),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MnemoColors.text)
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
