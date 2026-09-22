package agency.dynamicdata.steps.core

/**
 * How an average compares with the goal.
 *
 * The widget needs three different readings of the same fact — a bar to fill, a gap
 * to name, and whether to celebrate — so they are derived once, here, rather than
 * recomputed at each call site with subtly different rounding.
 */
data class GoalProgress(
    val goal: StepGoal,
    val averageStepsPerDay: Long,
) {
    /** Steps still needed per day to reach the goal, or 0 once it is met. */
    val stepsShort: Long get() = (goal.stepsPerDay - averageStepsPerDay).coerceAtLeast(0)

    /** Steps per day beyond the goal, or 0 while short of it. */
    val stepsOver: Long get() = (averageStepsPerDay - goal.stepsPerDay).coerceAtLeast(0)

    val isMet: Boolean get() = averageStepsPerDay >= goal.stepsPerDay

    /**
     * Progress as a fraction of the goal, **uncapped** — 1.4 means 40% past it.
     * Use for text like "140% of goal".
     */
    val fraction: Double get() = averageStepsPerDay.toDouble() / goal.stepsPerDay

    /**
     * Progress clamped to 0..1, for a bar that cannot overflow its track.
     *
     * Kept separate from [fraction] so exceeding the goal does not draw a bar past
     * its own end, while the text can still say by how much.
     */
    val barFraction: Float get() = fraction.coerceIn(0.0, 1.0).toFloat()

    /**
     * How far past the goal the average goes, as a fraction of a second lap, 0..1.
     *
     * A ring clamped at full carries no information once the goal is beaten — 101%
     * and 300% draw identically. This is what a second lap over the top is drawn
     * from. It saturates at 200% of the goal, past which the distinction stops
     * mattering more than the clutter of showing it.
     */
    val overflowFraction: Float get() = (fraction - 1.0).coerceIn(0.0, 1.0).toFloat()

    /** Whole percent of the goal, uncapped, rounded to nearest. */
    val percentOfGoal: Int get() = Math.round(fraction * 100).toInt()
}
