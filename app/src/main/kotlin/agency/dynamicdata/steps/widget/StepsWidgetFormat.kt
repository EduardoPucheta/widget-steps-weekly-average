package agency.dynamicdata.steps.widget

import agency.dynamicdata.steps.core.AverageBasis
import agency.dynamicdata.steps.core.GoalProgress
import agency.dynamicdata.steps.core.StepsSummary
import java.time.format.DateTimeFormatter
import java.util.Locale

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
        steps < 1_000_000 -> scaled(steps / 1_000.0, "k", locale)
        else -> scaled(steps / 1_000_000.0, "M", locale)
    }

    /**
     * One decimal place, dropped when it would be a zero.
     *
     * A round goal is the common case and "of 10.0k" reads like a measurement error
     * rather than a target, so exactly 10,000 renders as "10k".
     */
    private fun scaled(value: Double, suffix: String, locale: Locale): String {
        val rounded = Math.round(value * 10) / 10.0
        val pattern = if (rounded % 1.0 == 0.0) "%.0f%s" else "%.1f%s"
        return String.format(locale, pattern, rounded, suffix)
    }

    /** "15–21 Sep" — the days the number covers. */
    fun windowRange(summary: StepsSummary, locale: Locale): String {
        val dayOnly = DateTimeFormatter.ofPattern("d", locale)
        val dayAndMonth = DateTimeFormatter.ofPattern("d MMM", locale)
        val sameMonth = summary.window.start.month == summary.window.end.month
        val start = if (sameMonth) dayOnly else dayAndMonth
        return "${summary.window.start.format(start)}–${summary.window.end.format(dayAndMonth)}"
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
