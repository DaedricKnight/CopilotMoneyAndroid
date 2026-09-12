package com.artemkhateev.finance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.artemkhateev.finance.ui.format.MoneyFormatter
import com.artemkhateev.finance.ui.format.sanitizeAmountInput
import com.artemkhateev.finance.ui.theme.FinanceTheme

/** Подпись поля формы — в стиле заголовков секций. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = FinanceTheme.typography.sectionLabel,
        color = FinanceTheme.colors.sectionLabel,
        modifier = modifier.fillMaxWidth().padding(top = 6.dp),
    )
}

/** Однострочное поле без рамки с текстом по центру: мерчант, заметка, названия, эмодзи. */
@Composable
fun CenteredTextField(
    value: String,
    placeholder: String,
    style: TextStyle,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    keyboardType: KeyboardType = KeyboardType.Text,
    // Цветной эмодзи не красится цветом текста — подсказку-эмодзи приглушает прозрачность.
    placeholderAlpha: Float = 1f,
) {
    val colors = FinanceTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style.copy(textAlign = TextAlign.Center),
        singleLine = true,
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = style.copy(color = colors.textInactive, textAlign = TextAlign.Center),
                        modifier = Modifier.fillMaxWidth().alpha(placeholderAlpha),
                    )
                }
                innerTextField()
            }
        },
    )
}

/**
 * Поле суммы по центру. Префикс (знак и символ валюты) и подсказка рисуются внутри поля:
 * однострочное поле занимает всю ширину, и рядом с ним сумму не отцентровать.
 */
@Composable
fun MoneyInputField(
    text: String,
    onValueChange: (String) -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    sign: String = "",
    emptyHint: String = "0.00",
    /** Показывать ли знак и символ валюты перед подсказкой, когда поле пустое. */
    prefixWhenEmpty: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
) {
    val colors = FinanceTheme.colors
    val transformation = remember(sign, emptyHint, prefixWhenEmpty, textStyle.fontSize, colors.textInactive) {
        MoneyInputTransformation(
            sign = sign,
            symbolStyle = currencySymbolStyle(textStyle.fontSize),
            emptyHint = emptyHint,
            prefixWhenEmpty = prefixWhenEmpty,
            hintColor = colors.textInactive,
        )
    }
    BasicTextField(
        value = text,
        onValueChange = { onValueChange(sanitizeAmountInput(it)) },
        textStyle = textStyle.copy(textAlign = TextAlign.Center),
        singleLine = true,
        cursorBrush = SolidColor(colors.accent),
        visualTransformation = transformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        modifier = modifier.fillMaxWidth(),
    )
}

private class MoneyInputTransformation(
    private val sign: String,
    private val symbolStyle: SpanStyle,
    private val emptyHint: String,
    private val prefixWhenEmpty: Boolean,
    private val hintColor: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val prefix = buildAnnotatedString {
            append(sign)
            withStyle(symbolStyle) { append(MoneyFormatter.CURRENCY_SYMBOL) }
        }
        if (text.isEmpty()) {
            val hint = AnnotatedString(emptyHint, SpanStyle(color = hintColor))
            val cursor = if (prefixWhenEmpty) prefix.length else 0
            return TransformedText(
                if (prefixWhenEmpty) prefix + hint else hint,
                object : OffsetMapping {
                    override fun originalToTransformed(offset: Int) = cursor
                    override fun transformedToOriginal(offset: Int) = 0
                },
            )
        }
        return TransformedText(
            prefix + text,
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = offset + prefix.length
                override fun transformedToOriginal(offset: Int) = (offset - prefix.length).coerceIn(0, text.length)
            },
        )
    }
}

/** Круглая кнопка «+» в правом нижнем углу экрана. */
@Composable
fun RoundAddButton(contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FinanceTheme.colors
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(colors.accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = contentDescription,
            tint = colors.background,
            modifier = Modifier.size(28.dp),
        )
    }
}
