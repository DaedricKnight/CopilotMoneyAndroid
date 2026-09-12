package com.artemkhateev.finance.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalFinanceColors = staticCompositionLocalOf { DarkFinanceColors }
private val LocalFinanceTypography = staticCompositionLocalOf { DefaultFinanceTypography }

object FinanceTheme {
    val colors: FinanceColors
        @Composable @ReadOnlyComposable get() = LocalFinanceColors.current

    val typography: FinanceTypography
        @Composable @ReadOnlyComposable get() = LocalFinanceTypography.current
}

@Composable
fun FinanceTheme(content: @Composable () -> Unit) {
    val colors = DarkFinanceColors
    CompositionLocalProvider(
        LocalFinanceColors provides colors,
        LocalFinanceTypography provides DefaultFinanceTypography,
    ) {
        // Material 3 нужен ради базовых компонентов (рябь, IconButton); цвета и шрифты — свои.
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = colors.accent,
                background = colors.background,
                surface = colors.surface,
                onBackground = colors.textPrimary,
                onSurface = colors.textPrimary,
            ),
            content = content,
        )
    }
}
