package app.mnemo.platform

import android.speech.tts.TextToSpeech
import java.util.Locale

actual object Speech {
    private var tts: TextToSpeech? = null

    @Volatile private var ready = false
    private var currentLang: String? = null

    /** Text asked for before the engine finished starting; spoken as soon as it is ready. */
    private var pending: Pair<String, String?>? = null

    private fun engine(): TextToSpeech {
        tts?.let { return it }
        val t =
            TextToSpeech(appContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) pending?.let { (text, lang) -> say(text, lang) }
                pending = null
            }
        tts = t
        return t
    }

    actual val available: Boolean get() = true

    actual fun warmUp() {
        runCatching { engine() }
    }

    actual fun speak(
        text: String,
        lang: String?,
    ) {
        engine()
        if (!ready) {
            pending = text to lang
            return
        }
        say(text, lang)
    }

    private fun say(
        text: String,
        lang: String?,
    ) {
        val t = tts ?: return
        // Switching language is slow; only do it when it changes.
        if (lang != null && lang != currentLang) {
            t.language = Locale.forLanguageTag(lang)
            currentLang = lang
        }
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mnemo")
    }

    actual fun stop() {
        pending = null
        tts?.takeIf { ready }?.stop()
    }
}
