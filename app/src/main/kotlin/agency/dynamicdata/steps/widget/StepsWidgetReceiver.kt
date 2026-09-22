package agency.dynamicdata.steps.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * The manifest entry point for the widget.
 *
 * Scheduling the refresh work here — rather than in [android.app.Application] — means
 * nothing periodic runs for a user who never places the widget.
 */
class StepsWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = StepsWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        StepsRefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        StepsRefreshWorker.cancel(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Re-assert the schedule: work can be dropped by a backup restore or by the
        // user clearing app data, and the widget would then quietly go stale.
        StepsRefreshWorker.schedule(context)
    }
}
