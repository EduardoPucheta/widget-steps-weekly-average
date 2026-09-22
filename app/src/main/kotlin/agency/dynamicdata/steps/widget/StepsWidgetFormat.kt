package agency.dynamicdata.steps.widget

import agency.dynamicdata.steps.core.AverageBasis
import agency.dynamicdata.steps.core.GoalProgress
import agency.dynamicdata.steps.core.StepsSummary
import agency.dynamicdata.steps.core.StepsTrend
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Number and date formatting for the widget.
 *
 * A home-screen widget is a few square centimetres, so the big number is abbreviated
 * ("8.4k") while the supporting lines stay exact. Locale is threaded through rather
 * than defaulted so grouping separators follow the device, not the developer.
 */
object StepsWidgetFormat {

    /** "8,432" — the precise reading, for supporting lines and accessibility text. */
    fun exact(steps: Long, locale: Locale): String = String.format(locale, "%,d", steps)

    /** "8.4k" — the headline. Under 10,000 it stays exact; "8.4k" saves nothing there. */
    fun compact(steps: Long, locale: Locale): String = when {
        steps < 10_000 -> exact(steps, locale)
        steps < 1_000_000 -> String.format(locale, "%.1fk", steps / 1_000.0)
        else -> String.format(locale, "%.1fM", steps / 1_000_000.0)
    }

    /** "15–21 Sep" — the days the number covers. */
    fun windowRange(summary: StepsSummary, locale: Locale): String {
        val dayOnly = DateTimeFormatter.ofPattern("d", locale)
        val dayAndMonth = DateTimeFormatter.ofPattern("d MMM", locale)
        val sameMonth = summary.window.start.month == summary.window.end.month
        val start = if (sameMonth) dayOnly else dayAndMonth
        return "${summary.window.start.format(start)}–${summary.window.end.format(dayAndMonth)}"
    }

    /** "+12%", or "+400" when the previous window averaged zero and a percent is undefined. */
    fun trend(trend: StepsTrend, locale: Locale): String {
        val sign = when {
            trend.deltaSteps > 0 -> "+"
            trend.deltaSteps < 0 -> "−"
            else -> ""
        }
        val fraction = trend.deltaFraction
            ?: return "$sign${exact(abs(trend.deltaSteps), locale)}"
        return String.format(locale, "%s%.0f%%", sign, abs(fraction) * 100)
    }

    /**
     * The goal line: "1,600 short of 10k" or "1,200 over 10k".
     *
     * States the gap in steps rather than only a percentage, because "16% short" does
     * not tell you how much further to walk and "1,600 steps" does.
     */
    fun goal(progress: GoalProgress, locale: Locale): String {
        val target = compact(progress.goal.stepsPerDay, locale)
        return when {
            progress.stepsOver > 0 -> "${exact(progress.stepsOver, locale)} over $target"
            progress.isMet -> "goal met — $target"
            else -> "${exact(progress.stepsShort, locale)} short of $target"
        }
    }

    /** The spoken version of the goal line, for screen readers. */
    fun goalSpoken(progress: GoalProgress, locale: Locale): String {
        val target = exact(progress.goal.stepsPerDay, locale)
        return when {
            progress.stepsOver > 0 ->
                "${exact(progress.stepsOver, locale)} steps above your goal of $target"
            progress.isMet -> "goal of $target met"
            else ->
                "${exact(progress.stepsShort, locale)} steps below your goal of $target"
        }
    }

    /** The header, naming the window rather than calling it "this week". */
    fun windowLabel(summary: StepsSummary): String = when (summary.basis) {
        AverageBasis.ALL_DAYS -> "${summary.dayCount}-day average"
        AverageBasis.DAYS_WITH_DATA -> "avg over ${summary.daysWithData} tracked days"
    }
}
