package agency.dynamicdata.steps.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.DayOfWeek
import java.time.LocalDate

class WeeklyStepsCalculatorTest {

    // 2026-09-21 is a Monday, so this week runs Mon 21st .. Sun 27th.
    private val monday = LocalDate.of(2026, 9, 21)
    private val calculator = WeeklyStepsCalculator(
        firstDayOfWeek = DayOfWeek.MONDAY,
        basis = AverageBasis.ELAPSED_DAYS,
    )

    private fun days(vararg steps: Long, from: LocalDate = monday): List<DailySteps> =
        steps.mapIndexed { index, count -> DailySteps(from.plusDays(index.toLong()), count) }

    @Nested
    @DisplayName("a complete week")
    inner class CompleteWeek {

        @Test
        fun `averages the seven days`() {
            val summary = calculator.summarizeCurrentWeek(
                dailySteps = days(1000, 2000, 3000, 4000, 5000, 6000, 7000),
                today = monday.plusDays(6),
            )

            assertEquals(28_000L, summary.totalSteps)
            assertEquals(7, summary.daysWithData)
            assertEquals(7, summary.elapsedDays)
            assertEquals(4000L, summary.averageStepsPerDay)
            assertFalse(summary.isPartialWeek)
        }

        @Test
        fun `spans monday to sunday inclusive`() {
            val summary = calculator.summarizeCurrentWeek(days(1), today = monday)

            assertEquals(LocalDate.of(2026, 9, 21), summary.weekStart)
            assertEquals(LocalDate.of(2026, 9, 27), summary.weekEnd)
        }

        @Test
        fun `rounds half away from zero rather than truncating`() {
            // 7 steps over 2 elapsed days is 3.5 — reporting 3 would understate it.
            val summary = calculator.summarizeCurrentWeek(days(3, 4), today = monday.plusDays(1))

            assertEquals(4L, summary.averageStepsPerDay)
        }
    }

    @Nested
    @DisplayName("a partial week")
    inner class PartialWeek {

        @Test
        fun `divides by elapsed days, not by seven`() {
            // Tuesday: two days have happened, so 12000 steps is 6000 per day so far.
            val summary = calculator.summarizeCurrentWeek(days(5000, 7000), today = monday.plusDays(1))

            assertEquals(12_000L, summary.totalSteps)
            assertEquals(2, summary.elapsedDays)
            assertEquals(6000L, summary.averageStepsPerDay)
            assertTrue(summary.isPartialWeek)
        }

        @Test
        fun `counts an untracked day that has already passed as a zero`() {
            // Walked Monday and Wednesday, phone left at home on Tuesday.
            val tracked = listOf(
                DailySteps(monday, 9000),
                DailySteps(monday.plusDays(2), 3000),
            )

            val summary = calculator.summarizeCurrentWeek(tracked, today = monday.plusDays(2))

            assertEquals(2, summary.daysWithData)
            assertEquals(3, summary.elapsedDays)
            assertEquals(4000L, summary.averageStepsPerDay) // 12000 / 3, not / 2
        }

        @Test
        fun `does not let tomorrow's data inflate today's average`() {
            // A provider can report a future-dated day across a time-zone change; it
            // still counts toward the total but must not shrink the divisor.
            val summary = calculator.summarizeCurrentWeek(
                dailySteps = days(4000, 4000, 4000),
                today = monday.plusDays(1),
            )

            assertEquals(12_000L, summary.totalSteps)
            assertEquals(2, summary.elapsedDays)
            assertEquals(6000L, summary.averageStepsPerDay)
        }
    }

