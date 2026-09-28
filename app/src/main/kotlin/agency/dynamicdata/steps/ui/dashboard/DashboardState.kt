package agency.dynamicdata.steps.ui.dashboard

import agency.dynamicdata.steps.core.DayEntry
import agency.dynamicdata.steps.core.GoalProgress
import agency.dynamicdata.steps.core.MovingAveragePoint
import agency.dynamicdata.steps.core.StepsSummary

/** What the in-app screen is showing above its settings. */
sealed interface DashboardState {

    data object Loading : DashboardState

    data class HealthConnectUnavailable(val updatable: Boolean) : DashboardState

    data object PermissionRequired : DashboardState

    /**
     * A reading. [days] and [movingAverage] both have one entry per day of the
     * window, in the same order, including the days with nothing recorded.
     */
    data class Ready(
        val summary: StepsSummary,
        val progress: GoalProgress,
        val days: List<DayEntry>,
        val movingAverage: List<MovingAveragePoint>,
    ) : DashboardState {

        init {
            require(movingAverage.map { it.date } == days.map { it.date }) {
                "the moving average must line up with the days it is drawn over"
            }
        }

        /** The tallest bar the chart has to fit, ignoring days with no answer. */
        val highestDay: Long get() = days.mapNotNull { it.steps }.maxOrNull() ?: 0L

        /**
         * The highest thing on the chart, bar or line.
         *
         * Not the same as [highestDay]: the average draws on the six days before
         * the window, so after a big week it can sit above every bar in view, and a
         * scale fitted to the bars alone would push it off the top.
         */
        val highestPlotted: Long
            get() = maxOf(
                highestDay,
                movingAverage.mapNotNull { it.averageStepsPerDay }.maxOrNull() ?: 0L,
            )

        val trackedDays: Int get() = days.count { it.isTracked }
    }

    data object Error : DashboardState
}
