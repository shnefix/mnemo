package app.mnemo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mnemo.data.repo.DueCounts
import app.mnemo.domain.model.CardState
import app.mnemo.ui.theme.MnemoColors

/**
 * The three kinds of due card ("layers") and their colours, shared by Today, the deck list,
 * the deck page and Browse so a colour always means the same thing.
 */
object Layers {
    /** Amber for New stays the same in both themes so the dark label on it reads. */
    val amber = Color(0xFFFFC247)

    val review: Color get() = MnemoColors.sky
    val learning: Color get() = MnemoColors.lime
    val new: Color get() = amber

    /** A small dot on a surface: the fills above on dark, the stronger state colours on light. */
    fun dot(state: CardState): Color =
        when (state) {
            CardState.New -> if (MnemoColors.isDark) new else MnemoColors.stateNew
            CardState.Learning, CardState.Relearning -> if (MnemoColors.isDark) learning else MnemoColors.stateLearning
            CardState.Review -> if (MnemoColors.isDark) review else MnemoColors.stateReview
        }

    /** A deck's own colour: picked from its id, so it stays put when the list is re-sorted. */
    fun deckColor(id: Long): Color {
        val all = listOf(MnemoColors.sky, MnemoColors.lime, MnemoColors.lavender, amber, MnemoColors.coral)
        return all[(id % all.size).toInt().let { if (it < 0) it + all.size else it }]
    }
}

private class LayerTab(
    val id: String,
    val label: String,
    val count: Int,
    val color: Color,
    val inset: Int,
)

/**
 * A coral "due" card with REVIEW, LEARNING and NEW tabs peeking out above it like stacked folders.
 * Tap a tab to study just that type (the card changes to match), tap it again to go back to everything.
 * [onStart] gets the chosen layer id ("review", "learning", "new") or null for everything.
 */
@Composable
fun LayeredHero(
    due: DueCounts,
    minutes: Int,
    heading: String,
    startLabel: String,
    chosen: String?,
    onSelect: (String?) -> Unit,
    onStart: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layers =
        listOf(
            LayerTab("review", "REVIEW", due.review, Layers.review, 18),
            LayerTab("learning", "LEARNING", due.learning, Layers.learning, 12),
            LayerTab("new", "NEW", due.new, Layers.new, 6),
        )
    val current = layers.firstOrNull { it.id == chosen }
    val count = current?.count ?: due.total
    // The estimate is for everything due; a layer takes its share of it.
    val shownMinutes =
        if (current == null || due.total == 0) {
            minutes
        } else {
            (minutes * current.count / due.total).coerceAtLeast(if (current.count > 0) 1 else 0)
        }
    Column(modifier) {
        layers.forEach { t ->
            val on = t.id == chosen
            // 58dp tall, but only the top 38dp takes up room: the next tab (or the card) covers the rest.
            Box(
                Modifier
                    .padding(horizontal = t.inset.dp)
                    .fillMaxWidth()
                    .height(38.dp)
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .height(58.dp)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(t.color)
                    .semantics { selected = on }
                    .clickable(role = Role.Tab) { onSelect(if (on) null else t.id) }
                    .padding(horizontal = 18.dp),
            ) {
                Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        t.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.9.sp,
                        color = MnemoColors.onCoral,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${t.count} cards", style = MaterialTheme.typography.labelLarge, color = MnemoColors.onCoral)
                    if (on) {
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.size(22.dp).clip(CircleShape).background(MnemoColors.onCoral), contentAlignment = Alignment.Center) {
                            Icon(MnemoIcons.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
        Panel(
            color = MnemoColors.coral,
            radius = 26.dp,
            padding = PaddingValues(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (current == null) heading else "${current.label} only").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MnemoColors.onCoral,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                HeroPill("about $shownMinutes min")
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$count", style = MaterialTheme.typography.displayLarge, color = MnemoColors.onCoral)
                Spacer(Modifier.width(10.dp))
                Text(
                    "cards",
                    style = MaterialTheme.typography.titleLarge,
                    color = MnemoColors.onCoral,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (current == null) "Tap a layer to study just that type" else "Tap the layer again to study everything",
                style = MaterialTheme.typography.labelLarge,
                color = MnemoColors.onCoral,
            )
            Spacer(Modifier.height(12.dp))
            DarkButton(
                text = if (current == null) startLabel else "Study ${current.count} ${current.id} cards",
                onClick = { onStart(current?.id) },
                modifier = Modifier.fillMaxWidth(),
                enabled = count > 0,
            )
        }
    }
}

@Composable
fun HeroPill(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MnemoColors.onCoral.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MnemoColors.onCoral)
    }
}

/** The dark "Start" button that sits on a coloured card. */
@Composable
fun DarkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) MnemoColors.onCoral else MnemoColors.onCoral.copy(alpha = 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Color(0xFFF2F3F7), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(10.dp))
        Icon(MnemoIcons.ArrowRight, contentDescription = null, tint = Color(0xFFF2F3F7), modifier = Modifier.size(18.dp))
    }
}

/** Due cards as one bar split into Review / Learning / New, in the layer colours. */
@Composable
fun LayerBar(
    due: DueCounts,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
) {
    Row(modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        val parts =
            listOf(due.review to Layers.review, due.learning to Layers.learning, due.new to Layers.new).filter { it.first > 0 }
        parts.forEach { (n, c) ->
            Box(
                Modifier
                    .weight(n.toFloat())
                    .fillMaxSize()
                    .clip(RoundedCornerShape(999.dp))
                    .background(c),
            )
        }
        if (parts.isEmpty()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(999.dp))
                    .background(MnemoColors.surface3),
            )
        }
    }
}

/** "2 review · 4 new due", or "Nothing due today". */
fun dueSummary(due: DueCounts): String {
    val parts =
        listOfNotNull(
            due.review.takeIf { it > 0 }?.let { "$it review" },
            due.learning.takeIf { it > 0 }?.let { "$it learning" },
            due.new.takeIf { it > 0 }?.let { "$it new" },
        )
    return if (parts.isEmpty()) "Nothing due today" else parts.joinToString(" · ") + " due"
}

/** A ring filled to [mastery] in [color], with the percentage (or "New") inside. */
@Composable
fun MasteryRing(
    mastery: Double,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    stroke: Dp = 5.dp,
    caption: String? = null,
) {
    val track = MnemoColors.surface3
    val started = mastery > 0.0
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val d = this.size.minDimension - w
            val topLeft = Offset(w / 2, w / 2)
            drawArc(track, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(w))
            if (started) {
                drawArc(
                    color,
                    -90f,
                    (360f * mastery.coerceIn(0.0, 1.0)).toFloat(),
                    false,
                    topLeft,
                    Size(d, d),
                    style = Stroke(w, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (started) "${(mastery * 100).toInt()}%" else "New",
                style = if (size >= 60.dp) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MnemoColors.text,
            )
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MnemoColors.text2)
            }
        }
    }
}
