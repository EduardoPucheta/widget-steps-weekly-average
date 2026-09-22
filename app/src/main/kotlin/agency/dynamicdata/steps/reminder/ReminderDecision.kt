package agency.dynamicdata.steps.reminder

import agency.dynamicdata.steps.core.GoalProgress
import agency.dynamicdata.steps.core.StepsSummary

/**
 * Whether this morning is worth a notification.
 *
 * Every outcome is named, including the ones that stay quiet. A reminder that can
 * only say "yes" or "nothing" is impossible to debug when it does not arrive, and a
 * nudge is exactly the kind of feature people quietly stop trusting.
 */
sealed interface ReminderDecision {

    /** Below the goal, with a real reading behind it. */
    data class Notify(
        val summary: StepsSummary,
        val progress: GoalProgress,
    ) : ReminderDecision

    /** Nothing to say. */
    data class StayQuiet(val reason: Reason) : ReminderDecision

    enum class Reason {
        /** The goal is met. The whole point: no news is good news. */
        GOAL_MET,

        /** The user turned the reminder off. */
        DISABLED,

        /** Nothing was recorded, so there is no shortfall to report — only silence. */
        NO_DATA,

        /** Step access was never granted or has been revoked. */
        NO_PERMISSION,

        /** Health Connect is missing or too old to read. */
        HEALTH_CONNECT_UNAVAILABLE,

        /** The read failed. Better to miss a nudge than to invent a number. */
        READ_FAILED,
    }
}
