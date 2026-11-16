package app.mnemo.platform

/** Platform file dialogs. Return null when the user cancels or the platform has no dialog. */
expect object FileIo {
    /** Opens a file chooser and returns the chosen file's text, or null. */
    suspend fun openText(extensions: List<String>): String?

    /** Opens a save dialog and writes [content]; returns the path written, or null. */
    suspend fun saveText(
        suggestedName: String,
        content: String,
    ): String?
}
