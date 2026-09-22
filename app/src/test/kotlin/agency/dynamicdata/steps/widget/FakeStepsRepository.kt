package agency.dynamicdata.steps.widget

import agency.dynamicdata.steps.core.DailySteps
import agency.dynamicdata.steps.core.StepsWindow
import agency.dynamicdata.steps.health.HealthConnectAvailability
import agency.dynamicdata.steps.health.StepsRepository

/** A [StepsRepository] whose answers — including its failures — are set by the test. */
class FakeStepsRepository(
    private val availability: HealthConnectAvailability = HealthConnectAvailability.AVAILABLE,
    private val permitted: Boolean = true,
    private val steps: List<DailySteps> = emptyList(),
    private val failWith: Exception? = null,
) : StepsRepository {

    /** Every window the loader asked for, in order. */
    val requestedWindows: MutableList<StepsWindow> = mutableListOf()

    override fun availability(): HealthConnectAvailability = availability

    override suspend fun hasReadPermission(): Boolean = permitted

    override suspend fun dailySteps(window: StepsWindow): List<DailySteps> {
        requestedWindows += window
        failWith?.let { throw it }
        return steps.filter { it.date in window }
    }
}
