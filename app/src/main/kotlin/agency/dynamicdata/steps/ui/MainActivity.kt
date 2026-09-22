package agency.dynamicdata.steps.ui

import agency.dynamicdata.steps.health.HealthConnectAvailability
import agency.dynamicdata.steps.health.HealthConnectStepsRepository
import agency.dynamicdata.steps.widget.StepsWidget
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.glance.appwidget.updateAll
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * The screen behind the widget: explains what is read, asks for access, and redraws
 * the widget once the answer is in.
 *
 * Health Connect permissions are granted through its own system UI, launched via
 * [PermissionController.createRequestPermissionResultContract] — the normal runtime
 * permission dialog does not cover them.
 */
class MainActivity : ComponentActivity() {

    private val repository by lazy { HealthConnectStepsRepository(this) }

    private var permissionDenied by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requestPermissions = registerForActivityResult(
            PermissionController.createRequestPermissionResultContract(),
        ) { granted ->
            // Redraw either way: a denial should flip the widget to its
            // "tap to allow" state rather than leave a stale number on screen.
            lifecycleScope.launch {
                StepsWidget().updateAll(this@MainActivity)
            }
            if (!granted.containsAll(HealthConnectStepsRepository.REQUIRED_PERMISSIONS)) {
                permissionDenied = true
            }
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SetupScreen(
                        availability = repository.availability(),
                        denied = permissionDenied,
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
    }
}

@Composable
private fun SetupScreen(
    availability: HealthConnectAvailability,
    denied: Boolean,
    onRequestPermissions: () -> Unit,
    onInstallHealthConnect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Weekly step average", style = MaterialTheme.typography.headlineSmall)
        Text(
            "The widget shows your average steps per day for the current week. " +
                "It reads step counts from Health Connect and nothing else, " +
                "and the numbers never leave your phone.",
            style = MaterialTheme.typography.bodyMedium,
        )

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
