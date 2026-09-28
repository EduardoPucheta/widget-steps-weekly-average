package agency.dynamicdata.steps.widget

import agency.dynamicdata.steps.health.HealthConnectStepsRepository
import agency.dynamicdata.steps.settings.LastReadingStore
import agency.dynamicdata.steps.settings.SettingsStore
import agency.dynamicdata.steps.ui.MainActivity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * The widget's refresh button: an invisible screen that reads, redraws, and closes.
 *
 * It has to be an activity. Health Connect only answers an app that is on screen
 * (unless the background-read permission is granted), and a tap handled by a
 * broadcast or a worker runs in the background — it would be refused exactly like
 * the scheduled refresh that failed in the first place. A transparent activity is
 * on screen for the moment the read takes, without the full app opening.
 *
 * The read happens here rather than being left to the widget update, because that
 * update runs asynchronously and could land after this activity has closed. The
 * loader saves what it reads, so the update that follows finds it either way.
 */
class RefreshActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            try {
                val repository = HealthConnectStepsRepository(this@RefreshActivity)
                val state = StepsWidgetStateLoader(
                    repository = repository,
                    cache = LastReadingStore(this@RefreshActivity),
                ).load(
                    today = repository.today(),
                    goal = SettingsStore(this@RefreshActivity).currentGoal(),
                )
                StepsWidget().updateAll(this@RefreshActivity)

                // Something only the app can sort out — access really is missing, or
                // Health Connect needs installing. Refreshing again would not help, so
                // go where it can be fixed.
                if (state is StepsWidgetState.PermissionRequired ||
                    state is StepsWidgetState.HealthConnectUnavailable
                ) {
                    startActivity(
                        Intent(this@RefreshActivity, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "refresh failed", e)
            } finally {
                finish()
            }
        }
    }

    private companion object {
        const val TAG = "StepsRefresh"
    }
}
