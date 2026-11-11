package agency.dynamicdata.steps.ui.body

import agency.dynamicdata.steps.core.Measurement
import agency.dynamicdata.steps.core.MeasurementSummary
import agency.dynamicdata.steps.core.TrendPoint
import agency.dynamicdata.steps.core.YearPeriod

/** What the body section shows: one period, one chart per measurement. */
sealed interface BodyState {

    data object Loading : BodyState

    /** No Health Connect: the access card above already says so, so nothing here. */
    data object Unavailable : BodyState

    data class Loaded(
        val period: YearPeriod,
        /** 0 for the year ending today; the "next year" control is off there. */
        val yearsBack: Int,
        val weight: MetricState,
        val bodyFat: MetricState,
        /** Health Connect could share older data but hasn't been allowed to. */
        val historyBlocked: Boolean,
    ) : BodyState
}

/** One measurement's chart. */
sealed interface MetricState {

    data object PermissionRequired : MetricState

    /**
     * The period's readings, time-ordered, and the trend at each of them.
     *
     * [summary] is null when the period has no readings — an empty year is "nothing
     * recorded", never a chart of zeros.
     */
    data class Ready(
        val readings: List<Measurement>,
        val trend: List<TrendPoint>,
        val summary: MeasurementSummary?,
    ) : MetricState

    /** A read failed; [reason] is shown so a failure can be reported, not guessed at. */
    data class Error(val reason: String) : MetricState
}
