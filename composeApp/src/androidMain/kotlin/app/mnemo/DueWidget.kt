package app.mnemo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.mnemo.domain.reminder.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Home-screen widget: how many cards are due, and a button that opens a review session. */
class DueWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                render(context.applicationContext, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Re-renders every placed widget; call after reviews or imports. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DueWidget::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch { runCatching { render(context.applicationContext, manager, ids) } }
        }

        private suspend fun render(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
        ) {
            val due = runCatching { Reminder.dueTotal(GraphHolder.get(context)) }.getOrNull()
            val open =
                PendingIntent.getActivity(
                    context,
                    1,
                    Intent(context, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_START_REVIEW, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val views =
                RemoteViews(context.packageName, R.layout.widget_due).apply {
                    setTextViewText(R.id.widget_count, due?.toString() ?: "–")
                    setTextViewText(R.id.widget_label, if (due == 1) "card due" else "cards due")
                    setTextViewText(R.id.widget_button, if (due == 0) "All done ✓" else "Start review")
                    setOnClickPendingIntent(R.id.widget_root, open)
                }
            ids.forEach { manager.updateAppWidget(it, views) }
        }
    }
}
