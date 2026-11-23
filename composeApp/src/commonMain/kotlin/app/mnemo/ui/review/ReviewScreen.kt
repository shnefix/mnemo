package app.mnemo.ui.review

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.mnemo.LocalAppGraph
import app.mnemo.domain.model.CardState
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.Rating
import app.mnemo.domain.model.ReviewMode
import app.mnemo.domain.review.AnswerMatcher
import app.mnemo.platform.Speech
import app.mnemo.platform.SpeechInput
import app.mnemo.platform.isMobilePlatform
import app.mnemo.platform.rememberSpeechInput
import app.mnemo.ui.components.IconActionButton
import app.mnemo.ui.components.Kbd
import app.mnemo.ui.components.Layers
import app.mnemo.ui.components.MarkdownText
import app.mnemo.ui.components.MnemoButton
import app.mnemo.ui.components.MnemoIcons
import app.mnemo.ui.components.MnemoTextField
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
            ReviewTopBar(
                state = state,
                onExit = onExit,
                onMode = vm::setMode,
                onSearch = onSearch,
                onUndo = vm::undo,
                onEdit = { state.view?.let { onEdit(it.card.noteId) } },
                onSuspend = vm::suspendCurrent,
                onBury = vm::buryCurrent,
            )
            when {
                state.loading -> Box(Modifier.fillMaxSize())
                state.finished -> FinishedView(state, onExit, onUndo = vm::undo)
                else ->
                    Box(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.fillMaxSize().padding(horizontal = if (compact) 16.dp else 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().weight(1f)) {
                                Spacer(Modifier.height(if (compact) 8.dp else 20.dp))
                                // The card takes all the room there is; the buttons stay at the bottom, under the thumb.
                                FocusCard(
                                    state = state,
                                    compact = compact,
                                    onReveal = vm::reveal,
                                    onSwipe = vm::grade,
                                    onTyped = vm::setTyped,
                                    onSpoken = vm::setSpoken,
                                    onSpokenMore = vm::addSpoken,
                                    modifier = Modifier.weight(1f),
                                )
                                // Type mode keeps a Check button (the card itself holds the text field);
                                // everywhere else a tap on the card shows the answer.
                                AnimatedVisibility(
                                    visible = !state.revealed && state.typing,
                                    enter = fadeIn(tween(200)) + expandVertically(tween(260)),
                                    exit = fadeOut(tween(120)) + shrinkVertically(tween(200)),
                                ) {
                                    Column {
                                        Spacer(Modifier.height(14.dp))
                                        ShowAnswerButton("Check answer", onClick = vm::reveal)
                                    }
                                }
                                // The grade buttons rise in under the card once the answer shows.
                                AnimatedVisibility(
                                    visible = state.revealed,
                                    enter =
                                        fadeIn(tween(220, delayMillis = 60)) +
                                            expandVertically(spring(dampingRatio = 0.85f, stiffness = 500f), expandFrom = Alignment.Top),
                                    exit = fadeOut(tween(120)) + shrinkVertically(tween(220), shrinkTowards = Alignment.Top),
                                ) {
                                    Column {
                                        Spacer(Modifier.height(14.dp))
                                        GradeBar(state = state, onGrade = vm::grade)
                                        if (isMobilePlatform) {
                                            Spacer(Modifier.height(10.dp))
                                            Text(
                                                "Or swipe the card: left for Again, right for Good",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MnemoColors.text2,
                                                modifier = Modifier.align(Alignment.CenterHorizontally).then(rise(4)),
                                            )
                                        }
                                    }
                                }
                                if (!compact) {
                                    // Desktop has the room (and the keys): the actions stay in view with their shortcuts.
                                    Spacer(Modifier.height(12.dp))
                                    ActionsRow(state, compact, onUndo = vm::undo, onEdit = {
                                        state.view?.let { onEdit(it.card.noteId) }
                                    }, onSuspend = vm::suspendCurrent, onBury = vm::buryCurrent)
                                }
                                Spacer(Modifier.height(if (compact) 20.dp else 28.dp))
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
    onExit: () -> Unit,
    onMode: (ReviewMode) -> Unit,
    onSearch: () -> Unit,
    onUndo: () -> Unit,
    onEdit: () -> Unit,
    onSuspend: () -> Unit,
    onBury: () -> Unit,
) {
    var modeMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }

    @Suppress("DEPRECATION")
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconActionButton(MnemoIcons.Close, "End session", onClick = onExit, tint = MnemoColors.text, background = Color.Transparent)
        Column(Modifier.weight(1f).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                state.deckName.replace("::", " › "),
                style = MaterialTheme.typography.titleSmall,
                color = MnemoColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.semantics(mergeDescendants = true) { contentDescription = "${state.done} of ${state.total} cards done" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(120.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MnemoColors.surface3),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(state.progress)
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MnemoColors.coral),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("${state.done}/${state.total}", style = MaterialTheme.typography.labelSmall, color = MnemoColors.text2)
            }
        }
        Box {
            Row(
                Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, MnemoColors.outline, RoundedCornerShape(999.dp))
                    .clickable(role = Role.Button) { modeMenu = true }
                    .semantics { contentDescription = "How you answer: ${state.mode.label}" }
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    state.mode.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MnemoColors.text2,
                )
                Spacer(Modifier.width(2.dp))
                Icon(MnemoIcons.Chevron, contentDescription = null, tint = MnemoColors.text2, modifier = Modifier.size(16.dp).rotate(90f))
            }
            DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                ReviewMode.entries.forEach { m ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(m.label, color = MnemoColors.text, fontWeight = if (m == state.mode) FontWeight.Bold else null)
                                Text(modeHint(m), style = MaterialTheme.typography.bodySmall, color = MnemoColors.text2)
                            }
                        },
                        trailingIcon =
                            if (m == state.mode) {
                                { Icon(MnemoIcons.Check, contentDescription = "Selected", tint = MnemoColors.text) }
                            } else {
                                null
                            },
                        onClick = {
                            modeMenu = false
                            onMode(m)
                        },
                    )
                }
            }
        }
        Box {
            IconActionButton(
                MnemoIcons.More,
                "Undo, edit, suspend or bury",
                onClick = { moreMenu = true },
                tint = MnemoColors.text2,
                background = Color.Transparent,
            )
            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                MoreItem("Undo last answer", MnemoIcons.Undo, enabled = state.canUndo) {
                    moreMenu = false
                    onUndo()
                }
                MoreItem("Edit card", MnemoIcons.Edit) {
                    moreMenu = false
                    onEdit()
                }
                MoreItem("Copy card", MnemoIcons.Copy) {
                    moreMenu = false
                    val r = state.rendered
                    val text =
                        if (state.revealed) {
                            "${plainCardText(r?.question)}\n\n${plainCardText(r?.answer)}"
                        } else {
                            plainCardText(r?.question)
                        }
                    clipboard.setText(AnnotatedString(text))
                }
                MoreItem("Bury until tomorrow", MnemoIcons.Clock) {
                    moreMenu = false
                    onBury()
                }
                MoreItem("Suspend card", MnemoIcons.Pause) {
                    moreMenu = false
                    onSuspend()
                }
                MoreItem("Search cards", MnemoIcons.Search) {
                    moreMenu = false
                    onSearch()
                }
            }
        }
    }
}

