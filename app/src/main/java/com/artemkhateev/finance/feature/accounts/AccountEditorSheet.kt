package com.artemkhateev.finance.feature.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.AccountType
import com.artemkhateev.finance.ui.components.CenteredTextField
import com.artemkhateev.finance.ui.components.FieldLabel
import com.artemkhateev.finance.ui.components.MoneyInputField
import com.artemkhateev.finance.ui.components.PillButton
import com.artemkhateev.finance.ui.components.SelectablePill
import com.artemkhateev.finance.ui.theme.FinanceTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditorSheet(
    draft: AccountDraft,
    existing: List<Account>,
    onChange: ((AccountDraft) -> AccountDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    /** Стоимость счёта считается по его позициям — остаток вручную не вводится. */
    valuedByHoldings: Boolean = false,
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
                text = (if (editing) "Edit account" else "New account").uppercase(),
                style = typography.sectionLabel,
                color = colors.sectionLabel,
            )
            LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AccountType.entries) { type ->
                    SelectablePill(
                        text = type.label(),
                        selected = type == draft.type,
                        onClick = { onChange { it.copy(type = type) } },
                    )
                }
            }
            Text(draft.type.emoji(), fontSize = 40.sp)
            CenteredTextField(
                value = draft.name,
                placeholder = "Account name",
                style = typography.heroAmount.copy(color = colors.textPrimary),
                onValueChange = { value -> onChange { it.copy(name = value) } },
                capitalization = KeyboardCapitalization.Words,
            )
            CenteredTextField(
                value = draft.institution,
                placeholder = "Bank or institution",
                style = typography.bodySecondary.copy(color = colors.textSecondary),
                onValueChange = { value -> onChange { it.copy(institution = value) } },
                capitalization = KeyboardCapitalization.Words,
            )

            if (valuedByHoldings) {
                FieldLabel("Value")
                Text(
                    text = "Comes from its holdings. Update prices and quantities in Investments",
                    style = typography.bodySecondary,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            } else {
                FieldLabel(if (draft.type == AccountType.CreditCard) "Amount owed" else "Current balance")
                MoneyInputField(
                    text = draft.balanceText,
                    onValueChange = { text -> onChange { it.copy(balanceText = text) } },
                    textStyle = typography.heroAmount.copy(fontSize = 32.sp, color = colors.textPrimary),
                )
                Text(
                    text = "Transactions you add by hand change this balance",
                    style = typography.caption,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            FieldLabel("Last 4 digits")
            CenteredTextField(
                value = draft.mask,
                placeholder = "Optional",
                style = typography.body.copy(color = colors.textPrimary),
                // Банки показывают последние четыре цифры — больше хранить незачем.
                onValueChange = { value -> onChange { it.copy(mask = value.filter(Char::isDigit).takeLast(4)) } },
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            )

            PillButton(
                text = if (editing) "Save changes" else "Add account",
                onClick = onSave,
                enabled = problem == null,
                filled = true,
                modifier = Modifier.padding(top = 8.dp),
            )
            // Пустое имя видно и так; объясняем только неочевидное — занятое имя.
            if (problem != null && draft.name.isNotBlank()) {
                Text(problem, style = typography.bodySecondary, color = colors.negativeText, textAlign = TextAlign.Center)
            }
            if (editing) {
                Text(
                    text = if (confirmDelete) "Tap again to delete" else "Delete account",
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
                        text = if (valuedByHoldings) {
                            "Its holdings are deleted too. Transactions stay but no longer count toward net worth"
                        } else {
                            "Its transactions stay but no longer count toward net worth"
                        },
                        style = typography.caption,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

internal fun AccountType.label(): String = when (this) {
    AccountType.Checking -> "Checking"
    AccountType.Savings -> "Savings"
    AccountType.CreditCard -> "Credit card"
    AccountType.Investment -> "Investment"
}

internal fun AccountType.emoji(): String = when (this) {
    AccountType.Checking -> "🏦"
    AccountType.Savings -> "🐷"
    AccountType.CreditCard -> "💳"
    AccountType.Investment -> "📈"
}
