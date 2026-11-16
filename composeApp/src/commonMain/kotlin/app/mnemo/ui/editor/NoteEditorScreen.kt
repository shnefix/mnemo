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
import app.mnemo.domain.model.NoteKind
import app.mnemo.ui.components.Divider
import app.mnemo.ui.components.Eyebrow
import app.mnemo.ui.components.IconActionButton
import app.mnemo.ui.components.MarkdownText
import app.mnemo.ui.components.MnemoButton
import app.mnemo.ui.components.MnemoIcons
import app.mnemo.ui.components.MnemoSelect
import app.mnemo.ui.components.MnemoTextField
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
                    val idx = state.noteTypes.indexOf(state.noteType).coerceAtLeast(0)
                    Segmented(
                        options = state.noteTypes.map { it.kind.displayName },
                        selected = idx,
                        onSelect = { vm.selectNoteType(state.noteTypes[it]) },
                        enabledMask = state.noteTypes.map { state.editingNoteId == null || it.id == state.noteType?.id },
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

                state.noteType?.fields?.forEach { field ->
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
            if (state.noteType?.kind ==
                NoteKind.Cloze
            ) {
                "Add at least one {{c1::cloze}} to generate a card."
            } else {
                "Fill in the fields to see the cards."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MnemoColors.text2,
        )
    }
    state.previews.forEachIndexed { i, card ->
        Panel(padding = PaddingValues(16.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Card ${i + 1}", style = MaterialTheme.typography.labelSmall, color = MnemoColors.text3)
            Spacer(Modifier.height(6.dp))
            Box { MarkdownText(card.question.ifBlank { "…" }) }
            Spacer(Modifier.height(10.dp))
            Divider()
            Spacer(Modifier.height(10.dp))
            Box { MarkdownText(card.answer.ifBlank { "…" }) }
        }
        Spacer(Modifier.height(10.dp))
    }
}
