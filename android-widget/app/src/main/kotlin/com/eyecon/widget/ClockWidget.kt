package com.eyecon.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, mgr, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, ClockWidget::class.java))
            ids.forEach { updateWidget(context, mgr, it) }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.eyecon.widget.REFRESH"

        fun updateWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            // Refresh tap on header
            val refreshPi = PendingIntent.getBroadcast(
                context, widgetId,
                Intent(context, ClockWidget::class.java).apply { action = ACTION_REFRESH },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.tv_header, refreshPi)

            // Clock In button → opens ClockInOutActivity with action=CLOCK_IN
            val inPi = PendingIntent.getActivity(
                context, widgetId * 10 + 1,
                Intent(context, ClockInOutActivity::class.java).apply {
                    putExtra("action", "CLOCK_IN")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_clock_in, inPi)

            // Clock Out button → opens ClockInOutActivity with action=CLOCK_OUT
            val outPi = PendingIntent.getActivity(
                context, widgetId * 10 + 2,
                Intent(context, ClockInOutActivity::class.java).apply {
                    putExtra("action", "CLOCK_OUT")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_clock_out, outPi)

            views.setTextViewText(R.id.tv_clocked_in, "Loading…")
            mgr.updateAppWidget(widgetId, views)

            Thread {
                val entries = SupabaseApi.getOpenEntries()
                val statusText = if (entries.isEmpty()) {
                    "Nobody clocked in"
                } else {
                    entries.joinToString("\n") { e ->
                        if (e.jobName != null) "• ${e.employeeName}  —  ${e.jobName}"
                        else "• ${e.employeeName}"
                    }
                }
                val time = SimpleDateFormat("HH:mm", Locale.UK).format(Date())

                val updated = RemoteViews(context.packageName, R.layout.widget_layout)
                updated.setOnClickPendingIntent(R.id.tv_header, refreshPi)
                updated.setOnClickPendingIntent(R.id.btn_clock_in, inPi)
                updated.setOnClickPendingIntent(R.id.btn_clock_out, outPi)
                updated.setTextViewText(R.id.tv_clocked_in, statusText)
                updated.setTextViewText(R.id.tv_updated, "↻ $time  (tap title to refresh)")
                mgr.updateAppWidget(widgetId, updated)
            }.start()
        }

        /** Call this after a clock-in or clock-out to force all widget instances to refresh. */
        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, ClockWidget::class.java))
            ids.forEach { updateWidget(context, mgr, it) }
        }
    }
}
