package agency.dynamicdata.steps.widget

import agency.dynamicdata.steps.core.DailySteps
import agency.dynamicdata.steps.core.StepGoal
import agency.dynamicdata.steps.health.HealthConnectAvailability
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StepsWidgetStateLoaderTest {

    // "Today". The window under test is 15–21 Sep; the one before it is 8–14 Sep.
    private val today = LocalDate.of(2026, 9, 22)
    private val windowStart = LocalDate.of(2026, 9, 15)
    private val goal = StepGoal(10_000)

    private fun loader(repository: FakeStepsRepository) = StepsWidgetStateLoader(repository)

    private fun sevenDaysFrom(start: LocalDate, perDay: Long) =
        (0L..6L).map { DailySteps(start.plusDays(it), perDay) }

    @Test
    fun `asks for permission before reading anything`() = runTest {
        val repository = FakeStepsRepository(permitted = false)

        val state = loader(repository).load(today, goal)

        assertEquals(StepsWidgetState.PermissionRequired, state)
        assertTrue(repository.requestedWindows.isEmpty())
    }

    @Test
    fun `reports health connect missing without asking for permission`() = runTest {
        val repository = FakeStepsRepository(
            availability = HealthConnectAvailability.NOT_SUPPORTED,
        )

        val state = loader(repository).load(today, goal)

        assertEquals(StepsWidgetState.HealthConnectUnavailable(updatable = false), state)
        assertTrue(repository.requestedWindows.isEmpty())
    }

    @Test
    fun `distinguishes an outdated health connect from an unsupported device`() = runTest {
        val repository = FakeStepsRepository(
            availability = HealthConnectAvailability.UPDATE_REQUIRED,
        )

        val state = loader(repository).load(today, goal)

        assertEquals(StepsWidgetState.HealthConnectUnavailable(updatable = true), state)
    }

    @Test
    fun `reads the last seven complete days, and nothing else`() = runTest {
        val repository = FakeStepsRepository()

        loader(repository).load(today, goal)

        // One window, one read. Fetching more would spend a Health Connect round
        // trip per refresh on data the widget does not show.
        assertEquals(1, repository.requestedWindows.size)
        assertEquals(LocalDate.of(2026, 9, 15), repository.requestedWindows[0].start)
        assertEquals(LocalDate.of(2026, 9, 21), repository.requestedWindows[0].end)
    }

    @Test
    fun `never asks for today's steps`() = runTest {
        val repository = FakeStepsRepository()

        loader(repository).load(today, goal)

        assertTrue(repository.requestedWindows.none { today in it })
    }

    @Test
    fun `computes the average over the window only`() = runTest {
        val repository = FakeStepsRepository(
            steps = sevenDaysFrom(windowStart.minusDays(7), perDay = 6000) +
                sevenDaysFrom(windowStart, perDay = 9000),
        )

        val state = loader(repository).load(today, goal) as StepsWidgetState.Ready

        assertEquals(9000L, state.summary.averageStepsPerDay)
    }

    @Test
    fun `measures the average against the goal`() = runTest {
        val repository = FakeStepsRepository(steps = sevenDaysFrom(windowStart, perDay = 8400))

        val state = loader(repository).load(today, goal) as StepsWidgetState.Ready

        assertEquals(1600L, state.progress.stepsShort)
        assertEquals(84, state.progress.percentOfGoal)
        assertEquals(false, state.progress.isMet)
    }

    @Test
    fun `reports the goal as met once the average reaches it`() = runTest {
        val repository = FakeStepsRepository(steps = sevenDaysFrom(windowStart, perDay = 11_200))

        val state = loader(repository).load(today, goal) as StepsWidgetState.Ready

        assertTrue(state.progress.isMet)
        assertEquals(1200L, state.progress.stepsOver)
        assertEquals(1.0f, state.progress.barFraction, 1e-6f)
    }

    @Test
    fun `uses whichever goal it is handed`() = runTest {
        val repository = FakeStepsRepository(steps = sevenDaysFrom(windowStart, perDay = 6000))

        val state = loader(repository).load(today, StepGoal(6000)) as StepsWidgetState.Ready

        assertTrue(state.progress.isMet)
    }

    @Test
    fun `reports no data rather than an average of zero`() = runTest {
        val state = loader(FakeStepsRepository(steps = emptyList())).load(today, goal)

        assertTrue(state is StepsWidgetState.NoData)
    }

    @Test
    fun `falls back to asking for permission when it is revoked mid-read`() = runTest {
        val repository = FakeStepsRepository(failWith = SecurityException("revoked"))

        assertEquals(StepsWidgetState.PermissionRequired, loader(repository).load(today, goal))
    }

    @Test
    fun `surfaces an error instead of crashing the widget`() = runTest {
        val repository = FakeStepsRepository(failWith = IllegalStateException("provider died"))

        assertEquals(StepsWidgetState.Error, loader(repository).load(today, goal))
    }
}