@Composable
private fun MoreItem(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label, color = if (enabled) MnemoColors.text else MnemoColors.text3) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = MnemoColors.text2) },
        enabled = enabled,
        onClick = onClick,
    )
}

private fun modeHint(mode: ReviewMode): String =
    when (mode) {
        ReviewMode.Classic -> "Show the answer, then grade yourself"
        ReviewMode.Type -> "Type the answer; Mnemo checks it"
        ReviewMode.Speak -> "Hear the question, answer out loud"
    }

/** The big light button under the card. */
@Composable
private fun ShowAnswerButton(
    text: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MnemoColors.text)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MnemoColors.background)
        if (!isMobilePlatform) {
            Spacer(Modifier.width(10.dp))
            Kbd("Space")
        }
    }
}

/**
 * The one card, filling the screen. Before the reveal the question sits big in the middle;
 * after it, the question shrinks to a reminder and the answer gets the room.
 * Once revealed, swipe right for Good and left for Again.
 */
@Composable
private fun FocusCard(
    state: ReviewUiState,
    compact: Boolean,
    onReveal: () -> Unit,
    onSwipe: (Rating) -> Unit,
    onTyped: (String) -> Unit,
    onSpoken: (String) -> Unit,
    onSpokenMore: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val view = state.view ?: return
    val tapToReveal = !state.revealed && !state.typing
    val pad = if (compact) 22.dp else 32.dp

    Column(
        modifier
            .fillMaxWidth()
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
            .clickable(enabled = tapToReveal, role = Role.Button, onClick = onReveal),
    ) {
        // The card's kind stays put while its two sides change underneath.
        Box(Modifier.padding(start = pad, end = pad, top = if (compact) 20.dp else 28.dp)) { CardMeta(state) }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val viewport = maxHeight
            AnimatedContent(
                targetState = state,
                contentKey = { it.view?.card?.id to it.revealed },
                transitionSpec = {
                    val sameCard = initialState.view?.card?.id == targetState.view?.card?.id
                    if (sameCard) {
                        // Showing the answer: the question lifts away and the answer settles in.
                        (fadeIn(tween(220, delayMillis = 70)) + scaleIn(tween(360, easing = FastOutSlowInEasing), initialScale = 0.97f))
                            .togetherWith(fadeOut(tween(130)) + slideOutVertically(tween(200)) { -it / 14 })
                    } else {
                        // The next card.
                        (
                            fadeIn(tween(260, delayMillis = 60)) +
                                scaleIn(tween(340, delayMillis = 60, easing = FastOutSlowInEasing), initialScale = 0.94f)
                        ).togetherWith(fadeOut(tween(110)))
                    }.using(SizeTransform(clip = false))
                },
                label = "card face",
            ) { s ->
                CardFace(s, compact, viewport, onReveal, onTyped, onSpoken, onSpokenMore)
            }
        }
    }
}

