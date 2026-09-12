package com.artemkhateev.finance.feature.investments

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.AssetClass
import com.artemkhateev.finance.ui.components.CenteredTextField
import com.artemkhateev.finance.ui.components.FieldLabel
import com.artemkhateev.finance.ui.components.MoneyInputField
import com.artemkhateev.finance.ui.components.PillButton
import com.artemkhateev.finance.ui.components.SelectablePill
import com.artemkhateev.finance.ui.components.appendMoney
import com.artemkhateev.finance.ui.format.SignStyle
import com.artemkhateev.finance.ui.format.percentText
import com.artemkhateev.finance.ui.format.sanitizeQuantityInput
import com.artemkhateev.finance.ui.theme.FinanceTheme
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoldingEditorSheet(
    draft: HoldingDraft,
    accounts: List<Account>,
    onChange: ((HoldingDraft) -> HoldingDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    val editing = draft.id.isNotBlank()
    val problem = draft.problem()
    var confirmDelete by remember(draft.id) { mutableStateOf(false) }

    // Инвестиционные счета могут прийти позже, чем открылась форма: подставляем первый.
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
                text = (if (editing) "Edit holding" else "New holding").uppercase(),
                style = typography.sectionLabel,
                color = colors.sectionLabel,
            )
            LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AssetClass.entries) { assetClass ->
                    SelectablePill(
                        text = assetClass.pillLabel(),
                        selected = assetClass == draft.assetClass,
                        onClick = { onChange { it.copy(assetClass = assetClass) } },
                    )
                }
            }
            CenteredTextField(
                value = draft.symbol,
                placeholder = "Ticker",
                style = typography.heroAmount.copy(color = draft.assetClass.color()),
                // В тикерах бывают точка и дефис: BRK.B, BTC-EUR.
                onValueChange = { value ->
                    onChange { it.copy(symbol = value.uppercase().filter { c -> c.isLetterOrDigit() || c == '.' || c == '-' }.take(12)) }
                },
                capitalization = KeyboardCapitalization.Characters,
            )
            CenteredTextField(
                value = draft.name,
                placeholder = "Name",
                style = typography.bodySecondary.copy(color = colors.textSecondary),
                onValueChange = { value -> onChange { it.copy(name = value) } },
                capitalization = KeyboardCapitalization.Words,
            )

            FieldLabel("Quantity")
            CenteredTextField(
                value = draft.quantityText,
                placeholder = "0",
                style = typography.heroAmount.copy(fontSize = 28.sp, color = colors.textPrimary),
                onValueChange = { value -> onChange { it.copy(quantityText = sanitizeQuantityInput(value)) } },
                keyboardType = KeyboardType.Decimal,
            )
            FieldLabel("Current price")
            MoneyInputField(
                text = draft.priceText,
                onValueChange = { text -> onChange { it.copy(priceText = text) } },
                textStyle = typography.heroAmount.copy(fontSize = 28.sp, color = colors.textPrimary),
            )
            FieldLabel("Average cost")
            MoneyInputField(
                text = draft.costText,
                onValueChange = { text -> onChange { it.copy(costText = text) } },
                textStyle = typography.heroAmount.copy(fontSize = 22.sp, color = colors.textSecondary),
                emptyHint = "Same as price",
                prefixWhenEmpty = false,
                imeAction = ImeAction.Done,
            )

            FieldLabel("Account")
            if (accounts.isEmpty()) {
                Text(
                    text = "A Brokerage account will be created for it",
                    style = typography.caption,
                    color = colors.textSecondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(accounts, key = { it.id }) { account ->
                        SelectablePill(
                            text = account.name,
                            selected = account.id == draft.accountId,
                            onClick = { onChange { it.copy(accountId = account.id) } },
                        )
                    }
                }
            }

            val previewValue = draft.previewValue
            if (previewValue != null) {
                val gain = draft.previewGain
                Text(
                    text = buildAnnotatedString {
                        append("Value ")
                        appendMoney(previewValue, typography.bodySecondary.fontSize)
                        if (gain != null && gain.minor != 0L) {
                            append(" · ")
                            withStyle(SpanStyle(color = if (gain.minor > 0) colors.positiveText else colors.negativeText)) {
                                appendMoney(gain, typography.bodySecondary.fontSize, sign = SignStyle.Always)
                                val tenths = draft.previewGainTenths
                                if (tenths != null) append(" (${percentText(abs(tenths), signed = false)})")
                            }
                        }
                    },
                    style = typography.bodySecondary,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            PillButton(
                text = if (editing) "Save changes" else "Add holding",
                onClick = onSave,
                enabled = problem == null,
                filled = true,
                modifier = Modifier.padding(top = 8.dp),
            )
            // Пока тикер пуст, форма просто не начата — ошибку не показываем.
            if (problem != null && draft.symbol.isNotBlank()) {
                Text(problem, style = typography.bodySecondary, color = colors.negativeText, textAlign = TextAlign.Center)
            }
            if (editing) {
                Text(
                    text = if (confirmDelete) "Tap again to delete" else "Delete holding",
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
}
