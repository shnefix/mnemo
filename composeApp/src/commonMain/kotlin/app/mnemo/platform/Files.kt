package app.mnemo.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/** A file the user picked: its display name and full contents. */
class PickedFile(
    val name: String,
    val bytes: ByteArray,
) {
    val text: String get() = bytes.decodeToString()
}

/**
 * Returns a function that opens the platform file picker; [onPicked] receives
 * the file, or null when the user cancels. Must be called in composition
 * (Android needs an activity-result launcher).
 */
@Composable
expect fun rememberFilePicker(
    extensions: List<String>,
    onPicked: (PickedFile?) -> Unit,
): () -> Unit

/** Writes [content] somewhere the user chose (or the app folder on Android). Returns the path, or null on cancel. */
expect suspend fun saveTextFile(
    suggestedName: String,
    content: String,
): String?

/** Media files (images, audio) imported with decks, stored under the app data folder. */
expect object MediaFiles {
    fun dir(): String

    fun write(
        name: String,
        bytes: ByteArray,
    ): String

    fun path(name: String): String

    fun exists(name: String): Boolean

    fun read(name: String): ByteArray?
}

/** Entries of a zip archive, by name. */
expect fun unzip(bytes: ByteArray): Map<String, ByteArray>

/** Decompresses a zstd frame (used by Anki 2.1.50+ packages). */
expect fun zstdDecompress(bytes: ByteArray): ByteArray

/** Writes bytes to a temporary file and returns its path. */
expect fun writeTempFile(
    prefix: String,
    suffix: String,
    bytes: ByteArray,
): String

expect fun deleteFile(path: String)

/** Decodes an encoded image (png/jpg/gif/webp) for display. */
expect fun decodeImage(bytes: ByteArray): ImageBitmap?

/** Plays an audio file with the platform's player. */
expect fun playAudio(path: String)
