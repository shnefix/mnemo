package app.mnemo.platform

import android.graphics.BitmapFactory
import android.media.MediaPlayer
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
        player = MediaPlayer().apply {
            setDataSource(path)
            prepare()
            start()
        }
    }
}
