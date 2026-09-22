package agency.dynamicdata.steps.reminder

import agency.dynamicdata.steps.R
import agency.dynamicdata.steps.ui.MainActivity
import agency.dynamicdata.steps.widget.StepsWidgetFormat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Locale

/** Builds and posts the morning nudge. */
object StepsNotifications {

    private const val CHANNEL_ID = "goal_reminder"
    private const val NOTIFICATION_ID = 1

    /**
     * Creates the channel if it is missing.
     *
     * Channels are required from API 26, which is this app's minimum, so there is no
     * version check to do. Creating one that already exists is a no-op, except that
     * the name and description are refreshed — the importance is not, because the
     * user may have lowered it and that choice is theirs to keep.
     */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Goal reminder",
            // DEFAULT, not HIGH: this is a nudge about a seven-day average, not
            // something that has just happened. It should not interrupt.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "A morning note when your 7-day average is below your goal."
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    /**
     * Posts the shortfall.
     *
     * Returns false when the notification could not be shown — permission not
     * granted on Android 13+, or notifications switched off — so the caller can log
     * why nothing appeared instead of assuming it worked.
     */
    fun postShortfall(
        context: Context,
        decision: ReminderDecision.Notify,
        locale: Locale,
    ): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false

        ensureChannel(context)

        val progress = decision.progress
        val summary = decision.summary
        val short = StepsWidgetFormat.exact(progress.stepsShort, locale)
        val average = StepsWidgetFormat.exact(summary.averageStepsPerDay, locale)
        val goal = StepsWidgetFormat.exact(progress.goal.stepsPerDay, locale)

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val body = "$average a day over the last ${summary.dayCount}, against $goal."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_steps)
            .setContentTitle("$short steps short of your goal")
            .setContentText(body)
            // The title alone is the message; the body is the detail behind it, and
            // BigTextStyle keeps it readable when the shade is expanded.
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        return try {
            // A fixed id, so a morning's note replaces the previous one rather than
            // stacking up a week of identical reminders.
            manager.notify(NOTIFICATION_ID, notification)
            true
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS revoked between the check above and here.
            false
        }
    }
}
