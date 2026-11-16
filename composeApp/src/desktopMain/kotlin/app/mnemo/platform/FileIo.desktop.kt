package app.mnemo.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

actual object FileIo {
    actual suspend fun openText(extensions: List<String>): String? =
        withContext(Dispatchers.Swing) {
            val chooser = JFileChooser()
            if (extensions.isNotEmpty()) {
                chooser.fileFilter =
                    FileNameExtensionFilter(extensions.joinToString(", "), *extensions.toTypedArray())
            }
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@withContext null
            chooser.selectedFile.readText()
        }

    actual suspend fun saveText(
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
}
