package agency.dynamicdata.steps.widget

import agency.dynamicdata.steps.core.StepsTrend
import agency.dynamicdata.steps.health.HealthConnectStepsRepository
import agency.dynamicdata.steps.settings.StepGoalStore
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
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.util.Locale

/**
 * The home-screen widget: average steps per day over the last seven complete days,
 * measured against the user's daily goal.
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
        val goal = StepGoalStore(context).current()

        val state = StepsWidgetStateLoader(repository).load(
            today = repository.today(),
            goal = goal,
        )

        provideContent {
            GlanceTheme {
                StepsWidgetContent(state, locale)
            }
        }
    }
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

            is StepsWidgetState.NoData -> Message("No steps recorded in the last 7 days")

            is StepsWidgetState.Error -> Message("Couldn't read your steps. Tap to retry.")

            is StepsWidgetState.Ready -> ReadyContent(state, locale)
        }
    }
}

@Composable
private fun ReadyContent(state: StepsWidgetState.Ready, locale: Locale) {
    val summary = state.summary
    val progress = state.progress

    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            // One description for the whole tile: a screen reader should read a
            // sentence, not six disconnected fragments.
            .semantics {
                contentDescription = buildString {
                    append(StepsWidgetFormat.exact(summary.averageStepsPerDay, locale))
                    append(" steps per day on average over the last ")
                    append(summary.dayCount)
                    append(" days, ")
                    append(StepsWidgetFormat.goalSpoken(progress, locale))
                }
            },
    ) {
        Text(
            text = StepsWidgetFormat.windowLabel(summary),
            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
        )
        Spacer(GlanceModifier.height(2.dp))

        Row(verticalAlignment = Alignment.Vertical.Bottom) {
            Text(
                text = StepsWidgetFormat.compact(summary.averageStepsPerDay, locale),
                style = TextStyle(
                    fontSize = 30.sp,
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

        Spacer(GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            // Clamped at the source, so passing the goal fills the bar rather than
            // drawing past the end of its own track.
            progress = progress.barFraction,
            modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
            color = if (progress.isMet) GlanceTheme.colors.primary else GlanceTheme.colors.secondary,
            backgroundColor = GlanceTheme.colors.surfaceVariant,
        )
        Spacer(GlanceModifier.height(4.dp))

        Text(
            text = StepsWidgetFormat.goal(progress, locale),
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = if (progress.isMet) FontWeight.Medium else FontWeight.Normal,
                color = if (progress.isMet) {
                    GlanceTheme.colors.primary
                } else {
                    GlanceTheme.colors.onSurfaceVariant
                },
            ),
        )
        Text(
            text = StepsWidgetFormat.windowRange(summary, locale),
            style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant),
        )
    }
}

/**
 * Colour reinforces the trend, it does not carry it — the sign is already in the
 * text, so the widget still reads correctly without colour vision.
 */
@Composable
private fun trendColor(trend: StepsTrend): ColorProvider = when (trend.direction) {
    StepsTrend.Direction.UP -> GlanceTheme.colors.primary
    StepsTrend.Direction.DOWN -> GlanceTheme.colors.error
    StepsTrend.Direction.FLAT -> GlanceTheme.colors.onSurfaceVariant
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurface),
    )
}
