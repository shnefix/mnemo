package app.mnemo.platform

import kotlin.time.ExperimentalTime

/** Injectable wall clock so the scheduler and tests can control "now". */
fun interface Clock {
    fun now(): Long
}

@OptIn(ExperimentalTime::class)
object SystemClock : Clock {
    override fun now(): Long =
        kotlin.time.Clock.System
            .now()
            .toEpochMilliseconds()
}

/** Directory for the SQLite file and media, created if needed. */
expect fun appDataDir(): String

/** True on a touch-first platform (used to decide between swipe and keyboard hints). */
expect val isMobilePlatform: Boolean

/** Tells home-screen widgets the due count may have changed (no-op off Android). */
expect fun refreshWidgets()
