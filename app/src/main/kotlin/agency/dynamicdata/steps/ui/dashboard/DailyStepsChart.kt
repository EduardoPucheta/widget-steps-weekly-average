package agency.dynamicdata.steps.ui.dashboard

import agency.dynamicdata.steps.core.DayEntry
import agency.dynamicdata.steps.core.MovingAveragePoint
import agency.dynamicdata.steps.core.StepGoal
import agency.dynamicdata.steps.widget.StepsWidgetFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * The window's days as bars, the rolling average as a line over them, and the goal.
 *
 * Two series now, so there is a legend — identity is never left to colour alone —
 * and each series is also named at its end in the right-hand gutter. Values are not
 * written on the bars: the list underneath is the exact reading. The goal is a
 * reference rather than a series, so it gets a direct label but no legend entry.
 */
@Composable
fun DailyStepsChart(
    days: List<DayEntry>,
    movingAverage: List<MovingAveragePoint>,
    goal: StepGoal,
    locale: Locale,
    modifier: Modifier = Modifier,
    height: Dp = 168.dp,
) {
    val palette = ChartPalette.forTheme()
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = palette.goalLine)
    val goalLabel = "goal ${StepsWidgetFormat.compact(goal.stepsPerDay, locale)}"
    // Only when the line reaches the last day, which is when its end is the headline.
    val averageLabel = movingAverage.lastOrNull()?.averageStepsPerDay
        ?.let { "avg ${StepsWidgetFormat.compact(it, locale)}" }

    val spoken = days.zip(movingAverage).joinToString(separator = "; ") { (day, avg) ->
        val name = day.date.dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale)
        val value = day.steps?.let { StepsWidgetFormat.exact(it, locale) } ?: "no data"
        val average = avg.averageStepsPerDay?.let { ", 7-day average ${StepsWidgetFormat.exact(it, locale)}" } ?: ""
        "$name $value$average"
    }

    Column(
        modifier = modifier.semantics { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Legend(palette)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                // Covers the plot and the day labels together: sizing to the plot
                // alone leaves the axis band clipped.
                .height(height)
                .padding(top = 4.dp),
        ) {
            drawChart(
                days = days,
                movingAverage = movingAverage,
                goal = goal,
                palette = palette,
                measurer = measurer,
                labelStyle = labelStyle,
                goalLabel = goalLabel,
                averageLabel = averageLabel,
                locale = locale,
            )
        }
    }
}

/**
 * Names the two series. The swatches mirror the marks — a block for the bars, a
 * line with its end-dot for the average — so the key reads without needing colour.
 */
