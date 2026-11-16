package app.mnemo.platform

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
actual fun rememberFilePicker(
    extensions: List<String>,
    onPicked: (PickedFile?) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) {
                onPicked(null)
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                val picked =
                    withContext(Dispatchers.IO) {
                        val resolver = appContext.contentResolver
                        var name = "file"
                        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                            if (c.moveToFirst()) name = c.getString(0)
                        }
                        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                        PickedFile(name, bytes)
                    }
                onPicked(picked)
            }
        }
    return { launcher.launch(arrayOf("*/*")) }
}

@Composable
actual fun rememberFolderPicker(
    extensions: List<String>,
    onPicked: (PickedFolder?) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
            if (tree == null) {
                onPicked(null)
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                val picked = withContext(Dispatchers.IO) { readTree(tree, extensions) }
                onPicked(picked)
            }
        }
    return { launcher.launch(null) }
}

/** Walks a document tree (Storage Access Framework) and reads the matching text files. */
private fun readTree(
    tree: Uri,
    extensions: List<String>,
): PickedFolder {
    val resolver = appContext.contentResolver
    val rootId = DocumentsContract.getTreeDocumentId(tree)
    val files = mutableListOf<PickedTextFile>()
    val columns =
        arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )

    fun walk(
        docId: String,
        prefix: String,
    ) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        resolver.query(children, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val name = c.getString(1)
                val mime = c.getString(2)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (!name.startsWith(".")) walk(id, "$prefix$name/")
                } else if (name.substringAfterLast('.', "").lowercase() in extensions) {
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    val text = resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: continue
                    files += PickedTextFile("$prefix$name", text)
                }
            }
        }
    }
    walk(rootId, "")
    var folderName = rootId.substringAfterLast(':').substringAfterLast('/')
    resolver
        .query(DocumentsContract.buildDocumentUriUsingTree(tree, rootId), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) folderName = c.getString(0) }
    return PickedFolder(folderName, files.sortedBy { it.path })
}

actual suspend fun saveTextFile(
    suggestedName: String,
    content: String,
): String? =
    withContext(Dispatchers.IO) {
        val file = File(appDataDir(), suggestedName)
        file.writeText(content)
        file.absolutePath
    }

actual fun decodeImage(bytes: ByteArray): ImageBitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

private var player: MediaPlayer? = null

actual fun playAudio(path: String) {
    runCatching {
        player?.release()
        player =
            MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
            }
    }
}
