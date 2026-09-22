package agency.dynamicdata.steps.health

import agency.dynamicdata.steps.core.DailySteps
import agency.dynamicdata.steps.core.WeekWindow

/**
 * Source of daily step totals.
 *
 * An interface so the widget and the Compose screen can be exercised against a fake
 * without a device that has Health Connect installed.
 */
interface StepsRepository {

    /** Whether Health Connect is installed and usable on this device. */
    fun availability(): HealthConnectAvailability

    /** Whether the user has already granted read access to step records. */
    suspend fun hasReadPermission(): Boolean

    /**
     * Daily step totals covering [window], one entry per day the provider reported.
     *
     * @throws SecurityException if read permission was revoked since it was checked.
     */
    suspend fun dailySteps(window: WeekWindow): List<DailySteps>
}

enum class HealthConnectAvailability {
    /** Installed and ready to query. */
    AVAILABLE,

    /** Supported on this device, but the user has to install or update the app. */
    UPDATE_REQUIRED,

    /** Not supported at all — below API 26, or a device without the platform APK. */
    NOT_SUPPORTED,
}
