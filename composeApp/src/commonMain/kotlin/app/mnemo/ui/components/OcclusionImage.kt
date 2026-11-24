package app.mnemo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.mnemo.domain.cards.Mask
import app.mnemo.domain.cards.OcclusionView
import app.mnemo.platform.MediaFiles
import app.mnemo.platform.decodeImage
import app.mnemo.ui.theme.MnemoColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The picture of an occlusion note, decoded once per name. */
@Composable
private fun rememberMediaImage(name: String): ImageBitmap? = remember(name) { MediaFiles.read(name)?.let { decodeImage(it) } }

/**
 * An image-occlusion card: the asked box(es) in the accent colour; in "hide all" mode the
 * other boxes stay covered in grey. Once [revealed], the asked boxes open (with an outline).
 */
@Composable
fun OcclusionImage(
    view: OcclusionView,
    revealed: Boolean,
    modifier: Modifier = Modifier,
) {
    val bitmap = rememberMediaImage(view.image)
    if (bitmap == null) {
        Text("[missing image: ${view.image}]", style = MaterialTheme.typography.bodySmall, color = MnemoColors.text3)
        return
    }
    val accent = MnemoColors.coral
    val covered = MnemoColors.surface3
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1))
            .clip(RoundedCornerShape(12.dp)),
    ) {
        Image(bitmap, contentDescription = view.image, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            view.occlusions.masks.forEach { m ->
                val asked = m.group == view.group
                when {
                    asked && revealed -> drawBox(m, accent, outlineOnly = true)
                    asked -> drawBox(m, accent)
                    view.occlusions.hideAll -> drawBox(m, covered)
                }
            }
        }
    }
}

private fun DrawScope.drawBox(
    m: Mask,
    color: Color,
    outlineOnly: Boolean = false,
    selected: Boolean = false,
) {
    val topLeft = Offset((m.x * size.width).toFloat(), (m.y * size.height).toFloat())
    val boxSize = Size((m.w * size.width).toFloat(), (m.h * size.height).toFloat())
    val r = CornerRadius(6f, 6f)
    if (outlineOnly) {
        drawRoundRect(color, topLeft, boxSize, r, style = Stroke(width = 4f))
    } else {
        drawRoundRect(color.copy(alpha = 0.92f), topLeft, boxSize, r)
        if (selected) drawRoundRect(Color.White, topLeft, boxSize, r, style = Stroke(width = 4f))
    }
}

/**
 * Draws occlusion boxes on [image]: drag to draw a box, tap a box to select it. Each new box is
 * its own card unless [joinGroup] is set, which puts it in the group of the box drawn before.
 */
@Composable
fun OcclusionEditor(
    image: String,
    masks: List<Mask>,
    selected: Int?,
    joinGroup: Boolean,
    onMasks: (List<Mask>) -> Unit,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap = rememberMediaImage(image)
    if (bitmap == null) {
        Text("[missing image: $image]", style = MaterialTheme.typography.bodySmall, color = MnemoColors.text3)
        return
    }
    val current by rememberUpdatedState(masks)
    val join by rememberUpdatedState(joinGroup)
    var drag by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
    val accent = MnemoColors.coral
    val other = MnemoColors.lavender
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1))
            .clip(RoundedCornerShape(12.dp)),
    ) {
        Image(bitmap, contentDescription = image, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        val x = p.x / size.width
                        val y = p.y / size.height
                        val hit = current.indexOfLast { x >= it.x && x <= it.x + it.w && y >= it.y && y <= it.y + it.h }
                        onSelect(hit.takeIf { it >= 0 })
                    }
                }.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { drag = it to it },
                        onDrag = { change, _ -> drag = drag?.let { it.first to change.position } },
                        onDragCancel = { drag = null },
                        onDragEnd = {
                            val d = drag
                            drag = null
                            if (d != null) {
                                val w = size.width.toDouble()
                                val h = size.height.toDouble()
                                val x0 = (min(d.first.x, d.second.x) / w).coerceIn(0.0, 1.0)
                                val y0 = (min(d.first.y, d.second.y) / h).coerceIn(0.0, 1.0)
                                val x1 = (max(d.first.x, d.second.x) / w).coerceIn(0.0, 1.0)
                                val y1 = (max(d.first.y, d.second.y) / h).coerceIn(0.0, 1.0)
                                if (abs(x1 - x0) > 0.01 && abs(y1 - y0) > 0.01) {
                                    val group =
                                        if (join &&
                                            current.isNotEmpty()
                                        ) {
                                            current.last().group
                                        } else {
                                            (current.maxOfOrNull { it.group } ?: 0) + 1
                                        }
                                    onMasks(current + Mask(x0, y0, x1 - x0, y1 - y0, group))
                                    onSelect(current.size)
                                }
                            }
                        },
                    )
                },
        ) {
            val selectedGroup = selected?.let { masks.getOrNull(it)?.group }
            masks.forEachIndexed { i, m ->
                drawBox(m, if (m.group == selectedGroup) accent else other, selected = i == selected)
            }
            drag?.let { (a, b) ->
                drawRoundRect(
                    accent.copy(alpha = 0.5f),
                    Offset(min(a.x, b.x), min(a.y, b.y)),
                    Size(abs(b.x - a.x), abs(b.y - a.y)),
                    CornerRadius(6f, 6f),
                )
            }
        }
    }
}
