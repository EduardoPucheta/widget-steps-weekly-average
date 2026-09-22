package agency.dynamicdata.steps.widget

import agency.dynamicdata.steps.core.GoalProgress
import agency.dynamicdata.steps.core.StepGoal
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class StepsWidgetFormatTest {

    private val uk = Locale.UK

    @Test
    fun `counts below ten thousand stay exact`() {
        assertEquals("403", StepsWidgetFormat.compact(403, uk))
        assertEquals("9,999", StepsWidgetFormat.compact(9_999, uk))
    }

    @Test
    fun `a whole number of thousands loses its decimal`() {
        // "of 10.0k" reads like a measurement rather than a round target.
        assertEquals("10k", StepsWidgetFormat.compact(10_000, uk))
        assertEquals("12k", StepsWidgetFormat.compact(12_000, uk))
    }

    @Test
    fun `a value that rounds to a whole thousand also loses its decimal`() {
        assertEquals("10k", StepsWidgetFormat.compact(10_004, uk))
        assertEquals("15k", StepsWidgetFormat.compact(14_990, uk))
    }

    @Test
    fun `otherwise one decimal is kept`() {
        assertEquals("14.1k", StepsWidgetFormat.compact(14_100, uk))
        assertEquals("10.4k", StepsWidgetFormat.compact(10_400, uk))
    }

    @Test
    fun `millions are abbreviated the same way`() {
        assertEquals("1M", StepsWidgetFormat.compact(1_000_000, uk))
        assertEquals("1.5M", StepsWidgetFormat.compact(1_450_000, uk))
    }

    @Test
    fun `the goal line names the gap in steps, not only a percentage`() {
        val short = GoalProgress(StepGoal(10_000), averageStepsPerDay = 8_400)

        assertEquals("1,600 short of 10k", StepsWidgetFormat.goal(short, uk))
    }

    @Test
    fun `the goal line names the surplus once past the goal`() {
        val over = GoalProgress(StepGoal(10_000), averageStepsPerDay = 14_077)

        assertEquals("4,077 over 10k", StepsWidgetFormat.goal(over, uk))
    }

    @Test
    fun `hitting the goal exactly reads as met rather than as a zero gap`() {
        val met = GoalProgress(StepGoal(10_000), averageStepsPerDay = 10_000)

        assertEquals("goal met — 10k", StepsWidgetFormat.goal(met, uk))
    }

    @Test
    fun `the spoken goal line spells the numbers out in full`() {
        val short = GoalProgress(StepGoal(10_000), averageStepsPerDay = 8_400)

        assertEquals(
            "1,600 steps below your goal of 10,000",
            StepsWidgetFormat.goalSpoken(short, uk),
        )
    }
}
