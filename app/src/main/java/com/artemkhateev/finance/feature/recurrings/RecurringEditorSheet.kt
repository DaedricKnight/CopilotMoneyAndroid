package com.artemkhateev.finance.feature.recurrings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.ui.components.CategoryChip
import com.artemkhateev.finance.ui.components.CenteredTextField
import com.artemkhateev.finance.ui.components.FieldLabel
import com.artemkhateev.finance.ui.components.MoneyInputField
import com.artemkhateev.finance.ui.components.PillButton
import com.artemkhateev.finance.ui.format.lastGrapheme
import com.artemkhateev.finance.ui.format.ordinalDay
import com.artemkhateev.finance.ui.theme.FinanceTheme
import com.artemkhateev.finance.ui.theme.color

/** Эмодзи, которые чаще всего нужны счетам и подпискам: набирать их с клавиатуры дольше. */
private val EmojiSuggestions = listOf(
    "🏠", "🔑", "💡", "⚡", "🔥", "🚰", "📶", "📱", "📺", "🎧", "☁️", "🎮",
    "🏋️", "🚙", "☂️", "🏥", "🎓", "👶", "🐶", "💳", "🧾", "🔁",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringEditorSheet(
    draft: RecurringDraft,
    /** Категории расходов. */
    categories: List<Category>,
    onChange: ((RecurringDraft) -> RecurringDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    val editing = draft.id.isNotBlank()
    val problem = draft.problem()
    var confirmDelete by remember(draft.id) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = (if (editing) "Edit recurring" else "New recurring").uppercase(),
                style = typography.sectionLabel,
                color = colors.sectionLabel,
            )
            CenteredTextField(
                value = draft.emoji,
                placeholder = "🔁",
                style = TextStyle(fontSize = 44.sp, color = colors.textPrimary),
                onValueChange = { value -> onChange { it.copy(emoji = lastGrapheme(value)) } },
                capitalization = KeyboardCapitalization.None,
                placeholderAlpha = 0.35f,
            )
            LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                items(EmojiSuggestions) { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 22.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onChange { it.copy(emoji = emoji) } }
                            .padding(8.dp),
                    )
                }
            }
            CenteredTextField(
                value = draft.name,
                placeholder = "Payment name",
                style = typography.heroAmount.copy(color = colors.textPrimary),
                onValueChange = { value -> onChange { it.copy(name = value) } },
                capitalization = KeyboardCapitalization.Words,
            )

            FieldLabel("Amount")
            MoneyInputField(
                text = draft.amountText,
                onValueChange = { text -> onChange { it.copy(amountText = text) } },
                textStyle = typography.heroAmount.copy(fontSize = 32.sp, color = colors.textPrimary),
                imeAction = ImeAction.Done,
            )

            FieldLabel("Day of month")
            DayOfMonthGrid(selected = draft.dayOfMonth, onSelect = { day -> onChange { it.copy(dayOfMonth = day) } })
            draft.dayOfMonth?.let { day ->
                Text(
                    text = if (day > 28) {
                        "Every month on the ${ordinalDay(day)}, or on the last day of shorter months"
                    } else {
                        "Every month on the ${ordinalDay(day)}"
                    },
                    style = typography.caption,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            FieldLabel("Category")
            // Категория могла быть удалена — тогда платёж показываем как без категории.
            val selectedKnown = categories.any { it.id == draft.categoryId }
            LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories, key = { it.id }) { category ->
                    val selected = category.id == draft.categoryId
                    CategoryChip(
                        category = category,
                        modifier = Modifier
                            .alpha(if (selected || !selectedKnown) 1f else 0.45f)
                            .clip(CircleShape)
                            .then(if (selected) Modifier.border(1.5.dp, category.tone.color(), CircleShape) else Modifier)
                            .clickable { onChange { it.copy(categoryId = if (selected) null else category.id) } },
                    )
                }
            }
            Text(
                text = "Marked paid when this month has an expense with the same name, or the same amount in this category",
                style = typography.caption,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )

            PillButton(
                text = if (editing) "Save changes" else "Add payment",
                onClick = onSave,
                enabled = problem == null,
                filled = true,
                modifier = Modifier.padding(top = 8.dp),
            )
            // Пустые поля видны и так; объясняем только неверную сумму.
            if (problem != null && draft.name.isNotBlank() && draft.amountText.isNotBlank()) {
                Text(problem, style = typography.bodySecondary, color = colors.negativeText, textAlign = TextAlign.Center)
            }
            if (editing) {
                Text(
                    text = if (confirmDelete) "Tap again to delete" else "Delete payment",
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
                if (confirmDelete) {
                    Text(
                        text = "Transactions you already have stay as they are",
                        style = typography.caption,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Дни месяца неделями по семь, как в календаре, но без дней недели: платёж привязан к числу. */
@Composable
private fun DayOfMonthGrid(selected: Int?, onSelect: (Int) -> Unit) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..31).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    val isSelected = day == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) colors.accent else Color.Transparent)
                            .clickable { onSelect(day) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = day.toString(),
                            style = typography.bodySecondary,
                            color = if (isSelected) colors.background else colors.textPrimary,
                        )
                    }
                }
                // Неполная последняя неделя: дни сохраняют ширину седьмой части.
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
