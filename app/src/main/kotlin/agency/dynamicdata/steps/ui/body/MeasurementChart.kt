package agency.dynamicdata.steps.ui.body

import agency.dynamicdata.steps.core.AxisTicks
import agency.dynamicdata.steps.core.Measurement
import agency.dynamicdata.steps.core.MeasurementTrend
import agency.dynamicdata.steps.core.TrendPoint
import agency.dynamicdata.steps.core.YearPeriod
import agency.dynamicdata.steps.ui.dashboard.ChartPalette
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.abs

/**
 * A year of one measurement: every reading as a dot, the 30-day trend as a line.
 *
 * Dots, not a line through the readings: a line joining every weigh-in zig-zags
 * with the day-to-day noise and hides the direction it is meant to show. The dots
 * keep every submission visible; the trend carries the direction. Both in the
 * daily chart's colours, so a dot and a trend read the same way everywhere.
 *
 * The value axis spans the data rather than starting at zero. A year of weight
 * moves a few kilos out of eighty; drawn from zero it would be a flat line.
 *
 * Tap or drag across it to read any reading: the crosshair snaps to the nearest one
 * in time, and the readout above shows its value, when it was taken and the trend
 * there. The list under the chart has every value without needing to aim.
 */
@Composable
internal fun MeasurementChart(
    readings: List<Measurement>,
    trend: List<TrendPoint>,
    period: YearPeriod,
    format: MetricFormat,
    description: String,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val palette = ChartPalette.forTheme()
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = palette.goalLine)
    val ticks = remember(readings, trend, format) {
        val shown = readings.map { format.display(it.value) } + trend.map { format.display(it.value) }
        AxisTicks.nice(shown.min(), shown.max())
    }
    val bounds = remember { PlotBounds() }
    var selected by remember(readings) { mutableStateOf<Int?>(null) }
    val trendAt = remember(trend) { trend.associateBy { it.at } }

    fun nearest(x: Float): Int? {
        if (readings.isEmpty() || bounds.right <= bounds.left) return null
        val fraction = ((x - bounds.left) / (bounds.right - bounds.left)).coerceIn(0f, 1f)
        val target = period.startSecond() + fraction * (period.endSecond() - period.startSecond())
        return readings.indices.minBy { abs(readings[it].at.second() - target) }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Legend(palette)
        Readout(selected?.let(readings::get), selected?.let { trendAt[readings[it].at] }, format, palette, locale)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT)
                .semantics { contentDescription = description }
                .pointerInput(readings) { detectTapGestures { selected = nearest(it.x) } }
                .pointerInput(readings) {
                    detectHorizontalDragGestures(onDragStart = { selected = nearest(it.x) }) { change, _ ->
                        selected = nearest(change.position.x)
                    }
                },
        ) {
            drawChart(readings, trend, period, ticks, format, palette, measurer, labelStyle, selected, bounds, locale)
        }
    }
}

/**
 * Where the plot area ended up, written while drawing and read by the touch
 * handlers so a tap can be mapped back to a date. Plain fields, not state: changing
 * them must not trigger another draw.
 */
private class PlotBounds {
    var left = 0f
    var right = 0f
}

