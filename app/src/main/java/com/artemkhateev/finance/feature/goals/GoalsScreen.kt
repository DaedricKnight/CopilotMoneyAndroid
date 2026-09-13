package com.artemkhateev.finance.feature.goals

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artemkhateev.finance.data.AppGraph
import com.artemkhateev.finance.data.FinanceRepository
import com.artemkhateev.finance.data.model.Goal
import com.artemkhateev.finance.data.model.GoalContribution
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.newGoalId
import com.artemkhateev.finance.ui.components.CardShape
import com.artemkhateev.finance.ui.components.EmptyState
import com.artemkhateev.finance.ui.components.FinanceCard
import com.artemkhateev.finance.ui.components.MoneyInputField
import com.artemkhateev.finance.ui.components.MoneyText
import com.artemkhateev.finance.ui.components.PillButton
import com.artemkhateev.finance.ui.components.ProgressRing
import com.artemkhateev.finance.ui.components.RoundAddButton
import com.artemkhateev.finance.ui.components.SectionHeader
import com.artemkhateev.finance.ui.components.SegmentedControl
import com.artemkhateev.finance.ui.components.appendMoney
import com.artemkhateev.finance.ui.components.screenContentPadding
import com.artemkhateev.finance.ui.format.MoneyFormatter
import com.artemkhateev.finance.ui.format.SignStyle
import com.artemkhateev.finance.ui.format.historyDate
import com.artemkhateev.finance.ui.format.longDate
import com.artemkhateev.finance.ui.format.monthYear
import com.artemkhateev.finance.ui.theme.FinanceTheme
import com.artemkhateev.finance.ui.theme.TONE_BACKGROUND_ALPHA
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

class GoalsViewModel(
    private val repository: FinanceRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    /** null — данные ещё не пришли. */
    val state: StateFlow<GoalsUiState?> =
        combine(repository.goals, repository.goalContributions) { goals, contributions ->
            buildGoals(today(), goals, contributions)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val selectedId = MutableStateFlow<String?>(null)

    /** Открытая карточка цели; null — закрыта. */
    val detail: StateFlow<GoalRowUi?> =
        combine(selectedId, state) { id, current -> id?.let { current?.goal(it) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val mutableContribution = MutableStateFlow(ContributionDraft())

    /** Сумма взноса или снятия в карточке цели. */
    val contribution: StateFlow<ContributionDraft> = mutableContribution.asStateFlow()

    private val mutableDraft = MutableStateFlow<GoalDraft?>(null)

    /** Открытая форма цели; null — закрыта. */
    val draft: StateFlow<GoalDraft?> = mutableDraft.asStateFlow()

    fun open(goalId: String) {
        mutableContribution.value = ContributionDraft()
        selectedId.value = goalId
    }

    fun close() {
        selectedId.value = null
    }

    fun updateContribution(change: (ContributionDraft) -> ContributionDraft) {
        mutableContribution.update(change)
    }

    fun saveContribution() {
        val row = detail.value ?: return
        val entry = mutableContribution.value
        if (entry.problem(row.saved) != null) return
        // Сумму сбрасываем, вид операции оставляем: взносы часто идут подряд.
        mutableContribution.value = ContributionDraft(kind = entry.kind)
        viewModelScope.launch { repository.saveContribution(entry.toContribution(row.goal.id, today())) }
    }

    fun deleteContribution(contributionId: String) {
        viewModelScope.launch { repository.deleteContribution(contributionId) }
    }

    fun startNew() {
        mutableDraft.value = GoalDraft()
    }

    fun startEdit(goal: Goal) {
        // Две шторки сразу не показываем: карточка закрывается, открывается форма.
        selectedId.value = null
        mutableDraft.value = GoalDraft.from(goal)
    }

    fun updateDraft(change: (GoalDraft) -> GoalDraft) {
        mutableDraft.update { it?.let(change) }
    }

    fun dismissDraft() {
        mutableDraft.value = null
    }

    fun saveDraft() {
        val draft = mutableDraft.value ?: return
        if (draft.problem() != null) return
        mutableDraft.value = null
        viewModelScope.launch {
            val goal = draft.toGoal(newGoalId(), today())
            repository.saveGoal(goal)
            val saved = draft.saved
            // Уже отложенное становится первым взносом, иначе цель начиналась бы с нуля.
            if (draft.id.isBlank() && saved != null && saved.minor > 0) {
                repository.saveContribution(GoalContribution("", goal.id, saved, today()))
            }
        }
    }

    fun deleteDraft() {
        val id = mutableDraft.value?.id?.takeIf { it.isNotBlank() } ?: return
        mutableDraft.value = null
        viewModelScope.launch { repository.deleteGoal(id) }
    }
}

@Composable
fun GoalsScreen(
    viewModel: GoalsViewModel = viewModel { GoalsViewModel(AppGraph.repository) },
) {
    val loaded by viewModel.state.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val contribution by viewModel.contribution.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val state = loaded ?: return

    Box(Modifier.fillMaxSize()) {
        if (state.active.isEmpty() && state.completed.isEmpty()) {
            EmptyState("No goals yet", "Tap + to start saving for a trip, a new gadget or a rainy day.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Запас снизу, чтобы последнюю карточку не закрывала кнопка «+».
                contentPadding = screenContentPadding(extraBottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "summary") { SummaryCard(state) }
                if (state.active.isNotEmpty()) {
                    item(key = "active-header") { SectionHeader("In progress") }
                    items(state.active, key = { it.goal.id }) { row ->
                        GoalCard(row, onClick = { viewModel.open(row.goal.id) })
                    }
                }
                if (state.completed.isNotEmpty()) {
                    item(key = "completed-header") { SectionHeader("Completed") }
                    items(state.completed, key = { it.goal.id }) { row ->
                        GoalCard(row, onClick = { viewModel.open(row.goal.id) })
                    }
                }
            }
        }
        RoundAddButton(
            contentDescription = "Add goal",
            onClick = viewModel::startNew,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp),
        )
    }

    detail?.let { row ->
        GoalDetailSheet(
            row = row,
            contribution = contribution,
            onContributionChange = viewModel::updateContribution,
            onSaveContribution = viewModel::saveContribution,
            onDeleteContribution = viewModel::deleteContribution,
            onEdit = { viewModel.startEdit(row.goal) },
            onDismiss = viewModel::close,
        )
    }
    draft?.let { current ->
        GoalEditorSheet(
            draft = current,
            onChange = viewModel::updateDraft,
            onSave = viewModel::saveDraft,
            onDelete = viewModel::deleteDraft,
            onDismiss = viewModel::dismissDraft,
        )
    }
}

@Composable
private fun SummaryCard(state: GoalsUiState) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    FinanceCard(
        hero = true,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Text("Goals", style = typography.cardTitle, color = colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        if (state.active.isEmpty()) {
            Text("All goals reached 🎉", style = typography.body, color = colors.positiveText)
        } else {
            MoneyText(state.saved, style = typography.heroAmount, cents = false, sign = SignStyle.Negative)
            Text(
                text = buildAnnotatedString {
                    append("saved of ")
                    appendMoney(state.target, typography.bodySecondary.fontSize, cents = false)
                },
                style = typography.bodySecondary,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(14.dp))
            SavedBar(state)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            Stat("Saved this month", state.savedThisMonth, Modifier.weight(1f))
            Stat("Needed monthly", state.monthlyNeeded, Modifier.weight(1f))
        }
    }
}

/** Полоса целей в работе: у каждой свой отрезок её цвета длиной в накопленное, остаток — дорожка. */
@Composable
private fun SavedBar(state: GoalsUiState) {
    val colors = FinanceTheme.colors
    val total = state.target.minor.toFloat()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape)
            .background(colors.track),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        state.active.filter { it.saved.minor > 0 }.forEach { row ->
            Box(
                Modifier
                    .weight(row.saved.minor / total)
                    .fillMaxHeight()
                    .background(row.goal.tone.color()),
            )
        }
        val rest = (state.target - state.saved).minor
        if (rest > 0) Spacer(Modifier.weight(rest / total))
    }
}

