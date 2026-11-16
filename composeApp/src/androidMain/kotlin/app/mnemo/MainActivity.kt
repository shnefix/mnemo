package app.mnemo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.mnemo.di.AppGraph
import app.mnemo.domain.reminder.Reminder
import app.mnemo.platform.ReminderScheduler
import app.mnemo.platform.appContext
import app.mnemo.platform.refreshWidgets
import app.mnemo.ui.theme.ThemeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    /** Bumped each time the widget asks for a review, so a running app also jumps to it. */
    private var reviewRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appContext = applicationContext
        if (savedInstanceState == null && intent.wantsReview()) reviewRequests++
        val theme = ThemeState.fromPrefs()
        setContent {
            // Opening the database and seeding happen off the main thread, so the first
            // frame is drawn right away instead of after the disk work.
            var graph by remember { mutableStateOf<AppGraph?>(GraphHolder.peek()) }
            LaunchedEffect(Unit) {
                if (graph == null) graph = withContext(Dispatchers.IO) { GraphHolder.get(applicationContext) }
                // Alarms are lost on reinstall/update; re-arm the reminder if one is set.
                withContext(Dispatchers.IO) { runCatching { ReminderScheduler.sync(Reminder.hour) } }
            }
            graph?.let { App(it, theme, reviewRequests = reviewRequests) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.wantsReview()) reviewRequests++
    }

    override fun onStop() {
        super.onStop()
        // Reviews or imports may have changed what is due.
        refreshWidgets()
    }

    private fun Intent?.wantsReview() = this?.getBooleanExtra(EXTRA_START_REVIEW, false) == true

    companion object {
        const val EXTRA_START_REVIEW = "app.mnemo.START_REVIEW"
    }
}
