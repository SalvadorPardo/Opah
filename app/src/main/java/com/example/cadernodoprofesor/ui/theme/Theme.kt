package com.example.cadernodoprofesor.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = OnPrimaryBlue,
    secondary = SecondaryPurple,
    tertiary = TertiaryGreen,
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF1C1B1F),
    error = FunctionalColors.Red
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = OnPrimaryBlue,
    secondary = SecondaryPurple,
    tertiary = TertiaryGreen,
    background = LightGrayCanvas,
    surface = WhiteCanvas,
    onBackground = DarkText,
    onSurface = DarkText,
    error = FunctionalColors.Red,
    outlineVariant = OutlineVariant,
    surfaceVariant = Color(0xFFE7E0EC)
)

@Composable
fun CadernoDoProfesorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Desactivamos cores dinámicas por defecto para manter a identidade visual do "Arco da Vella Funcional"
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
