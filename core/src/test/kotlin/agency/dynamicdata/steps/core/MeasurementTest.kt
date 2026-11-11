package agency.dynamicdata.steps.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

class MeasurementTrendTest {

    private val start = LocalDateTime.of(2026, 1, 1, 8, 0)
    private fun reading(day: Long, value: Double, hour: Long = 0) = Measurement(start.plusDays(day).plusHours(hour), value)

    @Test
    fun `one trend point per reading, including two on the same day`() {
        val trend = MeasurementTrend.of(listOf(reading(0, 80.0), reading(0, 81.0, hour = 12), reading(1, 82.0)))

        assertEquals(3, trend.size)
    }

    @Test
    fun `averages the readings within fifteen days either side`() {
        val trend = MeasurementTrend.of(listOf(reading(0, 80.0), reading(10, 82.0), reading(40, 90.0)))

        assertEquals(81.0, trend[0].value, 1e-9)
        assertEquals(81.0, trend[1].value, 1e-9)
        // Thirty days from its nearest neighbour: only itself in range.
        assertEquals(90.0, trend[2].value, 1e-9)
    }

    @Test
    fun `is centred, so it does not lag behind a change`() {
        // Steady at 80, then steady at 76 from day 20.
        val readings = (0L..39L).map { reading(it, if (it < 20) 80.0 else 76.0) }

        val trend = MeasurementTrend.of(readings)

        // Across the change the trend is halfway, not still near 80.
        val middle = (trend[19].value + trend[20].value) / 2
        assertEquals(78.0, middle, 0.3)
    }

    @Test
    fun `comes out in time order whatever order the readings arrive in`() {
        val trend = MeasurementTrend.of(listOf(reading(5, 80.0), reading(1, 81.0), reading(3, 82.0)))

        assertEquals(listOf(1L, 3L, 5L), trend.map { ChronoUnit.DAYS.between(start, it.at) })
    }

    @Test
    fun `breaks the line across a gap of more than thirty days`() {
        val trend = MeasurementTrend.of(listOf(reading(0, 80.0), reading(20, 80.0), reading(60, 79.0), reading(70, 78.0)))

        assertEquals(listOf(2, 2), MeasurementTrend.runs(trend).map { it.size })
    }

    @Test
    fun `no readings, no trend`() {
        assertEquals(emptyList<TrendPoint>(), MeasurementTrend.of(emptyList()))
        assertEquals(emptyList<List<TrendPoint>>(), MeasurementTrend.runs(emptyList()))
    }

    @Test
    fun `a measurement must be positive`() {
        assertThrows<IllegalArgumentException> { Measurement(start, 0.0) }
    }
}

class MeasurementSummaryTest {

    private val start = LocalDateTime.of(2026, 1, 1, 8, 0)

    @Test
    fun `change is measured on the trend, not on the noisy ends`() {
        val readings = listOf(
            Measurement(start, 81.5),
            Measurement(start.plusDays(1), 80.5),
            Measurement(start.plusDays(200), 77.5),
            Measurement(start.plusDays(201), 76.5),
        )

        val summary = MeasurementSummary.of(readings, MeasurementTrend.of(readings))!!

        assertEquals(-4.0, summary.change!!, 1e-9)
        assertEquals(76.5, summary.latest.value)
        assertEquals(76.5, summary.lowest.value)
        assertEquals(81.5, summary.highest.value)
        assertEquals(4, summary.count)
    }

    @Test
    fun `a single reading has no change`() {
        val readings = listOf(Measurement(start, 80.0))

        assertNull(MeasurementSummary.of(readings, MeasurementTrend.of(readings))!!.change)
    }

    @Test
    fun `nothing to summarise is null, not zeros`() {
        assertNull(MeasurementSummary.of(emptyList(), emptyList()))
    }
}

class YearPeriodTest {

    private val today = LocalDate.of(2026, 11, 11)

    @Test
    fun `the current year ends today and spans twelve months`() {
        val period = YearPeriod.ending(today)

        assertEquals(LocalDate.of(2025, 11, 12), period.start)
        assertEquals(today, period.end)
    }

    @Test
    fun `earlier years step back a whole year without overlapping`() {
        assertEquals(YearPeriod.ending(today).start.minusDays(1), YearPeriod.ending(today, yearsBack = 1).end)
    }

    @Test
    fun `cannot look forward`() {
        assertThrows<IllegalArgumentException> { YearPeriod.ending(today, yearsBack = -1) }
    }
}

class AxisTicksTest {

    @Test
    fun `widens to clean steps around the data`() {
        assertEquals(listOf(78.0, 80.0, 82.0, 84.0, 86.0), AxisTicks.nice(78.3, 84.9))
    }

    @Test
    fun `a narrow band is not stretched to fill the chart`() {
        // 1.3 apart, shown on an axis at least 2 tall, in clean half steps.
        assertEquals(listOf(79.5, 80.0, 80.5, 81.0, 81.5, 82.0), AxisTicks.nice(80.1, 81.4))
    }

    @Test
    fun `a flat series still gets an axis around it`() {
        val ticks = AxisTicks.nice(80.0, 80.0)

        assert(ticks.first() < 80.0 && ticks.last() > 80.0) { "80 should sit inside $ticks" }
    }

    @Test
    fun `every value is inside the axis`() {
        val ticks = AxisTicks.nice(61.7, 63.2)

        assert(ticks.first() <= 61.7 && ticks.last() >= 63.2) { "$ticks should cover 61.7..63.2" }
    }
}

class WeightUnitTest {

    @Test
    fun `pounds where people are weighed in pounds, kilograms elsewhere`() {
        assertEquals(WeightUnit.POUNDS, WeightUnit.forLocale(Locale.US))
        assertEquals(WeightUnit.KILOGRAMS, WeightUnit.forLocale(Locale.forLanguageTag("es-AR")))
        assertEquals(WeightUnit.KILOGRAMS, WeightUnit.forLocale(Locale.UK))
    }

    @Test
    fun `converts kilograms to pounds`() {
        assertEquals(181.7, WeightUnit.POUNDS.fromKilograms(82.4), 0.05)
        assertEquals(82.4, WeightUnit.KILOGRAMS.fromKilograms(82.4), 1e-9)
    }
}
