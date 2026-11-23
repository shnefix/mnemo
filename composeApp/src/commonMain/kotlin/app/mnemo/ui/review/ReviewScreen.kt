package app.mnemo.ui.review

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.mnemo.LocalAppGraph
import app.mnemo.domain.model.CardState
import app.mnemo.domain.model.Rating
import app.mnemo.domain.model.ReviewMode
import app.mnemo.domain.review.AnswerMatcher
import app.mnemo.platform.Speech
import app.mnemo.platform.SpeechInput
import app.mnemo.platform.isMobilePlatform
import app.mnemo.platform.rememberSpeechInput
import app.mnemo.ui.components.IconActionButton
import app.mnemo.ui.components.Kbd
import app.mnemo.ui.components.MarkdownText
import app.mnemo.ui.components.MnemoButton
import app.mnemo.ui.components.MnemoIcons
import app.mnemo.ui.components.MnemoTextField
import app.mnemo.ui.components.Segmented
import app.mnemo.ui.nav.LocalWindowSize
import app.mnemo.ui.nav.WindowSize
import app.mnemo.ui.theme.MnemoColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ReviewScreen(
    deckId: Long?,
    weak: Boolean = false,
    layer: String? = null,
    onExit: () -> Unit,
    onEdit: (noteId: Long) -> Unit,
    onSearch: () -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val vm: ReviewViewModel = viewModel(key = "review-$deckId-$weak-$layer") { ReviewViewModel(graph, deckId, weak, layer) }
    val state by vm.state.collectAsState()
    val compact = LocalWindowSize.current == WindowSize.Compact
    val focus = remember { FocusRequester() }

    // Engine starts with the screen, and leaving the screen silences it.
    DisposableEffect(Unit) {
        Speech.warmUp()
        onDispose { Speech.stop() }
    }
    LaunchedEffect(state.view?.card?.id) {
        // A new card (swipe/grade) cuts off the previous card's audio.
        Speech.stop()
        if (!state.typing) focus.requestFocus()
        // Dictation cards read their sentence aloud as soon as they appear.
        state.rendered?.listen?.let { if (Speech.available) Speech.speak(it, state.language) }
        // Speak mode is a mock interview: the question is asked out loud.
        if (state.mode == ReviewMode.Speak && state.rendered?.listen == null && Speech.available) {
            state.rendered?.question?.let { Speech.speak(plainCardText(it), state.language) }
        }
    }
    LaunchedEffect(state.toast) {
        if (state.toast != null) {
            delay(2200)
            vm.dismissToast()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MnemoColors.background)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (state.typing &&
                    !state.revealed &&
                    e.key != Key.Enter &&
                    e.key != Key.Escape
                ) {
                    return@onPreviewKeyEvent false
                }
                when (e.key) {
                    Key.Spacebar, Key.Enter -> {
                        if (!state.revealed) vm.reveal() else vm.grade(state.suggested ?: Rating.Good)
                        true
                    }
                    Key.One -> {
                        vm.grade(Rating.Again)
                        true
                    }
                    Key.Two -> {
                        vm.grade(Rating.Hard)
                        true
                    }
                    Key.Three -> {
                        vm.grade(Rating.Good)
                        true
                    }
                    Key.Four -> {
                        vm.grade(Rating.Easy)
                        true
                    }
                    Key.U -> {
                        vm.undo()
                        true
                    }
                    Key.S -> {
                        vm.suspendCurrent()
                        true
                    }
                    Key.B -> {
                        vm.buryCurrent()
                        true
                    }
                    Key.E -> {
                        state.view?.let { onEdit(it.card.noteId) }
                        true
                    }
                    Key.Escape -> {
                        onExit()
                        true
                    }
                    else -> false
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            ReviewTopBar(state, compact, onExit, onMode = vm::setMode, onSearch = onSearch)
            when {
                state.loading -> Box(Modifier.fillMaxSize())
                state.finished -> FinishedView(state, onExit, onUndo = vm::undo)
                else ->
                    Box(Modifier.fillMaxSize()) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = if (compact) 16.dp else 32.dp, vertical = if (compact) 12.dp else 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                                CardStack(
                                    state = state,
                                    compact = compact,
                                    onReveal = vm::reveal,
                                    onSwipe = vm::grade,
                                    onTyped = vm::setTyped,
                                    onSpoken = vm::setSpoken,
                                    onSpokenMore = vm::addSpoken,
                                )
                                Spacer(Modifier.height(16.dp))
                                if (!state.revealed) {
                                    MnemoButton(
                                        if (state.typing) "Check answer" else "Show answer",
                                        onClick = vm::reveal,
                                        modifier = Modifier.fillMaxWidth(),
                                        height = 60.dp,
                                        primary = false,
                                        kbd = if (isMobilePlatform) null else "Space",
                                    )
                                } else {
                                    GradeBar(state = state, onGrade = vm::grade)
                                }
                                Spacer(Modifier.height(12.dp))
                                ActionsRow(state, compact, onUndo = vm::undo, onEdit = {
                                    state.view?.let { onEdit(it.card.noteId) }
                                }, onSuspend = vm::suspendCurrent, onBury = vm::buryCurrent)
                                Spacer(Modifier.height(24.dp))
                            }
                        }
                        state.toast?.let { Toast(it, Modifier.align(Alignment.BottomCenter).padding(24.dp)) }
                    }
            }
        }
    }
}

