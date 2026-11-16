package app.mnemo.platform

import androidx.compose.runtime.Composable

/** Schedules the daily reminder notification on platforms that have one (Android). */
expect object ReminderScheduler {
    /** False where the app cannot post a notification when it is not running. */
    val supported: Boolean

    /** Arms the next reminder for [hour] (local time), or cancels it when null. Safe to call repeatedly. */
    fun sync(hour: Int?)
}

/**
 * Returns a function that asks for notification permission where one is
 * needed (Android 13+); [onResult] receives whether notifications may be
 * posted. Must be called in composition.
 */
@Composable
expect fun rememberNotificationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit
