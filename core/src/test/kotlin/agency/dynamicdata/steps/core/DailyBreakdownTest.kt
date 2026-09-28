package agency.dynamicdata.steps.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DailyBreakdownTest {

    private val today = LocalDate.of(2026, 9, 22)
    private val window = StepsWindow.lastCompleteDays(today) // 15–21 Sep

    @Test
    fun `returns one entry per day, in order`() {
        val entries = DailyBreakdown.of(emptyList(), window)

        assertEquals(7, entries.size)
        assertEquals(LocalDate.of(2026, 9, 15), entries.first().date)
        assertEquals(LocalDate.of(2026, 9, 21), entries.last().date)
        assertEquals(entries.map { it.date }.sorted(), entries.map { it.date })
    }

    @Test
    fun `an unreported day is null, not zero`() {
        // The distinction the whole feature rests on: a chart cannot draw "unknown"
        // and "did not move" the same way without misrepresenting one of them.
        val entries = DailyBreakdown.of(
            listOf(DailySteps(window.start, 8_000)),
            window,
        )

        assertEquals(8_000L, entries[0].steps)
        assertNull(entries[1].steps)
        assertTrue(entries[0].isTracked)
        assertFalse(entries[1].isTracked)
    }

    @Test
    fun `a recorded zero stays a zero`() {
        val entries = DailyBreakdown.of(listOf(DailySteps(window.start, 0)), window)

        assertEquals(0L, entries[0].steps)
        assertTrue(entries[0].isTracked)
    }

    @Test
    fun `sums several sources for the same day`() {
        val entries = DailyBreakdown.of(
            listOf(DailySteps(window.start, 4_000), DailySteps(window.start, 1_500)),
            window,
        )

        assertEquals(5_500L, entries[0].steps)
    }

    @Test
    fun `ignores readings outside the window`() {
        val entries = DailyBreakdown.of(
            listOf(
                DailySteps(window.start.minusDays(1), 99_000),
                DailySteps(window.end.plusDays(1), 99_000),
                DailySteps(window.end, 7_000),
            ),
            window,
        )

        assertEquals(7_000L, entries.last().steps)
        assertEquals(1, entries.count { it.isTracked })
    }

    @Test
    fun `agrees with the average over the same data`() {
        // If the chart and the headline ever disagree, the app is not trustworthy.
        val readings = window.dates.mapIndexed { i, d -> DailySteps(d, (i + 1) * 1_000L) }

        val entries = DailyBreakdown.of(readings, window)
        val summary = StepsAverageCalculator().summarize(readings, window)

        assertEquals(summary.totalSteps, entries.sumOf { it.steps ?: 0L })
    }
}
