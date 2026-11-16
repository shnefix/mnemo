package app.mnemo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mnemo.domain.reminder.Reminder
import app.mnemo.platform.ReminderScheduler
import app.mnemo.platform.appContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires once a day at the chosen hour: counts what is due and posts
 * "You have N cards due" (nothing when N is 0), then arms the next day.
 * Also re-arms after a reboot, since alarms do not survive one.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        appContext = context.applicationContext
        val hour = Reminder.hour ?: return
        if (intent.action != ACTION_REMIND) {
            ReminderScheduler.sync(hour)
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val due = Reminder.dueTotal(GraphHolder.get(appContext))
                if (due > 0) ReminderScheduler.notify(appContext, Reminder.message(due))
            } catch (_: Exception) {
                // A failed count must not stop tomorrow's reminder.
            } finally {
                ReminderScheduler.sync(hour)
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "app.mnemo.action.REMIND"
    }
}
