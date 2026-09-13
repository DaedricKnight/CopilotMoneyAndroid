package com.artemkhateev.finance.feature.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artemkhateev.finance.data.AppGraph
import com.artemkhateev.finance.data.FinanceRepository
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryKind
import com.artemkhateev.finance.ui.components.CategoryChip
import com.artemkhateev.finance.ui.components.EmptyState
import com.artemkhateev.finance.ui.components.FinanceCard
import com.artemkhateev.finance.ui.components.MoneyText
import com.artemkhateev.finance.ui.components.RoundAddButton
import com.artemkhateev.finance.ui.components.SectionHeader
import com.artemkhateev.finance.ui.components.SegmentedControl
import com.artemkhateev.finance.ui.components.appendMoney
import com.artemkhateev.finance.ui.components.screenContentPadding
import com.artemkhateev.finance.ui.format.SignStyle
import com.artemkhateev.finance.ui.format.dayLabel
import com.artemkhateev.finance.ui.theme.FinanceTheme
import com.artemkhateev.finance.ui.theme.color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

class CategoriesViewModel(
    private val repository: FinanceRepository,
    today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    /** null — данные ещё не пришли. */
    val state: StateFlow<CategoriesUiState?> =
        combine(repository.categories, repository.transactions) { categories, transactions ->
            buildCategories(today(), categories, transactions)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Все категории: по ним форма проверяет, не занято ли имя. */
    val allCategories: StateFlow<List<Category>> =
        repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedId = MutableStateFlow<String?>(null)

    /** Открытая карточка категории; null — закрыта. */
    val detail: StateFlow<CategoryDetailUi?> =
        combine(selectedId, repository.categories, repository.transactions) { id, categories, transactions ->
            id?.let { buildCategoryDetail(today(), it, categories, transactions) }
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val mutableDraft = MutableStateFlow<CategoryDraft?>(null)

    /** Открытая форма категории; null — закрыта. */
    val draft: StateFlow<CategoryDraft?> = mutableDraft.asStateFlow()

    fun open(categoryId: String) {
        selectedId.value = categoryId
    }

    fun close() {
        selectedId.value = null
    }

    fun startNew() {
        mutableDraft.value = CategoryDraft()
    }

    fun startEdit(category: Category) {
        // Две шторки сразу не показываем: карточка закрывается, открывается форма.
        selectedId.value = null
        mutableDraft.value = CategoryDraft.from(category)
    }

    fun updateDraft(change: (CategoryDraft) -> CategoryDraft) {
        mutableDraft.update { it?.let(change) }
    }

    fun dismissDraft() {
        mutableDraft.value = null
    }

    fun saveDraft() {
        val draft = mutableDraft.value ?: return
        if (draft.problem(allCategories.value) != null) return
        mutableDraft.value = null
        viewModelScope.launch { repository.saveCategory(draft.toCategory()) }
    }

    fun deleteDraft() {
        val id = mutableDraft.value?.id?.takeIf { it.isNotBlank() } ?: return
        mutableDraft.value = null
        viewModelScope.launch { repository.deleteCategory(id) }
    }

    private val mutableSuggestionsOpen = MutableStateFlow(false)

    /** Открыт ли каталог готовых категорий. */
    val suggestionsOpen: StateFlow<Boolean> = mutableSuggestionsOpen.asStateFlow()

    fun openSuggestions() {
        mutableSuggestionsOpen.value = true
    }

    fun closeSuggestions() {
        mutableSuggestionsOpen.value = false
    }

    fun addSuggestions(chosen: List<SuggestedCategory>) {
        mutableSuggestionsOpen.value = false
        val taken = allCategories.value
        viewModelScope.launch {
            // Имя могло появиться, пока каталог был открыт: одинаковых категорий не заводим.
            chosen
                .filter { suggestion -> taken.none { it.name.equals(suggestion.name, ignoreCase = true) } }
                .forEach { repository.saveCategory(it.toCategory()) }
        }
    }
}

@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel = viewModel { CategoriesViewModel(AppGraph.repository) },
) {
    val loaded by viewModel.state.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val allCategories by viewModel.allCategories.collectAsStateWithLifecycle()
    val suggestionsOpen by viewModel.suggestionsOpen.collectAsStateWithLifecycle()
    val state = loaded ?: return
    val colors = FinanceTheme.colors
    var showPercent by rememberSaveable { mutableStateOf(false) }

    if (suggestionsOpen) {
        CategorySuggestionsSheet(existing = allCategories, onAdd = viewModel::addSuggestions, onDismiss = viewModel::closeSuggestions)
    }

    Box(Modifier.fillMaxSize()) {
        if (state.budgets.isEmpty() && state.others.isEmpty()) {
            CategoriesEmptyState(onBrowse = viewModel::openSuggestions)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Запас снизу, чтобы последнюю строку не закрывала кнопка «+».
                contentPadding = screenContentPadding(extraBottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.budgets.isNotEmpty()) {
                    item(key = "budgets") {
                        BudgetsCard(state, showPercent, onShowPercent = { showPercent = it }, onOpen = viewModel::open)
                    }
                }
                if (state.others.isNotEmpty()) {
                    item(key = "others-header") {
                        SectionHeader(if (state.budgets.isEmpty()) "Categories" else "Other categories")
                    }
                    item(key = "others") {
                        FinanceCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                            state.others.forEachIndexed { index, row ->
                                if (index > 0) HorizontalDivider(color = colors.border)
                                OtherCategoryRow(row, onClick = { viewModel.open(row.category.id) })
                            }
                        }
                    }
                }
                item(key = "suggestions") { SuggestionsLink(onClick = viewModel::openSuggestions) }
            }
        }
        RoundAddButton(
            contentDescription = "Add category",
            onClick = viewModel::startNew,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp),
        )
    }

    detail?.let { current ->
        CategoryDetailSheet(current, onEdit = { viewModel.startEdit(current.category) }, onDismiss = viewModel::close)
    }
    draft?.let { current ->
        CategoryEditorSheet(
            draft = current,
            existing = allCategories,
            onChange = viewModel::updateDraft,
            onSave = viewModel::saveDraft,
            onDelete = viewModel::deleteDraft,
            onDismiss = viewModel::dismissDraft,
        )
    }
}

