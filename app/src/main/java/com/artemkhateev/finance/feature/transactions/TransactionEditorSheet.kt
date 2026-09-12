package com.artemkhateev.finance.feature.transactions

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.unit.sp
import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryKind
import com.artemkhateev.finance.data.transactionsWindowStart
import com.artemkhateev.finance.ui.components.CategoryChip
import com.artemkhateev.finance.ui.components.PillButton
import com.artemkhateev.finance.ui.components.SegmentedControl
import com.artemkhateev.finance.ui.components.SelectablePill
import com.artemkhateev.finance.ui.components.currencySymbolStyle
import com.artemkhateev.finance.ui.format.MoneyFormatter
import com.artemkhateev.finance.ui.theme.FinanceTheme
import com.artemkhateev.finance.ui.theme.color
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shortDate = DateTimeFormatter.ofPattern("MMM d", Locale.US)

/** Шторка добавления и правки транзакции: крупная сумма, мерчант и заметка — как в карточке транзакции референса. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorSheet(
    draft: TransactionDraft,
    categories: List<Category>,
    accounts: List<Account>,
    onChange: ((TransactionDraft) -> TransactionDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    val today = remember { LocalDate.now() }
    val editing = draft.id.isNotBlank()
    var pickingDate by remember { mutableStateOf(false) }
    var confirmDelete by remember(draft.id) { mutableStateOf(false) }

    // На первом входе счета могут прийти позже, чем открылась форма: подставляем первый.
    LaunchedEffect(accounts, draft.accountId) {
        if (draft.accountId == null && accounts.isNotEmpty()) {
            onChange { it.copy(accountId = accounts.first().id) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
    ) {
        val amountFocus = remember { FocusRequester() }
        if (!editing) {
            // Новую транзакцию начинают с суммы. Шторка ещё выезжает, поэтому фокус — после кадра-другого.
            LaunchedEffect(Unit) {
                delay(150)
                runCatching { amountFocus.requestFocus() }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = (if (editing) "Edit transaction" else "New transaction").uppercase(),
                style = typography.sectionLabel,
                color = colors.sectionLabel,
            )
            SegmentedControl(
                options = listOf("Expense", "Income"),
                selectedIndex = draft.kind.ordinal,
                onSelect = { index ->
                    val kind = EntryKind.entries[index]
                    // Категории у расходов и доходов разные — выбранная больше не подходит.
                    if (kind != draft.kind) onChange { it.copy(kind = kind, categoryId = null) }
                },
            )
            AmountField(
                text = draft.amountText,
                kind = draft.kind,
                onValueChange = { text -> onChange { it.copy(amountText = sanitizeAmountInput(text)) } },
                modifier = Modifier.focusRequester(amountFocus),
            )
            CenteredField(
                value = draft.merchant,
                placeholder = "Merchant",
                style = typography.cardTitle.copy(fontSize = 22.sp, color = colors.textPrimary),
                onValueChange = { value -> onChange { it.copy(merchant = value) } },
            )
            CenteredField(
                value = draft.note,
                placeholder = "Add a note",
                style = typography.bodySecondary.copy(color = colors.textSecondary),
                onValueChange = { value -> onChange { it.copy(note = value) } },
                imeAction = ImeAction.Done,
            )

            FieldLabel("Category")
            val wantedKind = if (draft.kind == EntryKind.Income) CategoryKind.Income else CategoryKind.Expense
            LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories.filter { it.kind == wantedKind }, key = { it.id }) { category ->
                    val selected = category.id == draft.categoryId
                    CategoryChip(
                        category = category,
                        modifier = Modifier
                            .alpha(if (selected || draft.categoryId == null) 1f else 0.45f)
                            .clip(CircleShape)
                            .then(if (selected) Modifier.border(1.5.dp, category.tone.color(), CircleShape) else Modifier)
                            .clickable { onChange { it.copy(categoryId = if (selected) null else category.id) } },
                    )
                }
            }

            FieldLabel("Account")
            LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(accounts, key = { it.id }) { account ->
                    SelectablePill(
                        text = account.name,
                        selected = account.id == draft.accountId,
                        onClick = { onChange { it.copy(accountId = account.id) } },
                    )
                }
            }

            FieldLabel("Date")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val yesterday = today.minusDays(1)
                val otherDate = draft.date != today && draft.date != yesterday
                SelectablePill("Today", draft.date == today, onClick = { onChange { it.copy(date = today) } })
                SelectablePill("Yesterday", draft.date == yesterday, onClick = { onChange { it.copy(date = yesterday) } })
                SelectablePill(
                    text = if (otherDate) draft.date.format(shortDate) else "Other date…",
                    selected = otherDate,
                    onClick = { pickingDate = true },
                )
            }

            PillButton(
                text = if (editing) "Save changes" else "Add transaction",
                onClick = onSave,
                enabled = draft.isValid,
                filled = true,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (editing) {
                Text(
                    text = if (confirmDelete) "Tap again to delete" else "Delete transaction",
                    style = typography.bodySecondary,
                    color = colors.negativeText,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            if (confirmDelete) {
                                onDelete()
                            } else {
                                confirmDelete = true
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }

    if (pickingDate) {
        TransactionDatePicker(
            initial = draft.date,
            earliest = transactionsWindowStart(today),
            latest = today,
            onPick = { picked ->
                onChange { it.copy(date = picked) }
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
}

@Composable
private fun AmountField(
    text: String,
    kind: EntryKind,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FinanceTheme.colors
    val style = FinanceTheme.typography.heroAmount.copy(
        fontSize = 40.sp,
        color = if (kind == EntryKind.Income) colors.positiveText else colors.textPrimary,
        textAlign = TextAlign.Center,
    )
    val transformation = remember(kind, style.fontSize, colors.textInactive) {
        AmountTransformation(
            sign = if (kind == EntryKind.Income) "+" else "-",
            symbolStyle = currencySymbolStyle(style.fontSize),
            placeholderColor = colors.textInactive,
        )
    }
    BasicTextField(
        value = text,
        onValueChange = onValueChange,
        textStyle = style,
        singleLine = true,
        cursorBrush = SolidColor(colors.accent),
        visualTransformation = transformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Знак, символ валюты и подсказка «0.00» рисуются внутри поля, а не рядом с ним:
 * однострочное поле занимает всю ширину, и только так сумма остаётся по центру.
 */
