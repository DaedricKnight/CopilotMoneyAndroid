package com.artemkhateev.finance.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.SouthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.ui.format.MoneyFormatter
import com.artemkhateev.finance.ui.theme.FinanceTheme
import kotlin.math.abs

/** Часть столбика: положительные складываются вверх от нуля, отрицательные — вниз. */
data class ChartPart(val value: Long, val color: Color)

data class ChartBar(val parts: List<ChartPart>)

/** Столбчатая диаграмма как в референсе: пунктирная сетка, подписи оси слева, первая и последняя дата снизу. */
@Composable
fun BarChart(
    bars: List<ChartBar>,
    firstLabel: String,
    lastLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = FinanceTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = FinanceTheme.typography.caption.copy(color = colors.textSecondary)

    Canvas(modifier) {
        val maxUp = bars.maxOfOrNull { bar -> bar.parts.filter { it.value > 0 }.sumOf { it.value } } ?: 0L
        val maxDown = bars.maxOfOrNull { bar -> -bar.parts.filter { it.value < 0 }.sumOf { it.value } } ?: 0L
        val range = (maxUp + maxDown).coerceAtLeast(1L).toFloat()

        val topLabel = measurer.measure(MoneyFormatter.compact(Money(maxUp)), labelStyle)
        val zeroLabel = measurer.measure(MoneyFormatter.compact(Money.Zero), labelStyle)
        val bottomLabel = measurer.measure(MoneyFormatter.compact(Money(-maxDown)), labelStyle)
        val firstText = measurer.measure(firstLabel, labelStyle)
        val lastText = measurer.measure(lastLabel, labelStyle)

        val left = maxOf(topLabel.size.width, zeroLabel.size.width, bottomLabel.size.width) + 8.dp.toPx()
        val right = size.width
        val top = topLabel.size.height / 2f
        val bottom = size.height - firstText.size.height - 8.dp.toPx()
        val height = bottom - top
        val zeroY = top + height * (maxUp / range)

        val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
        fun gridLine(y: Float) = drawLine(colors.border, Offset(left, y), Offset(right, y), 1.dp.toPx(), pathEffect = dash)
        fun axisLabel(label: TextLayoutResult, y: Float) = drawText(label, topLeft = Offset(0f, y - label.size.height / 2f))

        gridLine(top)
        axisLabel(topLabel, top)
        gridLine(zeroY)
        // Подпись нуля прячется, если налезает на соседние.
        val labelGap = zeroLabel.size.height.toFloat()
        if (zeroY - top >= labelGap && (maxDown == 0L || bottom - zeroY >= labelGap)) axisLabel(zeroLabel, zeroY)
        if (maxDown > 0) {
            gridLine(bottom)
            axisLabel(bottomLabel, bottom)
        }

        val slot = (right - left) / bars.size.coerceAtLeast(1)
        val barWidth = slot * 0.56f
        val corner = CornerRadius(2.dp.toPx())
        bars.forEachIndexed { index, bar ->
            val x = left + slot * index + (slot - barWidth) / 2
            var upper = zeroY
            bar.parts.filter { it.value > 0 }.forEach { part ->
                val h = height * (part.value / range)
                drawRoundRect(part.color, Offset(x, upper - h), Size(barWidth, h), corner)
                upper -= h
            }
            var lower = zeroY
            bar.parts.filter { it.value < 0 }.forEach { part ->
                val h = height * (abs(part.value) / range)
                drawRoundRect(part.color, Offset(x, lower), Size(barWidth, h), corner)
                lower += h
            }
        }

        drawText(firstText, topLeft = Offset(left, size.height - firstText.size.height))
        drawText(lastText, topLeft = Offset(right - lastText.size.width, size.height - lastText.size.height))
    }
}

/** Линия с заливкой под ней — чистый капитал по дням. Шкала подстраивается под размах значений. */
@Composable
fun AreaLineChart(
    values: List<Long>,
    color: Color,
    firstLabel: String,
    lastLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = FinanceTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = FinanceTheme.typography.caption.copy(color = colors.textSecondary)

    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val firstText = measurer.measure(firstLabel, labelStyle)
        val lastText = measurer.measure(lastLabel, labelStyle)

        val inset = 6.dp.toPx()
        val top = inset
        val bottom = size.height - firstText.size.height - 8.dp.toPx()
        val low = values.min()
        val high = values.max()
        // Запас в десятую часть размаха (не меньше евро), чтобы ровная линия не легла на край.
        val padding = maxOf((high - low) / 10, 100L)
        val floor = low - padding
        val range = (high + padding - floor).toFloat()
        val stepX = if (values.size > 1) (size.width - inset * 2) / (values.size - 1) else 0f
        fun x(index: Int) = inset + stepX * index
        fun y(value: Long) = bottom - (bottom - top) * ((value - floor) / range)

        val line = Path().apply {
            values.forEachIndexed { index, value ->
                if (index == 0) moveTo(x(index), y(value)) else lineTo(x(index), y(value))
            }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(x(values.lastIndex), bottom)
            lineTo(x(0), bottom)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f)), startY = top, endY = bottom))
        drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        val end = Offset(x(values.lastIndex), y(values.last()))
        drawCircle(colors.surfaceHero, radius = 4.5.dp.toPx(), center = end)
        drawCircle(color, radius = 4.5.dp.toPx(), center = end, style = Stroke(2.dp.toPx()))

        drawText(firstText, topLeft = Offset(0f, size.height - firstText.size.height))
        drawText(lastText, topLeft = Offset(size.width - lastText.size.width, size.height - lastText.size.height))
    }
}

/** Изменение к прошлому периоду. [goodWhenUp]: рост — хорошо (доход) или плохо (расходы). */
@Composable
fun DeltaBadge(percent: Int, goodWhenUp: Boolean, modifier: Modifier = Modifier) {
    val colors = FinanceTheme.colors
    val up = percent > 0
    // Без изменений — нейтральный цвет и без стрелки.
    val tone = when {
        percent == 0 -> colors.textSecondary
        up == goodWhenUp -> colors.positiveText
        else -> colors.negativeText
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(tone.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (percent != 0) {
            Icon(
                imageVector = if (up) Icons.Rounded.NorthEast else Icons.Rounded.SouthEast,
                contentDescription = null,
                tint = tone,
                modifier = Modifier.size(14.dp),
            )
        }
        Text("${abs(percent)}%", style = FinanceTheme.typography.caption, color = tone)
    }
}
