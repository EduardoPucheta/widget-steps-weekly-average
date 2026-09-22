package agency.dynamicdata.steps.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider

/**
 * The widget's own colours, for both the Canvas-drawn ring and the Glance text on
 * top of it.
 *
 * Defined here as plain values rather than taken from [androidx.glance.GlanceTheme]
 * because the ring is drawn onto a Bitmap, and Canvas needs colour ints that a
 * theme's [ColorProvider] will not hand over outside a composition. Keeping both
 * forms in one place is what stops the ring and the text drifting apart.
 *
 * Which theme applies is resolved once, before rendering, and passed in as a plain
 * boolean — so nothing here has to read resources from inside a composable.
 */
object StepsRingPalette {

    /** The filled disc the whole widget sits on. */
    val surface = DayNight(day = 0xFFE9EDEA, night = 0xFF1B201D)

    /** The unfilled part of the ring. */
    val track = DayNight(day = 0xFFCBD3CD, night = 0xFF39403B)

    /** The filled part of the ring, and the goal line under the number. */
    val accent = DayNight(day = 0xFF00696D, night = 0xFF4FD8DE)

    /** The ring once the goal is met — a different hue, so it is visible at a glance. */
    val accentMet = DayNight(day = 0xFF386A20, night = 0xFF9BD67C)

    /** The big number. */
    val onSurface = DayNight(day = 0xFF111411, night = 0xFFE3E8E4)

    /** Labels and the trend. */
    val onSurfaceVariant = DayNight(day = 0xFF5A625C, night = 0xFFA8B2AC)

    /** A colour in both themes, usable as a Canvas int or a Glance provider. */
    data class DayNight(private val day: Long, private val night: Long) {

        /** The int Canvas needs. */
        fun toColorInt(night: Boolean): Int = (if (night) this.night else day).toInt()

        /** The provider Glance needs, resolved against the same theme as the ring. */
        fun toColorProvider(night: Boolean): ColorProvider =
            ColorProvider(Color(toColorInt(night)))
    }
}

/** Whether the device is currently in dark theme. Read outside the composition. */
fun Context.isNightMode(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
