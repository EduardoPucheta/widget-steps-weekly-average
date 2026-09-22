package agency.dynamicdata.steps.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Wakes on the daily alarm, and again after a reboot.
 *
 * Does as little as possible itself. A receiver has about ten seconds before the
 * system considers it stuck, and reading Health Connect can easily outlast that, so
 * the actual work is handed to [ReminderWorker].
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> {
                // Re-arm first. If the work fails or the process is killed, tomorrow's
                // reminder should still happen — one silent morning, not a dead feature.
                ReminderSchedule.enable(context)
                ReminderWorker.enqueue(context)
            }

            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // Alarms do not survive a reboot, and a package update clears them too.
                ReminderWorker.enqueueRescheduleCheck(context)
            }

            else -> Log.w(TAG, "ignoring unexpected action ${intent.action}")
        }
    }

    companion object {
        const val ACTION_FIRE = "agency.dynamicdata.steps.REMINDER_FIRE"
        private const val TAG = "ReminderReceiver"
    }
}
