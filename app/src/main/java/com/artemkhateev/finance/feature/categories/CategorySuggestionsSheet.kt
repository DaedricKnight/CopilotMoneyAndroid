package com.artemkhateev.finance.feature.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.ui.components.FieldLabel
import com.artemkhateev.finance.ui.components.FinanceCard
import com.artemkhateev.finance.ui.components.PillButton
import com.artemkhateev.finance.ui.theme.FinanceTheme
import com.artemkhateev.finance.ui.theme.TONE_BACKGROUND_ALPHA
import com.artemkhateev.finance.ui.theme.color

/** Каталог готовых категорий по группам: отмеченные добавляются одним нажатием. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategorySuggestionsSheet(
    existing: List<Category>,
    onAdd: (List<SuggestedCategory>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    val suggestions = remember(existing) { categorySuggestions(existing) }
    var selected by remember(existing) {
        mutableStateOf(suggestions.filter { it.selectedByDefault }.map { it.suggestion.name }.toSet())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "SUGGESTED CATEGORIES",
                style = typography.sectionLabel,
                color = colors.sectionLabel,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                text = "Pick what you need. Ones that look like categories you already have start unticked.",
                style = typography.caption,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextAction("Select all") { selected = suggestions.filterNot { it.added }.map { it.suggestion.name }.toSet() }
                TextAction("Clear") { selected = emptySet() }
            }
            PillButton(
                text = when (selected.size) {
                    0 -> "Pick categories"
                    1 -> "Add 1 category"
                    else -> "Add ${selected.size} categories"
                },
                onClick = { onAdd(suggestions.filter { it.suggestion.name in selected }.map { it.suggestion }) },
                enabled = selected.isNotEmpty(),
                filled = true,
            )
            suggestions.groupBy { it.suggestion.group }.forEach { (group, items) ->
                FieldLabel(group)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items.forEach { item ->
                        val name = item.suggestion.name
                        SuggestionChip(
                            item = item,
                            selected = name in selected,
                            onToggle = { selected = if (name in selected) selected - name else selected + name },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionChip(item: CategorySuggestionUi, selected: Boolean, onToggle: () -> Unit) {
    val colors = FinanceTheme.colors
    val tone = item.suggestion.tone.color()
    Row(
        modifier = Modifier
            .alpha(if (item.added) 0.45f else 1f)
            .clip(CircleShape)
            .background(if (selected) tone.copy(alpha = TONE_BACKGROUND_ALPHA) else Color.Transparent)
            .border(1.dp, if (selected) tone else colors.border, CircleShape)
            .clickable(enabled = !item.added, onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(item.suggestion.emoji, fontSize = 14.sp)
        Text(
            text = if (item.added) "${item.suggestion.name} ✓" else item.suggestion.name,
            style = FinanceTheme.typography.bodySecondary,
            // Отмеченный — светлым текстом: у серых категорий цвет текста не отличил бы выбор.
            color = if (selected) colors.textPrimary else colors.textSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = FinanceTheme.typography.bodySecondary,
        color = FinanceTheme.colors.accent,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Пустой экран категорий: сразу предлагает готовый набор. */
@Composable
fun CategoriesEmptyState(onBrowse: () -> Unit) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FinanceCard(
            hero = true,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 40.dp),
        ) {
            Text("No categories yet", style = typography.cardTitle, color = colors.textPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Pick from suggested categories or tap + to create your own.",
                style = typography.bodySecondary,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        PillButton(text = "Browse suggested categories", onClick = onBrowse, filled = true)
    }
}

/** Ссылка под списком категорий на каталог готовых. */
@Composable
fun SuggestionsLink(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = "Add suggested categories",
            style = FinanceTheme.typography.bodySecondary,
            color = FinanceTheme.colors.accent,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
