package agency.dynamicdata.steps.reminder

import agency.dynamicdata.steps.core.DailySchedule
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.Clock
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Arranges for [ReminderReceiver] to be woken once a day.
 *
 * Uses an inexact, doze-tolerant alarm. An exact one would need
 * `SCHEDULE_EXACT_ALARM`, which the user has to grant by hand on Android 12+ and
 * which Google restricts to alarm-clock apps — a heavy ask for a nudge that is just
 * as useful at 9:07. The alarm is re-armed after each firing and after a reboot,
 * because a repeating alarm cannot survive either on its own.
 */
object ReminderSchedule {

    /** When the reminder fires, in the device's own time zone. */
    val TIME_OF_DAY: LocalTime = LocalTime.of(9, 0)

    private const val TAG = "ReminderSchedule"
    private const val REQUEST_CODE = 1

    fun enable(context: Context, clock: Clock = Clock.systemDefaultZone()) {
        val next = DailySchedule.next(ZonedDateTime.now(clock), TIME_OF_DAY)
        schedule(context, next.toInstant().toEpochMilli())
        Log.i(TAG, "reminder scheduled for $next")
    }

    fun disable(context: Context) {
        alarmManager(context)?.cancel(pendingIntent(context))
        Log.i(TAG, "reminder cancelled")
    }

    @SuppressLint("MissingPermission")
    private fun schedule(context: Context, atEpochMillis: Long) {
        val alarms = alarmManager(context) ?: return
        // setAndAllowWhileIdle, not setExact: fires in a doze maintenance window
        // without needing the exact-alarm permission. Setting the same PendingIntent
        // again replaces any alarm already pending, so this cannot stack up.
        alarms.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            atEpochMillis,
            pendingIntent(context),
        )
    }

    private fun alarmManager(context: Context): AlarmManager? =
        context.getSystemService(AlarmManager::class.java)

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE),
        // IMMUTABLE is required from API 31 and correct everywhere: nothing should be
        // able to fill in extras on this intent.
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
