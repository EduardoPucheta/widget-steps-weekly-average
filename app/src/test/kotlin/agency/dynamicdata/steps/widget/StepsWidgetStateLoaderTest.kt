package agency.dynamicdata.steps.widget

import agency.dynamicdata.steps.core.DailySteps
import agency.dynamicdata.steps.core.StepGoal
import agency.dynamicdata.steps.core.StepsSummary
import agency.dynamicdata.steps.health.HealthConnectAvailability
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `a refused read is not reported as missing permission`() = runTest {
        // Access is granted — the check passed — but Health Connect refuses the read
        // because the widget is being drawn from the background. That used to show
        // "Tap to allow step access", asking for a permission the user already gave.
        val repository = FakeStepsRepository(failWith = SecurityException("background read"))

        val state = loader(repository).load(today, goal)

        assertEquals(StepsWidgetState.Error, state)
    }

    @Test
    fun `surfaces an error instead of crashing the widget`() = runTest {
        val repository = FakeStepsRepository(failWith = IllegalStateException("provider died"))

        assertEquals(StepsWidgetState.Error, loader(repository).load(today, goal))
    }

    @Test
    fun `remembers every reading that succeeds`() = runTest {
        val cache = FakeReadingCache()
        val repository = FakeStepsRepository(steps = sevenDaysFrom(windowStart, perDay = 8400))

        StepsWidgetStateLoader(repository, cache).load(today, goal)

        assertEquals(8400L, cache.saved?.averageStepsPerDay)
    }

    @Test
    fun `a refused read shows this week's saved reading as current`() = runTest {
        val cache = FakeReadingCache()
        StepsWidgetStateLoader(
            FakeStepsRepository(steps = sevenDaysFrom(windowStart, perDay = 8400)),
            cache,
        ).load(today, goal)

        val state = StepsWidgetStateLoader(
            FakeStepsRepository(failWith = SecurityException("background read")),
            cache,
        ).load(today, goal)

        val ready = state as StepsWidgetState.Ready
        assertEquals(8400L, ready.summary.averageStepsPerDay)
        assertTrue("the days in the window are over, so the number is still right", ready.current)
    }

    @Test
    fun `a saved reading from an earlier week is shown but flagged`() = runTest {
        val cache = FakeReadingCache()
        // Read on the 21st, so its window is 14–20 Sep, one day behind today's.
        StepsWidgetStateLoader(
            FakeStepsRepository(steps = sevenDaysFrom(windowStart.minusDays(1), perDay = 7000)),
            cache,
        ).load(today.minusDays(1), goal)

        val state = StepsWidgetStateLoader(
            FakeStepsRepository(failWith = SecurityException("background read")),
            cache,
        ).load(today, goal)

        val ready = state as StepsWidgetState.Ready
        assertEquals(7000L, ready.summary.averageStepsPerDay)
        assertFalse(ready.current)
    }

    @Test
    fun `a saved reading is measured against today's goal`() = runTest {
        val cache = FakeReadingCache()
        StepsWidgetStateLoader(
            FakeStepsRepository(steps = sevenDaysFrom(windowStart, perDay = 8400)),
            cache,
        ).load(today, StepGoal(10_000))

        val state = StepsWidgetStateLoader(
            FakeStepsRepository(failWith = IllegalStateException("provider died")),
            cache,
        ).load(today, StepGoal(8_000))

        assertTrue((state as StepsWidgetState.Ready).progress.isMet)
    }

    @Test
    fun `a reading that fails to save is still shown`() = runTest {
        val repository = FakeStepsRepository(steps = sevenDaysFrom(windowStart, perDay = 8400))

        val state = StepsWidgetStateLoader(repository, FakeReadingCache(failSave = true)).load(today, goal)

        assertTrue((state as StepsWidgetState.Ready).current)
    }

    @Test
    fun `missing access is still reported, and never covered up by a saved reading`() = runTest {
        val cache = FakeReadingCache()
        StepsWidgetStateLoader(
            FakeStepsRepository(steps = sevenDaysFrom(windowStart, perDay = 8400)),
            cache,
        ).load(today, goal)

        val state = StepsWidgetStateLoader(FakeStepsRepository(permitted = false), cache).load(today, goal)

        assertEquals(StepsWidgetState.PermissionRequired, state)
    }
}

private class FakeReadingCache(private val failSave: Boolean = false) : ReadingCache {
    var saved: StepsSummary? = null

    override suspend fun save(summary: StepsSummary) {
        if (failSave) throw java.io.IOException("disk full")
        saved = summary
    }

    override suspend fun last(): StepsSummary? = saved
}
