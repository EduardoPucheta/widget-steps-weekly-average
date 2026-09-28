package agency.dynamicdata.steps.widget

import agency.dynamicdata.steps.health.HealthConnectStepsRepository
import agency.dynamicdata.steps.settings.LastReadingStore
import agency.dynamicdata.steps.settings.SettingsStore
import agency.dynamicdata.steps.ui.MainActivity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import kotlinx.coroutines.delay
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
 *
 * It says how it went. A refresh that silently leaves the widget unchanged is
 * indistinguishable from a tap that never landed, so success and failure both get
 * a short toast — and a failure names Health Connect's reason, which is the only
 * way to tell a refused read from a broken one without a debugger.
 */
class RefreshActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            try {
                // Not before: during onCreate the screen is still being brought up,
                // and Health Connect may not yet count the app as in front.
                withResumed {}
                refresh()
            } catch (e: Exception) {
                Log.e(TAG, "refresh failed", e)
                toast("Couldn't refresh: ${e.describe()}")
            } finally {
                finish()
            }
        }
    }

    private suspend fun refresh() {
        val repository = HealthConnectStepsRepository(this)
        val loader = StepsWidgetStateLoader(repository, LastReadingStore(this))
        val goal = SettingsStore(this).currentGoal()

        var state = loader.load(repository.today(), goal)
        // Health Connect can take a moment to see the app as in front after a cold
        // start, so a refused read gets a couple more tries before giving up.
        repeat(RETRIES) {
            if (loader.lastFailure == null) return@repeat
            delay(RETRY_DELAY_MS)
            state = loader.load(repository.today(), goal)
        }

        StepsWidget().updateAll(this)

        val failure = loader.lastFailure
        when {
            // Something only the app can sort out — access really is missing, or
            // Health Connect needs installing. Go where it can be fixed.
            state is StepsWidgetState.PermissionRequired ||
                state is StepsWidgetState.HealthConnectUnavailable -> startActivity(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )

            failure != null -> toast("Couldn't read steps: ${failure.describe()}")

            else -> toast("Steps updated")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }

    private fun Exception.describe(): String =
        (message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName).take(MAX_REASON_CHARS)

    private companion object {
        const val TAG = "StepsRefresh"
        const val RETRIES = 2
        const val RETRY_DELAY_MS = 700L

        /** A toast is two lines at most; the full reason is in the log. */
        const val MAX_REASON_CHARS = 120
    }
}
