package com.artemkhateev.finance.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.artemkhateev.finance.data.model.CategoryTone

/**
 * Палитра подобрана по скриншотам референса (тёмная тема). Все цвета
 * интерфейса берутся только отсюда: своя айдентика перед публикацией — правка этого файла.
 */
@Immutable
data class FinanceColors(
    val background: Color,
    val backgroundTop: Color,
    val surface: Color,
    val surfaceHero: Color,
    val border: Color,
    val pill: Color,
    val button: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textInactive: Color,
    val sectionLabel: Color,
    val icon: Color,
    val accent: Color,
    val positive: Color,
    val positiveText: Color,
    val negative: Color,
    val negativeText: Color,
    val warning: Color,
    val track: Color,
    val tooltipPositive: Color,
    val chartLineStart: Color,
    val chartLineEnd: Color,
    val chartPace: Color,
)

val DarkFinanceColors = FinanceColors(
    background = Color(0xFF020913),
    backgroundTop = Color(0xFF000D1E),
    surface = Color(0xFF000F24),
    surfaceHero = Color(0xFF011432),
    border = Color(0xFF0F2544),
    pill = Color(0xFF091628),
    button = Color(0xFF0A1D38),
    textPrimary = Color(0xFFE6EFFB),
    textSecondary = Color(0xFF6B89B8),
    textMuted = Color(0xFF506282),
    textInactive = Color(0xFF334F79),
    sectionLabel = Color(0xFF718EBA),
    icon = Color(0xFF4A6FA5),
    accent = Color(0xFF6292D6),
    positive = Color(0xFF01C64A),
    positiveText = Color(0xFF5CA955),
    negative = Color(0xFFFF4433),
    negativeText = Color(0xFFD9464A),
    warning = Color(0xFFFD9F14),
    track = Color(0xFF111E2F),
    tooltipPositive = Color(0xFF07AA46),
    chartLineStart = Color(0xFF3DBE5A),
    chartLineEnd = Color(0xFFF0D062),
    chartPace = Color(0xFF334F79),
)

/** Основной цвет категории: текст чипа и, с прозрачностью, его подложка. */
fun CategoryTone.color(): Color = when (this) {
    CategoryTone.Orange -> Color(0xFFDB975E)
    CategoryTone.Yellow -> Color(0xFFE3B65B)
    CategoryTone.Green -> Color(0xFF60A65B)
    CategoryTone.Teal -> Color(0xFF79CFD4)
    CategoryTone.Blue -> Color(0xFF508BDC)
    CategoryTone.Purple -> Color(0xFF9B7BEA)
    CategoryTone.Magenta -> Color(0xFFC636B6)
    CategoryTone.Pink -> Color(0xFFE05DA8)
    CategoryTone.Red -> Color(0xFFE0484A)
    CategoryTone.Gray -> Color(0xFF8A9BB5)
}

const val TONE_BACKGROUND_ALPHA = 0.17f
