package app.mnemo.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.mnemo.LocalAppGraph
import app.mnemo.domain.cards.ClozeParser
import app.mnemo.domain.cards.Occlusions
import app.mnemo.domain.model.NoteKind
import app.mnemo.platform.MediaFiles
import app.mnemo.platform.rememberFilePicker
import app.mnemo.ui.components.Divider
import app.mnemo.ui.components.Eyebrow
import app.mnemo.ui.components.IconActionButton
import app.mnemo.ui.components.MarkdownText
import app.mnemo.ui.components.MnemoButton
import app.mnemo.ui.components.MnemoIcons
import app.mnemo.ui.components.MnemoSelect
import app.mnemo.ui.components.MnemoTextField
import app.mnemo.ui.components.OcclusionEditor
import app.mnemo.ui.components.OcclusionImage
import app.mnemo.ui.components.Panel
import app.mnemo.ui.components.Segmented
import app.mnemo.ui.components.TagEditor
import app.mnemo.ui.decks.NameDialog
import app.mnemo.ui.nav.LocalWindowSize
import app.mnemo.ui.nav.WindowSize
import app.mnemo.ui.theme.MnemoColors

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NoteEditorScreen(
    noteId: Long?,
    deckId: Long?,
    onClose: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val vm: NoteEditorViewModel = viewModel(key = "editor-$noteId-$deckId") { NoteEditorViewModel(graph, noteId, deckId) }
    val state by vm.state.collectAsState()
    val compact = LocalWindowSize.current == WindowSize.Compact
    var newDeck by remember { mutableStateOf(false) }
    // Keep TextFieldValues locally so selection survives recomposition (needed for the cloze helper).
    val fieldValues = remember { mutableMapOf<String, TextFieldValue>() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (compact) 20.dp else 40.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconActionButton(MnemoIcons.Close, "Close editor", onClick = onClose)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.editingNoteId != null) "Edit note" else "Add cards",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MnemoColors.text,
                )
                if (state.savedCount > 0) {
                    Text("${state.savedCount} saved this session", style = MaterialTheme.typography.bodySmall, color = MnemoColors.text2)
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).widthIn(max = 720.dp)) {
                if (state.noteTypes.isNotEmpty()) {
                    // A dropdown: there are too many types (and your own) for a segmented row.
                    MnemoSelect(
                        label = "Note type",
                        options = if (state.editingNoteId == null) state.noteTypes else listOfNotNull(state.noteType),
                        selected = state.noteType,
                        display = { it.name },
                        onSelect = vm::selectNoteType,
                    )
                    Spacer(Modifier.height(16.dp))
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                    MnemoSelect(
                        label = "Deck",
                        options = state.decks,
                        selected = state.deck,
                        display = { it.path },
                        onSelect = vm::selectDeck,
                        modifier = Modifier.weight(1f),
                        placeholder = "Create a deck first",
                    )
                    MnemoButton("New deck", onClick = { newDeck = true })
                }
                Spacer(Modifier.height(16.dp))

                val occlusion = state.noteType?.kind == NoteKind.Occlusion
                if (occlusion) {
                    OcclusionFields(state, vm)
                    Spacer(Modifier.height(14.dp))
                }
                state.noteType?.fields?.filter { !occlusion || (it != "Image" && it != "Occlusions") }?.forEach { field ->
                    val current =
                        fieldValues[field]?.takeIf { it.text == state.fields[field] } ?: TextFieldValue(state.fields[field].orEmpty())
                    val isClozeText = state.noteType?.kind == NoteKind.Cloze && field == "Text"
                    Row(verticalAlignment = Alignment.Bottom) {
                        MnemoTextField(
                            value = current,
                            onValueChange = { v ->
                                fieldValues[field] = v
                                vm.setField(field, v.text)
                            },
                            label = field,
                            placeholder = if (isClozeText) "Select a word and press “Cloze” → {{c1::word}}" else "Markdown allowed",
                            minLines = if (field == "Front" || field == "Text") 3 else 2,
                            modifier = Modifier.weight(1f),
                        )
                        if (state.editingNoteId == null) {
                            Spacer(Modifier.width(6.dp))
                            val pinned = field in state.sticky
                            MnemoButton(
                                if (pinned) "Pinned" else "Pin",
                                height = 36.dp,
                                primary = pinned,
                                onClick = { vm.toggleSticky(field) },
                            )
                        }
                        if (isClozeText) {
                            Spacer(Modifier.width(10.dp))
                            MnemoButton(
                                "Cloze",
                                onClick = {
                                    val sel = current.selection
                                    val wrapped = ClozeParser.wrapAsNextCloze(current.text, sel.min, sel.max)
                                    val v = TextFieldValue(wrapped, TextRange(wrapped.length))
                                    fieldValues[field] = v
                                    vm.setField(field, wrapped)
                                },
                                kbd = "⌘⇧C",
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                TagEditor(tags = state.tags, onChange = vm::setTags)
                Spacer(Modifier.height(20.dp))

                state.error?.let {
                    Text(it, color = MnemoColors.danger, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                }
                state.lastSavedMessage?.let {
                    Text(it, color = MnemoColors.success, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.editingNoteId == null) {
                        MnemoButton(
                            "Save & add another",
                            onClick = { vm.save(another = true) {} },
                            primary = true,
                            enabled = state.canSave,
                            kbd = "⌘⏎",
                        )
                        MnemoButton("Save & close", onClick = { vm.save(another = false, onDone = onClose) }, enabled = state.canSave)
                    } else {
                        MnemoButton(
                            "Save changes",
                            onClick = { vm.save(another = false, onDone = onClose) },
                            primary = true,
                            enabled = state.canSave,
                        )
                        MnemoButton("Cancel", onClick = onClose)
                    }
                }
                if (compact) {
                    Spacer(Modifier.height(24.dp))
                    PreviewColumn(state)
                }
            }
            if (!compact) {
                Column(Modifier.width(360.dp)) { PreviewColumn(state) }
            }
        }
    }

    if (newDeck) {
        NameDialog(
            title = "New deck",
            hint = "Use :: for nested decks",
            initial = "",
            confirm = "Create",
            onDismiss = { newDeck = false },
            onConfirm = { path ->
                vm.createDeck(path) {}
                newDeck = false
            },
        )
    }
}

@Composable
private fun PreviewColumn(state: EditorUiState) {
    Eyebrow("Preview · ${state.previews.size} card${if (state.previews.size == 1) "" else "s"}")
    Spacer(Modifier.height(10.dp))
    if (state.previews.isEmpty()) {
        Text(
            when (state.noteType?.kind) {
                NoteKind.Cloze -> "Add at least one {{c1::cloze}} to generate a card."
                NoteKind.Occlusion -> "Choose an image and drag boxes over what to hide."
                else -> "Fill in the fields to see the cards."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MnemoColors.text2,
        )
    }
    state.previews.forEachIndexed { i, card ->
        Panel(padding = PaddingValues(16.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Card ${i + 1}", style = MaterialTheme.typography.labelSmall, color = MnemoColors.text3)
            Spacer(Modifier.height(6.dp))
            card.occlusion?.let {
                OcclusionImage(it, revealed = false)
                Spacer(Modifier.height(6.dp))
            }
            if (card.occlusion == null || card.question.isNotBlank()) Box { MarkdownText(card.question.ifBlank { "…" }) }
            Spacer(Modifier.height(10.dp))
            Divider()
            Spacer(Modifier.height(10.dp))
            Box { MarkdownText(card.answer.ifBlank { "…" }) }
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** The image-occlusion part of the editor: pick a picture, then drag boxes over what to hide. */
@Composable
private fun OcclusionFields(
    state: EditorUiState,
    vm: NoteEditorViewModel,
) {
    val graph = LocalAppGraph.current
    val image = state.fields["Image"].orEmpty()
    val occ = Occlusions.parse(state.fields["Occlusions"])
    var selected by remember(image) { mutableStateOf<Int?>(null) }
    var join by remember { mutableStateOf(false) }
    val pick =
        rememberFilePicker(listOf("png", "jpg", "jpeg", "gif", "webp")) { file ->
            if (file == null) return@rememberFilePicker
            val safe =
                file.name
                    .substringAfterLast('/')
                    .substringAfterLast('\\')
                    .replace(Regex("[^A-Za-z0-9._-]"), "_")
            val name = "occlusion-${graph.clock.now()}-$safe"
            MediaFiles.write(name, file.bytes)
            vm.setField("Image", name)
            vm.setField("Occlusions", Occlusions(occ.mode).encode())
        }

    fun update(o: Occlusions) = vm.setField("Occlusions", o.encode())
    Text("Image", style = MaterialTheme.typography.labelLarge, color = MnemoColors.text2)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MnemoButton(if (image.isEmpty()) "Choose image…" else "Change image…", icon = MnemoIcons.Import, onClick = pick)
        if (image.isNotEmpty()) {
            MnemoButton("Delete box", enabled = selected != null, onClick = {
                selected?.let { i -> update(occ.copy(masks = occ.masks.filterIndexed { j, _ -> j != i })) }
                selected = null
            })
            MnemoButton("Clear boxes", enabled = occ.masks.isNotEmpty(), onClick = {
                update(occ.copy(masks = emptyList()))
                selected = null
            })
        }
    }
    if (image.isEmpty()) return
    Spacer(Modifier.height(12.dp))
    Segmented(
        options = listOf("Hide all, ask one", "Hide one, ask one"),
        selected = if (occ.hideAll) 0 else 1,
        onSelect = { update(occ.copy(mode = if (it == 0) Occlusions.MODE_ALL else Occlusions.MODE_ONE)) },
    )
    Spacer(Modifier.height(8.dp))
    Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = join,
            onCheckedChange = { join = it },
            colors = SwitchDefaults.colors(checkedThumbColor = MnemoColors.onCoral, checkedTrackColor = MnemoColors.coral),
        )
        Spacer(Modifier.width(10.dp))
        Text("New boxes join the last one (asked together)", style = MaterialTheme.typography.bodyMedium, color = MnemoColors.text)
    }
    Text(
        "Drag on the picture to draw a box; tap a box to select it. ${occ.groups.size} card${if (occ.groups.size == 1) "" else "s"}.",
        style = MaterialTheme.typography.bodySmall,
        color = MnemoColors.text2,
    )
    Spacer(Modifier.height(8.dp))
    OcclusionEditor(
        image = image,
        masks = occ.masks,
        selected = selected,
        joinGroup = join,
        onMasks = { update(occ.copy(masks = it)) },
        onSelect = { selected = it },
    )
}