@Composable
private fun Legend(palette: ChartPalette) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem("Daily steps") {
            Canvas(Modifier.size(10.dp)) {
                drawRoundRect(color = palette.bar, cornerRadius = CornerRadius(2.dp.toPx()))
            }
        }
        LegendItem("7-day average") {
            Canvas(Modifier.width(18.dp).height(10.dp)) {
                val y = size.height / 2f
                drawLine(
                    color = palette.average,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawCircle(palette.average, radius = 3.dp.toPx(), center = Offset(size.width - 3.dp.toPx(), y))
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) { swatch() }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            // Text in a text colour; the swatch beside it carries the identity.
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Mark specs: bars capped so the slot keeps its air, a 4dp rounded top squared off
// at the baseline, at least 2dp between neighbours; a 2dp line with round joins and
// a surface casing where it crosses bars; an 8dp end-dot with a 2dp surface ring.
private val MAX_BAR_WIDTH = 24.dp
private val MIN_BAR_GAP = 2.dp
private val BAR_CORNER = 4.dp
private val AXIS_BAND = 20.dp
private val GUTTER_GAP = 4.dp
private val LINE_WIDTH = 2.dp
private val LINE_CASING = 2.dp
private val END_DOT_RADIUS = 4.dp
private val END_DOT_RING = 2.dp
private val LABEL_SEPARATION = 2.dp

/** Headroom above the tallest mark, so nothing touches the top edge. */
private const val HEADROOM = 1.12f

private fun DrawScope.drawChart(
    days: List<DayEntry>,
    movingAverage: List<MovingAveragePoint>,
    goal: StepGoal,
    palette: ChartPalette,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
    goalLabel: String,
    averageLabel: String?,
    locale: Locale,
) {
    if (days.isEmpty()) return

    val axisBandPx = AXIS_BAND.toPx()
    val plotHeight = size.height - axisBandPx
    if (plotHeight <= 0f) return

    val goalText = measurer.measure(goalLabel, labelStyle)
    val averageText = averageLabel?.let { measurer.measure(it, labelStyle) }

    // Both labels share a gutter of their own. Letting the plot run the full width
    // puts them on top of whichever bars happen to be tall that week.
    val gutter = max(goalText.size.width, averageText?.size?.width ?: 0)
    val plotWidth = size.width - gutter - GUTTER_GAP.toPx()
    if (plotWidth <= 0f) return

    val goalSteps = goal.stepsPerDay.toFloat()
    val tallestDay = days.mapNotNull { it.steps }.maxOrNull()?.toFloat() ?: 0f
    val highestAverage = movingAverage.mapNotNull { it.averageStepsPerDay }.maxOrNull()?.toFloat() ?: 0f
    // The goal is always inside the scale so its rule stays on screen, and so is the
    // line: after a big week the average can sit above every bar in view.
    val ceiling = max(goalSteps, max(tallestDay, highestAverage)) * HEADROOM
    fun yFor(steps: Float) = plotHeight - (steps / ceiling) * plotHeight

    val slot = plotWidth / days.size
    val barWidth = min(MAX_BAR_WIDTH.toPx(), slot - MIN_BAR_GAP.toPx())
    val corner = CornerRadius(BAR_CORNER.toPx(), BAR_CORNER.toPx())
    fun centreOf(index: Int) = slot * index + slot / 2f

    // Baseline first, so the bars sit on it rather than under it.
    drawLine(
        color = palette.axis,
        start = Offset(0f, plotHeight),
        end = Offset(plotWidth, plotHeight),
        strokeWidth = 1.dp.toPx(),
    )

    days.forEachIndexed { index, day ->
        val left = centreOf(index) - barWidth / 2f
        val steps = day.steps
        if (steps == null) {
            // No answer: full height so it cannot be read as a small value, neutral
            // so it cannot be read as the series.
            drawRoundRect(
                color = palette.absent,
                topLeft = Offset(left, 0f),
                size = Size(barWidth, plotHeight),
                cornerRadius = corner,
            )
        } else if (steps > 0) {
            val top = yFor(steps.toFloat())
            drawPath(barPath(left, top, barWidth, plotHeight - top, corner), palette.bar)
        }

        val letter = day.date.dayOfWeek.getDisplayName(JavaTextStyle.NARROW, locale).uppercase(locale)
        val measured = measurer.measure(letter, labelStyle)
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(
                x = centreOf(index) - measured.size.width / 2f,
                y = plotHeight + (axisBandPx - measured.size.height) / 2f,
            ),
        )
    }

    // The goal rule: dashed, because it is a threshold — the one place dashing means
    // something. Drawn before the average so the data line sits on top of it.
    val goalY = yFor(goalSteps)
    drawLine(
        color = palette.goalLine,
        start = Offset(0f, goalY),
        end = Offset(plotWidth, goalY),
        strokeWidth = 2.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
    )

    val averageEnd = drawAverageLine(movingAverage, palette, ::centreOf, ::yFor)

    // Gutter labels, kept apart when the average finishes near the goal — the week
    // most worth looking at is the one where they would otherwise overprint.
    val textHeight = max(goalText.size.height, averageText?.size?.height ?: 0).toFloat()
    val labelX = plotWidth + GUTTER_GAP.toPx()
    if (averageText != null && averageEnd != null) {
        val (goalLabelY, averageLabelY) = GutterLabels.separate(
            goal = goalY,
            average = averageEnd.y,
            minGap = textHeight + LABEL_SEPARATION.toPx(),
            top = textHeight / 2f,
            bottom = plotHeight - textHeight / 2f,
        )
        drawText(goalText, topLeft = Offset(labelX, goalLabelY - goalText.size.height / 2f))
        drawText(averageText, topLeft = Offset(labelX, averageLabelY - averageText.size.height / 2f))
    } else {
        drawText(goalText, topLeft = Offset(labelX, goalY - goalText.size.height / 2f))
    }
}

/**
 * Draws the moving average and returns where it ends, or null if it never appears.
 *
 * A day whose span had nothing recorded breaks the line rather than dropping it to
 * the floor. A zero there would claim a week of not moving; a gap says there was no
 * reading, which is the truth.
 */
private fun DrawScope.drawAverageLine(
    points: List<MovingAveragePoint>,
    palette: ChartPalette,
    centreOf: (Int) -> Float,
    yFor: (Float) -> Float,
): Offset? {
    val runs = mutableListOf<MutableList<Offset>>()
    var current: MutableList<Offset>? = null
    points.forEachIndexed { index, point ->
        val value = point.averageStepsPerDay
        if (value == null) {
            current = null
        } else {
            val offset = Offset(centreOf(index), yFor(value.toFloat()))
            (current ?: mutableListOf<Offset>().also { current = it; runs += it }).add(offset)
        }
    }
    if (runs.isEmpty()) return null

    val lineWidth = LINE_WIDTH.toPx()
    val casingWidth = lineWidth + 2 * LINE_CASING.toPx()

    runs.forEach { run ->
        if (run.size == 1) {
            // A lone point between two gaps would draw nothing as a path.
            drawCircle(palette.surface, radius = END_DOT_RADIUS.toPx(), center = run.first())
            drawCircle(palette.average, radius = END_DOT_RADIUS.toPx() - END_DOT_RING.toPx() / 2f, center = run.first())
            return@forEach
        }
        val path = Path().apply {
            moveTo(run.first().x, run.first().y)
            run.drop(1).forEach { lineTo(it.x, it.y) }
        }
        // A casing in the surface colour, then the line: where the line crosses a
        // bar, the thin surface band keeps the two from merging into one shape.
        drawPath(path, palette.surface, style = Stroke(casingWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, palette.average, style = Stroke(lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    val end = runs.last().last()
    drawCircle(palette.surface, radius = END_DOT_RADIUS.toPx() + END_DOT_RING.toPx(), center = end)
    drawCircle(palette.average, radius = END_DOT_RADIUS.toPx(), center = end)
    return end
}

/** A bar with rounded top corners and square ones on the baseline. */
private fun barPath(left: Float, top: Float, width: Float, height: Float, corner: CornerRadius): Path =
    Path().apply {
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
