package agency.dynamicdata.steps.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class MovingAverageTest {

    private val today = LocalDate.of(2026, 9, 22)
    private val window = StepsWindow.lastCompleteDays(today) // 15–21 Sep
    private val calculator = StepsAverageCalculator()

    /** Every day from [from] to [to] inclusive at [perDay] steps. */
    private fun flat(from: LocalDate, to: LocalDate, perDay: Long): List<DailySteps> =
        generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .map { DailySteps(it, perDay) }
            .toList()

    @Test
    fun `reading a 7-day average across 7 days needs 13 days of data`() {
        val read = MovingAverage.readWindowFor(window)

        assertEquals(LocalDate.of(2026, 9, 9), read.start)
        assertEquals(LocalDate.of(2026, 9, 21), read.end)
        assertEquals(13, read.dayCount)
    }

    @Test
    fun `the read window never reaches today`() {
        val read = MovingAverage.readWindowFor(window)

        assertEquals(false, today in read)
    }

    @Test
    fun `gives one point per day of the window`() {
        val points = MovingAverage.of(emptyList(), window, calculator)

        assertEquals(window.dates, points.map { it.date })
    }

    @Test
    fun `the last point is exactly the headline average`() {
        // The property that matters most: the line ends where the big number says.
        val readings = window.dates.mapIndexed { i, d -> DailySteps(d, 3_000L + i * 1_517) } +
            flat(window.start.minusDays(6), window.start.minusDays(1), 20_000)

        val points = MovingAverage.of(readings, window, calculator)
        val headline = calculator.summarize(readings, window)

        assertEquals(headline.averageStepsPerDay, points.last().averageStepsPerDay)
    }

    @Test
    fun `earlier points draw on the days before the window`() {
        // A big week, then a quiet one: the line starts high and comes down, even
        // though every bar in view is low. That is the point of plotting it.
        val readings = flat(window.start.minusDays(6), window.start.minusDays(1), 14_000) +
            flat(window.start, window.end, 7_000)

        val points = MovingAverage.of(readings, window, calculator)

        // First point: six days at 14,000 plus one at 7,000, over 7.
        assertEquals(13_000L, points.first().averageStepsPerDay)
        assertEquals(7_000L, points.last().averageStepsPerDay)
        // And it falls steadily, one day's worth at a time.
        val values = points.map { it.averageStepsPerDay!! }
        assertEquals(values.sortedDescending(), values)
    }

    @Test
    fun `an untracked day counts as zero, as it does in the headline`() {
        val readings = flat(window.start.minusDays(6), window.end, 7_000)
            .filterNot { it.date == window.end }

        val points = MovingAverage.of(readings, window, calculator)

        assertEquals(6_000L, points.last().averageStepsPerDay) // 42,000 / 7
    }

    @Test
    fun `a span with nothing recorded has no point, not a zero`() {
        // Only the last day of the window has a reading. Every earlier point's span
        // is empty; they must be gaps in the line, not a line along the floor.
        val readings = listOf(DailySteps(window.end, 7_000))

        val points = MovingAverage.of(readings, window, calculator)

        points.dropLast(1).forEach { assertNull(it.averageStepsPerDay, "${it.date}") }
        assertEquals(1_000L, points.last().averageStepsPerDay)
    }

    @Test
    fun `a flat week gives a flat line`() {
        val readings = flat(window.start.minusDays(6), window.end, 8_400)

        val points = MovingAverage.of(readings, window, calculator)

        points.forEach { assertEquals(8_400L, it.averageStepsPerDay) }
    }

    @Test
    fun `rejects an average of no days`() {
        assertThrows<IllegalArgumentException> { MovingAverage.readWindowFor(window, days = 0) }
    }
}
