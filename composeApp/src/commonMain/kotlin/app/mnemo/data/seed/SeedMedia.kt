package app.mnemo.data.seed

import app.mnemo.platform.MediaFiles
import mnemo.composeapp.generated.resources.Res

/** Pictures bundled with the app for the built-in decks (`composeResources/files/seed/…`). */
internal object SeedMedia {
    /**
     * Copies each bundled `files/seed/[folder]/<file>` into the media folder as `[prefix]<file>`,
     * unless it is already there. Returns how many could not be read (the cards then show
     * "missing image" rather than failing the install).
     */
    suspend fun install(
        folder: String,
        files: List<String>,
        prefix: String,
    ): Int {
        var failed = 0
        files.forEach { file ->
            val name = prefix + file
            if (MediaFiles.exists(name)) return@forEach
            runCatching { MediaFiles.write(name, Res.readBytes("files/seed/$folder/$file")) }.onFailure { failed += 1 }
        }
        return failed
    }
}
