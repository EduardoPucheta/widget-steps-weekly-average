package agency.dynamicdata.steps.core

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Round gridline values for a value axis that does not start at zero.
 *
 * Weight sits in a narrow band far from zero, so a zero-based axis would flatten a
 * year into a straight line. The axis instead spans the data, widened out to the
 * nearest clean step (1, 2 or 5 times a power of ten) so the labels are numbers a
 * person would pick: 78, 80, 82 rather than 78.3, 80.6, 82.9.
 */
object AxisTicks {

    fun nice(min: Double, max: Double, targetCount: Int = 4): List<Double> {
        require(targetCount >= 2) { "an axis needs at least two ticks, asked for $targetCount" }
        require(max >= min) { "max must not be below min, got $min..$max" }

        // A narrow band is padded out to MIN_SPAN around its middle, so a flat series
        // still gets an axis to sit in and half a kilo of noise is not stretched to
        // fill the chart like a real change.
        val pad = ((MIN_SPAN - (max - min)) / 2).coerceAtLeast(0.0)
        val low = min - pad
        val high = max + pad

        val step = niceStep((high - low) / (targetCount - 1))
        val first = floor(low / step) * step
        val last = ceil(high / step) * step
        val count = Math.round((last - first) / step).toInt()
        // Rounded so 0.1 steps do not print as 80.30000000000001.
        return (0..count).map { Math.round((first + it * step) * 1e6) / 1e6 }
    }

    /**
     * The clean step nearest [rough] (Heckbert's "nice numbers"). Nearest, not next
     * up: rounding 2.2 up to 5 leaves three gridlines across a band that reads
     * better with five.
     */
    private fun niceStep(rough: Double): Double {
        val magnitude = 10.0.pow(floor(log10(rough)))
        val fraction = rough / magnitude
        val nice = when {
            fraction < 1.5 -> 1.0
            fraction < 3.0 -> 2.0
            fraction < 7.0 -> 5.0
            else -> 10.0
        }
        return nice * magnitude
    }

    /** The narrowest span an axis is given, in the data's units. */
    private const val MIN_SPAN = 2.0
}
