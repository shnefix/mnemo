package app.mnemo.platform

import java.io.File

actual fun readAppFile(name: String): String? = File(appDataDir(), name).takeIf { it.exists() }?.readText()

actual fun writeAppFile(
    name: String,
    text: String,
) {
    File(appDataDir(), name).writeText(text)
}
