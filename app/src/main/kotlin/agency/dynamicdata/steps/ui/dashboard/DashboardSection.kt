package agency.dynamicdata.steps.ui.dashboard

import agency.dynamicdata.steps.core.DayEntry
import agency.dynamicdata.steps.core.GoalProgress
import agency.dynamicdata.steps.core.MovingAveragePoint
import agency.dynamicdata.steps.core.StepsSummary
import agency.dynamicdata.steps.widget.StepsWidgetFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The reading: the ring and the number, the week as bars, then the exact days. */
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
            is DashboardState.Loading -> MessageCard("Reading your steps…")

            is DashboardState.HealthConnectUnavailable -> MessageCard(
                if (state.updatable) {
                    "Health Connect needs installing or updating before there is " +
                        "anything to show."
                } else {
                    "Health Connect isn't supported on this device, so there is no " +
                        "step data to read."
                },
            )

            // The request itself is the card above; saying it twice would only push
            // the button further down.
            is DashboardState.PermissionRequired -> Unit

            is DashboardState.Error -> MessageCard(
                "Couldn't read your steps just now. Reopening the app will retry.\n\n" +
                    "Details: ${state.reason}",
            )

            is DashboardState.Ready -> ReadyDashboard(state, locale)
        }
    }
}

@Composable
private fun ReadyDashboard(state: DashboardState.Ready, locale: Locale) {
    HeroCard(state.summary, state.progress, locale)

    SectionCard(title = "Last 7 days") {
        DailyStepsChart(
            days = state.days,
            movingAverage = state.movingAverage,
            goal = state.progress.goal,
            locale = locale,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.trackedDays < state.days.size) {
            Text(
                // Says out loud what the faint slots mean. Without it, a gap looks
                // like a bad day rather than a day nobody measured.
                text = "Faded days had nothing recorded — that is different from a day " +
                    "with no steps, and they are not counted as zero above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    SectionCard(title = "Day by day") {
        DayList(state.days, state.movingAverage, locale)
    }
}

/**
 * The widget's reading, larger: the ring, the average inside it, and the gap to the
 * goal in a pill underneath — the one thing to act on.
 */
@Composable
private fun HeroCard(summary: StepsSummary, progress: GoalProgress, locale: Locale) {
    val range = StepsWidgetFormat.windowRange(summary, locale)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (summary.hasNoData) {
                // Nothing recorded means there is no average, not an average of
                // zero. A ring would read "0" and "10,000 short", which is the
                // null-as-zero mistake the chart is careful not to make — and would
                // contradict the widget, which says "No steps in 7 days".
                Text(
                    text = "${summary.dayCount}-day average",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Nothing recorded",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "No steps reported for $range, so there is no average to show.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                return@Column
            }

            val average = StepsWidgetFormat.exact(summary.averageStepsPerDay, locale)
            val goal = StepsWidgetFormat.compact(progress.goal.stepsPerDay, locale)

            GoalRing(
                progress = progress,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription =
                        "$average steps per day on average, $range, " +
                            StepsWidgetFormat.goalSpoken(progress, locale)
                },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${summary.dayCount}-day average",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = average,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${progress.percentOfGoal}% of $goal",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            GoalPill(progress, locale)

            Text(
                text = range,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The gap in steps, tinted by whether the goal is met. */
@Composable
private fun GoalPill(progress: GoalProgress, locale: Locale) {
    val colours = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (progress.isMet) colours.tertiaryContainer else colours.primaryContainer,
        contentColor = if (progress.isMet) colours.onTertiaryContainer else colours.onPrimaryContainer,
    ) {
        Text(
            // Sentence case: the widget's line starts lower-case because it follows
            // other text, but here it stands alone.
            text = StepsWidgetFormat.goal(progress, locale).replaceFirstChar { it.titlecase(locale) },
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** A titled card; every section of the screen uses it so they line up. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
    }
}

/**
 * The exact numbers, for both series.
 *
 * The chart is for the shape of the week; this is the reading. Each row carries the
 * day's steps and the 7-day average as it stood that day, so every mark on the chart
 * — bar and line alike — has a text form, which is what a screen reader and a
 * colour-blind reader both need.
 */
@Composable
private fun DayList(days: List<DayEntry>, movingAverage: List<MovingAveragePoint>, locale: Locale) {
    val format = DateTimeFormatter.ofPattern("EEE d MMM", locale)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Steps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "7-day avg",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(min = AVERAGE_COLUMN),
                    textAlign = TextAlign.End,
                )
            }
        }
        days.zip(movingAverage).forEachIndexed { index, (day, average) ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = day.date.format(format),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = day.steps?.let { StepsWidgetFormat.exact(it, locale) } ?: "no data",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (day.isTracked) FontWeight.Medium else FontWeight.Normal,
                        color = if (day.isTracked) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        // A dash, not a zero, when the span behind this day had no
                        // reading — the same gap the line leaves on the chart.
                        text = average.averageStepsPerDay?.let { StepsWidgetFormat.exact(it, locale) } ?: "—",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(min = AVERAGE_COLUMN),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

/** Keeps the average column aligned whatever the width of each figure. */
private val AVERAGE_COLUMN = 64.dp

@Composable
private fun MessageCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}
