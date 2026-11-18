package app.mnemo.platform

import androidx.compose.runtime.Composable

actual object SpeechInput {
    actual val available: Boolean = false
}

@Composable
actual fun rememberSpeechInput(
    lang: String?,
    onResult: (String?) -> Unit,
): () -> Unit = { onResult(null) }
