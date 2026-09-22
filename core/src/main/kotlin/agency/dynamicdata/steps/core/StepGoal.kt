package agency.dynamicdata.steps.core

/**
 * A target number of steps per day.
 *
 * A type rather than a bare `Long` so a goal cannot be silently swapped with a step
 * count at a call site — the two are both large positive numbers and would compile
 * fine the wrong way round.
 */
@JvmInline
value class StepGoal(val stepsPerDay: Long) {
    init {
        require(stepsPerDay > 0) { "a goal must be a positive number of steps, was $stepsPerDay" }
    }

    companion object {
        /** The conventional daily target, and what a fresh install starts on. */
        val DEFAULT: StepGoal = StepGoal(10_000)

        /** Guards against a stored or typed value that is not a usable goal. */
        fun fromStoredValue(value: Long?): StepGoal =
            if (value == null || value <= 0) DEFAULT else StepGoal(value)
    }
}