@Composable
private fun Stat(label: String, amount: Money, modifier: Modifier = Modifier) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        MoneyText(amount, style = typography.body, cents = false, sign = SignStyle.Negative)
        Text(label, style = typography.caption, color = colors.textSecondary)
    }
}

@Composable
private fun GoalCard(row: GoalRowUi, onClick: () -> Unit) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    val tone = row.goal.tone.color()
    FinanceCard(modifier = Modifier.clip(CardShape).clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tone.copy(alpha = TONE_BACKGROUND_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                Text(row.goal.emoji, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.goal.name,
                    style = typography.body,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(goalSubtitle(row), style = typography.caption, color = colors.textSecondary, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            StatusChip(row.status)
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(colors.track),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(row.progress)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(tone),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.textPrimary)) {
                        appendMoney(row.saved, typography.bodySecondary.fontSize, cents = false)
                    }
                    append(" of ")
                    appendMoney(row.goal.target, typography.bodySecondary.fontSize, cents = false)
                },
                style = typography.bodySecondary,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text("${(row.progress * 100).toInt()}%", style = typography.bodySecondary, color = colors.textSecondary)
        }
    }
}

@Composable
private fun StatusChip(status: GoalStatus) {
    val colors = FinanceTheme.colors
    val (label, tone) = when (status) {
        GoalStatus.OnTrack -> "On track" to colors.positiveText
        GoalStatus.Behind -> "Behind" to colors.warning
        GoalStatus.Overdue -> "Overdue" to colors.negativeText
        GoalStatus.Done -> "Done" to colors.positiveText
        GoalStatus.Open -> return
    }
    Text(
        text = label.uppercase(),
        style = FinanceTheme.typography.chip,
        color = tone,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(tone.copy(alpha = TONE_BACKGROUND_ALPHA))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/** Карточка цели: кольцо прогресса, что делать дальше, взнос или снятие и история. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDetailSheet(
    row: GoalRowUi,
    contribution: ContributionDraft,
    onContributionChange: ((ContributionDraft) -> ContributionDraft) -> Unit,
    onSaveContribution: () -> Unit,
    onDeleteContribution: (String) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    val tone = row.goal.tone.color()
    val today = remember { LocalDate.now() }
    var confirmDeleteId by remember(row.goal.id) { mutableStateOf<String?>(null) }
    val withdraw = contribution.kind == ContributionKind.Withdraw
    val problem = contribution.problem(row.saved)

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
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth()) {
                Text(
                    text = "GOAL",
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
            ProgressRing(progress = row.progress, color = tone, modifier = Modifier.size(104.dp), strokeWidth = 7.dp) {
                Text(row.goal.emoji, fontSize = 36.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(row.goal.name, style = typography.heroAmount, color = tone, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            MoneyText(
                amount = row.saved,
                style = typography.heroAmount.copy(fontSize = 30.sp),
                cents = false,
                sign = SignStyle.Negative,
            )
            Text(
                text = buildAnnotatedString {
                    append("saved of ")
                    appendMoney(row.goal.target, typography.bodySecondary.fontSize, cents = false)
                },
                style = typography.bodySecondary,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = statusSentence(row),
                style = typography.bodySecondary,
                color = when (row.status) {
                    GoalStatus.Done, GoalStatus.OnTrack -> colors.positiveText
                    GoalStatus.Behind -> colors.warning
                    GoalStatus.Overdue -> colors.negativeText
                    GoalStatus.Open -> colors.textSecondary
                },
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(18.dp))
            SegmentedControl(
                options = listOf("Add money", "Withdraw"),
                selectedIndex = contribution.kind.ordinal,
                onSelect = { index -> onContributionChange { it.copy(kind = ContributionKind.entries[index]) } },
            )
            Spacer(Modifier.height(10.dp))
            MoneyInputField(
                text = contribution.amountText,
                onValueChange = { text -> onContributionChange { it.copy(amountText = text) } },
                textStyle = typography.heroAmount.copy(
                    fontSize = 32.sp,
                    color = if (withdraw) colors.textPrimary else colors.positiveText,
                ),
                sign = if (withdraw) "-" else "+",
                imeAction = ImeAction.Done,
            )
            Spacer(Modifier.height(10.dp))
            PillButton(
                text = if (withdraw) "Withdraw" else "Add money",
                onClick = onSaveContribution,
                enabled = problem == null,
                filled = true,
            )
            // Пустое поле видно и так; объясняем только снятие больше отложенного.
            if (problem != null && contribution.amount != null) {
                Spacer(Modifier.height(8.dp))
                Text(problem, style = typography.bodySecondary, color = colors.negativeText, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(8.dp))
            SectionHeader("History")
            Spacer(Modifier.height(8.dp))
            if (row.contributions.isEmpty()) {
                Text("Nothing saved yet", style = typography.bodySecondary, color = colors.textSecondary)
            } else {
                FinanceCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                    row.contributions.forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider(color = colors.border)
                        val confirming = confirmDeleteId == entry.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (confirming) {
                                        onDeleteContribution(entry.id)
                                    } else {
                                        confirmDeleteId = entry.id
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (confirming) "Tap again to delete" else historyDate(entry.date, today),
                                style = typography.body,
                                color = if (confirming) colors.negativeText else colors.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            MoneyText(
                                amount = entry.amount,
                                color = if (entry.amount.minor > 0) colors.positiveText else colors.textPrimary,
                                sign = SignStyle.Always,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Подпись под названием цели в списке. */
private fun goalSubtitle(row: GoalRowUi): String {
    val end = row.goal.targetDate
    val monthly = row.monthlyNeeded
    return when {
        row.status == GoalStatus.Done -> "Goal reached"
        end == null -> "No target date"
        row.status == GoalStatus.Overdue -> "Was due ${longDate(end)}"
        monthly != null -> "By ${monthYear(end)} · ${MoneyFormatter.format(monthly, cents = false)}/mo"
        else -> "By ${monthYear(end)}"
    }
}

/** Что с целью и что делать дальше — строка под суммой в карточке цели. */
private fun statusSentence(row: GoalRowUi): String {
    val left = MoneyFormatter.format(row.left, cents = false)
    val end = row.goal.targetDate
    val monthly = row.monthlyNeeded
    val behindBy = row.behindBy
    return when {
        row.status == GoalStatus.Done -> "Goal reached 🎉"
        row.status == GoalStatus.Overdue -> "Target date passed · $left to go"
        end == null || monthly == null -> "$left to go"
        behindBy != null ->
            "${MoneyFormatter.format(behindBy, cents = false)} behind plan · " +
                "${MoneyFormatter.format(monthly, cents = false)}/mo to reach it by ${monthYear(end)}"
        else -> "On track · ${MoneyFormatter.format(monthly, cents = false)}/mo until ${monthYear(end)}"
    }
}
