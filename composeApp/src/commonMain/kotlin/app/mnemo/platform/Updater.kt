package app.mnemo.platform

/** A newer build published on GitHub Releases. */
data class AppUpdate(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
)

/** Self-update from GitHub Releases (Android). Android always asks the user to confirm the install. */
expect object Updater {
    val supported: Boolean

    fun currentVersionName(): String

    /** The latest release when it is newer than this build, else null (also on network errors). */
    suspend fun check(): AppUpdate?

    /** Downloads the APK and opens the system installer. */
    suspend fun install(update: AppUpdate): Result<Unit>
}

/** GitHub repository releases are published to (`scripts/release.sh`). */
const val RELEASES_REPO = "shnefix/mnemo"
