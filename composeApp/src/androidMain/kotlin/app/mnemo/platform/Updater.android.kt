package app.mnemo.platform

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

actual object Updater {
    /**
     * Only the real app (`app.mnemo`) updates itself. The on-device test build (`app.mnemo.debug`)
     * must not offer the release APK, which would install over the user's real copy.
     */
    actual val supported: Boolean get() = appContext.packageName == RELEASE_PACKAGE

    private const val RELEASE_PACKAGE = "app.mnemo"

    private fun info() = appContext.packageManager.getPackageInfo(appContext.packageName, 0)

    private fun currentCode(): Long =
        if (Build.VERSION.SDK_INT >= 28) {
            info().longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info().versionCode.toLong()
        }

    actual fun currentVersionName(): String = info().versionName ?: "?"

    actual suspend fun check(): AppUpdate? =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL("https://api.github.com/repos/$RELEASES_REPO/releases/latest").openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                val body = conn.inputStream.use { it.readBytes().decodeToString() }
                val root = Json.parseToJsonElement(body).jsonObject
                // Tags are "v<versionCode>" (scripts/release.sh).
                val code =
                    root["tag_name"]!!
                        .jsonPrimitive.content
                        .removePrefix("v")
                        .toLong()
                val apk =
                    root["assets"]!!.jsonArray.map { it.jsonObject }.first {
                        it["name"]!!.jsonPrimitive.content.endsWith(".apk")
                    }
                if (code <= currentCode()) return@runCatching null
                AppUpdate(
                    versionCode = code,
                    versionName = root["name"]?.jsonPrimitive?.content ?: "v$code",
                    apkUrl = apk["browser_download_url"]!!.jsonPrimitive.content,
                    notes = root["body"]?.jsonPrimitive?.content.orEmpty(),
                    sizeBytes = apk["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                )
            }.getOrNull()
        }

    actual suspend fun install(update: AppUpdate): Result<Unit> =
        runCatching {
            val ctx = appContext
            if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
                // One-time: let Mnemo install updates ("Install unknown apps").
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                error("Allow Mnemo to install apps, then tap Install again")
            }
            val file =
                withContext(Dispatchers.IO) {
                    val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val f = File(dir, "mnemo-${update.versionCode}.apk")
                    val conn = URL(update.apkUrl).openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = true
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 30_000
                    check(conn.responseCode == 200) { "GitHub answered ${conn.responseCode} instead of the update. Try again later." }
                    conn.inputStream.use { input -> f.outputStream().use { input.copyTo(it) } }
                    // Android only says "problem parsing the package" for a cut-off or wrong file,
                    // so check it here and say what actually went wrong.
                    if (update.sizeBytes > 0 && f.length() != update.sizeBytes) {
                        val got = f.length()
                        f.delete()
                        error(
                            "The download was incomplete ($got of ${update.sizeBytes} bytes). Check your connection and tap Install again.",
                        )
                    }
                    val pkg = ctx.packageManager.getPackageArchiveInfo(f.path, 0)
                    if (pkg == null || pkg.packageName != ctx.packageName) {
                        f.delete()
                        error(
                            "The downloaded file is not a Mnemo update. A network filter may be blocking GitHub downloads: try another network.",
                        )
                    }
                    f
                }
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", file)
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
}
