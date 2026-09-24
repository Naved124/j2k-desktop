package dev.naved.j2kdesktop

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** The colours of each theme (all dark except Light). */
fun AppTheme.colors(): ColorScheme = when (this) {
    AppTheme.Dark -> darkColorScheme()
    AppTheme.Light -> lightColorScheme()
    AppTheme.Black -> palette(
        primary = 0xFFD0BCFF, onPrimary = 0xFF381E72, primaryContainer = 0xFF4F378B, onPrimaryContainer = 0xFFEADDFF,
        secondary = 0xFFCCC2DC, onSecondary = 0xFF332D41, secondaryContainer = 0xFF4A4458, onSecondaryContainer = 0xFFE8DEF8,
        background = 0xFF000000, surface = 0xFF000000, surfaceVariant = 0xFF1C1B1F,
        onSurface = 0xFFE6E1E5, onSurfaceVariant = 0xFFCAC4D0, outline = 0xFF79747E,
    )
    AppTheme.Tako -> palette(
        primary = 0xFFF3B375, onPrimary = 0xFF38294E, primaryContainer = 0xFF66577E, onPrimaryContainer = 0xFFF3B375,
        secondary = 0xFFF3B375, onSecondary = 0xFF38294E, secondaryContainer = 0xFF5A4D72, onSecondaryContainer = 0xFFFFDDBE,
        background = 0xFF21212E, surface = 0xFF21212E, surfaceVariant = 0xFF2E2B3F,
        onSurface = 0xFFE3E0F2, onSurfaceVariant = 0xFFCBC3DC, outline = 0xFF958DA5,
    )
    AppTheme.TokyoNight -> palette(
        primary = 0xFF7AA2F7, onPrimary = 0xFF1A1B26, primaryContainer = 0xFF3D59A1, onPrimaryContainer = 0xFFC0CAF5,
        secondary = 0xFFBB9AF7, onSecondary = 0xFF1A1B26, secondaryContainer = 0xFF4A3A6E, onSecondaryContainer = 0xFFE3D5FF,
        background = 0xFF1A1B26, surface = 0xFF1A1B26, surfaceVariant = 0xFF24283B,
        onSurface = 0xFFC0CAF5, onSurfaceVariant = 0xFFA9B1D6, outline = 0xFF565F89, error = 0xFFF7768E,
    )
    AppTheme.YinYang -> palette(
        primary = 0xFFFFFFFF, onPrimary = 0xFF1E1E1E, primaryContainer = 0xFF5A5A5A, onPrimaryContainer = 0xFFFFFFFF,
        secondary = 0xFFC8C8C8, onSecondary = 0xFF1E1E1E, secondaryContainer = 0xFF454545, onSecondaryContainer = 0xFFF0F0F0,
        background = 0xFF1E1E1E, surface = 0xFF1E1E1E, surfaceVariant = 0xFF313131,
        onSurface = 0xFFE6E1E5, onSurfaceVariant = 0xFFCAC4D0, outline = 0xFF8A8A8A,
    )
    AppTheme.FlatLime -> palette(
        primary = 0xFFB7E07A, onPrimary = 0xFF1D2A05, primaryContainer = 0xFF3E5A12, onPrimaryContainer = 0xFFD8F5A8,
        secondary = 0xFF8FD694, onSecondary = 0xFF0F2F14, secondaryContainer = 0xFF2E4D32, onSecondaryContainer = 0xFFC9F2CC,
        background = 0xFF202125, surface = 0xFF202125, surfaceVariant = 0xFF2C2E33,
        onSurface = 0xFFE3E4E8, onSurfaceVariant = 0xFFC3C6CC, outline = 0xFF8C9097,
    )
    AppTheme.MidnightDusk -> palette(
        primary = 0xFFF02475, onPrimary = 0xFFFFFFFF, primaryContainer = 0xFFBD1C5C, onPrimaryContainer = 0xFFFFD9E2,
        secondary = 0xFFF0A0BE, onSecondary = 0xFF4A0F28, secondaryContainer = 0xFF66183C, onSecondaryContainer = 0xFFFFD9E2,
        background = 0xFF16151D, surface = 0xFF16151D, surfaceVariant = 0xFF2A1F2A,
        onSurface = 0xFFE5E1E5, onSurfaceVariant = 0xFFD0C3CC, outline = 0xFF998D96,
    )
    AppTheme.ChocolateStrawberry -> palette(
        primary = 0xFFFF8FA3, onPrimary = 0xFF4A0F1C, primaryContainer = 0xFF8C2F45, onPrimaryContainer = 0xFFFFD9DF,
        secondary = 0xFFD9A38F, onSecondary = 0xFF3B2218, secondaryContainer = 0xFF5A3A2E, onSecondaryContainer = 0xFFFFDBCF,
        background = 0xFF1F1512, surface = 0xFF1F1512, surfaceVariant = 0xFF33241F,
        onSurface = 0xFFEFE0DB, onSurfaceVariant = 0xFFD7C2BB, outline = 0xFF9F8B84,
    )
    AppTheme.SapphireDusk -> palette(
        primary = 0xFF7FA8FF, onPrimary = 0xFF0B1F4D, primaryContainer = 0xFF1F3F8A, onPrimaryContainer = 0xFFD9E2FF,
        secondary = 0xFF9FB4E8, onSecondary = 0xFF122447, secondaryContainer = 0xFF2A3A5E, onSecondaryContainer = 0xFFD9E2FF,
        background = 0xFF0E1320, surface = 0xFF0E1320, surfaceVariant = 0xFF1B2336,
        onSurface = 0xFFE1E4F0, onSurfaceVariant = 0xFFC0C7DA, outline = 0xFF7A849E,
    )
}

