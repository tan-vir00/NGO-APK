package com.ngoloanmanager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = MintSoft,
    onPrimaryContainer = EmeraldDeep,
    secondary = GoldWarm,
    onSecondary = Color.White,
    secondaryContainer = GoldSoft,
    onSecondaryContainer = Color(0xFF4A2E0A),
    tertiary = EmeraldMid,
    onTertiary = Color.White,
    background = PearlMuted,
    onBackground = Charcoal,
    surface = PearlSurface,
    onSurface = Charcoal,
    surfaceVariant = MintPale,
    onSurfaceVariant = CharcoalMuted,
    outline = OutlineSoft,
    outlineVariant = Color(0xFFDCE6E1),
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorContainer,
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Charcoal,
    inverseOnSurface = PearlWhite,
    inversePrimary = DarkPrimary,
    surfaceTint = EmeraldPrimary
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkMint,
    onPrimaryContainer = MintSoft,
    secondary = DarkGold,
    onSecondary = Color(0xFF3A2400),
    secondaryContainer = Color(0xFF4A3518),
    onSecondaryContainer = GoldSoft,
    tertiary = EmeraldMid,
    onTertiary = Color.White,
    background = DarkBg,
    onBackground = PearlWhite,
    surface = DarkSurface,
    onSurface = PearlWhite,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB0C0B8),
    outline = DarkOutline,
    outlineVariant = Color(0xFF2A3832),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = PearlMuted,
    inverseOnSurface = Charcoal,
    inversePrimary = EmeraldPrimary,
    surfaceTint = DarkPrimary
)

@Composable
fun NgoLoanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
