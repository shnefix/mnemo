package app.mnemo.platform

import android.speech.tts.TextToSpeech
import java.util.Locale

actual object Speech {
    private var tts: TextToSpeech? = null
    private var ready = false

    private fun engine(): TextToSpeech {
        tts?.let { return it }
        val t = TextToSpeech(appContext) { status -> ready = status == TextToSpeech.SUCCESS }
        tts = t
        return t
    }

    actual val available: Boolean get() = true

    actual fun speak(
        text: String,
        lang: String?,
    ) {
        val t = engine()
        if (!ready) return
        lang?.let { t.language = Locale.forLanguageTag(it) }
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mnemo")
    }
}
