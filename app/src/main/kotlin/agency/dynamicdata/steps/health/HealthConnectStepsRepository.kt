package agency.dynamicdata.steps.health

import agency.dynamicdata.steps.core.DailySteps
import agency.dynamicdata.steps.core.StepsWindow
import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Period

/**
 * Reads step totals out of Health Connect.
 *
 * Aggregation is left to Health Connect rather than done here: it owns the
 * de-duplication rules across providers, so summing raw records from a phone and a
 * watch that both counted the same walk would double-count it.
 */
class HealthConnectStepsRepository(
    private val context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : StepsRepository {

    private val client: HealthConnectClient? by lazy {
        if (availability() == HealthConnectAvailability.AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    override fun availability(): HealthConnectAvailability =
        when (HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PACKAGE)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectAvailability.UPDATE_REQUIRED
            else -> HealthConnectAvailability.NOT_SUPPORTED
        }

    override suspend fun hasReadPermission(): Boolean = withContext(io) {
        val client = client ?: return@withContext false
        runCatching { client.permissionController.getGrantedPermissions() }
            .onFailure { Log.w(TAG, "could not read granted permissions", it) }
            .getOrDefault(emptySet())
            .contains(READ_STEPS)
    }

    /**
     * Whether this Health Connect offers reading while the app is not on screen.
     *
     * Older versions do not; there the widget can only refresh while the app is open,
     * and falls back to its last reading the rest of the time.
     */
    fun backgroundReadSupported(): Boolean {
        val client = client ?: return false
        return runCatching {
            client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        }.getOrDefault(false)
    }

    /** Whether the widget and the reminder may read while the app is closed. */
    suspend fun hasBackgroundReadPermission(): Boolean = withContext(io) {
        val client = client ?: return@withContext false
        runCatching { client.permissionController.getGrantedPermissions() }
            .getOrDefault(emptySet())
            .contains(READ_IN_BACKGROUND)
    }

    /**
     * What to ask for: steps, plus background reads where Health Connect has them.
     *
     * Background access is asked for in the same sheet, but it is not required —
     * [REQUIRED_PERMISSIONS] stays steps-only, so declining it never counts as
     * declining access.
     */
    fun permissionsToRequest(): Set<String> =
        if (backgroundReadSupported()) REQUIRED_PERMISSIONS + READ_IN_BACKGROUND else REQUIRED_PERMISSIONS

    override suspend fun dailySteps(window: StepsWindow): List<DailySteps> = withContext(io) {
        val client = client ?: return@withContext emptyList()

        // Local date-times, not instants: a "day" of steps is the user's calendar day.
        // Health Connect resolves these against the device zone, so a week containing a
        // daylight-saving change still has seven days, one of which is 23 or 25 hours.
        val request = AggregateGroupByPeriodRequest(
            metrics = setOf(StepsRecord.COUNT_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(
                startTime = window.start.atStartOfDay(),
                endTime = window.end.plusDays(1).atStartOfDay(),
            ),
            timeRangeSlicer = Period.ofDays(1),
        )

        client.aggregateGroupByPeriod(request).mapNotNull(::toDailySteps)
    }

    /**
     * A bucket with a null total is a day Health Connect has no record for. It is
     * dropped rather than turned into a zero so the calculator can tell "untracked"
     * apart from "did not move" — the two mean different things to the average.
     */
    private fun toDailySteps(bucket: AggregationResultGroupedByPeriod): DailySteps? {
        val steps = bucket.result[StepsRecord.COUNT_TOTAL] ?: return null
        return DailySteps(date = bucket.startTime.toLocalDate(), steps = steps)
    }

    /** Today, in the device's zone — resolved once here so the core stays clock-free. */
    fun today() = java.time.LocalDate.now(clock)

    companion object {
        private const val TAG = "StepsRepository"

        /** The Health Connect provider package the SDK status check targets. */
        const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

        val READ_STEPS: String = HealthPermission.getReadPermission(StepsRecord::class)

        /**
         * Lets the widget refresh itself. Without it Health Connect only answers while
         * the app is on screen, and every background refresh is refused.
         */
        const val READ_IN_BACKGROUND: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

        /** The full permission set the app asks for. */
        val REQUIRED_PERMISSIONS: Set<String> = setOf(READ_STEPS)
    }
}
