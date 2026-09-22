package com.artemkhateev.finance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.ui.format.MoneyFormatter
import com.artemkhateev.finance.ui.format.SignStyle
import com.artemkhateev.finance.ui.theme.FinanceColors
import com.artemkhateev.finance.ui.theme.FinanceTheme
import com.artemkhateev.finance.ui.theme.TONE_BACKGROUND_ALPHA
import com.artemkhateev.finance.ui.theme.color

val CardShape = RoundedCornerShape(22.dp)

/** Фон экранов: у шапки чуть светлее, как в референсе. */
fun appBackgroundBrush(colors: FinanceColors): Brush =
    Brush.verticalGradient(0f to colors.backgroundTop, 0.4f to colors.background)

/** Отступы прокручиваемого экрана: контент идёт под панель навигации, но не прячется за ней. */
@Composable
fun screenContentPadding(extraBottom: Dp = 0.dp): PaddingValues {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = bottomInset + 24.dp + extraBottom)
}

@Composable
fun FinanceCard(
    modifier: Modifier = Modifier,
    hero: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = FinanceTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(if (hero) colors.surfaceHero else colors.surface)
            .border(1.dp, colors.border, CardShape)
            .padding(contentPadding),
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = typography.sectionLabel,
            color = colors.sectionLabel,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(action, style = typography.bodySecondary, color = colors.textMuted)
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun CategoryChip(category: Category?, modifier: Modifier = Modifier) {
    val tone = category?.tone?.color() ?: FinanceTheme.colors.textSecondary
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(tone.copy(alpha = TONE_BACKGROUND_ALPHA))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(category?.emoji ?: "❔", fontSize = 11.sp)
        Text(
            text = (category?.name ?: "Uncategorized").uppercase(),
            style = FinanceTheme.typography.chip,
            color = tone,
            maxLines = 1,
        )
    }
}

/** Переключатель из нескольких вариантов, как «Amount | Percentage» в референсе. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Делить ширину поровну: так в ряд помещается больше вариантов, например периоды трат. */
    fill: Boolean = false,
) {
    val colors = FinanceTheme.colors
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.background)
            .border(1.dp, colors.border, CircleShape)
            .padding(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Text(
                text = option,
                style = FinanceTheme.typography.bodySecondary,
                color = if (selected) colors.textPrimary else colors.textSecondary,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .then(if (fill) Modifier.weight(1f) else Modifier)
                    .clip(CircleShape)
                    .then(if (selected) Modifier.background(colors.button) else Modifier)
                    .clickable { onSelect(index) }
                    .padding(horizontal = if (fill) 4.dp else 22.dp, vertical = 8.dp),
            )
        }
    }
}

/** Пилюля выбора: счёт, дата и прочие варианты «один из нескольких». */
@Composable
fun SelectablePill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FinanceTheme.colors
    Text(
        text = text,
        style = FinanceTheme.typography.bodySecondary,
        color = if (selected) colors.accent else colors.textSecondary,
        maxLines = 1,
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) colors.pill else Color.Transparent)
            .border(1.dp, if (selected) colors.accent.copy(alpha = 0.6f) else colors.border, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Символ валюты в суммах мельче цифр и приподнят, как в референсе. */
fun currencySymbolStyle(fontSize: TextUnit) = SpanStyle(fontSize = fontSize * 0.62f, baselineShift = BaselineShift(0.48f))

/** Сумма с мелким приподнятым символом валюты. */
fun AnnotatedString.Builder.appendMoney(
    amount: Money,
    fontSize: TextUnit,
    cents: Boolean = true,
    sign: SignStyle = SignStyle.None,
) {
    val parts = MoneyFormatter.parts(amount, cents, sign)
    append(parts.sign)
    withStyle(currencySymbolStyle(fontSize)) { append(parts.symbol) }
    append(parts.digits)
}

@Composable
fun MoneyText(
    amount: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = FinanceTheme.typography.body,
    color: Color = FinanceTheme.colors.textPrimary,
    cents: Boolean = true,
    sign: SignStyle = SignStyle.None,
    suffix: String = "",
) {
    Text(
        text = buildAnnotatedString {
            appendMoney(amount, style.fontSize, cents, sign)
            append(suffix)
        },
        modifier = modifier,
        style = style,
        color = color,
        maxLines = 1,
    )
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
) {
    val colors = FinanceTheme.colors
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .fillMaxWidth()
            .clip(CircleShape)
            .background(if (filled) colors.accent else colors.button)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            style = FinanceTheme.typography.button,
            color = if (filled) colors.background else colors.accent,
        )
    }
}

@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val colors = FinanceTheme.colors
    Box(modifier.fillMaxSize().padding(16.dp)) {
        FinanceCard(
            hero = true,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 40.dp),
        ) {
            Text(title, style = FinanceTheme.typography.cardTitle, color = colors.textPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = FinanceTheme.typography.bodySecondary,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 5.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val track = FinanceTheme.colors.track
    Box(
        modifier = modifier.drawBehind {
            val stroke = strokeWidth.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        },
        contentAlignment = Alignment.Center,
        content = content,
    )
}
