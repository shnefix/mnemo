package app.mnemo.platform

import android.content.Context

/** Set by [MnemoApplication] before the graph is built. */
lateinit var appContext: Context

actual fun appDataDir(): String = appContext.filesDir.absolutePath

actual val isMobilePlatform: Boolean = true

actual fun shareText(text: String): Boolean {
    val send =
        android.content
            .Intent(android.content.Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(android.content.Intent.EXTRA_TEXT, text)
    val chooser =
        android.content.Intent
            .createChooser(send, null)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { appContext.startActivity(chooser) }.isSuccess
}

actual fun refreshWidgets() {
    runCatching { app.mnemo.DueWidget.updateAll(appContext) }
}
