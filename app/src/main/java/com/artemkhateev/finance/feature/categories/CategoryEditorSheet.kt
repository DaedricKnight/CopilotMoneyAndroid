package com.artemkhateev.finance.feature.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryKind
import com.artemkhateev.finance.data.model.CategoryTone
import com.artemkhateev.finance.ui.components.CenteredTextField
import com.artemkhateev.finance.ui.components.FieldLabel
import com.artemkhateev.finance.ui.components.MoneyInputField
import com.artemkhateev.finance.ui.components.PillButton
import com.artemkhateev.finance.ui.components.SegmentedControl
import com.artemkhateev.finance.ui.format.lastGrapheme
import com.artemkhateev.finance.ui.theme.FinanceTheme
import com.artemkhateev.finance.ui.theme.color

/** Эмодзи, которые чаще всего нужны категориям: набирать их с клавиатуры дольше. */
private val EmojiSuggestions = listOf(
    "🛒", "🍔", "☕", "🏠", "🚗", "⛽", "🚌", "✈️", "💡", "📱", "💳", "🎁",
    "🐶", "👶", "💊", "🏋️", "🎮", "📚", "👕", "💇", "🍷", "🎟️", "💰", "📈",
)

/** Шторка категории: эмодзи и название в цвете категории — как в карточке категории референса. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditorSheet(
    draft: CategoryDraft,
    existing: List<Category>,
    onChange: ((CategoryDraft) -> CategoryDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    val editing = draft.id.isNotBlank()
    val problem = draft.problem(existing)
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
                text = (if (editing) "Edit category" else "New category").uppercase(),
                style = typography.sectionLabel,
                color = colors.sectionLabel,
            )
            SegmentedControl(
                options = listOf("Expense", "Income"),
                selectedIndex = draft.kind.ordinal,
                onSelect = { index -> onChange { it.copy(kind = CategoryKind.entries[index]) } },
            )
            CenteredTextField(
                value = draft.emoji,
                placeholder = "🙂",
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
                placeholder = "Category name",
                style = typography.heroAmount.copy(color = draft.tone.color()),
                onValueChange = { value -> onChange { it.copy(name = value) } },
                capitalization = KeyboardCapitalization.Words,
            )

            FieldLabel("Color")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CategoryTone.entries.forEach { tone ->
                    val selected = tone == draft.tone
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(tone.color())
                            .then(if (selected) Modifier.border(2.dp, colors.textPrimary, CircleShape) else Modifier)
                            .clickable { onChange { it.copy(tone = tone) } },
                    )
                }
            }

            if (draft.kind == CategoryKind.Expense) {
                FieldLabel("Monthly budget")
                MoneyInputField(
                    text = draft.budgetText,
                    onValueChange = { text -> onChange { it.copy(budgetText = text) } },
                    textStyle = typography.heroAmount.copy(fontSize = 32.sp, color = colors.textPrimary),
                    emptyHint = "No budget",
                    prefixWhenEmpty = false,
                    imeAction = ImeAction.Done,
                )
            }

            PillButton(
                text = if (editing) "Save changes" else "Add category",
                onClick = onSave,
                enabled = problem == null,
                filled = true,
                modifier = Modifier.padding(top = 8.dp),
            )
            // Пустые поля видны и так; объясняем только неочевидное — дубликат имени и неверный бюджет.
            if (problem != null && draft.name.isNotBlank() && draft.emoji.isNotBlank()) {
                Text(problem, style = typography.bodySecondary, color = colors.negativeText, textAlign = TextAlign.Center)
            }
            if (editing) {
                Text(
                    text = if (confirmDelete) "Tap again to delete" else "Delete category",
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
                        text = "Its transactions stay and become uncategorized",
                        style = typography.caption,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
