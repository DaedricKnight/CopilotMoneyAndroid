package com.artemkhateev.finance.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.artemkhateev.finance.R

/**
 * Шрифт референса на Android не лицензирован, поэтому близкий по характеру
 * геометрический гротеск из Google Fonts. Его скачивают Play Services при первом
 * запуске; пока не скачан или сервисов нет — системный шрифт.
 */
private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val plusJakartaSans = GoogleFont("Plus Jakarta Sans")

private val AppFontFamily = FontFamily(
    Font(googleFont = plusJakartaSans, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = plusJakartaSans, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = plusJakartaSans, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = plusJakartaSans, fontProvider = fontProvider, weight = FontWeight.Bold),
)

@Immutable
data class FinanceTypography(
    val screenTitle: TextStyle,
    val tab: TextStyle,
    val heroAmount: TextStyle,
    val cardTitle: TextStyle,
    val body: TextStyle,
    val bodySecondary: TextStyle,
    val caption: TextStyle,
    val sectionLabel: TextStyle,
    val chip: TextStyle,
    val button: TextStyle,
)

private fun style(size: Float, weight: FontWeight, letterSpacing: Float = 0f) = TextStyle(
    fontFamily = AppFontFamily,
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = letterSpacing.sp,
)

val DefaultFinanceTypography = FinanceTypography(
    screenTitle = style(28f, FontWeight.Bold),
    tab = style(16f, FontWeight.Medium),
    heroAmount = style(26f, FontWeight.SemiBold),
    cardTitle = style(17f, FontWeight.SemiBold),
    body = style(16f, FontWeight.Medium),
    bodySecondary = style(14f, FontWeight.Medium),
    caption = style(12f, FontWeight.SemiBold),
    sectionLabel = style(12f, FontWeight.Bold, letterSpacing = 0.8f),
    chip = style(10.5f, FontWeight.Bold, letterSpacing = 0.5f),
    button = style(13f, FontWeight.Bold, letterSpacing = 0.9f),
)
