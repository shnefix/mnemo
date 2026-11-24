package app.mnemo.ui.notetypes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.mnemo.LocalAppGraph
import app.mnemo.domain.cards.CardGenerator
import app.mnemo.domain.cards.Templates
import app.mnemo.domain.model.CardTemplate
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.NoteType
import app.mnemo.ui.components.Eyebrow
import app.mnemo.ui.components.IconActionButton
import app.mnemo.ui.components.MarkdownText
import app.mnemo.ui.components.MnemoButton
import app.mnemo.ui.components.MnemoIcons
import app.mnemo.ui.components.MnemoTextField
import app.mnemo.ui.components.Panel
import app.mnemo.ui.decks.NameDialog
import app.mnemo.ui.nav.LocalWindowSize
import app.mnemo.ui.nav.WindowSize
import app.mnemo.ui.theme.MnemoColors
import kotlinx.coroutines.launch

private data class FieldRow(
    val original: String?,
    val name: String,
)

private data class TemplateRow(
    val originalIndex: Int?,
    val name: String,
    val front: String,
    val back: String,
)

/** Lists every note type and edits your own: their fields and card templates. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteTypesScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val compact = LocalWindowSize.current == WindowSize.Compact
    var types by remember { mutableStateOf<List<Pair<NoteType, Long>>>(emptyList()) }
    var editing by remember { mutableStateOf<NoteType?>(null) }
    var creating by remember { mutableStateOf<NoteType?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        types = graph.notes.noteTypes().map { it to graph.notes.noteCountOfType(it.id) }
    }
    LaunchedEffect(Unit) { refresh() }

    val current = editing
    if (current != null) {
        NoteTypeEditor(
            type = current,
            noteCount = types.firstOrNull { it.first.id == current.id }?.second ?: 0,
            onBack = {
                editing = null
                scope.launch { refresh() }
            },
            onSaved = { msg ->
                message = msg
                editing = null
                scope.launch { refresh() }
            },
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (compact) 20.dp else 40.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconActionButton(MnemoIcons.Back, "Back", onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Text("Note types", style = MaterialTheme.typography.headlineLarge, color = MnemoColors.text, modifier = Modifier.weight(1f))
            MnemoButton("New type", icon = MnemoIcons.Plus, primary = true, onClick = { creating = CardGenerator.newCustomType("") })
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "A note holds fields; each card template turns them into a card. Make your own type to choose the fields and how the cards look.",
            style = MaterialTheme.typography.bodyMedium,
            color = MnemoColors.text2,
        )
        message?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MnemoColors.success)
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            types.forEach { (t, count) ->
                val custom = t.kind == NoteKind.Custom
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MnemoColors.surface2)
                        .clickable(enabled = custom, role = Role.Button) { editing = t }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t.name, style = MaterialTheme.typography.titleSmall, color = MnemoColors.text)
                        Text(
                            (if (custom) "Your type" else "Built in") +
                                " · ${t.fields.size} fields · ${t.templates.size} card type${if (t.templates.size == 1) "" else "s"} · $count notes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MnemoColors.text2,
                        )
                    }
                    if (custom) {
                        MnemoButton("Edit", height = 36.dp, onClick = { editing = t })
                    } else if (t.kind != NoteKind.Occlusion && t.kind != NoteKind.Cloze) {
                        MnemoButton("Copy", height = 36.dp, onClick = {
                            creating = t.copy(id = 0, name = "${t.name} (copy)", kind = NoteKind.Custom, templates = copyTemplates(t))
                        })
                    }
                }
            }
        }
    }

    creating?.let { base ->
        NameDialog(
            title = "New note type",
            hint = null,
            initial = base.name,
            confirm = "Create",
            onDismiss = { creating = null },
            onConfirm = { name ->
                val trimmed = name.trim()
                if (trimmed.isEmpty()) return@NameDialog
                creating = null
                scope.launch {
                    val id = graph.notes.createNoteType(base.copy(name = trimmed))
                    refresh()
                    editing = graph.notes.noteType(id)
                }
            },
        )
    }
}

/** Built-in templates written out with the fields they really show (cloze and vocabulary render specially). */
private fun copyTemplates(t: NoteType): List<CardTemplate> =
    when (t.kind) {
        NoteKind.TypeAnswer -> listOf(CardTemplate("Card 1", "{{Front}}\n\n{{type:Back}}", "{{Back}}"))
        NoteKind.Vocab ->
            listOf(
                CardTemplate("Word → Translation", "{{Word}}", "**{{Translation}}**\n\n{{#Example}}> {{Example}}{{/Example}}"),
                CardTemplate("Translation → Word", "{{Translation}}", "**{{Gender}} {{Word}}**\n\n{{#Example}}> {{Example}}{{/Example}}"),
            )
        else -> t.templates
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteTypeEditor(
    type: NoteType,
    noteCount: Long,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val compact = LocalWindowSize.current == WindowSize.Compact
    var name by remember(type.id) { mutableStateOf(type.name) }
    var fields by remember(type.id) { mutableStateOf(type.fields.map { FieldRow(it, it) }) }
    var templates by remember(type.id) {
        mutableStateOf(type.templates.mapIndexed { i, t -> TemplateRow(i, t.name, t.front, t.back) })
    }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf<List<Int>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun build(): Triple<NoteType, Map<String, String>, List<Int>>? {
        val names = fields.map { it.name.trim() }
        if (name.isBlank()) return null.also { error = "Give the note type a name." }
        if (names.any { it.isEmpty() }) return null.also { error = "Every field needs a name." }
        if (names.toSet().size != names.size) return null.also { error = "Two fields have the same name." }
        if (names.any { it.contains('{') || it.contains('}') || it.contains(':') }) {
            return null.also {
                error =
                    "Field names can't contain { } or :."
            }
        }
        if (templates.isEmpty()) return null.also { error = "Keep at least one card type." }
        val unknown =
            templates
                .flatMap {
                    Templates.fieldsUsed(
                        it.front,
                    ) + Templates.fieldsUsed(it.back)
                }.filter { it !in names }
                .distinct()
        if (unknown.isNotEmpty()) return null.also { error = "Templates use fields that don't exist: ${unknown.joinToString()}" }
        if (templates.any { t -> Templates.fieldsUsed(t.front).isEmpty() }) {
            return null.also {
                error =
                    "Each front needs at least one {{Field}}."
            }
        }
        val renamed = fields.filter { it.original != null && it.original != it.name.trim() }.associate { it.original!! to it.name.trim() }
        val removed = type.templates.indices.filter { i -> templates.none { it.originalIndex == i } }
        val updated =
            type.copy(
                name = name.trim(),
                fields = names,
                templates = templates.map { CardTemplate(it.name.trim().ifEmpty { "Card" }, it.front, it.back) },
            )
        return Triple(updated, renamed, removed)
    }

    fun save(force: Boolean = false) {
        val (updated, renamed, removed) = build() ?: return
        if (removed.isNotEmpty() && !force) {
            confirmRemove = removed
            return
        }
        scope.launch {
            graph.notes.updateNoteType(updated, renamed, removed)
            onSaved("Saved ${updated.name}")
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (compact) 20.dp else 40.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconActionButton(MnemoIcons.Back, "Back", onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Note type", style = MaterialTheme.typography.labelLarge, color = MnemoColors.text2)
                Text(type.name, style = MaterialTheme.typography.headlineSmall, color = MnemoColors.text)
            }
        }
        Spacer(Modifier.height(18.dp))
        Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Panel {
                MnemoTextField(value = name, onValueChange = { name = it }, label = "Name", singleLine = true)
            }
            Panel {
                Eyebrow("Fields")
                Spacer(Modifier.height(4.dp))
                Text(
                    "Renaming a field moves its text in all $noteCount notes; removing one deletes that text.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MnemoColors.text2,
                )
                Spacer(Modifier.height(10.dp))
                fields.forEachIndexed { i, f ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        MnemoTextField(
                            value = f.name,
                            onValueChange = { v -> fields = fields.toMutableList().also { it[i] = f.copy(name = v) } },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(6.dp))
                        MnemoButton("↑", height = 40.dp, enabled = i > 0, onClick = {
                            fields = fields.toMutableList().also { it.add(i - 1, it.removeAt(i)) }
                        })
                        Spacer(Modifier.width(4.dp))
                        MnemoButton("✕", height = 40.dp, enabled = fields.size > 1, onClick = {
                            fields = fields.toMutableList().also { it.removeAt(i) }
                        })
                    }
                }
                Spacer(Modifier.height(6.dp))
                MnemoButton("Add field", icon = MnemoIcons.Plus, onClick = { fields = fields + FieldRow(null, "Field ${fields.size + 1}") })
            }
            Panel {
                Eyebrow("Card types")
                Spacer(Modifier.height(4.dp))
                Text(
                    "{{Field}} shows a field · {{#Field}}…{{/Field}} only if it has text · {{^Field}}…{{/Field}} only if empty · " +
                        "{{type:Field}} a box to type the answer · {{hint:Field}} · {{FrontSide}} on the back. Markdown works.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MnemoColors.text2,
                )
                templates.forEachIndexed { i, t ->
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        MnemoTextField(
                            value = t.name,
                            onValueChange = { v -> templates = templates.toMutableList().also { it[i] = t.copy(name = v) } },
                            label = "Card type ${i + 1}",
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(6.dp))
                        MnemoButton("Remove", height = 44.dp, danger = true, enabled = templates.size > 1, onClick = {
                            templates = templates.toMutableList().also { it.removeAt(i) }
                        })
                    }
                    Spacer(Modifier.height(8.dp))
                    MnemoTextField(
                        value = t.front,
                        onValueChange = { v -> templates = templates.toMutableList().also { it[i] = t.copy(front = v) } },
                        label = "Front",
                        minLines = 2,
                    )
                    Spacer(Modifier.height(8.dp))
                    MnemoTextField(
                        value = t.back,
                        onValueChange = { v -> templates = templates.toMutableList().also { it[i] = t.copy(back = v) } },
                        label = "Back",
                        minLines = 2,
                    )
                    TemplatePreview(fields.map { it.name.trim() }, t)
                }
                Spacer(Modifier.height(12.dp))
                MnemoButton("Add card type", icon = MnemoIcons.Plus, onClick = {
                    val first = fields.firstOrNull()?.name ?: "Front"
                    val second = fields.getOrNull(1)?.name ?: first
                    templates = templates + TemplateRow(null, "Card ${templates.size + 1}", "{{$second}}", "{{$first}}")
                })
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MnemoColors.danger) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MnemoButton("Save", primary = true, onClick = { save() })
                MnemoButton("Cancel", onClick = onBack)
                MnemoButton("Delete type", danger = true, onClick = { confirmDelete = true })
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    confirmRemove?.let { removed ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            containerColor = MnemoColors.surface2,
            title = { Text("Remove ${removed.size} card type${if (removed.size == 1) "" else "s"}?", color = MnemoColors.text) },
            text = {
                Text(
                    "Their cards in all $noteCount notes are deleted, with their review history. This cannot be undone.",
                    color = MnemoColors.text2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = null
                    save(force = true)
                }) { Text("Remove and save", color = MnemoColors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel", color = MnemoColors.text2) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MnemoColors.surface2,
            title = { Text("Delete ${type.name}?", color = MnemoColors.text) },
            text = {
                Text(
                    if (noteCount > 0) "$noteCount notes still use this type. Delete or move them first." else "The note type is removed.",
                    color = MnemoColors.text2,
                )
            },
            confirmButton = {
                TextButton(enabled = noteCount == 0L, onClick = {
                    confirmDelete = false
                    scope.launch {
                        if (graph.notes.deleteNoteType(type.id)) onSaved("Deleted ${type.name}") else error = "Notes still use this type."
                    }
                }) { Text("Delete", color = if (noteCount == 0L) MnemoColors.danger else MnemoColors.text3) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = MnemoColors.text2) } },
        )
    }
}

/** The card this template makes from sample text ("Field name" in each field). */
@Composable
private fun TemplatePreview(
    fields: List<String>,
    t: TemplateRow,
) {
    val sample = fields.associateWith { it }
    val front = Templates.render(t.front, sample, question = true)
    val back = Templates.render(t.back, sample, question = false, frontSide = front.text)
    Spacer(Modifier.height(8.dp))
    Panel(padding = PaddingValues(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Preview", style = MaterialTheme.typography.labelSmall, color = MnemoColors.text3)
        Spacer(Modifier.height(4.dp))
        MarkdownText(front.text.ifBlank { "…" } + (front.typeField?.let { "\n\n_[type the $it]_" } ?: ""))
        Spacer(Modifier.height(6.dp))
        MarkdownText(back.text.ifBlank { "…" }, color = MnemoColors.text2)
    }
}
