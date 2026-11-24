package app.mnemo.platform

import android.content.ContentUris
import android.content.ContentValues
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
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
        // Download/Mnemo outlives the app (a copy in the app's own folder goes with it).
        runCatching { writeToDownloads(suggestedName, content, replace = true) }.getOrNull()
            ?: File(appDataDir(), suggestedName).also { it.writeText(content) }.absolutePath
    }

actual suspend fun saveSharedBackup(
    name: String,
    content: String,
    prefix: String,
    keep: Int,
): String? =
    withContext(Dispatchers.IO) {
        // Only the real app: the on-device test build (app.mnemo.debug) starts empty on every run
        // and would fill Download/Mnemo with test backups.
        if (appContext.packageName != "app.mnemo") return@withContext null
        runCatching {
            val where = writeToDownloads(name, content, replace = true) ?: return@runCatching null
            // Only this app's own files are visible here, so older copies of other apps stay put.
            ownDownloads()
                .filter { it.second.startsWith(prefix) }
                .sortedByDescending { it.second }
                .drop(keep)
                .forEach { appContext.contentResolver.delete(it.first, null, null) }
            where
        }.getOrNull()
    }

private const val DOWNLOAD_DIR = "Mnemo"

/** This app's files in Download/Mnemo, as (uri, display name). Android 10+ only. */
private fun ownDownloads(): List<Pair<Uri, String>> {
    if (Build.VERSION.SDK_INT < 29) return emptyList()
    val out = mutableListOf<Pair<Uri, String>>()
    appContext.contentResolver
        .query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf("${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOAD_DIR%"),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                out += ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0)) to c.getString(1)
            }
        }
    return out
}

/** Writes [content] to Download/Mnemo/[name]; null on Android 9 and older (that needs a storage permission). */
actual suspend fun saveBinaryFile(
    suggestedName: String,
    bytes: ByteArray,
    mimeType: String,
): String? =
    withContext(Dispatchers.IO) {
        runCatching { writeToDownloads(suggestedName, bytes, replace = true, mimeType = mimeType) }.getOrNull()
            ?: File(appDataDir(), suggestedName).also { it.writeBytes(bytes) }.absolutePath
    }

private fun writeToDownloads(
    name: String,
    content: String,
    replace: Boolean,
): String? = writeToDownloads(name, content.encodeToByteArray(), replace, "application/json")

private fun writeToDownloads(
    name: String,
    content: ByteArray,
    replace: Boolean,
    mimeType: String,
): String? {
    if (Build.VERSION.SDK_INT < 29) return null
    val resolver = appContext.contentResolver
    // Same name again (the sync file): replace it instead of piling up "name (1)" copies.
    if (replace) ownDownloads().filter { it.second == name }.forEach { resolver.delete(it.first, null, null) }
    val values =
        ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOAD_DIR")
        }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
    resolver.openOutputStream(uri)?.use { it.write(content) } ?: return null
    return "Download/$DOWNLOAD_DIR/$name"
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