/** One side of the card: the question (centred) or the answer (with the question as a reminder). */
@Composable
private fun CardFace(
    state: ReviewUiState,
    compact: Boolean,
    viewport: Dp,
    onReveal: () -> Unit,
    onTyped: (String) -> Unit,
    onSpoken: (String) -> Unit,
    onSpokenMore: (String) -> Unit,
) {
    val view = state.view ?: return
    val rendered = state.rendered
    val tapToReveal = !state.revealed && !state.typing
    val pad = if (compact) 22.dp else 32.dp
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .heightIn(min = viewport)
            .padding(start = pad, end = pad, top = 4.dp, bottom = if (compact) 20.dp else 28.dp),
        verticalArrangement = if (state.revealed) Arrangement.Top else Arrangement.SpaceBetween,
    ) {
        if (!state.revealed) {
            // Something at the top, so SpaceBetween centres the question between it and the hint.
            Spacer(Modifier.height(1.dp))
            // The question, centred; short ones big and in the display face.
            val question = rendered?.question.orEmpty().replace("[...]", "\\_\\_\\_\\_\\_\\_")
            val short = plainCardText(question).length <= 140
            Column(
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val big =
                    MaterialTheme.typography.headlineLarge.copy(
                        fontSize = if (compact) 28.sp else 32.sp,
                        lineHeight = if (compact) 35.sp else 40.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.sp,
                        textAlign = TextAlign.Center,
                    )
                val simple = if (short) remember(question) { inlineMarkdown(question) } else null
                when {
                    // Plain text with bold / italics: drawn here so every line is centred.
                    simple != null -> Text(simple, style = big, color = MnemoColors.text, modifier = Modifier.fillMaxWidth())
                    short -> CardText(question, style = big)
                    else -> CardText(question, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 28.sp))
                }
                ListenButton(state, rendered?.listen)
                if (state.typing) {
                    Spacer(Modifier.height(22.dp))
                    MnemoTextField(
                        value = state.typed,
                        onValueChange = onTyped,
                        placeholder = "Type your answer, then Enter",
                        singleLine = true,
                        onSubmit = onReveal,
                    )
                }
                if (state.mode == ReviewMode.Speak && !state.typing) {
                    Spacer(Modifier.height(22.dp))
                    SpokenAnswer(state, onSpoken, onSpokenMore, onReveal)
                }
            }
            Text(
                when {
                    !tapToReveal -> ""
                    isMobilePlatform -> "Tap the card to see the answer"
                    else -> "Click the card or press Space to see the answer"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MnemoColors.text2,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        } else {
            Spacer(Modifier.height(16.dp))
            // The question again, small: a reminder of what was asked. A cloze answer already
            // is the whole sentence, so it doesn't need one.
            if (view.noteKind != NoteKind.Cloze) {
                CardText(
                    rendered?.question.orEmpty().replace("[...]", "\\_\\_\\_\\_"),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp),
                    color = MnemoColors.text2,
                    modifier = rise(0),
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .then(rise(0))
                        .background(MnemoColors.outline),
                )
                Spacer(Modifier.height(18.dp))
            } else {
                Spacer(Modifier.height(6.dp))
            }
            if (state.typing) {
                Box(rise(1)) { TypedFeedback(state) }
                Spacer(Modifier.height(16.dp))
            }
            if (state.mode == ReviewMode.Speak && !state.typing) {
                SpokenAnswer(state, onSpoken, onSpokenMore, onReveal)
                if (state.spoken.isNotBlank()) Spacer(Modifier.height(16.dp))
            }
            AnswerText(rendered?.answer.orEmpty(), compact)
            Column(rise(3)) { ListenButton(state, rendered?.listen) }
            if (view.tags.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    view.tags.joinToString("  ") { "#$it" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MnemoColors.text2,
                    modifier = rise(3),
                )
            }
        }
    }
}

