package app.mnemo.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mnemo.data.repo.parseDueSpec
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.NoteType
import app.mnemo.ui.components.MnemoSelect
import app.mnemo.ui.components.MnemoTextField
import app.mnemo.ui.theme.MnemoColors

/** Anki's "Set due date": `0` today, `3` in three days, `1-7` spread over a week, `!` also sets the interval. */
@Composable
fun SetDueDialog(
    count: Int,
    onDismiss: () -> Unit,
    onSet: (String) -> Unit,
) {
    var spec by remember { mutableStateOf("0") }
    val ok = parseDueSpec(spec) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MnemoColors.surface2,
        title = { Text("Set due date · $count cards", color = MnemoColors.text) },
        text = {
            Column {
                MnemoTextField(value = spec, onValueChange = { spec = it }, label = "Due in (days)", singleLine = true)
                Spacer(Modifier.height(8.dp))
                Text(
                    "0 = today · 3 = in 3 days · 1-7 = a random day within a week · add ! (e.g. 30!) to also make that the interval. " +
                        "New cards become review cards.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ok) MnemoColors.text2 else MnemoColors.danger,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = { onSet(spec) }) { Text("Set", color = if (ok) MnemoColors.coral else MnemoColors.text3) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MnemoColors.text2) } },
    )
}

/** Find and replace in the selected notes (or every match when [selectedNotes] is 0). */
@Composable
fun FindReplaceDialog(
    selectedCards: Int,
    matched: Int,
    noteTypes: List<NoteType>,
    onDismiss: () -> Unit,
    onReplace: (find: String, replace: String, field: String?, regex: Boolean, matchCase: Boolean) -> String?,
) {
    var find by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    var field by remember { mutableStateOf<String?>(null) }
    var regex by remember { mutableStateOf(false) }
    var matchCase by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val fieldNames = remember(noteTypes) { noteTypes.flatMap { it.fields }.distinct() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MnemoColors.surface2,
        title = {
            Text(
                if (selectedCards > 0) "Find and replace · $selectedCards selected" else "Find and replace · all $matched matches",
                color = MnemoColors.text,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                MnemoTextField(value = find, onValueChange = { find = it }, label = "Find", singleLine = true)
                Spacer(Modifier.height(8.dp))
                MnemoTextField(value = replace, onValueChange = { replace = it }, label = "Replace with", singleLine = true)
                Spacer(Modifier.height(8.dp))
                MnemoSelect(
                    label = "In",
                    options = listOf<String?>(null) + fieldNames,
                    selected = field,
                    display = { it ?: "All fields" },
                    onSelect = { field = it },
                    placeholder = "All fields",
                )
                Toggle("Regular expression ($1 in the replacement)", regex) { regex = it }
                Toggle("Match case", matchCase) { matchCase = it }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MnemoColors.danger) }
            }
        },
        confirmButton = {
            TextButton(enabled = find.isNotEmpty(), onClick = {
                error = onReplace(find, replace, field, regex, matchCase)
                if (error == null) onDismiss()
            }) { Text("Replace", color = if (find.isNotEmpty()) MnemoColors.coral else MnemoColors.text3) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MnemoColors.text2) } },
    )
}

/** Notes of one type sharing the same text in one field; tapping a group shows those notes. */
@Composable
fun DuplicatesDialog(
    noteTypes: List<NoteType>,
    find: suspend (NoteType, String) -> List<Pair<String, List<Long>>>,
    onDismiss: () -> Unit,
    onShow: (List<Long>) -> Unit,
) {
    val usable = remember(noteTypes) { noteTypes.filter { it.kind != NoteKind.Occlusion } }
    var type by remember { mutableStateOf(usable.firstOrNull()) }
    var field by remember(type) { mutableStateOf(type?.fields?.firstOrNull()) }
    var groups by remember { mutableStateOf<List<Pair<String, List<Long>>>?>(null) }
    LaunchedEffect(type, field) {
        groups = null
        val t = type ?: return@LaunchedEffect
        val f = field ?: return@LaunchedEffect
        groups = find(t, f)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MnemoColors.surface2,
        title = { Text("Find duplicates", color = MnemoColors.text) },
        text = {
            Column {
                MnemoSelect(label = "Note type", options = usable, selected = type, display = { it.name }, onSelect = { type = it })
                Spacer(Modifier.height(8.dp))
                MnemoSelect(
                    label = "Field",
                    options = type?.fields.orEmpty(),
                    selected = field,
                    display = { it },
                    onSelect = { field = it },
                )
                Spacer(Modifier.height(12.dp))
                val g = groups
                when {
                    g == null -> Text("Looking…", color = MnemoColors.text2)
                    g.isEmpty() -> Text("No duplicates.", color = MnemoColors.text2)
                    else -> {
                        Text(
                            "${g.size} groups · tap one to see its notes",
                            style = MaterialTheme.typography.labelMedium,
                            color = MnemoColors.text2,
                        )
                        Spacer(Modifier.height(6.dp))
                        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                            g.forEach { (text, ids) ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MnemoColors.surface3)
                                        .clickable(role = Role.Button) { onShow(ids) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text.lineSequence().first(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MnemoColors.text,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("×${ids.size}", color = MnemoColors.coral, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = MnemoColors.coral) } },
    )
}

@Composable
private fun Toggle(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = MnemoColors.onCoral, checkedTrackColor = MnemoColors.coral),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MnemoColors.text)
    }
}
