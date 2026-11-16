package app.mnemo.platform

import java.io.File

actual fun readAppFile(name: String): String? = File(appDataDir(), name).takeIf { it.exists() }?.readText()

actual fun writeAppFile(
    name: String,
    text: String,
) {
    val f = File(appDataDir(), name)
    f.parentFile?.mkdirs()
    f.writeText(text)
}

actual fun listAppFiles(dir: String): List<String> =
    File(appDataDir(), dir)
        .listFiles()
        ?.filter { it.isFile }
        ?.map { it.name }
        ?.sorted()
        .orEmpty()

actual fun deleteAppFile(name: String) {
    File(appDataDir(), name).delete()
}

actual fun appFileSize(name: String): Long = File(appDataDir(), name).takeIf { it.exists() }?.length() ?: 0L
