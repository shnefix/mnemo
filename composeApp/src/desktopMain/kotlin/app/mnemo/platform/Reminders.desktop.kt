package app.mnemo.platform

import androidx.compose.runtime.Composable

/** No background process on desktop, so there is nothing to schedule. */
actual object ReminderScheduler {
    actual val supported: Boolean = false

    actual fun sync(hour: Int?) {}
}

@Composable
actual fun rememberNotificationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit = { onResult(true) }