@Composable
private fun ReviewTopBar(
    state: ReviewUiState,
    compact: Boolean,
    onExit: () -> Unit,
    onMode: (ReviewMode) -> Unit,
    onSearch: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(MnemoColors.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconActionButton(MnemoIcons.Close, "End session", onClick = onExit)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    state.deckName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MnemoColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${state.done} of ${state.total}", style = MaterialTheme.typography.labelMedium, color = MnemoColors.text2)
            }
            IconActionButton(MnemoIcons.Search, "Search cards", onClick = onSearch)
            if (!compact) {
                Spacer(Modifier.width(12.dp))
                ModeSwitch(state, onMode, Modifier.width(240.dp))
            }
        }
        if (compact) {
            // Phones are too narrow to share a row with the deck name, so the mode switch gets its own.
            ModeSwitch(state, onMode, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp))
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(MnemoColors.surface3)) {
            Box(Modifier.fillMaxWidth(state.progress).height(4.dp).background(MnemoColors.coral))
        }
    }
}

@Composable
private fun ModeSwitch(
    state: ReviewUiState,
    onMode: (ReviewMode) -> Unit,
    modifier: Modifier,
) {
    Segmented(
        options = listOf("Classic", "Type", "Speak"),
        selected = state.mode.ordinal,
        onSelect = { onMode(ReviewMode.entries[it]) },
        modifier = modifier,
    )
}

