package agency.dynamicdata.steps.reminder

import agency.dynamicdata.steps.health.HealthConnectStepsRepository
import agency.dynamicdata.steps.settings.SettingsStore
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Locale

/**
 * Decides and posts the morning reminder, off the receiver's thread.
 *
 * Also used to re-arm the alarm after a reboot, because that needs a DataStore read
 * to know whether the reminder is switched on at all, and a receiver is not the
 * place to do it.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext)
        val enabled = settings.currentReminderEnabled()

        if (inputData.getBoolean(KEY_RESCHEDULE_ONLY, false)) {
            if (enabled) ReminderSchedule.enable(applicationContext)
            return Result.success()
        }

        val repository = HealthConnectStepsRepository(applicationContext)
        val decision = GoalReminderDecider(repository).decide(
            today = repository.today(),
            goal = settings.currentGoal(),
            enabled = enabled,
        )

        when (decision) {
            is ReminderDecision.Notify -> {
                val locale = applicationContext.resources.configuration.locales[0]
                    ?: Locale.getDefault()
                val posted = StepsNotifications.postShortfall(
                    applicationContext,
                    decision,
                    locale,
                )
                // Logged rather than retried: the reason a notification is blocked is
                // a permission the user controls, and retrying will not change it.
                Log.i(TAG, if (posted) "reminder posted" else "reminder suppressed by settings")
            }

            // Every quiet morning says why. Without this, "it didn't notify me" is
            // impossible to tell apart from "it wasn't supposed to".
            is ReminderDecision.StayQuiet -> Log.i(TAG, "no reminder: ${decision.reason}")
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "ReminderWorker"
        private const val WORK_NAME = "goal-reminder"
        private const val KEY_RESCHEDULE_ONLY = "reschedule_only"

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                // A second alarm arriving before the first finished should not queue a
                // duplicate notification.
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ReminderWorker>().build(),
            )
        }

        /** After a reboot or an update: put the alarm back, if the reminder is on. */
        fun enqueueRescheduleCheck(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$WORK_NAME-reschedule",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInputData(Data.Builder().putBoolean(KEY_RESCHEDULE_ONLY, true).build())
                    .build(),
            )
        }
    }
}
