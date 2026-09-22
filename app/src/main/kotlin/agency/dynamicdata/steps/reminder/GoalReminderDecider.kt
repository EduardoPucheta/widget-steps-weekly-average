package agency.dynamicdata.steps.reminder

import agency.dynamicdata.steps.core.AverageBasis
import agency.dynamicdata.steps.core.GoalProgress
import agency.dynamicdata.steps.core.StepGoal
import agency.dynamicdata.steps.core.StepsAverageCalculator
import agency.dynamicdata.steps.core.StepsWindow
import agency.dynamicdata.steps.health.HealthConnectAvailability
import agency.dynamicdata.steps.health.StepsRepository
import android.util.Log
import java.time.LocalDate

/**
 * Decides whether the morning reminder should fire.
 *
 * Deliberately separate from the posting of the notification, so the rule — and in
 * particular every reason it declines — can be tested without an Android framework.
 */
class GoalReminderDecider(
    private val repository: StepsRepository,
    private val days: Int = StepsWindow.DEFAULT_DAYS,
    basis: AverageBasis = AverageBasis.ALL_DAYS,
) {
    private val calculator = StepsAverageCalculator(days, basis)

    suspend fun decide(today: LocalDate, goal: StepGoal, enabled: Boolean): ReminderDecision {
        if (!enabled) return ReminderDecision.StayQuiet(ReminderDecision.Reason.DISABLED)

        when (repository.availability()) {
            HealthConnectAvailability.NOT_SUPPORTED, HealthConnectAvailability.UPDATE_REQUIRED ->
                return ReminderDecision.StayQuiet(
                    ReminderDecision.Reason.HEALTH_CONNECT_UNAVAILABLE,
                )
            HealthConnectAvailability.AVAILABLE -> Unit
        }

        if (!repository.hasReadPermission()) {
            return ReminderDecision.StayQuiet(ReminderDecision.Reason.NO_PERMISSION)
        }

        val window = StepsWindow.lastCompleteDays(today, days)

        return try {
            val summary = calculator.summarize(repository.dailySteps(window), window)
            if (summary.hasNoData) {
                return ReminderDecision.StayQuiet(ReminderDecision.Reason.NO_DATA)
            }

            val progress = GoalProgress(goal, summary.averageStepsPerDay)
            if (progress.isMet) {
                ReminderDecision.StayQuiet(ReminderDecision.Reason.GOAL_MET)
            } else {
                ReminderDecision.Notify(summary, progress)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "step read denied", e)
            ReminderDecision.StayQuiet(ReminderDecision.Reason.NO_PERMISSION)
        } catch (e: Exception) {
            Log.e(TAG, "step read failed", e)
            ReminderDecision.StayQuiet(ReminderDecision.Reason.READ_FAILED)
        }
    }

    private companion object {
        const val TAG = "GoalReminder"
    }
}
