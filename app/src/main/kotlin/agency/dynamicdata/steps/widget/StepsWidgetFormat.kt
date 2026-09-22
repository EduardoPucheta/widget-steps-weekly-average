package agency.dynamicdata.steps.widget

import agency.dynamicdata.steps.core.AverageBasis
import agency.dynamicdata.steps.core.WeekOverWeekTrend
import agency.dynamicdata.steps.core.WeeklyStepsSummary
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Number and date formatting for the widget.
 *
 * A home-screen widget is a few square centimetres, so the big number is abbreviated
 * ("8.4k") while the supporting line stays exact. Locale is threaded through rather
 * than defaulted so grouping separators follow the device, not the developer.
 */
object StepsWidgetFormat {

    /** "8,432" — the precise reading, for the secondary line and accessibility text. */
    fun exact(steps: Long, locale: Locale): String = String.format(locale, "%,d", steps)

    /** "8.4k" — the headline. Under 10,000 it stays exact; "8.4k" saves nothing there. */
    fun compact(steps: Long, locale: Locale): String = when {
        steps < 10_000 -> exact(steps, locale)
        steps < 1_000_000 -> String.format(locale, "%.1fk", steps / 1_000.0)
        else -> String.format(locale, "%.1fM", steps / 1_000_000.0)
    }

    /** "22–28 Sep" — the week the number covers. */
    fun weekRange(summary: WeeklyStepsSummary, locale: Locale): String {
        val dayOnly = DateTimeFormatter.ofPattern("d", locale)
        val dayAndMonth = DateTimeFormatter.ofPattern("d MMM", locale)
        val sameMonth = summary.weekStart.month == summary.weekEnd.month
        val start = if (sameMonth) dayOnly else dayAndMonth
        return "${summary.weekStart.format(start)}–${summary.weekEnd.format(dayAndMonth)}"
    }

    /** "+12%" or "−400" when last week averaged zero and a percentage is undefined. */
    fun trend(trend: WeekOverWeekTrend, locale: Locale): String {
        val fraction = trend.deltaFraction
        val sign = if (trend.deltaSteps > 0) "+" else if (trend.deltaSteps < 0) "−" else ""
        return if (fraction == null) {
            "$sign${exact(kotlin.math.abs(trend.deltaSteps), locale)}"
        } else {
            String.format(locale, "%s%.0f%%", sign, kotlin.math.abs(fraction) * 100)
        }
    }

    /** The line under the headline, saying what the average was actually divided by. */
    fun basisLabel(summary: WeeklyStepsSummary): String = when (summary.basis) {
        AverageBasis.CALENDAR_DAYS -> "per day, 7-day week"
        AverageBasis.ELAPSED_DAYS ->
            if (summary.isPartialWeek) "per day, ${summary.elapsedDays} days so far" else "per day"
        AverageBasis.DAYS_WITH_DATA -> "per tracked day (${summary.daysWithData})"
    }
}
