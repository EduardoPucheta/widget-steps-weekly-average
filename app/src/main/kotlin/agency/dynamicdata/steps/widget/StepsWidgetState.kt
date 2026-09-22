package agency.dynamicdata.steps.widget

import agency.dynamicdata.steps.core.WeekOverWeekTrend
import agency.dynamicdata.steps.core.WeeklyStepsSummary

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

    /** Installed, but the user has not granted step access yet — tapping opens the app. */
    data object PermissionRequired : StepsWidgetState

    /** Permission is granted but nothing was reported this week. */
    data class NoData(val summary: WeeklyStepsSummary) : StepsWidgetState

    /** The normal case. */
    data class Ready(
        val summary: WeeklyStepsSummary,
        val trend: WeekOverWeekTrend?,
    ) : StepsWidgetState

    /** A read failed. The last good [summary] is kept so the widget does not go blank. */
    data class Error(val summary: WeeklyStepsSummary?) : StepsWidgetState
}
