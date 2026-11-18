package app.mnemo.platform

import android.content.Context

/** Set by [MnemoApplication] before the graph is built. */
lateinit var appContext: Context

actual fun appDataDir(): String = appContext.filesDir.absolutePath

actual val isMobilePlatform: Boolean = true

actual fun refreshWidgets() {
    runCatching { app.mnemo.DueWidget.updateAll(appContext) }
}
