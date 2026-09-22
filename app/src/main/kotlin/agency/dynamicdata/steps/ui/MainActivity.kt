package agency.dynamicdata.steps.ui

import agency.dynamicdata.steps.core.StepGoal
import agency.dynamicdata.steps.health.HealthConnectAvailability
import agency.dynamicdata.steps.health.HealthConnectStepsRepository
import agency.dynamicdata.steps.settings.StepGoalStore
import agency.dynamicdata.steps.widget.StepsWidget
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.glance.appwidget.updateAll
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * The screen behind the widget: explains what is read, asks for access, sets the
 * goal, and redraws the widget whenever any of that changes.
 *
 * Health Connect permissions are granted through its own system UI, launched via
 * [PermissionController.createRequestPermissionResultContract] — the normal runtime
 * permission dialog does not cover them.
 */
class MainActivity : ComponentActivity() {

    private val repository by lazy { HealthConnectStepsRepository(this) }
    private val goalStore by lazy { StepGoalStore(this) }

    private var permissionDenied by mutableStateOf(false)
    private var goalInput by mutableStateOf("")
    private var savedGoal by mutableStateOf(StepGoal.DEFAULT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            savedGoal = goalStore.current()
            goalInput = savedGoal.stepsPerDay.toString()
        }

        val requestPermissions = registerForActivityResult(
            PermissionController.createRequestPermissionResultContract(),
        ) { granted ->
            // Redraw either way: a denial should flip the widget to its
            // "tap to allow" state rather than leave a stale number on screen.
            refreshWidget()
            permissionDenied =
                !granted.containsAll(HealthConnectStepsRepository.REQUIRED_PERMISSIONS)
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SetupScreen(
                        availability = repository.availability(),
                        denied = permissionDenied,
                        goalInput = goalInput,
                        savedGoal = savedGoal,
                        onGoalInputChange = { typed ->
                            // Digits only: the field feeds a step count, and letting a
                            // stray character in would only fail later at parse time.
                            goalInput = typed.filter(Char::isDigit).take(MAX_GOAL_DIGITS)
                        },
                        onSaveGoal = ::saveGoal,
                        onRequestPermissions = {
                            permissionDenied = false
                            requestPermissions.launch(
                                HealthConnectStepsRepository.REQUIRED_PERMISSIONS,
                            )
                        },
                        onInstallHealthConnect = ::openHealthConnectListing,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have granted access in Health Connect's own settings while the
        // app was backgrounded, so the widget is refreshed on every return.
        refreshWidget()
    }

    private fun saveGoal() {
        val goal = goalInput.toLongOrNull()?.takeIf { it > 0 }?.let(::StepGoal) ?: return
        lifecycleScope.launch {
            goalStore.set(goal)
            savedGoal = goal
            // The widget renders against the stored goal, so it has to be redrawn
            // here — nothing else observes the change.
            StepsWidget().updateAll(this@MainActivity)
        }
    }

    private fun refreshWidget() {
        lifecycleScope.launch { StepsWidget().updateAll(this@MainActivity) }
    }

    private fun openHealthConnectListing() {
        val uri = ("market://details?id=${HealthConnectStepsRepository.HEALTH_CONNECT_PACKAGE}" +
            "&url=healthconnect%3A%2F%2Fonboarding").toUri()
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(PLAY_STORE_PACKAGE))
        }.onFailure {
            // No Play Store (or it is disabled) — fall back to the web listing.
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    ("https://play.google.com/store/apps/details?id=" +
                        HealthConnectStepsRepository.HEALTH_CONNECT_PACKAGE).toUri(),
                ),
            )
        }
    }

    private companion object {
        const val PLAY_STORE_PACKAGE = "com.android.vending"

        /** Six digits tops — 999,999 steps a day is already far past any real target. */
        const val MAX_GOAL_DIGITS = 6
    }
}

@Composable
private fun SetupScreen(
    availability: HealthConnectAvailability,
    denied: Boolean,
    goalInput: String,
    savedGoal: StepGoal,
    onGoalInputChange: (String) -> Unit,
    onSaveGoal: () -> Unit,
    onRequestPermissions: () -> Unit,
    onInstallHealthConnect: () -> Unit,
) {
    val parsedGoal = goalInput.toLongOrNull()
    val goalIsValid = parsedGoal != null && parsedGoal > 0
    val goalIsUnsaved = goalIsValid && parsedGoal != savedGoal.stepsPerDay

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text("7-day step average", style = MaterialTheme.typography.headlineSmall)
        Text(
            "The widget shows your average steps per day over the last seven complete " +
                "days, ending yesterday — today is left out so the number doesn't sag " +
                "every morning. It reads step counts from Health Connect and nothing " +
                "else, and the numbers never leave your phone.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Text("Daily goal", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = goalInput,
            onValueChange = onGoalInputChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Steps per day") },
            singleLine = true,
            isError = goalInput.isNotEmpty() && !goalIsValid,
            supportingText = {
                Text(
                    when {
                        goalInput.isEmpty() || !goalIsValid -> "Enter a number above zero."
                        goalIsUnsaved -> "Not saved yet."
                        else -> "Saved. The widget measures your average against this."
                    },
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
        )
        Button(onClick = onSaveGoal, enabled = goalIsUnsaved) { Text("Save goal") }

        when (availability) {
            HealthConnectAvailability.NOT_SUPPORTED -> Text(
                "Health Connect isn't supported on this device, so the widget has no " +
                    "step data to read.",
                style = MaterialTheme.typography.bodyMedium,
            )

            HealthConnectAvailability.UPDATE_REQUIRED -> {
                Text(
                    "Health Connect needs to be installed or updated first.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onInstallHealthConnect) { Text("Get Health Connect") }
            }

            HealthConnectAvailability.AVAILABLE -> {
                if (denied) {
                    Text(
                        "Without step access the widget can't show an average. " +
                            "You can grant it any time from Health Connect settings.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(onClick = onRequestPermissions) { Text("Allow step access") }
            }
        }
    }
}
