package com.artemkhateev.finance.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.artemkhateev.finance.ui.theme.FinanceTheme

/**
 * Линия расходов месяца: накопленная сумма по дням против ожидаемого темпа
 * (пунктир на весь месяц). Подпись над текущей точкой — запас или перерасход.
 */
@Composable
fun SpendingLineChart(
    dailyCumulative: List<Long>,
    pace: List<Long>,
    label: AnnotatedString,
    labelColor: Color,
    lineColors: List<Color>,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = FinanceTheme.typography.caption,
) {
    val colors = FinanceTheme.colors
    val measurer = rememberTextMeasurer()

    Canvas(modifier) {
        val labelLayout = measurer.measure(label, labelStyle.copy(color = Color.White))
        val padH = 6.dp.toPx()
        val padV = 2.dp.toPx()
        val labelSize = Size(labelLayout.size.width + padH * 2, labelLayout.size.height + padV * 2)
        val pointer = 5.dp.toPx()

        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = labelSize.height + pointer + 14.dp.toPx()
        val bottom = size.height - 6.dp.toPx()
        val maxValue = maxOf(pace.maxOrNull() ?: 0L, dailyCumulative.maxOrNull() ?: 0L, 1L).toFloat()
        val lastDay = (pace.size - 1).coerceAtLeast(1)
        fun x(dayIndex: Int) = left + (right - left) * dayIndex / lastDay
        fun y(value: Long) = bottom - (bottom - top) * (value / maxValue)
        fun polyline(values: List<Long>) = Path().apply {
            values.forEachIndexed { day, value ->
                if (day == 0) moveTo(x(day), y(value)) else lineTo(x(day), y(value))
            }
        }

        drawPath(
            path = polyline(pace),
            color = colors.chartPace,
            style = Stroke(
                width = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())),
            ),
        )
        if (dailyCumulative.isEmpty()) return@Canvas

        val end = Offset(x(dailyCumulative.lastIndex), y(dailyCumulative.last()))
        drawPath(
            path = polyline(dailyCumulative),
            brush = Brush.horizontalGradient(lineColors, startX = left, endX = maxOf(end.x, left + 1f)),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(colors.surfaceHero, radius = 5.dp.toPx(), center = end)
        drawCircle(lineColors.last(), radius = 5.dp.toPx(), center = end, style = Stroke(2.dp.toPx()))

        val labelTopLeft = Offset(
            x = (end.x - labelSize.width / 2).coerceIn(0f, size.width - labelSize.width),
            y = end.y - 9.dp.toPx() - pointer - labelSize.height,
        )
        drawRoundRect(labelColor, labelTopLeft, labelSize, CornerRadius(4.dp.toPx()))
        val tip = Path().apply {
            val baseY = labelTopLeft.y + labelSize.height
            moveTo(end.x - pointer, baseY)
            lineTo(end.x + pointer, baseY)
            lineTo(end.x, baseY + pointer)
            close()
        }
        drawPath(tip, labelColor)
        drawText(labelLayout, topLeft = Offset(labelTopLeft.x + padH, labelTopLeft.y + padV))
    }
}
