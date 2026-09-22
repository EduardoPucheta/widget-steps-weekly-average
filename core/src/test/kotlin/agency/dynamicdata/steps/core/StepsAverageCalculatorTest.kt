package agency.dynamicdata.steps.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class StepsAverageCalculatorTest {

    // "Today" throughout. The window under test is therefore 15–21 Sep.
    private val today = LocalDate.of(2026, 9, 22)
    private val windowStart = LocalDate.of(2026, 9, 15)
    private val calculator = StepsAverageCalculator()

    private fun days(vararg steps: Long, from: LocalDate = windowStart): List<DailySteps> =
        steps.mapIndexed { index, count -> DailySteps(from.plusDays(index.toLong()), count) }

    @Nested
    @DisplayName("the rolling window")
    inner class Window {

        @Test
        fun `covers the seven complete days ending yesterday`() {
            val summary = calculator.summarizeRecentDays(emptyList(), today)

            assertEquals(LocalDate.of(2026, 9, 15), summary.window.start)
            assertEquals(LocalDate.of(2026, 9, 21), summary.window.end)
            assertEquals(7, summary.dayCount)
        }

        @Test
        fun `excludes today entirely`() {
            // A big partial count today must not move the number at all.
            val summary = calculator.summarizeRecentDays(
                dailySteps = days(7000, 7000, 7000, 7000, 7000, 7000, 7000) +
                    listOf(DailySteps(today, 99_000)),
                today = today,
            )

            assertEquals(49_000L, summary.totalSteps)
            assertEquals(7000L, summary.averageStepsPerDay)
        }

        @Test
        fun `moves forward by one day at midnight`() {
            val tomorrow = calculator.summarizeRecentDays(emptyList(), today.plusDays(1))

            assertEquals(LocalDate.of(2026, 9, 16), tomorrow.window.start)
            assertEquals(LocalDate.of(2026, 9, 22), tomorrow.window.end)
        }

        @Test
        fun `does not reset on a monday`() {
            // 2026-09-21 is a Monday. A calendar week would restart here; this must not.
            val monday = calculator.summarizeRecentDays(emptyList(), LocalDate.of(2026, 9, 21))

            assertEquals(LocalDate.of(2026, 9, 14), monday.window.start)
            assertEquals(7, monday.dayCount)
        }

        @Test
        fun `spans a month boundary without changing length`() {
            val summary = calculator.summarizeRecentDays(emptyList(), LocalDate.of(2026, 10, 3))

            assertEquals(LocalDate.of(2026, 9, 26), summary.window.start)
            assertEquals(LocalDate.of(2026, 10, 2), summary.window.end)
            assertEquals(7, summary.dayCount)
        }

        @Test
        fun `rejects a window of no days`() {
            assertThrows<IllegalArgumentException> { StepsWindow.lastCompleteDays(today, days = 0) }
        }

        @Test
        fun `the preceding window is the seven days before`() {
            val preceding = StepsWindow.lastCompleteDays(today).preceding()

            assertEquals(LocalDate.of(2026, 9, 8), preceding.start)
            assertEquals(LocalDate.of(2026, 9, 14), preceding.end)
        }
    }

    @Nested
    @DisplayName("the average")
    inner class Average {

        @Test
        fun `divides the total by the seven days`() {
            val summary = calculator.summarizeRecentDays(
                days(1000, 2000, 3000, 4000, 5000, 6000, 7000), today,
            )

            assertEquals(28_000L, summary.totalSteps)
            assertEquals(4000L, summary.averageStepsPerDay)
        }

        @Test
        fun `counts an untracked day as a zero`() {
            // Walked two days out of the seven.
            val summary = calculator.summarizeRecentDays(
                dailySteps = listOf(
                    DailySteps(windowStart, 9000),
                    DailySteps(windowStart.plusDays(2), 5000),
                ),
                today = today,
            )

            assertEquals(2, summary.daysWithData)
            assertEquals(2000L, summary.averageStepsPerDay) // 14000 / 7, not / 2
        }

        @Test
        fun `rounds half away from zero rather than truncating`() {
            // 10,500 over 7 days is 1500 exactly; 10,504 is 1500.57 and must round up.
            val summary = calculator.summarizeRecentDays(
                days(1501, 1501, 1501, 1500, 1500, 1500, 1501), today,
            )

            assertEquals(10_504L, summary.totalSteps)
            assertEquals(1501L, summary.averageStepsPerDay)
        }

        @Test
        fun `ignores days outside the window`() {
            val history = days(50_000, from = windowStart.minusDays(10)) + days(7000)

            val summary = calculator.summarizeRecentDays(history, today)

            assertEquals(7000L, summary.totalSteps)
            assertEquals(1, summary.daysWithData)
        }

        @Test
        fun `sums duplicate entries for the same day`() {
            // Phone and watch both reporting the same date.
            val summary = calculator.summarizeRecentDays(
                listOf(DailySteps(windowStart, 4000), DailySteps(windowStart, 1500)), today,
            )

            assertEquals(5500L, summary.totalSteps)
            assertEquals(1, summary.daysWithData)
        }
    }

    @Nested
    @DisplayName("averaging basis")
    inner class Basis {

        private val tracked = listOf(
            DailySteps(windowStart, 9000),
            DailySteps(windowStart.plusDays(2), 5000),
        )

        @Test
        fun `all days divides by the whole window`() {
            val summary = StepsAverageCalculator(basis = AverageBasis.ALL_DAYS)
                .summarizeRecentDays(tracked, today)

            assertEquals(2000L, summary.averageStepsPerDay) // 14000 / 7
        }

        @Test
        fun `days with data divides by reported days only`() {
            val summary = StepsAverageCalculator(basis = AverageBasis.DAYS_WITH_DATA)
                .summarizeRecentDays(tracked, today)

            assertEquals(7000L, summary.averageStepsPerDay) // 14000 / 2
        }
    }

    @Nested
    @DisplayName("empty input")
    inner class EdgeCases {

        @Test
        fun `reports no data rather than a zero average`() {
            val summary = calculator.summarizeRecentDays(emptyList(), today)

            assertTrue(summary.hasNoData)
            assertEquals(0L, summary.averageStepsPerDay)
        }

        @Test
        fun `does not divide by zero when no day was tracked`() {
            val summary = StepsAverageCalculator(basis = AverageBasis.DAYS_WITH_DATA)
                .summarizeRecentDays(emptyList(), today)

            assertEquals(0L, summary.averageStepsPerDay)
            assertTrue(summary.hasNoData)
        }

        @Test
        fun `rejects a negative step count at construction`() {
            assertThrows<IllegalArgumentException> { DailySteps(today, -1) }
        }
    }
}
