package app.mnemo.platform

import androidx.compose.runtime.Composable

/** Speech-to-text for answering out loud (Speak mode). */
expect object SpeechInput {
    /** False where there is no recognizer at all (desktop): the UI offers a text box instead. */
    val available: Boolean
}

/**
 * Returns a function that listens once and calls [onResult] with what was said, or null when
 * nothing was recognised or recognition is not available on this device.
 */
@Composable
expect fun rememberSpeechInput(
    lang: String?,
    onResult: (String?) -> Unit,
): () -> Unit
