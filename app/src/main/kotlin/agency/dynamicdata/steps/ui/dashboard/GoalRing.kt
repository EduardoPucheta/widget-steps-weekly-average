package agency.dynamicdata.steps.ui.dashboard

import agency.dynamicdata.steps.core.GoalProgress
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The widget's ring, in the app.
 *
 * Same rules as [agency.dynamicdata.steps.widget.StepsRingRenderer] so the two never
 * disagree about a reading: the first lap fills to the goal, a second lap drawn over
 * a faded first one shows how far past it you are, and the colour changes once the
 * goal is met. Drawn with Compose rather than reusing the widget's bitmap, which is
 * capped at 512px for the launcher and would blur at this size on a dense screen.
 *
 * [content] sits in the middle.
 */
@Composable
fun GoalRing(
    progress: GoalProgress,
    modifier: Modifier = Modifier,
    diameter: Dp = 208.dp,
    strokeWidth: Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    val colours = MaterialTheme.colorScheme
    val accent = if (progress.isMet) colours.tertiary else colours.primary
    val track = colours.outlineVariant

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val stroke = strokeWidth.toPx()
            val lap = progress.overflowFraction

            arc(track, 1f, stroke, rounded = false)
            if (progress.barFraction > 0f) {
                arc(
                    // Faded once a second lap sits on top, so the two stay tellable
                    // apart where they overlap.
                    colour = if (lap > 0f) accent.copy(alpha = COMPLETED_LAP_ALPHA) else accent,
                    fraction = progress.barFraction,
                    stroke = stroke,
                    rounded = lap == 0f,
                )
            }
            if (lap > 0f) arc(accent, lap, stroke, rounded = true)
        }
        content()
    }
}

private fun DrawScope.arc(
    colour: Color,
    fraction: Float,
    stroke: Float,
    rounded: Boolean,
) {
    val inset = stroke / 2f
    drawArc(
        color = colour,
        startAngle = START_ANGLE_DEGREES,
        sweepAngle = fraction.coerceIn(0f, 1f) * FULL_CIRCLE_DEGREES,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        style = Stroke(width = stroke, cap = if (rounded) StrokeCap.Round else StrokeCap.Butt),
    )
}

/** Arcs are measured from 3 o'clock; the ring starts at 12, like the widget's. */
private const val START_ANGLE_DEGREES = -90f
private const val FULL_CIRCLE_DEGREES = 360f

/** Matches the widget's faded first lap (90 of 255). */
private const val COMPLETED_LAP_ALPHA = 90f / 255f
