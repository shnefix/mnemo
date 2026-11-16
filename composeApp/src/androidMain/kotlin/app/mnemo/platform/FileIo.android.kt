package app.mnemo.platform

import java.io.File

/**
 * Phase 1 Android fallback: no system picker yet (Phase 2 adds the Storage
 * Access Framework). Export writes into the app's files dir; import returns
 * null so the UI falls back to pasting text.
 */
actual object FileIo {
    actual suspend fun openText(extensions: List<String>): String? = null

    actual suspend fun saveText(
        suggestedName: String,
        content: String,
    ): String? {
        val file = File(appDataDir(), suggestedName)
        file.writeText(content)
        return file.absolutePath
    }
}
