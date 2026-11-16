package app.mnemo.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val oledScheme =
    darkColorScheme(
        primary = MnemoColors.coral,
        onPrimary = MnemoColors.onCoral,
        primaryContainer = Color(0xFF3A1D1A),
        onPrimaryContainer = MnemoColors.coral,
        secondary = MnemoColors.lime,
        onSecondary = MnemoColors.onLime,
        secondaryContainer = Color(0xFF2A3310),
        onSecondaryContainer = MnemoColors.lime,
        tertiary = MnemoColors.lavender,
        onTertiary = MnemoColors.onLavender,
        tertiaryContainer = Color(0xFF26203D),
        onTertiaryContainer = MnemoColors.lavender,
        background = MnemoColors.background,
        onBackground = MnemoColors.text,
        surface = MnemoColors.background,
        onSurface = MnemoColors.text,
        surfaceVariant = MnemoColors.surface2,
        onSurfaceVariant = MnemoColors.text2,
        surfaceContainerLowest = MnemoColors.background,
        surfaceContainerLow = MnemoColors.surface,
        surfaceContainer = MnemoColors.surface,
        surfaceContainerHigh = MnemoColors.surface2,
        surfaceContainerHighest = MnemoColors.surface3,
        outline = MnemoColors.outline,
        outlineVariant = MnemoColors.outlineStrong,
        error = MnemoColors.danger,
        onError = MnemoColors.onCoral,
        errorContainer = Color(0xFF3A1D1A),
        onErrorContainer = MnemoColors.danger,
    )

val mnemoShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )

@Composable
fun MnemoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = oledScheme,
        typography = mnemoTypography(),
        shapes = mnemoShapes,
    ) {
        // Paint the true-black ground once, so every screen sits on OLED black.
        Box(Modifier.fillMaxSize().background(MnemoColors.background)) { content() }
    }
}