@Composable
private fun OtherCategoryRow(row: CategoryAmountUi, onClick: () -> Unit) {
    val colors = FinanceTheme.colors
    val income = row.category.kind == CategoryKind.Income
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryChip(row.category)
        Spacer(Modifier.weight(1f))
        MoneyText(
            amount = row.amount,
            color = when {
                row.amount.minor == 0L -> colors.textSecondary
                income -> colors.positiveText
                else -> colors.textPrimary
            },
            sign = if (income && row.amount.minor > 0) SignStyle.Always else SignStyle.None,
        )
    }
}

private val DeltaColumnWidth = 76.dp

@Composable
private fun BudgetsCard(
    state: CategoriesUiState,
    showPercent: Boolean,
    onShowPercent: (Boolean) -> Unit,
    onOpen: (String) -> Unit,
) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    val under = state.totalLeft.minor >= 0

    FinanceCard(
        hero = true,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Text("Budgets", style = typography.cardTitle, color = colors.textPrimary)
        Spacer(Modifier.height(12.dp))
        SegmentedControl(
            options = listOf("Amount", "Percentage"),
            selectedIndex = if (showPercent) 1 else 0,
            onSelect = { onShowPercent(it == 1) },
        )
        Spacer(Modifier.height(12.dp))
        MoneyText(
            amount = state.totalLeft.abs(),
            style = typography.heroAmount,
            color = if (under) colors.positiveText else colors.negativeText,
            cents = false,
        )
        Text(
            text = if (under) "under total budget" else "over total budget",
            style = typography.bodySecondary,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // Пунктир «ровно бюджет» идёт через все строки по середине дорожки.
                    val x = (size.width - DeltaColumnWidth.toPx()) / 2
                    drawLine(
                        color = colors.textInactive,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
                    )
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = "TARGET",
                        style = typography.chip,
                        color = colors.sectionLabel,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.pill)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.width(DeltaColumnWidth))
            }
            state.budgets.forEach { item ->
                BudgetBarRow(item, showPercent, onClick = { onOpen(item.category.id) })
            }
        }
    }
}

