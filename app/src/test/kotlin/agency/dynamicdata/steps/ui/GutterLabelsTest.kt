package agency.dynamicdata.steps.ui

import agency.dynamicdata.steps.ui.dashboard.GutterLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GutterLabelsTest {

    private val gap = 14f
    private val top = 0f
    private val bottom = 150f

    private fun separate(goal: Float, average: Float) =
        GutterLabels.separate(goal, average, gap, top, bottom)

    @Test
    fun `labels already far apart stay where they are`() {
        assertEquals(40f to 100f, separate(goal = 40f, average = 100f))
    }

    @Test
    fun `labels that would overprint are pushed apart`() {
        val (goal, average) = separate(goal = 60f, average = 64f)

        assertTrue("got $goal and $average", abs(goal - average) >= gap - 0.001f)
    }

    @Test
    fun `the label above stays above, so each still matches its line`() {
        // Average line slightly higher on screen (smaller y) than the goal line.
        val (goal, average) = separate(goal = 64f, average = 60f)

        assertTrue("average label should stay above the goal label", average < goal)
    }

    @Test
    fun `the label below stays below`() {
        val (goal, average) = separate(goal = 60f, average = 64f)

        assertTrue("average label should stay below the goal label", average > goal)
    }

    @Test
    fun `lines at exactly the same height still get separate labels`() {
        val (goal, average) = separate(goal = 80f, average = 80f)

        assertTrue(abs(goal - average) >= gap - 0.001f)
    }

    @Test
    fun `a pair crowded against the top slides down instead of leaving the plot`() {
        val (goal, average) = separate(goal = 2f, average = 3f)

        assertTrue(goal >= top && average >= top)
        assertTrue(abs(goal - average) >= gap - 0.001f)
    }

    @Test
    fun `a pair crowded against the bottom slides up instead of leaving the plot`() {
        val (goal, average) = separate(goal = 149f, average = 148f)

        assertTrue(goal <= bottom && average <= bottom)
        assertTrue(abs(goal - average) >= gap - 0.001f)
    }

    @Test
    fun `labels are always kept inside the plot`() {
        val (goal, average) = separate(goal = -20f, average = 400f)

        assertEquals(top, goal)
        assertEquals(bottom, average)
    }
}
