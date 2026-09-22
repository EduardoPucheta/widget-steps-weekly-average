package agency.dynamicdata.steps.settings

import agency.dynamicdata.steps.core.StepGoal
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

// The file is still called "step_goal" although the store now holds more than the
// goal. Renaming it would start a fresh, empty store and silently reset everyone's
// settings; the name is data, not documentation.
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "step_goal")

/**
 * Where the app's settings live: the daily step goal, and whether the morning
 * reminder is on.
 *
 * DataStore rather than Glance widget state, because these are written by the app
 * and read by the widget and the reminder alike.
 */
class SettingsStore(private val context: Context) {

    /** The current goal, re-emitting whenever it is changed. */
    val goal: Flow<StepGoal> = context.settingsDataStore.data
        // A corrupt or unreadable file should leave the widget on the default goal
        // rather than take it down; nothing here is worth crashing over.
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { preferences -> StepGoal.fromStoredValue(preferences[GOAL_KEY]) }

    /** A single read, for the widget's one-shot render. */
    suspend fun currentGoal(): StepGoal = goal.first()

    suspend fun setGoal(goal: StepGoal) {
        context.settingsDataStore.edit { it[GOAL_KEY] = goal.stepsPerDay }
    }

    /**
     * Whether the morning reminder is on. Off until asked for: an app that starts
     * sending notifications on its own gets its notifications turned off.
     */
    val reminderEnabled: Flow<Boolean> = context.settingsDataStore.data
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { preferences -> preferences[REMINDER_KEY] ?: false }

    suspend fun currentReminderEnabled(): Boolean = reminderEnabled.first()

    suspend fun setReminderEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[REMINDER_KEY] = enabled }
    }

    private companion object {
        val GOAL_KEY = longPreferencesKey("steps_per_day")
        val REMINDER_KEY = booleanPreferencesKey("reminder_enabled")
    }
}
