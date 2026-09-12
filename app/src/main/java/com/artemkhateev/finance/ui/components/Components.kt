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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.ui.format.MoneyFormatter
import com.artemkhateev.finance.ui.format.SignStyle
import com.artemkhateev.finance.ui.theme.FinanceTheme
import com.artemkhateev.finance.ui.theme.TONE_BACKGROUND_ALPHA
import com.artemkhateev.finance.ui.theme.color

val CardShape = RoundedCornerShape(22.dp)

/** Отступы прокручиваемого экрана: контент идёт под панель навигации, но не прячется за ней. */
@Composable
fun screenContentPadding(): PaddingValues {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = bottomInset + 24.dp)
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

/** Сумма с мелким приподнятым символом валюты, как в референсе. */
fun AnnotatedString.Builder.appendMoney(
    amount: Money,
    fontSize: TextUnit,
    cents: Boolean = true,
    sign: SignStyle = SignStyle.None,
) {
    val parts = MoneyFormatter.parts(amount, cents, sign)
    append(parts.sign)
    withStyle(SpanStyle(fontSize = fontSize * 0.62f, baselineShift = BaselineShift(0.48f))) {
        append(parts.symbol)
    }
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
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FinanceTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(colors.button)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = FinanceTheme.typography.button, color = colors.accent)
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
