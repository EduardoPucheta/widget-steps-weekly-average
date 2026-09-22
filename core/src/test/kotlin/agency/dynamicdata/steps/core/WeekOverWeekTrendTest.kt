package agency.dynamicdata.steps.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.DayOfWeek
import java.time.LocalDate

class WeekOverWeekTrendTest {

    private val monday = LocalDate.of(2026, 9, 21)
    private val calculator = WeeklyStepsCalculator(basis = AverageBasis.ELAPSED_DAYS)

    private fun summary(window: WeekWindow, perDay: Long, today: LocalDate) =
        calculator.summarize(
            dailySteps = window.dates.map { DailySteps(it, perDay) },
            window = window,
            today = today,
        )

    private val thisWeek = WeekWindow.containing(monday, DayOfWeek.MONDAY)
    private val lastWeek = WeekWindow.before(monday, DayOfWeek.MONDAY, weeksAgo = 1)
    private val sunday = monday.plusDays(6)

    @Test
    fun `reports the gain over last week`() {
        val trend = WeekOverWeekTrend.of(
            current = summary(thisWeek, perDay = 9000, today = sunday),
            previous = summary(lastWeek, perDay = 6000, today = sunday),
        )!!

        assertEquals(3000L, trend.deltaSteps)
        assertEquals(WeekOverWeekTrend.Direction.UP, trend.direction)
        assertEquals(0.5, trend.deltaFraction!!, 1e-9)
    }

    @Test
    fun `reports a drop`() {
        val trend = WeekOverWeekTrend.of(
            current = summary(thisWeek, perDay = 4000, today = sunday),
            previous = summary(lastWeek, perDay = 8000, today = sunday),
        )!!

        assertEquals(-4000L, trend.deltaSteps)
        assertEquals(WeekOverWeekTrend.Direction.DOWN, trend.direction)
    }

    @Test
    fun `reports flat when the averages match`() {
        val trend = WeekOverWeekTrend.of(
            current = summary(thisWeek, perDay = 5000, today = sunday),
            previous = summary(lastWeek, perDay = 5000, today = sunday),
        )!!

        assertEquals(WeekOverWeekTrend.Direction.FLAT, trend.direction)
        assertEquals(0.0, trend.deltaFraction!!, 1e-9)
    }

    @Test
    fun `has no percentage when last week averaged zero`() {
        val trend = WeekOverWeekTrend.of(
            current = summary(thisWeek, perDay = 5000, today = sunday),
            previous = summary(lastWeek, perDay = 0, today = sunday),
        )!!

        assertEquals(5000L, trend.deltaSteps)
        assertNull(trend.deltaFraction)
    }

    @Test
    fun `is absent when a week has no data at all`() {
        val blank = calculator.summarize(emptyList(), lastWeek, today = sunday)

        assertNull(WeekOverWeekTrend.of(summary(thisWeek, 5000, sunday), blank))
    }

    @Test
    fun `refuses to compare averages built on different bases`() {
        val elapsed = summary(thisWeek, perDay = 5000, today = sunday)
        val calendar = WeeklyStepsCalculator(basis = AverageBasis.CALENDAR_DAYS)
            .summarize(lastWeek.dates.map { DailySteps(it, 5000) }, lastWeek, sunday)

        assertThrows<IllegalArgumentException> { WeekOverWeekTrend.of(elapsed, calendar) }
    }
}
