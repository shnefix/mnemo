package app.mnemo.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.mnemo.platform.MediaFiles
import app.mnemo.platform.decodeImage
import app.mnemo.platform.playAudio
import app.mnemo.ui.theme.MnemoColors
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

private val imageRef = Regex("""!\[[^\]]*\]\(([^)]+)\)""")
private val soundRef = Regex("""\[sound:([^\]]+)\]""")

/**
 * Renders a field: Markdown text, with `![](file)` images and `[sound:file]`
 * clips (from the media folder) shown inline, as Anki cards expect.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val sounds = remember(text) { soundRef.findAll(text).map { it.groupValues[1] }.toList() }
    val images = remember(text) { imageRef.findAll(text).map { it.groupValues[1] }.toList() }
    val plain = remember(text) { text.replace(soundRef, "").replace(imageRef, "").trim() }

    Column(modifier) {
        if (plain.isNotEmpty()) {
            Markdown(
                content = plain,
                colors = markdownColor(text = MnemoColors.text),
                typography = markdownTypography(),
            )
        }
        images.forEach { name ->
            val bitmap: ImageBitmap? = remember(name) { MediaFiles.read(name)?.let { decodeImage(it) } }
            if (bitmap != null) {
                Spacer(Modifier.height(10.dp))
                Image(
                    bitmap = bitmap,
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Spacer(Modifier.height(6.dp))
                Text("[missing image: $name]", style = MaterialTheme.typography.bodySmall, color = MnemoColors.text3)
            }
        }
        sounds.forEach { name ->
            Spacer(Modifier.height(8.dp))
            val exists = remember(name) { MediaFiles.exists(name) }
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MnemoColors.surface2)
                    .clickable(enabled = exists, role = Role.Button) { playAudio(MediaFiles.path(name)) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    MnemoIcons.Speaker,
                    contentDescription = "Play $name",
                    tint = if (exists) MnemoColors.coral else MnemoColors.text3,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (exists) "Play audio" else "Audio missing: $name",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (exists) MnemoColors.text else MnemoColors.text3,
                )
            }
        }
    }
}