@Composable
private fun BudgetBarRow(item: CategoryBudgetUi, showPercent: Boolean, onClick: () -> Unit) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    val tone = item.category.tone.color()
    val over = item.spent > item.budget

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            // Бюджет — середина дорожки: видно и недорасход, и перерасход до двух бюджетов.
            // Короче 14 % полосу не делаем, иначе в неё не влезает эмодзи.
            Box(
                Modifier
                    .fillMaxWidth((item.ratio / 2f).coerceIn(0.14f, 1f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(tone.copy(alpha = 0.22f)),
            )
            Row(
                modifier = Modifier.padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(tone))
                Text(item.category.emoji, fontSize = 16.sp)
            }
            Text(
                text = item.category.name,
                style = typography.body,
                color = tone,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Box(Modifier.width(DeltaColumnWidth), contentAlignment = Alignment.CenterEnd) {
            val deltaColor = if (over) colors.negativeText else colors.textPrimary
            if (showPercent) {
                Text("${(item.ratio * 100).roundToInt()}%", style = typography.bodySecondary, color = deltaColor)
            } else {
                val difference = item.spent - item.budget
                Text(
                    text = buildAnnotatedString {
                        // Ровно в бюджет — без стрелки: нет ни недорасхода, ни перерасхода.
                        if (difference.minor != 0L) append(if (over) "↑ " else "↓ ")
                        appendMoney(difference.abs(), typography.bodySecondary.fontSize, cents = false)
                    },
                    style = typography.bodySecondary,
                    color = deltaColor,
                )
            }
        }
    }
}

/** Карточка категории, как в референсе: эмодзи, название в цвете категории, сумма и транзакции месяца. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDetailSheet(detail: CategoryDetailUi, onEdit: () -> Unit, onDismiss: () -> Unit) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    val tone = detail.category.tone.color()
    val income = detail.category.kind == CategoryKind.Income
    val today = remember { LocalDate.now() }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth()) {
                Text(
                    text = "CATEGORY",
                    style = typography.sectionLabel,
                    color = colors.sectionLabel,
                    modifier = Modifier.align(Alignment.Center),
                )
                Text(
                    text = "Edit",
                    style = typography.bodySecondary,
                    color = colors.accent,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clip(CircleShape)
                        .clickable(onClick = onEdit)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(detail.category.emoji, fontSize = 34.sp)
            Spacer(Modifier.height(6.dp))
            Text(detail.category.name, style = typography.heroAmount, color = tone)
            Spacer(Modifier.height(12.dp))
            MoneyText(
                amount = detail.amount,
                style = typography.heroAmount.copy(fontSize = 30.sp),
                color = if (income) colors.positiveText else colors.textPrimary,
                cents = false,
            )
            val budget = detail.budget
            Text(
                text = buildAnnotatedString {
                    when {
                        income -> append("received this month")
                        budget == null -> append("spent this month")
                        else -> {
                            append("spent of ")
                            appendMoney(budget, typography.bodySecondary.fontSize, cents = false)
                            append(" budget")
                        }
                    }
                },
                style = typography.bodySecondary,
                color = colors.textSecondary,
            )
            if (budget != null) {
                val ratio = detail.amount.minor.toFloat() / budget.minor
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(colors.track)) {
                    Box(
                        Modifier
                            .fillMaxWidth(ratio.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(if (ratio > 1f) colors.negative else tone),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SectionHeader("This month")
            Spacer(Modifier.height(8.dp))
            if (detail.transactions.isEmpty()) {
                Text("No transactions this month", style = typography.bodySecondary, color = colors.textSecondary)
            } else {
                FinanceCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                    detail.transactions.forEachIndexed { index, transaction ->
                        if (index > 0) HorizontalDivider(color = colors.border)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = transaction.merchant,
                                    style = typography.body,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(dayLabel(transaction.date, today), style = typography.caption, color = colors.textSecondary)
                            }
                            val positive = transaction.amount.minor > 0
                            MoneyText(
                                amount = if (positive) transaction.amount else transaction.amount.abs(),
                                color = if (positive) colors.positiveText else colors.textPrimary,
                                sign = if (positive) SignStyle.Always else SignStyle.None,
                            )
                        }
                    }
                }
            }
        }
    }
}
