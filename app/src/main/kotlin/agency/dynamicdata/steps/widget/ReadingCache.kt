package agency.dynamicdata.steps.widget

import agency.dynamicdata.steps.core.StepsSummary

/**
 * The last reading that actually succeeded.
 *
 * Health Connect refuses reads from an app that is not on screen unless it holds
 * the background-read permission, and the widget is almost always redrawn from the
 * background. Without somewhere to fall back to, every refused read turned into an
 * error on the home screen even though nothing was wrong with the data or the
 * access. With it, the widget keeps showing the last good number.
 */
interface ReadingCache {

    suspend fun save(summary: StepsSummary)

    /** The most recent saved reading, or null if there has never been one. */
    suspend fun last(): StepsSummary?

    /** For callers with nowhere to keep a reading, and for tests that do not care. */
    object None : ReadingCache {
        override suspend fun save(summary: StepsSummary) = Unit
        override suspend fun last(): StepsSummary? = null
    }
}
