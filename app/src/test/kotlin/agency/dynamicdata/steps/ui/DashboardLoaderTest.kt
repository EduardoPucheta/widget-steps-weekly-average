package agency.dynamicdata.steps.ui

import agency.dynamicdata.steps.core.DailySteps
import agency.dynamicdata.steps.core.StepGoal
import agency.dynamicdata.steps.health.HealthConnectAvailability
import agency.dynamicdata.steps.ui.dashboard.DashboardLoader
import agency.dynamicdata.steps.ui.dashboard.DashboardState
import agency.dynamicdata.steps.widget.FakeStepsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DashboardLoaderTest {

    private val today = LocalDate.of(2026, 9, 22)
    private val windowStart = LocalDate.of(2026, 9, 15)
    private val goal = StepGoal(10_000)

    private suspend fun load(repository: FakeStepsRepository) =
        DashboardLoader(repository).load(today, goal)

    @Test
    fun `gives one entry per day even when nothing was recorded`() = runTest {
        val state = load(FakeStepsRepository(steps = emptyList())) as DashboardState.Ready

        assertEquals(7, state.days.size)
        assertEquals(0, state.trackedDays)
        assertTrue(state.days.all { it.steps == null })
    }

    @Test
    fun `keeps gaps as gaps rather than zeros`() = runTest {
        val repository = FakeStepsRepository(
            steps = listOf(
                DailySteps(windowStart, 9_000),
                DailySteps(windowStart.plusDays(2), 5_000),
            ),
        )

        val state = load(repository) as DashboardState.Ready

        assertEquals(9_000L, state.days[0].steps)
        assertNull(state.days[1].steps)
        assertEquals(5_000L, state.days[2].steps)
        assertEquals(2, state.trackedDays)
    }

    @Test
    fun `the headline and the chart come from the same read`() = runTest {
        // One round trip. Two would let the number and the bars drift apart.
        val repository = FakeStepsRepository(
            steps = (0L..6L).map { DailySteps(windowStart.plusDays(it), 7_000) },
        )

        val state = load(repository) as DashboardState.Ready

        assertEquals(1, repository.requestedWindows.size)
        assertEquals(7_000L, state.summary.averageStepsPerDay)
        assertEquals(49_000L, state.days.sumOf { it.steps ?: 0L })
    }

    @Test
    fun `reports the tallest day so the chart can scale`() = runTest {
        val repository = FakeStepsRepository(
            steps = listOf(
                DailySteps(windowStart, 4_000),
                DailySteps(windowStart.plusDays(1), 16_500),
            ),
        )

        val state = load(repository) as DashboardState.Ready

        assertEquals(16_500L, state.highestDay)
    }

    @Test
    fun `the tallest day is zero when nothing is known, not an error`() = runTest {
        val state = load(FakeStepsRepository(steps = emptyList())) as DashboardState.Ready

        assertEquals(0L, state.highestDay)
    }

    @Test
    fun `measures the average against the goal`() = runTest {
        val repository = FakeStepsRepository(
            steps = (0L..6L).map { DailySteps(windowStart.plusDays(it), 8_400) },
        )

        val state = load(repository) as DashboardState.Ready

        assertEquals(1_600L, state.progress.stepsShort)
    }

    @Test
    fun `asks for permission before reading`() = runTest {
        val repository = FakeStepsRepository(permitted = false)

        assertEquals(DashboardState.PermissionRequired, load(repository))
        assertTrue(repository.requestedWindows.isEmpty())
    }

    @Test
    fun `distinguishes an outdated health connect from an unsupported device`() = runTest {
        assertEquals(
            DashboardState.HealthConnectUnavailable(updatable = true),
            load(FakeStepsRepository(availability = HealthConnectAvailability.UPDATE_REQUIRED)),
        )
        assertEquals(
            DashboardState.HealthConnectUnavailable(updatable = false),
            load(FakeStepsRepository(availability = HealthConnectAvailability.NOT_SUPPORTED)),
        )
    }

    @Test
    fun `falls back to asking for permission when it is revoked mid-read`() = runTest {
        assertEquals(
            DashboardState.PermissionRequired,
            load(FakeStepsRepository(failWith = SecurityException("revoked"))),
        )
    }

    @Test
    fun `surfaces an error rather than an empty chart when the read fails`() = runTest {
        assertEquals(
            DashboardState.Error,
            load(FakeStepsRepository(failWith = IllegalStateException("boom"))),
        )
    }

    @Test
    fun `never asks for today`() = runTest {
        val repository = FakeStepsRepository()

        load(repository)

        assertTrue(repository.requestedWindows.none { today in it })
    }
}