    @Nested
    @DisplayName("averaging basis")
    inner class Basis {

        private val tracked = listOf(
            DailySteps(monday, 9000),
            DailySteps(monday.plusDays(2), 3000),
        )
        private val wednesday = monday.plusDays(2)

        @Test
        fun `calendar days always divides by seven`() {
            val summary = WeeklyStepsCalculator(basis = AverageBasis.CALENDAR_DAYS)
                .summarizeCurrentWeek(tracked, today = wednesday)

            assertEquals(1714L, summary.averageStepsPerDay) // 12000 / 7
        }

        @Test
        fun `elapsed days divides by days started`() {
            val summary = WeeklyStepsCalculator(basis = AverageBasis.ELAPSED_DAYS)
                .summarizeCurrentWeek(tracked, today = wednesday)

            assertEquals(4000L, summary.averageStepsPerDay) // 12000 / 3
        }

        @Test
        fun `days with data divides by reported days only`() {
            val summary = WeeklyStepsCalculator(basis = AverageBasis.DAYS_WITH_DATA)
                .summarizeCurrentWeek(tracked, today = wednesday)

            assertEquals(6000L, summary.averageStepsPerDay) // 12000 / 2
        }
    }

    @Nested
    @DisplayName("empty and malformed input")
    inner class EdgeCases {

        @Test
        fun `reports no data rather than a zero average`() {
            val summary = calculator.summarizeCurrentWeek(emptyList(), today = monday)

            assertTrue(summary.hasNoData)
            assertEquals(0L, summary.totalSteps)
            assertEquals(0L, summary.averageStepsPerDay)
        }

        @Test
        fun `does not divide by zero when no day was tracked`() {
            val summary = WeeklyStepsCalculator(basis = AverageBasis.DAYS_WITH_DATA)
                .summarizeCurrentWeek(emptyList(), today = monday)

            assertEquals(0L, summary.averageStepsPerDay)
            assertTrue(summary.hasNoData)
        }

        @Test
        fun `ignores days outside the week`() {
            val history = days(1000, 2000, from = monday.minusWeeks(1)) + days(6000, 6000)

            val summary = calculator.summarizeCurrentWeek(history, today = monday.plusDays(1))

            assertEquals(12_000L, summary.totalSteps)
            assertEquals(2, summary.daysWithData)
        }

        @Test
        fun `sums duplicate entries for the same day`() {
            // Two providers (phone and watch) both reporting Monday.
            val summary = calculator.summarizeCurrentWeek(
                dailySteps = listOf(DailySteps(monday, 4000), DailySteps(monday, 1500)),
                today = monday,
            )

            assertEquals(5500L, summary.totalSteps)
            assertEquals(1, summary.daysWithData)
        }

        @Test
        fun `rejects a negative step count at construction`() {
            assertThrows<IllegalArgumentException> { DailySteps(monday, -1) }
        }
    }

    @Nested
    @DisplayName("week boundaries")
    inner class Boundaries {

        @Test
        fun `a sunday-start locale puts sunday at the front of the week`() {
            val sundayStart = WeeklyStepsCalculator(firstDayOfWeek = DayOfWeek.SUNDAY)
            // The Sunday before Monday 2026-09-21.
            val summary = sundayStart.summarizeCurrentWeek(emptyList(), today = monday)

            assertEquals(LocalDate.of(2026, 9, 20), summary.weekStart)
            assertEquals(LocalDate.of(2026, 9, 26), summary.weekEnd)
            assertEquals(2, summary.elapsedDays) // Sunday and Monday
        }

        @Test
        fun `a past week always counts as seven elapsed days`() {
            val lastWeek = WeekWindow.before(monday, DayOfWeek.MONDAY, weeksAgo = 1)

            val summary = calculator.summarize(
                dailySteps = days(7000, 7000, 7000, 7000, 7000, 7000, 7000, from = lastWeek.start),
                window = lastWeek,
                today = monday,
            )

            assertEquals(7, summary.elapsedDays)
            assertEquals(7000L, summary.averageStepsPerDay)
            assertFalse(summary.isPartialWeek)
        }

        @Test
        fun `a week window spans exactly seven days`() {
            assertThrows<IllegalArgumentException> {
                WeekWindow(monday, monday.plusDays(5))
            }
        }

        @Test
        fun `week boundaries survive a month and year rollover`() {
            val newYearsEve = LocalDate.of(2026, 12, 31) // a Thursday
            val window = WeekWindow.containing(newYearsEve, DayOfWeek.MONDAY)

            assertEquals(LocalDate.of(2026, 12, 28), window.start)
            assertEquals(LocalDate.of(2027, 1, 3), window.end)
            assertTrue(LocalDate.of(2027, 1, 1) in window)
        }
    }
}
