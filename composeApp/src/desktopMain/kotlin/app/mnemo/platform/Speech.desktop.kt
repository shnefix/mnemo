package app.mnemo.platform

import java.io.File

/**
 * Desktop has no built-in TTS; use a speech command if one is installed:
 * espeak-ng / espeak (Linux, macOS via brew), `say` (macOS), or
 * PowerShell's SpeechSynthesizer (Windows).
 */
actual object Speech {
    private val os = System.getProperty("os.name").lowercase()

    private fun onPath(cmd: String): Boolean =
        System
            .getenv("PATH")
            .orEmpty()
            .split(File.pathSeparator)
            .any { File(it, cmd).canExecute() || File(it, "$cmd.exe").canExecute() }

    private val engine: String? by lazy {
        when {
            os.contains("mac") && onPath("say") -> "say"
            onPath("espeak-ng") -> "espeak-ng"
            onPath("espeak") -> "espeak"
            onPath("spd-say") -> "spd-say"
            os.contains("win") -> "powershell"
            else -> null
        }
    }

    actual val available: Boolean get() = engine != null

    actual fun speak(
        text: String,
        lang: String?,
    ) {
        val e = engine ?: return
        val cmd: List<String> =
            when (e) {
                "say" -> listOf("say", text)
                "espeak-ng", "espeak" -> listOf(e, "-v", lang ?: "en", text)
                "spd-say" -> listOfNotNull("spd-say", lang?.let { "-l" }, lang, text)
                else ->
                    listOf(
                        "powershell",
                        "-NoProfile",
                        "-Command",
                        "Add-Type -AssemblyName System.Speech; (New-Object System.Speech.Synthesis.SpeechSynthesizer).Speak('${text.replace(
                            "'",
                            "''",
                        )}')",
                    )
            }
        runCatching { ProcessBuilder(cmd).redirectErrorStream(true).start() }
    }
}