/**
 * Fades a piece in while it rises a little, [index] steps after the others, so the answer
 * arrives top to bottom instead of all at once.
 */
@Composable
private fun rise(index: Int): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(380, delayMillis = 90 + index * 70, easing = FastOutSlowInEasing)) }
    val dy = with(LocalDensity.current) { 14.dp.toPx() }
    return Modifier.graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * dy
    }
}

/** "● Review  ·  recall 82%" along the top of the card, in the layer colours. */
@Composable
private fun CardMeta(state: ReviewUiState) {
    val card = state.view?.card ?: return
    val leech = card.lapses >= state.leechThreshold
    val (label, dot) =
        when {
            leech -> "Leech" to MnemoColors.danger
            else ->
                when (card.state) {
                    CardState.New -> "New"
                    CardState.Learning -> "Learning"
                    CardState.Relearning -> "Relearning"
                    CardState.Review -> "Review"
                } to Layers.dot(card.state)
        }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MnemoColors.text2)
        val kind = state.view.noteKind.displayName
        if (kind.isNotBlank()) {
            Text("  ·  $kind", style = MaterialTheme.typography.labelLarge, color = MnemoColors.text2)
        }
        Spacer(Modifier.weight(1f))
        if (card.state != CardState.New) {
            Text(
                "recall ${(state.retrievability * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MnemoColors.text2,
            )
        }
    }
}

/**
 * The answer: a short first line (a word, a name, a number) is shown big in the accent colour,
 * the rest as normal reading text underneath.
 */
@Composable
private fun AnswerText(
    answer: String,
    compact: Boolean,
) {
    val trimmed = answer.trim()
    val firstLine = trimmed.substringBefore('\n').trim()
    val rest = trimmed.substringAfter('\n', "").trim()
    val headline =
        firstLine.isNotEmpty() &&
            plainCardText(firstLine).length <= 32 &&
            !firstLine.startsWith("#") &&
            !firstLine.startsWith("```") &&
            !firstLine.startsWith("|") &&
            !firstLine.startsWith("- ") &&
            "![" !in firstLine &&
            "[sound:" !in firstLine
    val body = MaterialTheme.typography.bodyLarge.copy(fontSize = if (compact) 18.sp else 19.sp, lineHeight = if (compact) 27.sp else 29.sp)
    if (headline) {
        CardText(
            firstLine,
            modifier = rise(1),
            style =
                MaterialTheme.typography.displaySmall.copy(
                    fontSize = if (compact) 40.sp else 46.sp,
                    lineHeight = if (compact) 44.sp else 50.sp,
                ),
            color = if (MnemoColors.isDark) MnemoColors.lime else MnemoColors.text,
        )
        if (rest.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            CardText(rest, style = body, modifier = rise(2))
        }
    } else {
        CardText(trimmed, style = body, modifier = rise(1))
    }
}

/**
 * Card Markdown in a given text style: the renderer reads its sizes from the theme, so the
 * theme's body styles are swapped for [style] around it.
 */
