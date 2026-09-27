package agency.dynamicdata.steps.ui.theme

import agency.dynamicdata.steps.widget.StepsRingPalette
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The app's colours, built from the widget's rather than Material's default purple.
 *
 * Two things tie it down. The cards take the widget's disc colour, so the ring on
 * the home screen and the ring in the app sit on the same ground. And the chart is
 * drawn inside a card: its line casing and end-dot ring are painted in
 * [ChartPalette.surface][agency.dynamicdata.steps.ui.dashboard.ChartPalette], which
 * has to be the card colour exactly or the casing shows as a halo. Both are
 * `surfaceContainer` here for that reason.
 *
 * Follows the system theme. Without this the screen stayed light in dark mode
 * while the chart switched to its dark palette, drawing dark marks on a light card.
 */
@Composable
fun StepsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}

private fun StepsRingPalette.DayNight.day() = Color(toColorInt(night = false))
private fun StepsRingPalette.DayNight.night() = Color(toColorInt(night = true))

private val Light: ColorScheme = lightColorScheme(
    primary = StepsRingPalette.accent.day(),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9ECEE),
    onPrimaryContainer = Color(0xFF002021),
    secondary = Color(0xFF4A6364),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E8),
    onSecondaryContainer = Color(0xFF051F20),
    tertiary = StepsRingPalette.accentMet.day(),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC6EDB0),
    onTertiaryContainer = Color(0xFF072100),
    background = Color(0xFFF8FAF8),
    onBackground = StepsRingPalette.onSurface.day(),
    surface = Color(0xFFF8FAF8),
    onSurface = StepsRingPalette.onSurface.day(),
    surfaceVariant = Color(0xFFDDE4DF),
    onSurfaceVariant = StepsRingPalette.onSurfaceVariant.day(),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F4F1),
    surfaceContainer = StepsRingPalette.surface.day(),
    surfaceContainerHigh = Color(0xFFE2E7E3),
    surfaceContainerHighest = Color(0xFFDCE2DD),
    outline = Color(0xFF727972),
    outlineVariant = StepsRingPalette.track.day(),
)

private val Dark: ColorScheme = darkColorScheme(
    primary = StepsRingPalette.accent.night(),
    onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF004F52),
    onPrimaryContainer = Color(0xFFB9ECEE),
    secondary = Color(0xFFB0CCCC),
    onSecondary = Color(0xFF1B3435),
    secondaryContainer = Color(0xFF324B4C),
    onSecondaryContainer = Color(0xFFCCE8E8),
    tertiary = StepsRingPalette.accentMet.night(),
    onTertiary = Color(0xFF113800),
    tertiaryContainer = Color(0xFF285015),
    onTertiaryContainer = Color(0xFFC6EDB0),
    background = Color(0xFF111412),
    onBackground = StepsRingPalette.onSurface.night(),
    surface = Color(0xFF111412),
    onSurface = StepsRingPalette.onSurface.night(),
    surfaceVariant = Color(0xFF3F4541),
    onSurfaceVariant = StepsRingPalette.onSurfaceVariant.night(),
    surfaceContainerLowest = Color(0xFF0C0F0D),
    surfaceContainerLow = Color(0xFF171B18),
    surfaceContainer = StepsRingPalette.surface.night(),
    surfaceContainerHigh = Color(0xFF252A27),
    surfaceContainerHighest = Color(0xFF303532),
    outline = Color(0xFF8B928D),
    outlineVariant = StepsRingPalette.track.night(),
)
