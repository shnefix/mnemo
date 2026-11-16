package app.mnemo.platform

import java.io.File

actual fun appDataDir(): String {
    (System.getProperty("mnemo.dataDir") ?: System.getenv("MNEMO_DATA_DIR"))?.let { return File(it).apply { mkdirs() }.absolutePath }
    val os = System.getProperty("os.name").lowercase()
    val home = System.getProperty("user.home")
    val base =
        when {
            os.contains("win") -> System.getenv("APPDATA") ?: "$home/AppData/Roaming"
            os.contains("mac") -> "$home/Library/Application Support"
            else -> System.getenv("XDG_DATA_HOME") ?: "$home/.local/share"
        }
    return File(base, "Mnemo").apply { mkdirs() }.absolutePath
}

actual val isMobilePlatform: Boolean = false

actual fun shareText(text: String): Boolean = false

actual fun refreshWidgets() {}
