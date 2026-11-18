package app.mnemo.platform

actual object Updater {
    actual val supported: Boolean = false

    actual fun currentVersionName(): String = "desktop"

    actual suspend fun check(): AppUpdate? = null

    actual suspend fun install(update: AppUpdate): Result<Unit> = Result.failure(UnsupportedOperationException())
}