@Composable
private fun Legend(palette: ChartPalette) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem("Each reading") {
            Canvas(Modifier.size(10.dp)) { drawCircle(palette.bar, radius = DOT_RADIUS.toPx()) }
        }
        LegendItem("30-day trend") {
            Canvas(Modifier.width(18.dp).height(10.dp)) {
                val y = size.height / 2f
                drawLine(palette.average, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) { swatch() }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The selected reading, value first — the reader already knows which chart this is
 * and wants the number. Always the same height, so selecting does not shift the
 * chart under the finger.
 */
@Composable
private fun Readout(
    reading: Measurement?,
    trend: TrendPoint?,
    format: MetricFormat,
    palette: ChartPalette,
    locale: Locale,
) {
    Column(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalArrangement = Arrangement.Center) {
        if (reading == null) {
            Text(
                "Tap or drag across the chart to read a value.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(format.value(reading.value, locale), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                reading.at.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trend != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // A short stroke of the line's colour keys the row, not a box.
                Canvas(Modifier.width(12.dp).height(8.dp)) {
                    val y = size.height / 2f
                    drawLine(palette.average, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), StrokeCap.Round)
                }
                Text(
                    "trend ${format.value(trend.value, locale)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val CHART_HEIGHT = 200.dp
private val AXIS_BAND = 20.dp
private val TOP_PAD = 8.dp
private val GUTTER_GAP = 6.dp
private val RIGHT_PAD = 8.dp
private val MONTH_LABEL_GAP = 6.dp
private val MONTH_TICK = 4.dp
private val DOT_RADIUS = 3.dp
private val END_DOT_RADIUS = 4.dp
private val SELECTED_RADIUS = 5.dp
private val RING = 2.dp
private val LINE_WIDTH = 2.dp
private val LINE_CASING = 2.dp

/**
 * The readings' dots are see-through so a cluster of weigh-ins reads darker than a
 * lone one: on a year of daily readings the dots overlap, and opacity is what keeps
 * density visible instead of a solid band.
 */
private const val DOT_ALPHA = 0.55f

private fun YearPeriod.startSecond(): Float = start.atStartOfDay().second()
private fun YearPeriod.endSecond(): Float = end.plusDays(1).atStartOfDay().second()

/** Seconds on a local clock, as a float: only ever compared within one period. */
private fun LocalDateTime.second(): Float = toEpochSecond(ZoneOffset.UTC).toFloat()

private fun DrawScope.drawChart(
    readings: List<Measurement>,
    trend: List<TrendPoint>,
    period: YearPeriod,
    ticks: List<Double>,
    format: MetricFormat,
    palette: ChartPalette,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
    selected: Int?,
    bounds: PlotBounds,
    locale: Locale,
) {
    if (readings.isEmpty() || ticks.size < 2) return

    val step = ticks[1] - ticks[0]
    val tickLabels = ticks.map { measurer.measure(format.tick(it, step, locale), labelStyle) }
    val gutter = tickLabels.maxOf { it.size.width } + GUTTER_GAP.toPx()

    val left = gutter
    val right = size.width - RIGHT_PAD.toPx()
    val top = TOP_PAD.toPx()
    val bottom = size.height - AXIS_BAND.toPx()
    if (right <= left || bottom <= top) return
    bounds.left = left
    bounds.right = right

    val low = ticks.first()
    val high = ticks.last()
    val startSecond = period.startSecond()
    val span = period.endSecond() - startSecond
    fun xFor(at: LocalDateTime) = left + (at.second() - startSecond) / span * (right - left)
    fun yFor(shown: Double) = (bottom - (shown - low) / (high - low) * (bottom - top)).toFloat()

    // Gridlines and their values: hairlines, recessive, the values in a text colour.
    ticks.forEachIndexed { index, tick ->
        val y = yFor(tick)
        drawLine(palette.axis, Offset(left, y), Offset(right, y), 1.dp.toPx())
        val label = tickLabels[index]
        drawText(label, topLeft = Offset(left - GUTTER_GAP.toPx() - label.size.width, y - label.size.height / 2f))
    }

    drawMonths(period, ::xFor, bottom, right, palette, measurer, labelStyle, locale)

    readings.forEach {
        drawCircle(palette.bar.copy(alpha = DOT_ALPHA), DOT_RADIUS.toPx(), Offset(xFor(it.at), yFor(format.display(it.value))))
    }

    // The trend over the dots, cased in the surface colour where it crosses them.
    MeasurementTrend.runs(trend).filter { it.size > 1 }.forEach { run ->
        val path = Path().apply {
            run.forEachIndexed { index, point ->
                val x = xFor(point.at)
                val y = yFor(format.display(point.value))
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        val line = LINE_WIDTH.toPx()
        drawPath(path, palette.surface, style = Stroke(line + 2 * LINE_CASING.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, palette.average, style = Stroke(line, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    // The newest reading, solid and ringed: the one the summary above calls "latest".
    val latest = readings.maxBy { it.at }
    ringedDot(palette, Offset(xFor(latest.at), yFor(format.display(latest.value))), END_DOT_RADIUS.toPx())

    selected?.let(readings::getOrNull)?.let { reading ->
        val point = Offset(xFor(reading.at), yFor(format.display(reading.value)))
        drawLine(palette.goalLine, Offset(point.x, top), Offset(point.x, bottom), 1.dp.toPx())
        ringedDot(palette, point, SELECTED_RADIUS.toPx())
    }
}

private fun DrawScope.ringedDot(palette: ChartPalette, centre: Offset, radius: Float) {
    drawCircle(palette.surface, radius + RING.toPx(), centre)
    drawCircle(palette.bar, radius, centre)
}

/**
 * A tick at the start of each month and its short name, January written as its
 * year so a view spanning two years says where one turns into the next. A label
 * that would run into the one before it is left out rather than squeezed.
 */
private fun DrawScope.drawMonths(
    period: YearPeriod,
    xFor: (LocalDateTime) -> Float,
    bottom: Float,
    right: Float,
    palette: ChartPalette,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
    locale: Locale,
) {
    var month = period.start.withDayOfMonth(1).let { if (it.isBefore(period.start)) it.plusMonths(1) else it }
    var lastLabelEnd = Float.NEGATIVE_INFINITY
    while (!month.isAfter(period.end)) {
        val x = xFor(month.atStartOfDay())
        drawLine(palette.axis, Offset(x, bottom), Offset(x, bottom + MONTH_TICK.toPx()), 1.dp.toPx())
        val name = if (month.monthValue == 1) month.year.toString() else month.month.getDisplayName(JavaTextStyle.SHORT, locale)
        val label = measurer.measure(name, labelStyle)
        val labelLeft = x + 2.dp.toPx()
        if (labelLeft >= lastLabelEnd + MONTH_LABEL_GAP.toPx() && labelLeft + label.size.width <= right + RIGHT_PAD.toPx()) {
            drawText(label, topLeft = Offset(labelLeft, bottom + (AXIS_BAND.toPx() - label.size.height) / 2f))
            lastLabelEnd = labelLeft + label.size.width
        }
        month = month.plusMonths(1)
    }
}
