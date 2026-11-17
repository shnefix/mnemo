package app.mnemo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mnemo.ui.theme.MnemoColors

/** A labelled text field on a dark surface. */
@Composable
fun MnemoTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    onSubmit: (() -> Unit)? = null,
) {
    Column(modifier) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MnemoColors.text2)
            Spacer(Modifier.height(6.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (singleLine) 44.dp else (24 * minLines + 24).dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MnemoColors.surface2)
                .border(1.dp, MnemoColors.outline, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.text.isEmpty() && placeholder != null) {
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MnemoColors.text3)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = singleLine,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MnemoColors.text),
                cursorBrush = SolidColor(MnemoColors.coral),
                keyboardOptions = KeyboardOptions(imeAction = if (onSubmit != null) ImeAction.Done else ImeAction.Default),
                keyboardActions = KeyboardActions(onDone = { onSubmit?.invoke() }),
            )
        }
    }
}

@Composable
fun MnemoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    onSubmit: (() -> Unit)? = null,
) {
    var tfv by remember(value) { mutableStateOf(TextFieldValue(value)) }
    MnemoTextField(
        value = if (tfv.text == value) tfv else TextFieldValue(value),
        onValueChange = {
            tfv = it
            onValueChange(it.text)
        },
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        singleLine = singleLine,
        minLines = minLines,
        onSubmit = onSubmit,
    )
}

/** A dropdown picker. */
@Composable
fun <T> MnemoSelect(
    label: String?,
    options: List<T>,
    selected: T?,
    display: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Choose…",
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MnemoColors.text2)
            Spacer(Modifier.height(6.dp))
        }
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MnemoColors.surface2)
                    .border(1.dp, MnemoColors.outline, RoundedCornerShape(12.dp))
                    .clickable(role = Role.DropdownList) { open = true }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    selected?.let(display) ?: placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected == null) MnemoColors.text3 else MnemoColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(MnemoIcons.Chevron, contentDescription = null, tint = MnemoColors.text2, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(display(option), color = MnemoColors.text) },
                        onClick = {
                            open = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

/** Tag chips with an inline input to add more. */
@Composable
fun TagEditor(
    tags: List<String>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = "Tags",
) {
    var draft by remember { mutableStateOf(TextFieldValue("")) }

    fun commit() {
        val t = draft.text.trim().replace(' ', '-')
        if (t.isNotEmpty() && t !in tags) onChange(tags + t)
        draft = TextFieldValue("")
    }
    Column(modifier) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MnemoColors.text2)
            Spacer(Modifier.height(6.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MnemoColors.surface2)
                .border(1.dp, MnemoColors.outline, RoundedCornerShape(12.dp))
                .padding(8.dp),
        ) {
            if (tags.isNotEmpty()) {
                FlowTags(tags = tags, onRemove = { onChange(tags - it) })
                Spacer(Modifier.height(6.dp))
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
                if (draft.text.isEmpty()) {
                    Text("Add tag and press Enter", style = MaterialTheme.typography.bodyMedium, color = MnemoColors.text3)
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { v ->
                        if (v.text.endsWith(",") || v.text.endsWith(" ")) {
                            draft = v.copy(text = v.text.dropLast(1))
                            commit()
                        } else {
                            draft = v
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MnemoColors.text),
                    cursorBrush = SolidColor(MnemoColors.coral),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun FlowTags(
    tags: List<String>,
    onRemove: (String) -> Unit,
) {
    // Simple wrapping: rows of chips.
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { tag ->
                    Row(
                        Modifier
                            .heightIn(min = 32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MnemoColors.surface3)
                            .padding(start = 10.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(tag, style = MaterialTheme.typography.labelMedium, color = MnemoColors.text)
                        Spacer(Modifier.width(4.dp))
                        Box(
                            Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { onRemove(tag) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                MnemoIcons.Close,
                                contentDescription = "Remove tag $tag",
                                tint = MnemoColors.text2,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
