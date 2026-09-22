package agency.dynamicdata.steps.widget

import agency.dynamicdata.steps.core.WeekOverWeekTrend
import agency.dynamicdata.steps.health.HealthConnectStepsRepository
import agency.dynamicdata.steps.ui.MainActivity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The home-screen widget: average steps per day for the current week.
 *
 * The state is computed before `provideContent` rather than inside a composable,
 * because a widget is rendered once per update rather than continuously recomposed —
 * there is nothing to observe here, only a snapshot to draw.
 */
class StepsWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = HealthConnectStepsRepository(context)
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val loader = StepsWidgetStateLoader(
            repository = repository,
            firstDayOfWeek = firstDayOfWeek(locale),
        )

        val state = loader.load(today = repository.today())

        provideContent {
            GlanceTheme {
                StepsWidgetContent(state, locale)
            }
        }
    }

    /**
     * The locale's own first day of week, so the widget agrees with the user's
     * calendar rather than imposing Monday on a Sunday-start locale.
     */
    private fun firstDayOfWeek(locale: Locale): DayOfWeek =
        WeekFields.of(locale).firstDayOfWeek
}

@Composable
private fun StepsWidgetContent(state: StepsWidgetState, locale: Locale) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.Start,
    ) {
        when (state) {
            is StepsWidgetState.Loading -> Message("Reading steps…")

            is StepsWidgetState.HealthConnectUnavailable -> Message(
                if (state.updatable) {
                    "Update Health Connect to see your steps"
                } else {
                    "Health Connect isn't available on this device"
                },
            )

            is StepsWidgetState.PermissionRequired -> Message("Tap to allow step access")

            is StepsWidgetState.NoData -> Message("No steps recorded this week")

            is StepsWidgetState.Error -> Message("Couldn't read your steps. Tap to retry.")

            is StepsWidgetState.Ready -> ReadyContent(state, locale)
        }
    }
}

@Composable
private fun ReadyContent(state: StepsWidgetState.Ready, locale: Locale) {
    val summary = state.summary
    val exact = StepsWidgetFormat.exact(summary.averageStepsPerDay, locale)
    val weekRange = StepsWidgetFormat.weekRange(summary, locale)

    Column(
        // One description for the whole tile: a screen reader should read a sentence,
        // not four disconnected fragments.
        modifier = GlanceModifier.semantics {
            contentDescription = "$exact steps per day on average, week of $weekRange"
        },
    ) {
        Text(
            text = "Weekly average",
            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
        )
        Spacer(GlanceModifier.height(2.dp))
        Row(verticalAlignment = Alignment.Vertical.Bottom) {
            Text(
                text = StepsWidgetFormat.compact(summary.averageStepsPerDay, locale),
                style = TextStyle(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface,
                ),
            )
            state.trend?.let { trend ->
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = StepsWidgetFormat.trend(trend, locale),
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = trendColor(trend),
                    ),
                )
            }
        }
        Text(
            text = StepsWidgetFormat.basisLabel(summary),
            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
        )
        Text(
            text = weekRange,
            style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant),
        )
    }
}

/**
 * Colour reinforces the trend, it does not carry it — the sign is already in the
 * text, so the widget still reads correctly without colour vision.
 */
@Composable
private fun trendColor(trend: WeekOverWeekTrend): ColorProvider = when (trend.direction) {
    WeekOverWeekTrend.Direction.UP -> GlanceTheme.colors.primary
    WeekOverWeekTrend.Direction.DOWN -> GlanceTheme.colors.error
    WeekOverWeekTrend.Direction.FLAT -> GlanceTheme.colors.onSurfaceVariant
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurface),
    )
}
