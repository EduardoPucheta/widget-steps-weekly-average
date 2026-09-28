package agency.dynamicdata.steps.settings

import agency.dynamicdata.steps.core.AverageBasis
import agency.dynamicdata.steps.core.StepsSummary
import agency.dynamicdata.steps.core.StepsWindow
import agency.dynamicdata.steps.widget.ReadingCache
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

// Its own file, not the settings one: this is a cache, and clearing it must never be
// able to take the user's goal with it.
private val Context.lastReadingStore: DataStore<Preferences> by preferencesDataStore(name = "last_reading")

/** [ReadingCache] kept in DataStore, so it survives the process being killed. */
class LastReadingStore(private val context: Context) : ReadingCache {

    override suspend fun save(summary: StepsSummary) {
        context.lastReadingStore.edit {
            it[WINDOW_START] = summary.window.start.toEpochDay()
            it[WINDOW_END] = summary.window.end.toEpochDay()
            it[TOTAL] = summary.totalSteps
            it[DAYS_WITH_DATA] = summary.daysWithData
            it[BASIS] = summary.basis.name
            it[AVERAGE] = summary.averageStepsPerDay
        }
    }

    override suspend fun last(): StepsSummary? {
        val stored = context.lastReadingStore.data
            // An unreadable cache is the same as an empty one: there is no last
            // reading to show, which the widget already knows how to say.
            .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
            .first()

        val start = stored[WINDOW_START] ?: return null
        val end = stored[WINDOW_END] ?: return null
        val basis = stored[BASIS]?.let { name -> AverageBasis.entries.find { it.name == name } }
            ?: return null

        return StepsSummary(
            window = StepsWindow(LocalDate.ofEpochDay(start), LocalDate.ofEpochDay(end)),
            totalSteps = stored[TOTAL] ?: return null,
            daysWithData = stored[DAYS_WITH_DATA] ?: return null,
            basis = basis,
            averageStepsPerDay = stored[AVERAGE] ?: return null,
        )
    }

    private companion object {
        val WINDOW_START = longPreferencesKey("window_start_epoch_day")
        val WINDOW_END = longPreferencesKey("window_end_epoch_day")
        val TOTAL = longPreferencesKey("total_steps")
        val DAYS_WITH_DATA = intPreferencesKey("days_with_data")
        val BASIS = stringPreferencesKey("basis")
        val AVERAGE = longPreferencesKey("average_steps_per_day")
    }
}
