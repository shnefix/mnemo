package app.mnemo.platform

import com.github.luben.zstd.Zstd
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

actual object MediaFiles {
    actual fun dir(): String = File(appDataDir(), "media").apply { mkdirs() }.absolutePath

    actual fun write(
        name: String,
        bytes: ByteArray,
    ): String {
        val f = File(dir(), safe(name))
        f.writeBytes(bytes)
        return f.absolutePath
    }

    actual fun path(name: String): String = File(dir(), safe(name)).absolutePath

    actual fun exists(name: String): Boolean = File(dir(), safe(name)).exists()

    actual fun read(name: String): ByteArray? = File(dir(), safe(name)).takeIf { it.exists() }?.readBytes()

    private fun safe(name: String): String = name.substringAfterLast('/').substringAfterLast('\\')
}

actual fun unzip(bytes: ByteArray): Map<String, ByteArray> {
    val out = LinkedHashMap<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory) out[entry.name] = zip.readBytes()
            zip.closeEntry()
        }
    }
    return out
}

actual fun zstdDecompress(bytes: ByteArray): ByteArray {
    val size = Zstd.decompressedSize(bytes)
    return if (size > 0) {
        Zstd.decompress(bytes, size.toInt())
    } else {
        // Unknown frame size: stream it.
        com.github.luben.zstd
            .ZstdInputStream(ByteArrayInputStream(bytes))
            .use { it.readBytes() }
    }
}

actual fun writeTempFile(
    prefix: String,
    suffix: String,
    bytes: ByteArray,
): String {
    val f = File.createTempFile(prefix, suffix)
    f.deleteOnExit()
    f.writeBytes(bytes)
    return f.absolutePath
}

actual fun deleteFile(path: String) {
    File(path).delete()
}
