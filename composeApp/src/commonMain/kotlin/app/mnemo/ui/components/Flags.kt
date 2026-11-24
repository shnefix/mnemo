package app.mnemo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mnemo.domain.model.Flag
import app.mnemo.ui.theme.MnemoColors

/** Anki's flag colours. */
fun flagColor(number: Int): Color =
    when (number) {
        1 -> Color(0xFFFF5A5F)
        2 -> Color(0xFFFF9F43)
        3 -> Color(0xFF3DDC84)
        4 -> Color(0xFF4DA3FF)
        5 -> Color(0xFFFF7AD9)
        6 -> Color(0xFF2ED3C6)
        7 -> Color(0xFFA77BFF)
        else -> Color.Transparent
    }

/** A small coloured flag mark; nothing for flag 0. */
@Composable
fun FlagMark(
    number: Int,
    size: Dp = 14.dp,
) {
    if (number == 0) return
    Icon(
        MnemoIcons.Flag,
        contentDescription = "${Flag.of(number)?.label ?: ""} flag",
        tint = flagColor(number),
        modifier = Modifier.size(size),
    )
}

/** A dropdown of the seven flags plus "No flag"; [current] is ticked. */
@Composable
fun FlagMenu(
    expanded: Boolean,
    current: Int?,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Flag.entries.forEach { f ->
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(f.label, color = MnemoColors.text)
                        Spacer(Modifier.width(10.dp))
                        Text("Ctrl+${f.number}", color = MnemoColors.text3)
                    }
                },
                leadingIcon = {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(flagColor(f.number)))
                },
                trailingIcon = {
                    if (current ==
                        f.number
                    ) {
                        Icon(MnemoIcons.Check, contentDescription = "Current", tint = MnemoColors.text2)
                    }
                },
                onClick = { onPick(f.number) },
            )
        }
        DropdownMenuItem(text = { Text("No flag", color = MnemoColors.text) }, onClick = { onPick(0) })
    }
}
