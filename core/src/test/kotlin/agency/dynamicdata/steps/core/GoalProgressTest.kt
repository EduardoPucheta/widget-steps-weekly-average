package agency.dynamicdata.steps.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GoalProgressTest {

    private val goal = StepGoal(10_000)

    @Test
    fun `names the gap while short of the goal`() {
        val progress = GoalProgress(goal, averageStepsPerDay = 8400)

        assertEquals(1600L, progress.stepsShort)
        assertEquals(0L, progress.stepsOver)
        assertFalse(progress.isMet)
        assertEquals(84, progress.percentOfGoal)
    }

    @Test
    fun `names the surplus once past the goal`() {
        val progress = GoalProgress(goal, averageStepsPerDay = 11_200)

        assertEquals(0L, progress.stepsShort)
        assertEquals(1200L, progress.stepsOver)
        assertTrue(progress.isMet)
        assertEquals(112, progress.percentOfGoal)
    }

    @Test
    fun `hitting the goal exactly counts as met with no gap either way`() {
        val progress = GoalProgress(goal, averageStepsPerDay = 10_000)

        assertEquals(0L, progress.stepsShort)
        assertEquals(0L, progress.stepsOver)
        assertTrue(progress.isMet)
        assertEquals(1.0f, progress.barFraction)
    }

    @Test
    fun `the bar fills but never overflows its track`() {
        // 140% of the goal: the text may say so, the bar must not draw past its end.
        val progress = GoalProgress(goal, averageStepsPerDay = 14_000)

        assertEquals(1.4, progress.fraction, 1e-9)
        assertEquals(1.0f, progress.barFraction)
        assertEquals(140, progress.percentOfGoal)
    }

    @Test
    fun `beating the goal starts a second lap`() {
        // 140% of the goal: the first lap is full, the second is 40% round.
        val progress = GoalProgress(goal, averageStepsPerDay = 14_000)

        assertEquals(1.0f, progress.barFraction)
        assertEquals(0.4f, progress.overflowFraction, 1e-6f)
    }

    @Test
    fun `there is no second lap until the goal is actually met`() {
        assertEquals(0.0f, GoalProgress(goal, averageStepsPerDay = 9_999).overflowFraction)
        assertEquals(0.0f, GoalProgress(goal, averageStepsPerDay = 10_000).overflowFraction)
    }

    @Test
    fun `the second lap saturates at twice the goal`() {
        assertEquals(1.0f, GoalProgress(goal, averageStepsPerDay = 20_000).overflowFraction)
        assertEquals(1.0f, GoalProgress(goal, averageStepsPerDay = 90_000).overflowFraction)
    }

    @Test
    fun `an empty week leaves the bar empty rather than undefined`() {
        val progress = GoalProgress(goal, averageStepsPerDay = 0)

        assertEquals(0.0f, progress.barFraction)
        assertEquals(10_000L, progress.stepsShort)
        assertFalse(progress.isMet)
    }

    @Test
    fun `a goal must be positive`() {
        assertThrows<IllegalArgumentException> { StepGoal(0) }
        assertThrows<IllegalArgumentException> { StepGoal(-500) }
    }

    @Test
    fun `an unusable stored goal falls back to the default`() {
        assertEquals(StepGoal.DEFAULT, StepGoal.fromStoredValue(null))
        assertEquals(StepGoal.DEFAULT, StepGoal.fromStoredValue(0))
        assertEquals(StepGoal.DEFAULT, StepGoal.fromStoredValue(-1))
        assertEquals(StepGoal(12_000), StepGoal.fromStoredValue(12_000))
    }

    @Test
    fun `the default goal is ten thousand`() {
        assertEquals(10_000L, StepGoal.DEFAULT.stepsPerDay)
    }
}
