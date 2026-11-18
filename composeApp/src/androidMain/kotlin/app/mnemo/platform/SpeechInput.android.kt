package app.mnemo.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState

actual object SpeechInput {
    // The system's speech recognizer (Google's on most phones) runs in its own app, so no
    // microphone permission is needed here; a phone without one reports it when tapped.
    actual val available: Boolean = true
}

@Composable
actual fun rememberSpeechInput(
    lang: String?,
    onResult: (String?) -> Unit,
): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val text =
                if (r.resultCode == Activity.RESULT_OK) {
                    r.data
                        ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                        ?.firstOrNull()
                } else {
                    null
                }
            callback.value(text)
        }
    return {
        val intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Answer out loud")
                lang?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
                // Interview answers run long: give pauses some room before it stops listening.
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4_000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3_000L)
            }
        try {
            launcher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            callback.value(null)
        }
    }
}
