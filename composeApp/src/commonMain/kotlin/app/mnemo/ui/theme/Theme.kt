package app.mnemo.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.mnemo.platform.Prefs

enum class ThemeMode { Dark, Light, System }

/** The theme switch, available to every screen (Settings changes it). */
class ThemeState(
    initial: ThemeMode,
) {
    var mode: ThemeMode by mutableStateOf(initial)
        private set

    fun set(mode: ThemeMode) {
        this.mode = mode
        Prefs.set(Prefs.THEME, mode.name)
    }

    companion object {
        fun fromPrefs(): ThemeState =
            ThemeState(runCatching { ThemeMode.valueOf(Prefs.get(Prefs.THEME) ?: "Dark") }.getOrDefault(ThemeMode.Dark))
    }
}

val LocalThemeMode = staticCompositionLocalOf { ThemeState(ThemeMode.Dark) }

private fun scheme(p: Palette): ColorScheme {
    val base = if (p.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.coral,
        onPrimary = p.onCoral,
        primaryContainer = p.gradeAgainBg,
        onPrimaryContainer = p.coral,
        secondary = p.lime,
        onSecondary = p.onLime,
        tertiary = p.lavender,
        onTertiary = p.onLavender,
        background = p.background,
        onBackground = p.text,
        surface = p.background,
        onSurface = p.text,
        surfaceVariant = p.surface2,
        onSurfaceVariant = p.text2,
        surfaceContainerLowest = p.background,
        surfaceContainerLow = p.surface,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surface2,
        surfaceContainerHighest = p.surface3,
        outline = p.outline,
        outlineVariant = p.outlineStrong,
        error = p.danger,
        onError = if (p.isDark) p.onCoral else Color.White,
        errorContainer = p.gradeAgainBg,
        onErrorContainer = p.danger,
    )
}

val mnemoShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )

@Composable
fun MnemoTheme(
    themeState: ThemeState? = null,
    content: @Composable () -> Unit,
) {
    val state = themeState ?: remember { ThemeState(ThemeMode.Dark) }
    val systemDark = isSystemInDarkTheme()
    val dark =
        when (state.mode) {
            ThemeMode.Dark -> true
            ThemeMode.Light -> false
            ThemeMode.System -> systemDark
        }
    val palette = if (dark) OledPalette else LightPalette
    SideEffect { if (MnemoColors.palette !== palette) MnemoColors.palette = palette }
    CompositionLocalProvider(LocalThemeMode provides state) {
        MaterialTheme(
            colorScheme = scheme(palette),
            typography = mnemoTypography(),
            shapes = mnemoShapes,
        ) {
            // Paint the ground once, so every screen sits on it (true black in OLED mode).
            Box(Modifier.fillMaxSize().background(palette.background)) { content() }
        }
    }
}