/** A dark scheme from a handful of colours; the surface layers are mixed from surface + onSurface. */
private fun palette(
    primary: Long, onPrimary: Long, primaryContainer: Long, onPrimaryContainer: Long,
    secondary: Long, onSecondary: Long, secondaryContainer: Long, onSecondaryContainer: Long,
    background: Long, surface: Long, surfaceVariant: Long,
    onSurface: Long, onSurfaceVariant: Long, outline: Long,
    error: Long = 0xFFF2B8B5,
): ColorScheme {
    val s = Color(surface)
    val on = Color(onSurface)
    fun layer(f: Float) = lerp(s, on, f)
    return darkColorScheme(
        primary = Color(primary),
        onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer),
        onPrimaryContainer = Color(onPrimaryContainer),
        inversePrimary = Color(primaryContainer),
        secondary = Color(secondary),
        onSecondary = Color(onSecondary),
        secondaryContainer = Color(secondaryContainer),
        onSecondaryContainer = Color(onSecondaryContainer),
        tertiary = Color(secondary),
        onTertiary = Color(onSecondary),
        tertiaryContainer = Color(secondaryContainer),
        onTertiaryContainer = Color(onSecondaryContainer),
        background = Color(background),
        onBackground = on,
        surface = s,
        onSurface = on,
        surfaceVariant = Color(surfaceVariant),
        onSurfaceVariant = Color(onSurfaceVariant),
        surfaceTint = Color(primary),
        inverseSurface = on,
        inverseOnSurface = s,
        error = Color(error),
        outline = Color(outline),
        outlineVariant = lerp(Color(surfaceVariant), Color(outline), 0.35f),
        surfaceBright = layer(0.14f),
        surfaceDim = s,
        surfaceContainerLowest = lerp(s, Color.Black, 0.3f),
        surfaceContainerLow = layer(0.03f),
        surfaceContainer = layer(0.06f),
        surfaceContainerHigh = layer(0.09f),
        surfaceContainerHighest = layer(0.12f),
    )
}
