package agency.dynamicdata.steps.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The privacy explanation Health Connect links to from its own permission screen.
 *
 * Required: Health Connect refuses to show the permission dialog at all unless the
 * app declares an activity handling `ACTION_SHOW_PERMISSIONS_RATIONALE`.
 */
class PermissionsRationaleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("How your step data is used", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "This app reads your daily step counts so the home-screen " +
                                "widget can show an average for the current week.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "It reads step counts only, for the last two weeks. Nothing " +
                                "is written back to Health Connect, nothing is sent off " +
                                "the device, and there are no accounts or analytics.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "You can withdraw access at any time from Health Connect " +
                                "settings; the widget will then ask for it again instead " +
                                "of showing a stale number.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
