package agency.dynamicdata.steps.settings

import agency.dynamicdata.steps.core.StepGoal
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.goalDataStore: DataStore<Preferences> by preferencesDataStore(name = "step_goal")

/**
 * Where the daily step goal lives.
 *
 * DataStore rather than a Glance widget state, because the value is written by the
 * app and read by the widget — two processes that need to see the same number.
 */
class StepGoalStore(private val context: Context) {

    /** The current goal, re-emitting whenever it is changed. */
    val goal: Flow<StepGoal> = context.goalDataStore.data
        // A corrupt or unreadable file should leave the widget on the default goal
        // rather than take it down; nothing here is worth crashing over.
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { preferences -> StepGoal.fromStoredValue(preferences[GOAL_KEY]) }

    /** A single read, for the widget's one-shot render. */
    suspend fun current(): StepGoal = goal.first()

    suspend fun set(goal: StepGoal) {
        context.goalDataStore.edit { preferences -> preferences[GOAL_KEY] = goal.stepsPerDay }
    }

    private companion object {
        val GOAL_KEY = longPreferencesKey("steps_per_day")
    }
}
