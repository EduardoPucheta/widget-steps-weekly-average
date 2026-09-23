package agency.dynamicdata.steps.ui.dashboard

import agency.dynamicdata.steps.core.DayEntry
import agency.dynamicdata.steps.core.StepGoal
import agency.dynamicdata.steps.widget.StepsWidgetFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * The window's days as bars, against the goal.
 *
 * One series, so there is no legend: the heading says what is plotted. Values are
 * not written on the bars — the list underneath is the exact reading, and a number
 * on every bar is noise. The only direct label is the goal rule, because a threshold
 * that is not named is just a line.
 */
@Composable
fun DailyStepsChart(
    days: List<DayEntry>,
    goal: StepGoal,
    locale: Locale,
    modifier: Modifier = Modifier,
    height: Dp = 168.dp,
) {
    val palette = ChartPalette.forTheme()
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
    val goalStyle = labelStyle.copy(color = palette.goalLine)
    val goalLabel = "goal ${StepsWidgetFormat.compact(goal.stepsPerDay, locale)}"

    val spoken = days.joinToString(separator = "; ") { day ->
        val name = day.date.dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale)
        val value = day.steps?.let { StepsWidgetFormat.exact(it, locale) } ?: "no data"
        "$name $value"
    }

    Column(modifier = modifier.semantics { contentDescription = spoken }) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                // The height covers the plot and the day labels together: sizing to
                // the plot alone leaves the axis band clipped.
                .height(height)
                .padding(top = 4.dp),
        ) {
            drawChart(
                days = days,
                goal = goal,
                palette = palette,
                measurer = measurer,
                labelStyle = labelStyle.copy(color = palette.goalLine),
                goalStyle = goalStyle,
                goalLabel = goalLabel,
                locale = locale,
            )
        }
    }
}

// Mark specs: bars capped so the slot keeps its air, a 4dp rounded top squared off
// at the baseline, and at least a 2dp gap between neighbours.
private val MAX_BAR_WIDTH = 24.dp
private val MIN_BAR_GAP = 2.dp
private val BAR_CORNER = 4.dp
private val AXIS_BAND = 20.dp
private val GOAL_LABEL_GAP = 4.dp

/** Headroom above the tallest mark, so a bar never touches the top edge. */
private const val HEADROOM = 1.12f

private fun DrawScope.drawChart(
    days: List<DayEntry>,
    goal: StepGoal,
    palette: ChartPalette,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
    goalStyle: TextStyle,
    goalLabel: String,
    locale: Locale,
) {
    if (days.isEmpty()) return

    val axisBandPx = AXIS_BAND.toPx()
    val plotHeight = size.height - axisBandPx
    if (plotHeight <= 0f) return

    val goalSteps = goal.stepsPerDay.toFloat()
    val tallestDay = days.mapNotNull { it.steps }.maxOrNull()?.toFloat() ?: 0f
    // The goal is always inside the scale, so the rule stays on screen even in a week
    // that never came close to it.
    val ceiling = max(goalSteps, tallestDay) * HEADROOM

    // The goal label gets a gutter of its own. Letting the plot run the full width
    // puts the label on top of whichever bars happen to be tall that week.
    val goalText = measurer.measure(goalLabel, goalStyle)
    val plotWidth = size.width - goalText.size.width - GOAL_LABEL_GAP.toPx()
    if (plotWidth <= 0f) return

    val slot = plotWidth / days.size
    val barWidth = min(MAX_BAR_WIDTH.toPx(), slot - MIN_BAR_GAP.toPx())
    val corner = CornerRadius(BAR_CORNER.toPx(), BAR_CORNER.toPx())

    // Drawn before the bars so they sit on it rather than under it.
    drawLine(
        color = palette.axis,
        start = Offset(0f, plotHeight),
        end = Offset(plotWidth, plotHeight),
        strokeWidth = 1.dp.toPx(),
    )

    days.forEachIndexed { index, day ->
        val centre = slot * index + slot / 2f
        val left = centre - barWidth / 2f

        val steps = day.steps
        if (steps == null) {
            // A slot with no answer: full height, so it cannot be read as a small
            // value, and neutral, so it cannot be read as the series at all.
            drawRoundRect(
                color = palette.absent,
                topLeft = Offset(left, 0f),
                size = androidx.compose.ui.geometry.Size(barWidth, plotHeight),
                cornerRadius = corner,
            )
        } else if (steps > 0) {
            val barHeight = (steps / ceiling) * plotHeight
            drawPath(
                path = barPath(left, plotHeight - barHeight, barWidth, barHeight, corner),
                color = palette.bar,
            )
        }

        val label = day.date.dayOfWeek
            .getDisplayName(JavaTextStyle.NARROW, locale)
            .uppercase(locale)
        val measured = measurer.measure(label, labelStyle)
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(
                x = centre - measured.size.width / 2f,
                y = plotHeight + (axisBandPx - measured.size.height) / 2f,
            ),
        )
    }

    // The goal rule, over the bars and dashed because it is a threshold — the one
    // place dashing carries meaning rather than adding noise.
    val goalY = plotHeight - (goalSteps / ceiling) * plotHeight
    drawLine(
        color = palette.goalLine,
        start = Offset(0f, goalY),
        end = Offset(plotWidth, goalY),
        strokeWidth = 2.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
    )
    drawText(
        textLayoutResult = goalText,
        topLeft = Offset(
            x = plotWidth + GOAL_LABEL_GAP.toPx(),
            y = goalY - goalText.size.height / 2f,
        ),
    )
}

/** A bar with rounded top corners and square ones on the baseline. */
private fun barPath(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    corner: CornerRadius,
): Path = Path().apply {
    addRoundRect(
        RoundRect(
            rect = Rect(left, top, left + width, top + height),
            topLeft = corner,
            topRight = corner,
            bottomRight = CornerRadius.Zero,
            bottomLeft = CornerRadius.Zero,
        ),
    )
}

