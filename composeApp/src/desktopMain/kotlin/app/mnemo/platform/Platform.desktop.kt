package app.mnemo.platform

import java.io.File

actual fun appDataDir(): String {
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