private class AmountTransformation(
    private val sign: String,
    private val symbolStyle: SpanStyle,
    private val placeholderColor: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val prefix = buildAnnotatedString {
            append(sign)
            withStyle(symbolStyle) { append(MoneyFormatter.CURRENCY_SYMBOL) }
        }
        val body = if (text.isEmpty()) AnnotatedString("0.00", SpanStyle(color = placeholderColor)) else text
        val originalLength = text.length
        return TransformedText(
            prefix + body,
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = offset + prefix.length
                override fun transformedToOriginal(offset: Int) = (offset - prefix.length).coerceIn(0, originalLength)
            },
        )
    }
}

@Composable
private fun CenteredField(
    value: String,
    placeholder: String,
    style: TextStyle,
    onValueChange: (String) -> Unit,
    imeAction: ImeAction = ImeAction.Next,
) {
    val colors = FinanceTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style.copy(textAlign = TextAlign.Center),
        singleLine = true,
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = imeAction),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = style.copy(color = colors.textInactive, textAlign = TextAlign.Center),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = FinanceTheme.typography.sectionLabel,
        color = FinanceTheme.colors.sectionLabel,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDatePicker(
    initial: LocalDate,
    earliest: LocalDate,
    latest: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toUtcMillis(),
        yearRange = earliest.year..latest.year,
        selectableDates = object : SelectableDates {
            // Раньше окна экраны транзакцию не покажут, а в будущем расходов ещё нет.
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = utcTimeMillis.toUtcDate()
                return !date.isBefore(earliest) && !date.isAfter(latest)
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(it.toUtcDate()) } ?: onDismiss() }) {
                Text("OK")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

// DatePicker работает с полночью по UTC, а даты транзакций — без часового пояса.
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
