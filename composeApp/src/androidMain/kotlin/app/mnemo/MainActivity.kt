package app.mnemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.mnemo.domain.reminder.Reminder
import app.mnemo.platform.ReminderScheduler
import app.mnemo.platform.appContext
import app.mnemo.ui.theme.ThemeState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appContext = applicationContext
        val graph = GraphHolder.get(applicationContext)
        // Alarms are lost on reinstall/update; re-arm the reminder if one is set.
        runCatching { ReminderScheduler.sync(Reminder.hour) }
        setContent { App(graph, ThemeState.fromPrefs()) }
    }
}
