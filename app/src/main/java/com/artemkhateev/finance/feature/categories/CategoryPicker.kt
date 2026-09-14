package com.artemkhateev.finance.feature.categories

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryKind
import com.artemkhateev.finance.data.model.newCategoryId
import com.artemkhateev.finance.ui.components.CategoryChip
import com.artemkhateev.finance.ui.components.CenteredTextField
import com.artemkhateev.finance.ui.components.FieldLabel
import com.artemkhateev.finance.ui.theme.FinanceTheme
import com.artemkhateev.finance.ui.theme.color

/*
 * Выбор категории в формах транзакции и регулярного платежа: в форме сразу видны частые категории,
 * а полный список — свои и каталог готовых — открывается в той же шторке.
 */

/** Категория, выбранная из каталога: уже заведённая с тем же именем или новая с готовым id — её нужно сохранить. */
fun SuggestedCategory.existingOrNew(existing: List<Category>): Category =
    existing.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: toCategory().copy(id = newCategoryId())

/** Категории, видные в форме сразу: сначала частые, затем по имени; выбранная — всегда в списке. */
internal fun quickCategories(
    categories: List<Category>,
    kind: CategoryKind,
    usage: Map<String, Int>,
    selectedId: String?,
    limit: Int,
): List<Category> {
    val ofKind = categories.filter { it.kind == kind }
    val top = ofKind
        .sortedWith(compareByDescending<Category> { usage[it.id] ?: 0 }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        .take(limit)
    val selected = ofKind.firstOrNull { it.id == selectedId }
    return if (selected == null || selected in top) top else top.dropLast(1) + selected
}

/** Готовые категории нужного вида, которых у пользователя ещё нет, с поиском по названию. */
internal fun catalogChoices(categories: List<Category>, kind: CategoryKind, query: String): List<SuggestedCategory> =
    CategoryCatalog.filter { suggestion ->
        suggestion.kind == kind &&
            categories.none { it.name.equals(suggestion.name, ignoreCase = true) } &&
            suggestion.name.contains(query.trim(), ignoreCase = true)
    }

/** Полный список категорий: свои и каталог; выбранная из каталога сразу заводится. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryPicker(
    kind: CategoryKind,
    categories: List<Category>,
    selectedId: String?,
    onPick: (String?) -> Unit,
    onCreate: (SuggestedCategory) -> Unit,
    onBack: () -> Unit,
) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    var query by remember { mutableStateOf("") }
    val yours = categories.filter { it.kind == kind && it.name.contains(query.trim(), ignoreCase = true) }
    val more = catalogChoices(categories, kind, query)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Text(
                text = "Back",
                style = typography.bodySecondary,
                color = colors.accent,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Text(
                text = "CHOOSE CATEGORY",
                style = typography.sectionLabel,
                color = colors.sectionLabel,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        CenteredTextField(
            value = query,
            placeholder = "Search categories",
            style = typography.body.copy(color = colors.textPrimary),
            onValueChange = { query = it },
            imeAction = ImeAction.Done,
            capitalization = KeyboardCapitalization.None,
        )

        if (yours.isNotEmpty()) {
            FieldLabel("Your categories")
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                yours.forEach { category ->
                    val selected = category.id == selectedId
                    CategoryChip(
                        category = category,
                        modifier = Modifier
                            .clip(CircleShape)
                            .then(if (selected) Modifier.border(1.5.dp, category.tone.color(), CircleShape) else Modifier)
                            .clickable { onPick(if (selected) null else category.id) },
                    )
                }
            }
        }

        if (more.isNotEmpty()) {
            FieldLabel("More categories")
            Text(
                text = "Picking one adds it to your categories",
                style = typography.caption,
                color = colors.textSecondary,
            )
            more.groupBy { it.group }.forEach { (group, suggestions) ->
                Text(group, style = typography.caption, color = colors.textSecondary)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.forEach { suggestion -> CatalogChip(suggestion, onClick = { onCreate(suggestion) }) }
                }
            }
        }

        if (yours.isEmpty() && more.isEmpty()) {
            Text("Nothing found", style = typography.bodySecondary, color = colors.textSecondary)
        }
    }
}

/** Последний чип в форме: открывает полный список категорий. В ряду чипов его растягивают до их высоты. */
@Composable
internal fun AllCategoriesChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FinanceTheme.colors
    Box(
        modifier = modifier
            .clip(CircleShape)
            .border(1.dp, colors.border, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "ALL CATEGORIES ›",
            style = FinanceTheme.typography.chip,
            color = colors.accent,
            maxLines = 1,
        )
    }
}

/** Категория из каталога: ещё не заведена — контур вместо подложки и плюс. */
@Composable
private fun CatalogChip(suggestion: SuggestedCategory, onClick: () -> Unit) {
    val tone = suggestion.tone.color()
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .border(1.dp, tone.copy(alpha = 0.45f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(suggestion.emoji, fontSize = 11.sp)
        Text("+ ${suggestion.name.uppercase()}", style = FinanceTheme.typography.chip, color = tone, maxLines = 1)
    }
}
