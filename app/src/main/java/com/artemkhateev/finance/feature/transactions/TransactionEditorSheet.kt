package com.artemkhateev.finance.feature.transactions

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryKind
import com.artemkhateev.finance.data.transactionsWindowStart
import com.artemkhateev.finance.feature.categories.AllCategoriesChip
import com.artemkhateev.finance.feature.categories.CategoryPicker
import com.artemkhateev.finance.feature.categories.SuggestedCategory
import com.artemkhateev.finance.feature.categories.quickCategories
import com.artemkhateev.finance.ui.components.BoundedDatePickerDialog
import com.artemkhateev.finance.ui.components.CategoryChip
import com.artemkhateev.finance.ui.components.CenteredTextField
import com.artemkhateev.finance.ui.components.FieldLabel
import com.artemkhateev.finance.ui.components.MoneyInputField
import com.artemkhateev.finance.ui.components.PillButton
import com.artemkhateev.finance.ui.components.SegmentedControl
import com.artemkhateev.finance.ui.components.SelectablePill
import com.artemkhateev.finance.ui.format.shortDate
import com.artemkhateev.finance.ui.theme.FinanceTheme
import com.artemkhateev.finance.ui.theme.color
import kotlinx.coroutines.delay
import java.time.LocalDate

/** Сколько категорий видно в форме сразу; остальные — в полном списке. */
private const val QUICK_CATEGORY_LIMIT = 12

/** Шторка добавления и правки транзакции: крупная сумма, мерчант и заметка — как в карточке транзакции референса. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionEditorSheet(
    draft: TransactionDraft,
    categories: List<Category>,
    accounts: List<Account>,
    onChange: ((TransactionDraft) -> TransactionDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    /** Сколько транзакций у каждой категории: частые видны в форме первыми. */
    categoryUsage: Map<String, Int> = emptyMap(),
    /** Заводит категорию из каталога и возвращает её id. */
    onCreateCategory: (SuggestedCategory) -> String = { "" },
) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    val today = remember { LocalDate.now() }
    val editing = draft.id.isNotBlank()
    var pickingDate by remember { mutableStateOf(false) }
    var pickingCategory by remember { mutableStateOf(false) }
    // Прокрутка формы переживает полный список категорий: после выбора форма остаётся на том же месте.
    val formScroll = rememberScrollState()
    var confirmDelete by remember(draft.id) { mutableStateOf(false) }
    val wantedKind = if (draft.kind == EntryKind.Income) CategoryKind.Income else CategoryKind.Expense

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

        // Полный список категорий открывается в той же шторке: две шторки сразу не показываем.
        if (pickingCategory) {
            CategoryPicker(
                kind = wantedKind,
                categories = categories,
                selectedId = draft.categoryId,
                onPick = { id ->
                    onChange { it.copy(categoryId = id) }
                    pickingCategory = false
                },
                onCreate = { suggestion ->
                    val id = onCreateCategory(suggestion)
                    onChange { it.copy(categoryId = id) }
                    pickingCategory = false
                },
                onBack = { pickingCategory = false },
            )
            return@ModalBottomSheet
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(formScroll)
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
            MoneyInputField(
                text = draft.amountText,
                onValueChange = { text -> onChange { it.copy(amountText = text) } },
                textStyle = typography.heroAmount.copy(
                    fontSize = 40.sp,
                    color = if (draft.kind == EntryKind.Income) colors.positiveText else colors.textPrimary,
                ),
                sign = if (draft.kind == EntryKind.Income) "+" else "-",
                modifier = Modifier.focusRequester(amountFocus),
            )
            CenteredTextField(
                value = draft.merchant,
                placeholder = "Merchant",
                style = typography.cardTitle.copy(fontSize = 22.sp, color = colors.textPrimary),
                onValueChange = { value -> onChange { it.copy(merchant = value) } },
            )
            CenteredTextField(
                value = draft.note,
                placeholder = "Add a note",
                style = typography.bodySecondary.copy(color = colors.textSecondary),
                onValueChange = { value -> onChange { it.copy(note = value) } },
                imeAction = ImeAction.Done,
            )

            FieldLabel("Category")
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                quickCategories(categories, wantedKind, categoryUsage, draft.categoryId, QUICK_CATEGORY_LIMIT).forEach { category ->
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
                AllCategoriesChip(onClick = { pickingCategory = true }, modifier = Modifier.fillMaxRowHeight())
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
                    text = if (otherDate) shortDate(draft.date) else "Other date…",
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
        BoundedDatePickerDialog(
            initial = draft.date,
            // Раньше окна новую транзакцию не заводят, но открытую из длинного периода правят как есть.
            earliest = minOf(transactionsWindowStart(today), draft.date),
            latest = today,
            onPick = { picked ->
                onChange { it.copy(date = picked) }
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
}
