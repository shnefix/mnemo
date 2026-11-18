package app.mnemo.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.awt.Desktop
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberFilePicker(
    extensions: List<String>,
    onPicked: (PickedFile?) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val file =
                withContext(Dispatchers.Swing) {
                    val chooser = JFileChooser()
                    if (extensions.isNotEmpty()) {
                        chooser.fileFilter = FileNameExtensionFilter(extensions.joinToString(", "), *extensions.toTypedArray())
                    }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
                }
            val picked = file?.let { f -> withContext(Dispatchers.IO) { PickedFile(f.name, f.readBytes()) } }
            onPicked(picked)
        }
    }
}

@Composable
actual fun rememberFolderPicker(
    extensions: List<String>,
    onPicked: (PickedFolder?) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val dir =
                withContext(Dispatchers.Swing) {
                    val chooser = JFileChooser()
                    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    chooser.dialogTitle = "Choose a folder of Markdown notes"
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
                }
            val picked =
                dir?.let { root ->
                    withContext(Dispatchers.IO) {
                        val files =
                            root
                                .walkTopDown()
                                .onEnter { d -> d == root || !d.name.startsWith(".") }
                                .filter { it.isFile && it.extension.lowercase() in extensions }
                                .sortedBy { it.path }
                                .map { f -> PickedTextFile(f.relativeTo(root).path.replace(File.separatorChar, '/'), f.readText()) }
                                .toList()
                        PickedFolder(root.name, files)
                    }
                }
            onPicked(picked)
        }
    }
}

actual suspend fun saveTextFile(
    suggestedName: String,
    content: String,
): String? =
    withContext(Dispatchers.Swing) {
        val chooser = JFileChooser()
        chooser.selectedFile = File(suggestedName)
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return@withContext null
        val file = chooser.selectedFile
        file.writeText(content)
        file.absolutePath
    }

actual fun decodeImage(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

actual fun playAudio(path: String) {
    runCatching {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(File(path))
    }
}

actual suspend fun saveSharedBackup(
    name: String,
    content: String,
    prefix: String,
    keep: Int,
): String? = null
