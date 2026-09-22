package agency.dynamicdata.steps.reminder

import agency.dynamicdata.steps.core.DailySteps
import agency.dynamicdata.steps.core.StepGoal
import agency.dynamicdata.steps.health.HealthConnectAvailability
import agency.dynamicdata.steps.widget.FakeStepsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalReminderDeciderTest {

    // "Today" is 22 Sep, so the window is 15–21 Sep.
    private val today = LocalDate.of(2026, 9, 22)
    private val windowStart = LocalDate.of(2026, 9, 15)
    private val goal = StepGoal(10_000)

    private fun sevenDaysOf(perDay: Long) =
        (0L..6L).map { DailySteps(windowStart.plusDays(it), perDay) }

    private suspend fun decide(
        repository: FakeStepsRepository,
        enabled: Boolean = true,
        goal: StepGoal = this.goal,
    ) = GoalReminderDecider(repository).decide(today, goal, enabled)

    private fun quietBecause(decision: ReminderDecision): ReminderDecision.Reason =
        (decision as ReminderDecision.StayQuiet).reason

    @Test
    fun `notifies when the average is below the goal`() = runTest {
        val decision = decide(FakeStepsRepository(steps = sevenDaysOf(8_400)))

        val notify = decision as ReminderDecision.Notify
        assertEquals(8_400L, notify.summary.averageStepsPerDay)
        assertEquals(1_600L, notify.progress.stepsShort)
    }

    @Test
    fun `stays quiet when the goal is met`() = runTest {
        val decision = decide(FakeStepsRepository(steps = sevenDaysOf(11_200)))

        assertEquals(ReminderDecision.Reason.GOAL_MET, quietBecause(decision))
    }

    @Test
    fun `hitting the goal exactly counts as met, so it stays quiet`() = runTest {
        // The boundary matters: this is the difference between being nudged every
        // morning for landing exactly on target and not being nudged at all.
        val decision = decide(FakeStepsRepository(steps = sevenDaysOf(10_000)))

        assertEquals(ReminderDecision.Reason.GOAL_MET, quietBecause(decision))
    }

    @Test
    fun `one step short is still short`() = runTest {
        val decision = decide(FakeStepsRepository(steps = sevenDaysOf(9_999)))

        assertTrue(decision is ReminderDecision.Notify)
    }

    @Test
    fun `stays quiet when switched off, without reading anything`() = runTest {
        val repository = FakeStepsRepository(steps = sevenDaysOf(1_000))

        val decision = decide(repository, enabled = false)

        assertEquals(ReminderDecision.Reason.DISABLED, quietBecause(decision))
        assertTrue(repository.requestedWindows.isEmpty())
    }

    @Test
    fun `stays quiet when nothing was recorded`() = runTest {
        // No data is not a shortfall. Nagging someone whose tracker was off is the
        // fastest way to get the notification muted for good.
        val decision = decide(FakeStepsRepository(steps = emptyList()))

        assertEquals(ReminderDecision.Reason.NO_DATA, quietBecause(decision))
    }

    @Test
    fun `stays quiet without step permission`() = runTest {
        val decision = decide(FakeStepsRepository(permitted = false))

        assertEquals(ReminderDecision.Reason.NO_PERMISSION, quietBecause(decision))
    }

    @Test
    fun `stays quiet when permission is revoked mid-read`() = runTest {
        val decision = decide(FakeStepsRepository(failWith = SecurityException("revoked")))

        assertEquals(ReminderDecision.Reason.NO_PERMISSION, quietBecause(decision))
    }

    @Test
    fun `stays quiet when health connect is unavailable`() = runTest {
        val decision = decide(
            FakeStepsRepository(availability = HealthConnectAvailability.NOT_SUPPORTED),
        )

        assertEquals(
            ReminderDecision.Reason.HEALTH_CONNECT_UNAVAILABLE,
            quietBecause(decision),
        )
    }

    @Test
    fun `stays quiet rather than inventing a number when the read fails`() = runTest {
        val decision = decide(FakeStepsRepository(failWith = IllegalStateException("boom")))

        assertEquals(ReminderDecision.Reason.READ_FAILED, quietBecause(decision))
    }

    @Test
    fun `reads only the seven complete days, never today`() = runTest {
        val repository = FakeStepsRepository(steps = sevenDaysOf(8_400))

        decide(repository)

        assertEquals(1, repository.requestedWindows.size)
        assertEquals(windowStart, repository.requestedWindows[0].start)
        assertEquals(LocalDate.of(2026, 9, 21), repository.requestedWindows[0].end)
        assertTrue(repository.requestedWindows.none { today in it })
    }

    @Test
    fun `uses whichever goal it is handed`() = runTest {
        val repository = FakeStepsRepository(steps = sevenDaysOf(8_400))

        assertEquals(
            ReminderDecision.Reason.GOAL_MET,
            quietBecause(decide(repository, goal = StepGoal(8_000))),
        )
    }
}
