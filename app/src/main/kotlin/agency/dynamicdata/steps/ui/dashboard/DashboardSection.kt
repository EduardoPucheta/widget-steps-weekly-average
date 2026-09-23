package agency.dynamicdata.steps.ui.dashboard

import agency.dynamicdata.steps.core.DayEntry
import agency.dynamicdata.steps.core.GoalProgress
import agency.dynamicdata.steps.core.StepsSummary
import agency.dynamicdata.steps.widget.StepsWidgetFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The reading: the headline number, the week as bars, then the exact days. */
@Composable
fun DashboardSection(
    state: DashboardState,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state) {
            is DashboardState.Loading ->
                Message("Reading your steps…")

            is DashboardState.HealthConnectUnavailable -> Message(
                if (state.updatable) {
                    "Health Connect needs installing or updating before there is " +
                        "anything to show."
                } else {
                    "Health Connect isn't supported on this device, so there is no " +
                        "step data to read."
                },
            )

            is DashboardState.PermissionRequired ->
                Message("Allow step access below and your last 7 days will appear here.")

            is DashboardState.Error ->
                Message("Couldn't read your steps just now. Reopening the app will retry.")

            is DashboardState.Ready -> ReadyDashboard(state, locale)
        }
    }
}

@Composable
private fun ReadyDashboard(state: DashboardState.Ready, locale: Locale) {
    Headline(state.summary, state.progress, locale)

    DailyStepsChart(
        days = state.days,
        goal = state.progress.goal,
        locale = locale,
        modifier = Modifier.fillMaxWidth(),
    )

    if (state.trackedDays < state.days.size) {
        Text(
            // Says out loud what the faint slots mean. Without it, a gap looks like
            // a bad day rather than a day nobody measured.
            text = "Faded days had nothing recorded — that is different from a day " +
                "with no steps, and they are not counted as zero above.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    HorizontalDivider()
    DayList(state.days, locale)
}

@Composable
private fun Headline(summary: StepsSummary, progress: GoalProgress, locale: Locale) {
    val range = StepsWidgetFormat.windowRange(summary, locale)

    if (summary.hasNoData) {
        // Nothing recorded means there is no average, not an average of zero. The
        // number would read "0" and "10,000 short", which is the null-as-zero
        // mistake the chart below is careful not to make — and would contradict the
        // widget, which says "No steps in 7 days" for the same week.
        Column {
            Text(
                text = "${summary.dayCount}-day average",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Nothing recorded",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "No steps reported for $range, so there is no average to show.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val average = StepsWidgetFormat.exact(summary.averageStepsPerDay, locale)

    Column(
        modifier = Modifier.semantics {
            contentDescription =
                "$average steps per day on average, $range, " +
                    StepsWidgetFormat.goalSpoken(progress, locale)
        },
    ) {
        Text(
            text = "${summary.dayCount}-day average",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = average,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "${StepsWidgetFormat.goal(progress, locale)} · $range",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The exact numbers.
 *
 * The chart is for the shape of the week; this is the reading. It also means every
 * value has a text form, which is what a screen reader and a colour-blind reader
 * both need.
 */
@Composable
private fun DayList(days: List<DayEntry>, locale: Locale) {
    val format = DateTimeFormatter.ofPattern("EEE d MMM", locale)

    Column(modifier = Modifier.fillMaxWidth()) {
        days.forEach { day ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = day.date.format(format),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = day.steps?.let { StepsWidgetFormat.exact(it, locale) }
                        ?: "no data",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (day.isTracked) FontWeight.Medium else FontWeight.Normal,
                    color = if (day.isTracked) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium)
}
