package com.artemkhateev.finance.feature.goals

import com.artemkhateev.finance.data.model.ContributionsNewestFirst
import com.artemkhateev.finance.data.model.Goal
import com.artemkhateev.finance.data.model.GoalContribution
import com.artemkhateev.finance.data.model.Money
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class GoalStatus {
    /** Срока нет — плана тоже. */
    Open,
    OnTrack,
    Behind,

    /** Срок прошёл, а сумма не набрана. */
    Overdue,
    Done,
}

data class GoalRowUi(
    val goal: Goal,
    val saved: Money,
    /** Сколько осталось отложить; у достигнутой цели — ноль. */
    val left: Money,
    /** Доля накопленного, 0..1. */
    val progress: Float,
    val status: GoalStatus,
    /** Сколько откладывать в месяц, чтобы успеть к сроку; null — срока нет, он прошёл или цель достигнута. */
    val monthlyNeeded: Money?,
    /** Насколько накопленное отстаёт от равномерного плана; null — не отстаёт. */
    val behindBy: Money?,
    /** Взносы цели, новые сверху. */
    val contributions: List<GoalContribution>,
)

data class GoalsUiState(
    /** Отложено на цели в работе. */
    val saved: Money,
    /** Сумма целей в работе. */
    val target: Money,
    /** Взносы этого месяца во все цели за вычетом снятий. */
    val savedThisMonth: Money,
    /** Сколько в месяц откладывать, чтобы все цели со сроком успели. */
    val monthlyNeeded: Money,
    /** Цели в работе: сначала ближайший срок, цели без срока в конце. */
    val active: List<GoalRowUi>,
    val completed: List<GoalRowUi>,
) {
    fun goal(id: String): GoalRowUi? =
        active.firstOrNull { it.goal.id == id } ?: completed.firstOrNull { it.goal.id == id }
}

fun buildGoals(today: LocalDate, goals: List<Goal>, contributions: List<GoalContribution>): GoalsUiState {
    val byGoal = contributions.groupBy { it.goalId }
    val (completed, active) = goals
        .map { goalRow(today, it, byGoal[it.id].orEmpty()) }
        .partition { it.status == GoalStatus.Done }
    val goalIds = goals.map { it.id }.toSet()
    val monthStart = today.withDayOfMonth(1)

    return GoalsUiState(
        saved = Money(active.sumOf { it.saved.minor }),
        target = Money(active.sumOf { it.goal.target.minor }),
        // Взносы удалённых целей не считаются: в облаке их удаление могло и не дойти.
        savedThisMonth = Money(
            contributions
                .filter { it.goalId in goalIds && !it.date.isBefore(monthStart) && !it.date.isAfter(today) }
                .sumOf { it.amount.minor },
        ),
        monthlyNeeded = Money(active.sumOf { it.monthlyNeeded?.minor ?: 0L }),
        active = active.sortedWith(ActiveOrder),
        completed = completed.sortedWith(compareBy<GoalRowUi, String>(String.CASE_INSENSITIVE_ORDER) { it.goal.name }),
    )
}

/** Ближайший срок сверху, цели без срока в конце, при равенстве — по имени. */
private val ActiveOrder: Comparator<GoalRowUi> =
    compareBy<GoalRowUi, Long?>(nullsLast()) { it.goal.targetDate?.toEpochDay() }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.goal.name }

internal fun goalRow(today: LocalDate, goal: Goal, contributions: List<GoalContribution>): GoalRowUi {
    val saved = contributions.sumOf { it.amount.minor }
    val target = goal.target.minor
    val left = (target - saved).coerceAtLeast(0)
    val end = goal.targetDate
    val planned = plannedBy(today, goal)
    val status = when {
        saved >= target -> GoalStatus.Done
        end == null -> GoalStatus.Open
        end.isBefore(today) -> GoalStatus.Overdue
        planned != null && saved < planned -> GoalStatus.Behind
        else -> GoalStatus.OnTrack
    }
    val months = if (end != null && (status == GoalStatus.OnTrack || status == GoalStatus.Behind)) monthsLeft(today, end) else null

    return GoalRowUi(
        goal = goal,
        saved = Money(saved),
        left = Money(left),
        progress = if (target <= 0) 1f else (saved.toFloat() / target).coerceIn(0f, 1f),
        status = status,
        // С округлением вверх до цента: откладывая столько, к сроку точно успеешь.
        monthlyNeeded = months?.let { Money((left + it - 1) / it) },
        behindBy = if (status == GoalStatus.Behind && planned != null) Money(planned - saved) else null,
        contributions = contributions.sortedWith(ContributionsNewestFirst),
    )
}

/** Сколько должно быть отложено к [today], если откладывать поровну от начала плана до срока; null — срока нет. */
internal fun plannedBy(today: LocalDate, goal: Goal): Long? {
    val end = goal.targetDate ?: return null
    val total = ChronoUnit.DAYS.between(goal.startDate, end)
    if (total <= 0) return goal.target.minor
    val elapsed = ChronoUnit.DAYS.between(goal.startDate, today).coerceIn(0, total)
    return goal.target.minor * elapsed / total
}

/** Сколько месяцев до срока, с округлением вверх: с 15 сентября до 31 декабря — 4. Не меньше одного. */
internal fun monthsLeft(today: LocalDate, end: LocalDate): Long {
    if (!end.isAfter(today)) return 1
    val whole = ChronoUnit.MONTHS.between(today, end)
    val months = if (today.plusMonths(whole).isBefore(end)) whole + 1 else whole
    return months.coerceAtLeast(1)
}