@Composable
private fun CardText(
    text: String,
    style: TextStyle,
    color: Color = MnemoColors.text,
    modifier: Modifier = Modifier,
) {
    val t = MaterialTheme.typography
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = t.copy(bodyLarge = style, bodyMedium = style, bodySmall = style.copy(fontSize = style.fontSize * 0.85f)),
    ) {
        MarkdownText(text, color = color, modifier = modifier.fillMaxWidth())
    }
}

@Composable
private fun ListenButton(
    state: ReviewUiState,
    listen: String?,
) {
    val rendered = state.rendered
    if ((state.language != null || listen != null) && Speech.available) {
        Spacer(Modifier.height(14.dp))
        MnemoButton(
            if (listen != null) "Listen again" else "Listen",
            icon = MnemoIcons.Speaker,
            height = 40.dp,
            onClick = {
                Speech.speak(
                    listen ?: if (state.revealed) plainCardText(rendered?.answer) else plainCardText(rendered?.question),
                    state.language,
                )
            },
        )
    } else if (listen != null && !state.revealed) {
        // No voice on this device: show the sentence, so the card still works as a typing drill.
        Spacer(Modifier.height(12.dp))
        Text("No voice available — read it: $listen", style = MaterialTheme.typography.bodyMedium, color = MnemoColors.text2)
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
                "Suggested: ${state.suggested.label}" + if (isMobilePlatform) "" else " · Enter accepts, 1–4 overrides",
                style = MaterialTheme.typography.labelMedium,
                color = MnemoColors.text2,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Rating.entries.forEach { r ->
                // Good is filled in: it's the answer you give most.
                val filled = r == Rating.Good
                val (fg, bg) =
                    when (r) {
                        Rating.Again -> MnemoColors.gradeAgain to MnemoColors.gradeAgainBg
                        Rating.Hard -> MnemoColors.gradeHard to MnemoColors.gradeHardBg
                        Rating.Good ->
                            (if (MnemoColors.isDark) Color(0xFF0B2A1F) else Color.White) to MnemoColors.gradeGood
                        Rating.Easy -> MnemoColors.gradeEasy to MnemoColors.gradeEasyBg
                    }
                val suggested = state.suggested == r
                val ring = if (filled) MnemoColors.text else fg
                Column(
                    Modifier
                        .weight(1f)
                        .then(rise(r.ordinal))
                        .height(if (isMobilePlatform) 68.dp else 80.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(bg)
                        // The suggested grade (Type mode) gets a ring so Enter's target is obvious.
                        .then(if (suggested) Modifier.border(2.5.dp, ring, RoundedCornerShape(18.dp)) else Modifier)
                        .clickable(role = Role.Button) { onGrade(r) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(r.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = fg)
                    Text(
                        state.previews[r]?.intervalLabel ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (filled) FontWeight.SemiBold else null,
                        color = if (filled) fg else fg.copy(alpha = 0.9f),
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

private val blockMarkdown =
    Regex("""!\[|\[sound:|```|^\s*[-+] |^\s*\* |^\s*\d+\. |^\s*#|^\s*>|\||\$|<[a-zA-Z/]|\[[^\]]*]\(""", RegexOption.MULTILINE)

/**
 * Short card text with only **bold**, *italics* and line breaks, as styled text; null when it
 * has anything more (images, lists, code, maths, links, HTML), which the Markdown renderer handles.
 */
private fun inlineMarkdown(text: String): AnnotatedString? {
    if (blockMarkdown.containsMatchIn(text)) return null
    val s = text.trim().replace(Regex("""\n{2,}"""), "\n")
    return buildAnnotatedString {
        var bold = false
        var italic = false
        var i = 0
        while (i < s.length) {
            when {
                s[i] == '\\' && i + 1 < s.length -> {
                    append(s[i + 1])
                    i += 2
                }
                s.startsWith("**", i) -> {
                    if (bold) pop() else pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    bold = !bold
                    i += 2
                }
                s[i] == '*' -> {
                    if (italic) {
                        pop()
                    } else {
                        pushStyle(
                            SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium, color = MnemoColors.text2),
                        )
                    }
                    italic = !italic
                    i++
                }
                else -> {
                    append(s[i])
                    i++
                }
            }
        }
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
