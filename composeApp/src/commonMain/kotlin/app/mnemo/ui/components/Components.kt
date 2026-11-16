package app.mnemo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mnemo.ui.theme.MnemoColors
import app.mnemo.ui.theme.masteryColor
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

/** A raised surface on the black ground. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = MnemoColors.surface,
    radius: Dp = 20.dp,
    padding: PaddingValues = PaddingValues(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var m = modifier.clip(RoundedCornerShape(radius)).background(color)
    if (onClick != null) m = m.clickable(role = Role.Button, onClick = onClick)
    Column(modifier = m.padding(padding), content = content)
}

/** Page title with optional subtitle, Design A style. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelLarge, color = MnemoColors.text2)
                Spacer(Modifier.height(2.dp))
            }
            Text(title, style = MaterialTheme.typography.displaySmall, color = MnemoColors.text)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun MnemoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 44.dp,
    kbd: String? = null,
) {
    val bg =
        when {
            !enabled -> MnemoColors.surface2
            primary -> MnemoColors.coral
            danger -> MnemoColors.gradeAgainBg
            else -> MnemoColors.surface2
        }
    val fg =
        when {
            !enabled -> MnemoColors.text3
            primary -> MnemoColors.onCoral
            danger -> MnemoColors.danger
            else -> MnemoColors.text
        }
    Row(
        modifier =
            modifier
                .height(height)
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
            if (kbd != null) {
                Spacer(Modifier.width(10.dp))
                Kbd(kbd, onDark = !primary)
            }
        }
    }
}

@Composable
fun IconActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MnemoColors.text2,
    background: Color = MnemoColors.surface2,
    size: Dp = 44.dp,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(RoundedCornerShape(size / 2))
                .background(background)
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Keyboard shortcut hint. */
@Composable
fun Kbd(
    text: String,
    onDark: Boolean = true,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (onDark) MnemoColors.text2 else MnemoColors.onCoral.copy(alpha = 0.8f),
        modifier =
            Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(if (onDark) MnemoColors.surface3 else Color.Black.copy(alpha = 0.12f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MnemoColors.surface2,
    textColor: Color = MnemoColors.text2,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    var m =
        modifier
            .height(32.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) MnemoColors.coral else color)
    if (onClick != null) m = m.clickable(role = Role.Button, onClick = onClick)
    Box(m.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MnemoColors.onCoral else textColor,
        )
    }
}

/** Segmented control (Classic / Type / Speak, Q&A / Cloze / Both …). */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabledMask: List<Boolean>? = null,
) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(MnemoColors.surface2).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val enabled = enabledMask?.getOrNull(i) ?: true
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (on) MnemoColors.text else Color.Transparent)
                        .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color =
                        when {
                            on -> MnemoColors.background
                            !enabled -> MnemoColors.text3
                            else -> MnemoColors.text2
                        },
                )
            }
        }
    }
}

/** Thin bar with a mastery percentage, colored by the mastery ramp. */
@Composable
fun MasteryBar(
    mastery: Double,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MnemoColors.surface3),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(mastery.coerceIn(0.0, 1.0).toFloat())
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(masteryColor(mastery)),
            )
        }
        if (showLabel) {
            Spacer(Modifier.width(10.dp))
            Text(
                "${(mastery * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MnemoColors.text,
                modifier = Modifier.width(40.dp),
            )
        }
    }
}

/** Stacked bar: new / learning / review. */
@Composable
fun DueBar(
    new: Int,
    learning: Int,
    review: Int,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
) {
    val total = (new + learning + review).coerceAtLeast(1)
    Row(modifier.height(height), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(new to MnemoColors.stateNew, learning to MnemoColors.stateLearning, review to MnemoColors.stateReview)
            .filter { it.first > 0 }
            .forEach { (n, c) ->
                Box(
                    Modifier
                        .weight(n.toFloat() / total)
                        .height(height)
                        .clip(RoundedCornerShape(4.dp))
                        .background(c),
                )
            }
        if (new + learning + review == 0) {
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MnemoColors.surface3),
            )
        }
    }
}

@Composable
fun LegendDot(
    color: Color,
    label: String,
    value: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MnemoColors.text2)
        if (value != null) {
            Spacer(Modifier.width(6.dp))
            Text(value, style = MaterialTheme.typography.labelMedium, color = MnemoColors.text)
        }
    }
}

/** Section label in small caps. */
@Composable
fun Eyebrow(
    text: String,
    color: Color = MnemoColors.text2,
) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
}

/** Renders light Markdown (bold, italic, code, lists) with the app's colors. */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Markdown(
        content = text,
        colors = markdownColor(text = MnemoColors.text),
        typography = markdownTypography(),
        modifier = modifier,
    )
}

@Composable
fun StatTile(
    label: String,
    value: String,
    note: String? = null,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    Panel(modifier = modifier, padding = PaddingValues(18.dp)) {
        Eyebrow(label)
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.displayMedium, color = accent ?: MnemoColors.text)
        if (note != null) {
            Spacer(Modifier.height(2.dp))
            Text(note, style = MaterialTheme.typography.bodySmall, color = MnemoColors.text2)
        }
    }
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MnemoColors.outline))
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().border(1.dp, MnemoColors.outline, RoundedCornerShape(20.dp)).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MnemoColors.text)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MnemoColors.text2)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}
