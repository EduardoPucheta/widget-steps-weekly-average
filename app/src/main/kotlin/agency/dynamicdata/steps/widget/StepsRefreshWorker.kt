package agency.dynamicdata.steps.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Redraws the widget on a schedule.
 *
 * WorkManager rather than the widget's own `updatePeriodMillis`: the system rounds
 * that interval up to 30 minutes and ignores it on some launchers, and WorkManager
 * batches the wake-ups with other deferrable work instead of holding its own alarm.
 */
class StepsRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        StepsWidget().updateAll(applicationContext)
        Result.success()
    } catch (e: Exception) {
        // Retry rather than fail: a transient Health Connect error should not stop the
        // widget refreshing for the rest of the day.
        Log.w(TAG, "widget refresh failed, will retry", e)
        Result.retry()
    }

    companion object {
        private const val TAG = "StepsRefreshWorker"
        private const val WORK_NAME = "steps-widget-refresh"

        /**
         * Every 30 minutes. Step counts change slowly at a weekly-average scale, so a
         * tighter interval would spend battery to move a number by a rounding error.
         */
        private const val REFRESH_MINUTES = 30L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<StepsRefreshWorker>(
                REFRESH_MINUTES, TimeUnit.MINUTES,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                // KEEP, not UPDATE: re-scheduling on every widget update would reset
                // the interval each time and the refresh could starve.
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
