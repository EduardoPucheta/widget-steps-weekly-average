package agency.dynamicdata.steps.widget

import agency.dynamicdata.steps.core.GoalProgress
import agency.dynamicdata.steps.core.StepsSummary

/**
 * Everything the widget can be showing.
 *
 * Modelled as a sealed hierarchy so each case is rendered deliberately. A widget that
 * falls back to "0 steps" whenever something is wrong teaches the user to distrust
 * it; every failure here says what happened and what to do about it.
 */
sealed interface StepsWidgetState {

    /** First paint, before the first read has returned. */
    data object Loading : StepsWidgetState

    /** Health Connect is missing or too old — tapping opens the Play Store listing. */
    data class HealthConnectUnavailable(val updatable: Boolean) : StepsWidgetState

    /**
     * Health Connect says step access is not granted — tapping opens the app.
     *
     * Only ever from the permission check itself. A read that is refused while
     * access *is* granted is Health Connect's background rule, not a missing
     * permission, and asking the user to "allow" something they already allowed was
     * exactly the bug this replaced.
     */
    data object PermissionRequired : StepsWidgetState

    /** Permission is granted but nothing was reported in the window. */
    data class NoData(val summary: StepsSummary) : StepsWidgetState

    /**
     * The normal case.
     *
     * [current] is false when the reading had to come from the last saved one and
     * that one covers an earlier window — the number is real, but no longer this
     * week's, so the widget says so and offers a refresh.
     */
    data class Ready(
        val summary: StepsSummary,
        val progress: GoalProgress,
        val current: Boolean = true,
    ) : StepsWidgetState

    /** Nothing could be read and there is no earlier reading to fall back to. */
    data object Error : StepsWidgetState
}
