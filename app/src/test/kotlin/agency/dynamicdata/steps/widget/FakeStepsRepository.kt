package agency.dynamicdata.steps.widget

import agency.dynamicdata.steps.core.DailySteps
import agency.dynamicdata.steps.core.WeekWindow
import agency.dynamicdata.steps.health.HealthConnectAvailability
import agency.dynamicdata.steps.health.StepsRepository

/** A [StepsRepository] whose answers — including its failures — are set by the test. */
class FakeStepsRepository(
    private var availability: HealthConnectAvailability = HealthConnectAvailability.AVAILABLE,
    private var permitted: Boolean = true,
    private var steps: List<DailySteps> = emptyList(),
    private var failWith: Exception? = null,
) : StepsRepository {

    var readCount: Int = 0
        private set

    override fun availability(): HealthConnectAvailability = availability

    override suspend fun hasReadPermission(): Boolean = permitted

    override suspend fun dailySteps(window: WeekWindow): List<DailySteps> {
        readCount++
        failWith?.let { throw it }
        return steps.filter { it.date in window }
    }
}