@Composable
private fun CardStack(
    state: ReviewUiState,
    compact: Boolean,
    onReveal: () -> Unit,
    onSwipe: (Rating) -> Unit,
    onTyped: (String) -> Unit,
    onSpoken: (String) -> Unit = {},
    onSpokenMore: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val view = state.view ?: return
    val rendered = state.rendered
    var copied by remember(view.card.id) { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth()) {
        // Deck of cards behind (Design C); they take the front card's size.
        Box(
            Modifier
                .matchParentSize()
                .padding(
                    top = 14.dp,
                    start = 10.dp,
                    end = 10.dp,
                ).rotate(2f)
                .clip(RoundedCornerShape(32.dp))
                .background(MnemoColors.lime.copy(alpha = 0.35f)),
        )
        Box(
            Modifier
                .matchParentSize()
                .padding(
                    top = 8.dp,
                    start = 4.dp,
                    end = 4.dp,
                ).rotate(-2f)
                .clip(RoundedCornerShape(32.dp))
                .background(MnemoColors.lavender.copy(alpha = 0.35f)),
        )

        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) 360.dp else 300.dp)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(view.card.id, state.revealed) {
                    if (!state.revealed) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val x = offsetX.value
                            scope.launch {
                                when {
                                    x > 140 -> {
                                        offsetX.animateTo(900f, tween(180))
                                        onSwipe(Rating.Good)
                                        offsetX.snapTo(0f)
                                    }
                                    x < -140 -> {
                                        offsetX.animateTo(-900f, tween(180))
                                        onSwipe(Rating.Again)
                                        offsetX.snapTo(0f)
                                    }
                                    else -> offsetX.animateTo(0f)
                                }
                            }
                        },
                        onHorizontalDrag = { _, dragAmount -> scope.launch { offsetX.snapTo(offsetX.value + dragAmount) } },
                    )
                }.clip(RoundedCornerShape(32.dp))
                .background(MnemoColors.surface)
                .clickable(enabled = !state.revealed && !state.typing, role = Role.Button, onClick = onReveal)
                .padding(if (compact) 22.dp else 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatePill(view.card.state, view.card.lapses, state.leechThreshold)
                Text(view.noteKind.displayName, style = MaterialTheme.typography.labelMedium, color = MnemoColors.text3)
                Spacer(Modifier.weight(1f))
                @Suppress("DEPRECATION")
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                // Before the reveal: the question. After it: question and answer together.
                val cardText =
                    if (state.revealed) {
                        "${plainCardText(rendered?.question)}\n\n${plainCardText(rendered?.answer)}"
                    } else {
                        plainCardText(rendered?.question)
                    }
                IconActionButton(
                    MnemoIcons.Copy,
                    if (state.revealed) "Copy question and answer" else "Copy question",
                    onClick = {
                        clipboard.setText(
                            androidx.compose.ui.text
                                .AnnotatedString(cardText),
                        )
                        copied = true
                    },
                    size = 30.dp,
                    tint = if (copied) MnemoColors.text else MnemoColors.text3,
                )
                if (view.card.state != CardState.New) {
                    Text(
                        "recall ${(state.retrievability * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MnemoColors.text3,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            MarkdownText(rendered?.question.orEmpty())
            val listen = rendered?.listen
            if ((state.language != null || listen != null) && Speech.available) {
                Spacer(Modifier.height(10.dp))
                MnemoButton(
                    if (listen != null) "Listen again" else "Listen",
                    icon = MnemoIcons.Speaker,
                    height = 36.dp,
                    onClick = {
                        Speech.speak(
                            listen ?: if (state.revealed) rendered?.answer.orEmpty() else rendered?.question.orEmpty(),
                            state.language,
                        )
                    },
                )
            } else if (listen != null && !state.revealed) {
                // No voice on this device: show the sentence, so the card still works as a typing drill.
                Spacer(Modifier.height(10.dp))
                Text("No voice available — read it: $listen", style = MaterialTheme.typography.bodyMedium, color = MnemoColors.text2)
            }

            if (state.typing) {
                Spacer(Modifier.height(18.dp))
                if (!state.revealed) {
                    MnemoTextField(
                        value = state.typed,
                        onValueChange = onTyped,
                        placeholder = "Type your answer, then Enter",
                        singleLine = true,
                        onSubmit = onReveal,
                    )
                } else {
                    TypedFeedback(state)
                }
            }

            if (state.mode == ReviewMode.Speak && !state.typing) {
                Spacer(Modifier.height(18.dp))
                SpokenAnswer(state, onSpoken, onSpokenMore, onReveal)
            }

            if (state.revealed) {
                Spacer(Modifier.height(20.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(MnemoColors.outline))
                Spacer(Modifier.height(20.dp))
                MarkdownText(rendered?.answer.orEmpty())
                if (view.tags.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(view.tags.joinToString("  ") { "#$it" }, style = MaterialTheme.typography.labelMedium, color = MnemoColors.text3)
                }
            } else if (compact) {
                Spacer(Modifier.height(28.dp))
                Text(
                    "Tap to reveal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MnemoColors.text3,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
    if (compact && state.revealed) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("← Again", style = MaterialTheme.typography.labelMedium, color = MnemoColors.gradeAgain)
            Text("swipe the card", style = MaterialTheme.typography.labelMedium, color = MnemoColors.text3)
            Text("Good →", style = MaterialTheme.typography.labelMedium, color = MnemoColors.gradeGood)
        }
    }
}

/**
 * Speak mode: answer out loud like in an interview, then compare with the model answer.
 * Each recording adds to the answer; without a recognizer (desktop) it is a text box.
 */
@Composable
private fun SpokenAnswer(
    state: ReviewUiState,
    onSpoken: (String) -> Unit,
    onSpokenMore: (String) -> Unit,
    onReveal: () -> Unit,
) {
    var failed by remember(state.view?.card?.id) { mutableStateOf(false) }
    val listen =
        rememberSpeechInput(state.language) { text ->
            failed = text == null
            if (text != null) onSpokenMore(text)
        }
    if (state.revealed) {
        if (state.spoken.isNotBlank()) {
            Text("Your answer", style = MaterialTheme.typography.labelMedium, color = MnemoColors.text3)
            Spacer(Modifier.height(4.dp))
            Text(state.spoken, style = MaterialTheme.typography.bodyMedium, color = MnemoColors.text2)
        }
        return
    }
    if (SpeechInput.available) {
        if (state.spoken.isNotBlank()) {
            Text("You said", style = MaterialTheme.typography.labelMedium, color = MnemoColors.text3)
            Spacer(Modifier.height(4.dp))
            Text(state.spoken, style = MaterialTheme.typography.bodyMedium, color = MnemoColors.text)
            Spacer(Modifier.height(12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MnemoButton(
                if (state.spoken.isBlank()) "Answer out loud" else "Add more",
                icon = MnemoIcons.Mic,
                primary = state.spoken.isBlank(),
                onClick = {
                    Speech.stop()
                    listen()
                },
            )
            if (state.spoken.isNotBlank()) MnemoButton("Show answer", primary = true, onClick = onReveal)
        }
        if (failed) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Didn't catch that. Try again, or just answer in your head and show the answer.",
                style = MaterialTheme.typography.bodySmall,
                color = MnemoColors.text3,
            )
        }
    } else {
        MnemoTextField(
            value = state.spoken,
            onValueChange = onSpoken,
            placeholder = "Say your answer out loud, or jot down the key points",
            minLines = 3,
        )
    }
}

@Composable
private fun StatePill(
    state: CardState,
    lapses: Int,
    leechThreshold: Int,
) {
    val (label, color, bg) =
        when {
            lapses >= leechThreshold -> Triple("Leech", MnemoColors.danger, MnemoColors.gradeAgainBg)
            state == CardState.New -> Triple("New", MnemoColors.stateNew, MnemoColors.gradeEasyBg)
            state == CardState.Learning -> Triple("Learning", MnemoColors.stateLearning, MnemoColors.gradeHardBg)
            state == CardState.Relearning -> Triple("Relearning", MnemoColors.stateLearning, MnemoColors.gradeHardBg)
            else -> Triple("Review", MnemoColors.stateReview, MnemoColors.gradeGoodBg)
        }
    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Verdict banner for Type mode: correct / close / wrong, with a character diff against the expected answer. */
@Composable
private fun TypedFeedback(state: ReviewUiState) {
    val verdict = state.verdict ?: return
    val (title, fg, bg) =
        when (verdict) {
            AnswerMatcher.Verdict.Correct -> Triple("Correct", MnemoColors.gradeGood, MnemoColors.gradeGoodBg)
            AnswerMatcher.Verdict.Close -> Triple("Close", MnemoColors.gradeHard, MnemoColors.gradeHardBg)
            AnswerMatcher.Verdict.Wrong -> Triple("Not quite", MnemoColors.gradeAgain, MnemoColors.gradeAgainBg)
        }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            if (verdict == AnswerMatcher.Verdict.Wrong) MnemoIcons.Close else MnemoIcons.Check,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(18.dp).padding(top = 1.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge, color = fg)
            val diff = state.comparison?.diff.orEmpty()
            if (verdict == AnswerMatcher.Verdict.Correct || diff.isEmpty()) {
                Text(
                    "You typed: ${state.typed.ifBlank { "—" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MnemoColors.text2,
                )
            } else {
                Spacer(Modifier.height(4.dp))
                Text(diffText(diff), style = MaterialTheme.typography.bodyLarge, color = MnemoColors.text)
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        if (diff.any { it.kind == AnswerMatcher.Kind.Missing }) append("underlined: missing")
                        if (diff.any { it.kind == AnswerMatcher.Kind.Extra }) {
                            if (isNotEmpty()) append(" · ")
                            append("struck out: extra")
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MnemoColors.text3,
                )
            }
        }
    }
}

/** Expected spelling in plain text; missing letters underlined in the Again colour, extra ones struck out. */
@Composable
private fun diffText(diff: List<AnswerMatcher.Segment>): AnnotatedString =
    buildAnnotatedString {
        diff.forEach { seg ->
            when (seg.kind) {
                AnswerMatcher.Kind.Same -> append(seg.text)
                AnswerMatcher.Kind.Missing ->
                    withStyle(
                        SpanStyle(
                            color = MnemoColors.gradeAgain,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ) { append(seg.text) }
                AnswerMatcher.Kind.Extra ->
                    withStyle(SpanStyle(color = MnemoColors.text3, textDecoration = TextDecoration.LineThrough)) { append(seg.text) }
            }
        }
    }

@Composable
private fun GradeBar(
    state: ReviewUiState,
    onGrade: (Rating) -> Unit,
) {
    Column {
        if (state.suggested != null) {
            Text(
                "Suggested: ${state.suggested.label} · Enter accepts, 1–4 overrides",
                style = MaterialTheme.typography.labelMedium,
                color = MnemoColors.text2,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Rating.entries.forEach { r ->
                val (fg, bg) =
                    when (r) {
                        Rating.Again -> MnemoColors.gradeAgain to MnemoColors.gradeAgainBg
                        Rating.Hard -> MnemoColors.gradeHard to MnemoColors.gradeHardBg
                        Rating.Good -> MnemoColors.gradeGood to MnemoColors.gradeGoodBg
                        Rating.Easy -> MnemoColors.gradeEasy to MnemoColors.gradeEasyBg
                    }
                val suggested = state.suggested == r
                Column(
                    Modifier
                        .weight(1f)
                        .height(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(bg)
                        // The suggested grade (Type mode) gets a ring so Enter's target is obvious.
                        .then(if (suggested) Modifier.border(2.5.dp, fg, RoundedCornerShape(16.dp)) else Modifier)
                        .clickable(role = Role.Button) { onGrade(r) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(r.label, style = MaterialTheme.typography.titleMedium, color = fg)
                    Text(
                        state.previews[r]?.intervalLabel ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = fg.copy(alpha = 0.85f),
                    )
                    if (!isMobilePlatform) {
                        Spacer(Modifier.height(2.dp))
                        Kbd(if (suggested) "⏎ / ${r.key}" else r.key)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionsRow(
    state: ReviewUiState,
    compact: Boolean,
    onUndo: () -> Unit,
    onEdit: () -> Unit,
    onSuspend: () -> Unit,
    onBury: () -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        MnemoButton(
            "Undo",
            onClick = onUndo,
            icon = MnemoIcons.Undo,
            enabled = state.canUndo,
            kbd = if (compact) null else "U",
            height = 40.dp,
        )
        MnemoButton("Edit", onClick = onEdit, icon = MnemoIcons.Edit, kbd = if (compact) null else "E", height = 40.dp)
        MnemoButton("Suspend", onClick = onSuspend, icon = MnemoIcons.Pause, kbd = if (compact) null else "S", height = 40.dp)
        MnemoButton("Bury", onClick = onBury, icon = MnemoIcons.Clock, kbd = if (compact) null else "B", height = 40.dp)
        state.view?.card?.let { c ->
            if (!compact && c.stability != null) {
                Text(
                    "S ${fmt(c.stability)} d · D ${fmt(c.difficulty ?: 0.0)} · R ${(state.retrievability * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MnemoColors.text3,
                )
            }
        }
    }
}

private fun fmt(d: Double): String = ((d * 10).roundToInt() / 10.0).toString()

@Composable
private fun FinishedView(
    state: ReviewUiState,
    onExit: () -> Unit,
    onUndo: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(36.dp)).background(MnemoColors.lime), contentAlignment = Alignment.Center) {
            Icon(MnemoIcons.Check, contentDescription = null, tint = MnemoColors.onLime, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (state.total == 0) "Nothing due" else "Session done",
            style = MaterialTheme.typography.displaySmall,
            color = MnemoColors.text,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (state.total ==
                0
            ) {
                "Come back later, or add new cards to ${state.deckName}."
            } else {
                "${state.done} reviews · ${state.sessionCorrect} remembered · ${state.sessionAgain} again"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MnemoColors.text2,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MnemoButton("Back", onClick = onExit, primary = true, height = 48.dp)
            if (state.canUndo) MnemoButton("Undo last", onClick = onUndo, icon = MnemoIcons.Undo, height = 48.dp)
        }
    }
}

@Composable
private fun Toast(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(MnemoColors.surface3).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MnemoColors.text)
    }
}

/** Card text without Markdown images or `[sound:…]` tags, for copying and sharing. */
private fun plainCardText(text: String?): String =
    text
        .orEmpty()
        .replace(Regex("""!\[[^\]]*\]\([^)]+\)"""), "")
        .replace(Regex("""\[sound:[^\]]+\]"""), "")
        .replace(Regex("""<[^>]+>"""), "")
        .trim()
