package agency.dynamicdata.steps.ui.body

import agency.dynamicdata.steps.core.Measurement
import agency.dynamicdata.steps.core.MeasurementSummary
import agency.dynamicdata.steps.core.YearPeriod
import agency.dynamicdata.steps.ui.dashboard.SectionCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Weight and body fat, a year at a time.
 *
 * One period control above both charts rather than one per chart, so the two always
 * cover the same months and can be read against each other: a drop in weight with
 * body fat flat reads differently from both falling together.
 */
@Composable
fun BodySection(
    state: BodyState,
    locale: Locale,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onRequestAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        BodyState.Unavailable -> Unit
        BodyState.Loading -> SectionCard(title = "Body", modifier = modifier) {
            Text("Reading your measurements…", style = MaterialTheme.typography.bodyMedium)
        }
        is BodyState.Loaded -> Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.weight is MetricState.PermissionRequired && state.bodyFat is MetricState.PermissionRequired) {
                // Neither is shared: one invitation, not two empty charts and a
                // period control with nothing under it.
                SectionCard(title = "Body") {
                    Text(
                        "See every weigh-in and body-fat reading from Health Connect on a " +
                            "yearly chart, with the trend through them.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FilledTonalButton(onClick = onRequestAccess) { Text("Allow weight & body fat") }
                }
                return@Column
            }

            PeriodControl(state.period, state.yearsBack, locale, onPreviousYear, onNextYear)

            if (state.historyBlocked) {
                SectionCard(title = "Only the last 30 days so far") {
                    Text(
                        "Health Connect shares older readings only once you allow past " +
                            "data. Until then the charts start a month before access was " +
                            "first given.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FilledTonalButton(onClick = onRequestAccess) { Text("Allow past data") }
                }
            }

            MetricCard("Weight", "weigh-ins", state.weight, state.period, MetricFormat.weight(locale), locale, onRequestAccess)
            MetricCard("Body fat", "readings", state.bodyFat, state.period, MetricFormat.BODY_FAT, locale, onRequestAccess)
        }
    }
}

/** ‹ the year shown ›, the one filter, sitting above everything it scopes. */
@Composable
private fun PeriodControl(
    period: YearPeriod,
    yearsBack: Int,
    locale: Locale,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                "Body",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "${period.start.format(dates)} – ${period.end.format(dates)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onPrevious, modifier = Modifier.semantics { contentDescription = "Previous year" }) {
            Text("‹", style = MaterialTheme.typography.headlineMedium)
        }
        IconButton(
            onClick = onNext,
            // The current year ends today; there is nothing after it to show.
            enabled = yearsBack > 0,
            modifier = Modifier.semantics { contentDescription = "Next year" },
        ) {
            Text("›", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    noun: String,
    state: MetricState,
    period: YearPeriod,
    format: MetricFormat,
    locale: Locale,
    onRequestAccess: () -> Unit,
) {
    SectionCard(title = title) {
        when (state) {
            MetricState.PermissionRequired -> {
                Text(
                    "${title.replaceFirstChar { it.uppercase() }} isn't shared with Stride yet.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FilledTonalButton(onClick = onRequestAccess) { Text("Allow") }
            }

            is MetricState.Error -> Text(
                "Couldn't read your ${title.lowercase()} just now. Reopening the app will retry.\n\n" +
                    "Details: ${state.reason}",
                style = MaterialTheme.typography.bodyMedium,
            )

            is MetricState.Ready -> {
                val summary = state.summary
                if (summary == null) {
                    val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
                    Text(
                        "No $noun recorded between ${period.start.format(dates)} and ${period.end.format(dates)}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    return@SectionCard
                }

                Summary(summary, format, locale)
                MeasurementChart(
                    readings = state.readings,
                    trend = state.trend,
                    period = period,
                    format = format,
                    description = describe(title, noun, summary, period, format, locale),
                    locale = locale,
                    modifier = Modifier.fillMaxWidth(),
                )
                ReadingList(state.readings, noun, format, locale)
            }
        }
    }
}

/** Latest, change over the year, and range: the numbers a chart makes you hunt for. */
@Composable
private fun Summary(summary: MeasurementSummary, format: MetricFormat, locale: Locale) {
    val day = DateTimeFormatter.ofPattern("d MMM", locale)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat("Latest", format.value(summary.latest.value, locale), summary.latest.at.format(day))
        Stat(
            "Change",
            summary.change?.let { format.change(it, locale) } ?: "—",
            if (summary.change == null) "needs 2 readings" else "trend, start to end",
        )
        Stat(
            "Range",
            "${format.value(summary.lowest.value, locale).substringBefore(' ')}–${format.value(summary.highest.value, locale)}",
            "lowest – highest",
        )
    }
}

@Composable
private fun RowScope.Stat(label: String, value: String, detail: String) {
    Column(Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Every reading as text, newest first, behind a toggle: the chart's table view, so
 * no value is only reachable by aiming at a dot.
 */
@Composable
private fun ReadingList(readings: List<Measurement>, noun: String, format: MetricFormat, locale: Locale) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) {
        Text(if (open) "Hide $noun" else "Show all ${readings.size} $noun")
    }
    if (!open) return

    val stamp = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
    Column(Modifier.fillMaxWidth()) {
        readings.sortedByDescending { it.at }.forEachIndexed { index, reading ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(reading.at.format(stamp), style = MaterialTheme.typography.bodyMedium)
                Text(
                    format.value(reading.value, locale),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** What a screen reader says for the chart: the summary a sighted reader gets at a glance. */
private fun describe(
    title: String,
    noun: String,
    summary: MeasurementSummary,
    period: YearPeriod,
    format: MetricFormat,
    locale: Locale,
): String {
    val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
    val change = summary.change?.let { ", trend change ${format.change(it, locale)}" } ?: ""
    return "$title chart, ${summary.count} $noun from ${period.start.format(dates)} to ${period.end.format(dates)}. " +
        "Latest ${format.value(summary.latest.value, locale)}, lowest ${format.value(summary.lowest.value, locale)}, " +
        "highest ${format.value(summary.highest.value, locale)}$change."
}
