package com.artemkhateev.finance.feature.recurrings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
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
import com.artemkhateev.finance.data.model.Recurring
import com.artemkhateev.finance.feature.categories.SuggestedCategory
import com.artemkhateev.finance.feature.categories.existingOrNew
import com.artemkhateev.finance.ui.components.EmptyState
import com.artemkhateev.finance.ui.components.FinanceCard
import com.artemkhateev.finance.ui.components.MoneyText
import com.artemkhateev.finance.ui.components.ProgressRing
import com.artemkhateev.finance.ui.components.RoundAddButton
import com.artemkhateev.finance.ui.components.SectionHeader
import com.artemkhateev.finance.ui.components.screenContentPadding
import com.artemkhateev.finance.ui.theme.FinanceTheme
import com.artemkhateev.finance.ui.theme.color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

class RecurringsViewModel(
    private val repository: FinanceRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    /** null — данные ещё не пришли. */
    val state: StateFlow<RecurringsUiState?> =
        combine(repository.recurrings, repository.categories, repository.transactions) { recurrings, categories, transactions ->
            buildRecurrings(today(), recurrings, categories, transactions)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Категории расходов для формы платежа. */
    val expenseCategories: StateFlow<List<Category>> =
        repository.categories.map { list -> list.filter { it.kind == CategoryKind.Expense } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Сколько транзакций у каждой категории: частые видны в форме первыми. */
    val categoryUsage: StateFlow<Map<String, Int>> =
        repository.transactions.map { list -> list.mapNotNull { it.categoryId }.groupingBy { it }.eachCount() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val mutableDraft = MutableStateFlow<RecurringDraft?>(null)

    /** Открытая форма платежа; null — закрыта. */
    val draft: StateFlow<RecurringDraft?> = mutableDraft.asStateFlow()

    fun startNew() {
        mutableDraft.value = RecurringDraft.startingOn(today())
    }

    fun startEdit(recurring: Recurring) {
        mutableDraft.value = RecurringDraft.from(recurring)
    }

    fun updateDraft(change: (RecurringDraft) -> RecurringDraft) {
        mutableDraft.update { it?.let(change) }
    }

    fun dismissDraft() {
        mutableDraft.value = null
    }

    fun saveDraft() {
        val draft = mutableDraft.value ?: return
        if (draft.problem() != null) return
        mutableDraft.value = null
        viewModelScope.launch { repository.saveRecurring(draft.toRecurring()) }
    }

    fun deleteDraft() {
        val id = mutableDraft.value?.id?.takeIf { it.isNotBlank() } ?: return
        mutableDraft.value = null
        viewModelScope.launch { repository.deleteRecurring(id) }
    }

    /** Категория из каталога, выбранная прямо в форме: заводится сразу, а её id форма получает до ответа репозитория. */
    fun createCategory(suggestion: SuggestedCategory): String {
        val existing = expenseCategories.value
        val category = suggestion.existingOrNew(existing)
        if (category !in existing) viewModelScope.launch { repository.saveCategory(category) }
        return category.id
    }
}

@Composable
fun RecurringsScreen(
    viewModel: RecurringsViewModel = viewModel { RecurringsViewModel(AppGraph.repository) },
) {
    val loaded by viewModel.state.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val categories by viewModel.expenseCategories.collectAsStateWithLifecycle()
    val usage by viewModel.categoryUsage.collectAsStateWithLifecycle()
    val state = loaded ?: return
    val colors = FinanceTheme.colors

    Box(Modifier.fillMaxSize()) {
        if (state.thisMonth.isEmpty() && state.later.isEmpty()) {
            EmptyState("No recurring payments", "Tap + to add rent, bills and subscriptions.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Запас снизу, чтобы последний ряд не закрывала кнопка «+».
                contentPadding = screenContentPadding(extraBottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "summary") { SummaryCard(state) }
                if (state.thisMonth.isNotEmpty()) {
                    item(key = "month-header") { SectionHeader("This month") }
                    items(state.thisMonth.chunked(3), key = { row -> row.first().recurring.id }) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { tile ->
                                RecurringTile(tile, onClick = { viewModel.startEdit(tile.recurring) }, modifier = Modifier.weight(1f))
                            }
                            // Неполный последний ряд: плитки сохраняют ширину трети.
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                if (state.later.isNotEmpty()) {
                    item(key = "later-header") { SectionHeader("Later") }
                    item(key = "later") {
                        FinanceCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                            state.later.forEachIndexed { index, upcoming ->
                                if (index > 0) HorizontalDivider(color = colors.border)
                                UpcomingRow(upcoming, onClick = { viewModel.startEdit(upcoming.recurring) })
                            }
                        }
                    }
                }
            }
        }
        RoundAddButton(
            contentDescription = "Add recurring payment",
            onClick = viewModel::startNew,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp),
        )
    }

    draft?.let { current ->
        RecurringEditorSheet(
            draft = current,
            categories = categories,
            onChange = viewModel::updateDraft,
            onSave = viewModel::saveDraft,
            onDelete = viewModel::deleteDraft,
            onDismiss = viewModel::dismissDraft,
            categoryUsage = usage,
            onCreateCategory = viewModel::createCategory,
        )
    }
}

@Composable
private fun SummaryCard(state: RecurringsUiState) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    val amountStyle = typography.heroAmount.copy(fontSize = 22.sp)
    FinanceCard(hero = true, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                MoneyText(state.leftToPay, style = amountStyle, cents = false)
                Text("left to pay", style = typography.bodySecondary, color = colors.textSecondary)
            }
            ProgressRing(
                progress = state.progress,
                color = colors.accent,
                strokeWidth = 10.dp,
                modifier = Modifier.size(84.dp),
            )
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                MoneyText(state.paidSoFar, style = amountStyle, cents = false)
                Text("paid so far", style = typography.bodySecondary, color = colors.textSecondary)
            }
        }
    }
}

private val TileShape = RoundedCornerShape(18.dp)

@Composable
private fun RecurringTile(tile: RecurringTileUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    Box(
        modifier = modifier
            .clip(TileShape)
            .clickable(onClick = onClick)
            .background(colors.surface)
            .border(1.dp, colors.border, TileShape),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(tile.recurring.emoji, fontSize = 24.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                text = tile.recurring.name,
                style = typography.bodySecondary.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MoneyText(tile.amount, style = typography.bodySecondary, color = colors.textPrimary)
            Text(tile.dueLabel, style = typography.caption, color = colors.textSecondary, maxLines = 1)
        }
        if (tile.paid) {
            PaidCorner(tile.tone?.color() ?: colors.accent, Modifier.align(Alignment.TopEnd))
        }
    }
}

/** Годовой платёж другого месяца: строка с датой следующего списания. */
@Composable
private fun UpcomingRow(upcoming: UpcomingRecurringUi, onClick: () -> Unit) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(upcoming.recurring.emoji, fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = upcoming.recurring.name,
                style = typography.body,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("Yearly · ${upcoming.dueLabel}", style = typography.caption, color = colors.textSecondary)
        }
        Spacer(Modifier.width(8.dp))
        MoneyText(upcoming.recurring.amount)
    }
}

/** Уголок с галочкой в цвете категории: все списания этого месяца уже прошли. */
@Composable
private fun PaidCorner(tone: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(30.dp)
            .drawBehind {
                val corner = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(corner, tone.copy(alpha = 0.28f))
            },
        contentAlignment = Alignment.TopEnd,
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = "Paid",
            tint = tone,
            modifier = Modifier.padding(top = 3.dp, end = 3.dp).size(12.dp),
        )
    }
}
