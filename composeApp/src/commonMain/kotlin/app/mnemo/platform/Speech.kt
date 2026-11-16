package app.mnemo.platform

/**
 * Text-to-speech. [lang] is a BCP-47-ish code ("de", "es"). Returns false when
 * the platform has no voice available, so the UI can hide the button.
 */
expect object Speech {
    val available: Boolean

    fun speak(
        text: String,
        lang: String?,
    )
}
