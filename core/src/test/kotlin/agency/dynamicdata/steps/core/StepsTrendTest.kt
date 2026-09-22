package agency.dynamicdata.steps.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class StepsTrendTest {

    private val today = LocalDate.of(2026, 9, 22)
    private val calculator = StepsAverageCalculator()

    private val current = StepsWindow.lastCompleteDays(today)
    private val previous = current.preceding()

    private fun summary(window: StepsWindow, perDay: Long) =
        calculator.summarize(window.dates.map { DailySteps(it, perDay) }, window)

    @Test
    fun `reports the gain over the previous seven days`() {
        val trend = StepsTrend.of(summary(current, 9000), summary(previous, 6000))!!

        assertEquals(3000L, trend.deltaSteps)
        assertEquals(StepsTrend.Direction.UP, trend.direction)
        assertEquals(0.5, trend.deltaFraction!!, 1e-9)
    }

    @Test
    fun `reports a drop`() {
        val trend = StepsTrend.of(summary(current, 4000), summary(previous, 8000))!!

        assertEquals(-4000L, trend.deltaSteps)
        assertEquals(StepsTrend.Direction.DOWN, trend.direction)
    }

    @Test
    fun `reports flat when the averages match`() {
        val trend = StepsTrend.of(summary(current, 5000), summary(previous, 5000))!!

        assertEquals(StepsTrend.Direction.FLAT, trend.direction)
        assertEquals(0.0, trend.deltaFraction!!, 1e-9)
    }

    @Test
    fun `has no percentage when the previous window averaged zero`() {
        val trend = StepsTrend.of(summary(current, 5000), summary(previous, 0))!!

        assertEquals(5000L, trend.deltaSteps)
        assertNull(trend.deltaFraction)
    }

    @Test
    fun `is absent when a window has no data at all`() {
        val blank = calculator.summarize(emptyList(), previous)

        assertNull(StepsTrend.of(summary(current, 5000), blank))
    }

    @Test
    fun `refuses to compare averages built on different bases`() {
        val allDays = summary(current, 5000)
        val tracked = StepsAverageCalculator(basis = AverageBasis.DAYS_WITH_DATA)
            .summarize(previous.dates.map { DailySteps(it, 5000) }, previous)

        assertThrows<IllegalArgumentException> { StepsTrend.of(allDays, tracked) }
    }

    @Test
    fun `refuses to compare windows of different lengths`() {
        val threeDay = StepsAverageCalculator(days = 3).summarizeRecentDays(emptyList(), today)
        assertThrows<IllegalArgumentException> { StepsTrend.of(summary(current, 5000), threeDay) }
    }
}
