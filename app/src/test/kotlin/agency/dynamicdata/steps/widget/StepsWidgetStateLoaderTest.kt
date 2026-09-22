package agency.dynamicdata.steps.widget

import agency.dynamicdata.steps.core.DailySteps
import agency.dynamicdata.steps.core.WeekOverWeekTrend
import agency.dynamicdata.steps.health.HealthConnectAvailability
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class StepsWidgetStateLoaderTest {

    // Monday, so the current week is 21–27 Sep and the previous one is 14–20 Sep.
    private val monday = LocalDate.of(2026, 9, 21)
    private val wednesday = monday.plusDays(2)

    private fun loader(repository: FakeStepsRepository) =
        StepsWidgetStateLoader(repository, firstDayOfWeek = DayOfWeek.MONDAY)

    private fun week(start: LocalDate, perDay: Long) =
        (0L..6L).map { DailySteps(start.plusDays(it), perDay) }

    @Test
    fun `asks for permission before reading anything`() = runTest {
        val repository = FakeStepsRepository(permitted = false)

        val state = loader(repository).load(today = monday)

        assertEquals(StepsWidgetState.PermissionRequired, state)
        assertEquals(0, repository.readCount)
    }

    @Test
    fun `reports health connect missing without asking for permission`() = runTest {
        val repository = FakeStepsRepository(
            availability = HealthConnectAvailability.NOT_SUPPORTED,
        )

        val state = loader(repository).load(today = monday)

        assertEquals(StepsWidgetState.HealthConnectUnavailable(updatable = false), state)
        assertEquals(0, repository.readCount)
    }

    @Test
    fun `distinguishes an outdated health connect from an unsupported device`() = runTest {
        val repository = FakeStepsRepository(
            availability = HealthConnectAvailability.UPDATE_REQUIRED,
        )

        val state = loader(repository).load(today = monday)

        assertEquals(StepsWidgetState.HealthConnectUnavailable(updatable = true), state)
    }

    @Test
    fun `computes this week's average and the trend against last week`() = runTest {
        val repository = FakeStepsRepository(
            steps = week(monday.minusWeeks(1), perDay = 6000) +
                listOf(DailySteps(monday, 9000), DailySteps(monday.plusDays(1), 9000)),
        )

        val state = loader(repository).load(today = monday.plusDays(1)) as StepsWidgetState.Ready

        assertEquals(9000L, state.summary.averageStepsPerDay)
        assertEquals(6000L, state.trend!!.previousAverage)
        assertEquals(WeekOverWeekTrend.Direction.UP, state.trend!!.direction)
    }

    @Test
    fun `has no trend when last week was never tracked`() = runTest {
        val repository = FakeStepsRepository(steps = listOf(DailySteps(monday, 5000)))

        val state = loader(repository).load(today = monday) as StepsWidgetState.Ready

        assertEquals(5000L, state.summary.averageStepsPerDay)
        assertNull(state.trend)
    }

    @Test
    fun `reports no data rather than an average of zero`() = runTest {
        val repository = FakeStepsRepository(steps = emptyList())

        val state = loader(repository).load(today = wednesday)

        assertTrue(state is StepsWidgetState.NoData)
    }

    @Test
    fun `falls back to asking for permission when it is revoked mid-read`() = runTest {
        val repository = FakeStepsRepository(failWith = SecurityException("revoked"))

        val state = loader(repository).load(today = monday)

        assertEquals(StepsWidgetState.PermissionRequired, state)
    }

    @Test
    fun `surfaces an error instead of crashing the widget`() = runTest {
        val repository = FakeStepsRepository(failWith = IllegalStateException("provider died"))

        val state = loader(repository).load(today = monday)

        assertEquals(StepsWidgetState.Error(summary = null), state)
    }

    @Test
    fun `follows a sunday-start locale`() = runTest {
        val sunday = monday.minusDays(1)
        val repository = FakeStepsRepository(steps = listOf(DailySteps(sunday, 8000)))

        val state = StepsWidgetStateLoader(repository, firstDayOfWeek = DayOfWeek.SUNDAY)
            .load(today = monday) as StepsWidgetState.Ready

        assertEquals(sunday, state.summary.weekStart)
        assertEquals(2, state.summary.elapsedDays)
        assertEquals(4000L, state.summary.averageStepsPerDay) // 8000 over Sun + Mon
    }
}
