package agency.dynamicdata.steps.ui.body

import agency.dynamicdata.steps.core.WeightUnit
import java.util.Locale
import kotlin.math.abs

/**
 * How one measurement is written: its unit, and how a change in it is written.
 *
 * A change in body fat is in percentage *points*, not percent: going from 20% to
 * 18% is 2 points, but a 10% drop. Writing "−2%" would claim the second.
 */
internal class MetricFormat(
    private val symbol: String,
    private val spaced: Boolean,
    private val changeSymbol: String,
    private val factor: Double,
) {
    /** The axis caption: what the numbers on it are in. */
    val unit: String get() = symbol

    /** A stored value (kilograms, percent) in the unit it is shown in. */
    fun display(stored: Double): Double = stored * factor

    /** "82.4 kg", "18.2%". */
    fun value(stored: Double, locale: Locale): String = oneDecimal(display(stored), locale) + suffix(symbol)

    /** "−1.3 kg", "+0.4 pts"; "±0.0" when it rounds to nothing. */
    fun change(stored: Double, locale: Locale): String {
        val shown = display(stored)
        val magnitude = oneDecimal(abs(shown), locale)
        val sign = when {
            magnitude == oneDecimal(0.0, locale) -> "±"
            shown < 0 -> "−"
            else -> "+"
        }
        return "$sign$magnitude $changeSymbol"
    }

    /** An axis label, already in display units: as many decimals as the step needs. */
    fun tick(shown: Double, step: Double, locale: Locale): String {
        val decimals = if (step >= 1.0) 0 else 1
        return String.format(locale, "%.${decimals}f", shown)
    }

    private fun suffix(symbol: String) = if (spaced) " $symbol" else symbol

    private fun oneDecimal(value: Double, locale: Locale) = String.format(locale, "%.1f", value)

    companion object {
        fun weight(locale: Locale): MetricFormat {
            val unit = WeightUnit.forLocale(locale)
            return MetricFormat(unit.symbol, spaced = true, changeSymbol = unit.symbol, factor = unit.fromKilograms(1.0))
        }

        val BODY_FAT = MetricFormat("%", spaced = false, changeSymbol = "pts", factor = 1.0)
    }
}
