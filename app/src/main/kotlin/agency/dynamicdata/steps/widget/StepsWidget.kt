package agency.dynamicdata.steps.widget

import agency.dynamicdata.steps.health.HealthConnectStepsRepository
import agency.dynamicdata.steps.settings.StepGoalStore
import agency.dynamicdata.steps.ui.MainActivity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import java.util.Locale
import kotlin.math.min

/**
 * The home-screen widget: a ring showing the average steps per day over the last
 * seven complete days, filled toward the user's daily goal.
 *
 * The state is computed before `provideContent` rather than inside a composable,
 * because a widget is rendered once per update rather than continuously recomposed —
 * there is nothing to observe here, only a snapshot to draw.
 */
class StepsWidget : GlanceAppWidget() {

    /** Exact, so [LocalSize] reports the real size the ring has to be drawn at. */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = HealthConnectStepsRepository(context)
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val goal = StepGoalStore(context).current()

        val state = StepsWidgetStateLoader(repository).load(
            today = repository.today(),
            goal = goal,
        )

        // Density, locale and theme are read here, outside the composition. Glance
        // renders once per update, so reading resources inside a composable buys
        // nothing and trips lint for good reason.
        val density = context.resources.displayMetrics.density
        val night = context.isNightMode()

        provideContent { StepsRing(state, locale, density, night) }
    }
}

@Composable
private fun StepsRing(
    state: StepsWidgetState,
    locale: Locale,
    density: Float,
    night: Boolean,
) {
    val size = LocalSize.current
    // The ring is a circle, so it is sized by the shorter side: on a widget the user
    // has stretched, the disc stays round instead of becoming an ellipse.
    val sidePx = (min(size.width.value, size.height.value) * density).toInt()

    val ready = state as? StepsWidgetState.Ready

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(
                StepsRingRenderer.render(
                    sidePx = sidePx,
                    fraction = ready?.progress?.barFraction ?: 0f,
                    overflowFraction = ready?.progress?.overflowFraction ?: 0f,
                    goalMet = ready?.progress?.isMet == true,
                    night = night,
                    // Without a reading there is nothing to be part-way through, so
                    // the empty track is left off rather than implying a real zero.
                    showTrack = ready != null,
                ),
            ),
            contentDescription = null,
            modifier = GlanceModifier.size((sidePx / density).dp),
        )

        // The ring's own padding keeps the text clear of the stroke.
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (ready != null) ReadyFace(ready, locale, night) else StatusFace(state, night)
        }
    }
}

@Composable
private fun ReadyFace(state: StepsWidgetState.Ready, locale: Locale, night: Boolean) {
    val summary = state.summary
    val progress = state.progress

    Column(
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        modifier = GlanceModifier.semantics {
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
            text = "${summary.dayCount}-day avg",
            style = TextStyle(
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                color = StepsRingPalette.onSurfaceVariant.toColorProvider(night),
            ),
        )
        Text(
            text = StepsWidgetFormat.compact(summary.averageStepsPerDay, locale),
            style = TextStyle(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = StepsRingPalette.onSurface.toColorProvider(night),
            ),
        )
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(
                text = "of ${StepsWidgetFormat.compact(progress.goal.stepsPerDay, locale)}",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (progress.isMet) {
                        StepsRingPalette.accentMet.toColorProvider(night)
                    } else {
                        StepsRingPalette.accent.toColorProvider(night)
                    },
                ),
            )
            state.trend?.let { trend ->
                Spacer(GlanceModifier.width(5.dp))
                Text(
                    text = StepsWidgetFormat.trend(trend, locale),
                    // Deliberately muted rather than red or green. The ring already
                    // carries how you are doing; a small week-on-week wobble should
                    // not be the loudest thing on a widget that is past its goal.
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = StepsRingPalette.onSurfaceVariant.toColorProvider(night),
                    ),
                )
            }
        }
    }
}

/**
 * Anything that is not a reading.
 *
 * The messages are terse because they have to fit inside a circle; tapping opens the
 * app, which has room to explain properly.
 */
@Composable
private fun StatusFace(state: StepsWidgetState, night: Boolean) {
    val message = when (state) {
        is StepsWidgetState.Loading -> "Reading…"
        is StepsWidgetState.HealthConnectUnavailable ->
            if (state.updatable) "Update\nHealth Connect" else "Not\nsupported"
        is StepsWidgetState.PermissionRequired -> "Tap to allow\nstep access"
        is StepsWidgetState.NoData -> "No steps\nin 7 days"
        is StepsWidgetState.Error -> "Tap to\nretry"
        is StepsWidgetState.Ready -> return
    }

    Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
        Text(
            text = message,
            style = TextStyle(
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = StepsRingPalette.onSurfaceVariant.toColorProvider(night),
            ),
        )
        Spacer(GlanceModifier.height(0.dp))
    }
}
