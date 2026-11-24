package app.mnemo.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.mnemo.domain.review.CustomStudy
import app.mnemo.ui.components.MnemoTextField
import app.mnemo.ui.theme.MnemoColors

/** Anki's Custom Study choices for one deck; [onStart] gets the session to run. */
@Composable
fun CustomStudyDialog(
    deckName: String,
    onDismiss: () -> Unit,
    onStart: (CustomStudy) -> Unit,
) {
    var choice by remember { mutableStateOf(0) }
    var number by remember { mutableStateOf("10") }
    var query by remember { mutableStateOf("is:review") }
    var reschedule by remember { mutableStateOf(true) }
    val options =
        listOf(
            Triple("Review forgotten cards", "Cards you pressed Again on in the last … days", "1"),
            Triple("Review ahead", "Review cards due in the next … days, early", "2"),
            Triple("Study extra new cards", "This many new cards on top of today's limit", "10"),
            Triple("Preview new cards", "Look at this many new cards without starting them", "20"),
            Triple("Study by search", "Cards matching a search, e.g. tag:verb, is:review prop:ivl<7, flag:1", "50"),
        )
    val n = number.toIntOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MnemoColors.surface2,
        title = { Text("Custom study · ${deckName.replace("::", " › ")}", color = MnemoColors.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEachIndexed { i, (title, body, default) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (choice == i) MnemoColors.surface3 else MnemoColors.surface2)
                            .clickable(role = Role.RadioButton) {
                                choice = i
                                number = default
                            }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (choice == i) MnemoColors.coral else MnemoColors.outlineStrong),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleSmall, color = MnemoColors.text)
                            Text(body, style = MaterialTheme.typography.bodySmall, color = MnemoColors.text2)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                MnemoTextField(
                    value = number,
                    onValueChange = { t -> number = t.filter { it.isDigit() }.take(5) },
                    label =
                        when (choice) {
                            0, 1 -> "Days"
                            4 -> "At most this many cards"
                            else -> "Cards"
                        },
                    singleLine = true,
                )
                if (choice == 4) {
                    Spacer(Modifier.height(10.dp))
                    MnemoTextField(value = query, onValueChange = { query = it }, label = "Search", singleLine = true)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = reschedule,
                            onCheckedChange = { reschedule = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = MnemoColors.onCoral, checkedTrackColor = MnemoColors.coral),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (reschedule) "Answers reschedule the cards" else "Practice only: cards keep their schedule",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MnemoColors.text,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = n != null, onClick = {
                val count = n ?: return@TextButton
                onStart(
                    when (choice) {
                        0 -> CustomStudy.Forgotten(count)
                        1 -> CustomStudy.Ahead(count)
                        2 -> CustomStudy.ExtraNew(count)
                        3 -> CustomStudy.PreviewNew(count)
                        else -> CustomStudy.Cram(query.trim(), count, reschedule)
                    },
                )
            }) { Text("Start", color = if (n != null) MnemoColors.coral else MnemoColors.text3) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MnemoColors.text2) } },
    )
}
