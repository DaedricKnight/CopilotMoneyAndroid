package com.artemkhateev.finance.feature.goals

import com.artemkhateev.finance.data.model.CategoryTone
import com.artemkhateev.finance.data.model.Goal
import com.artemkhateev.finance.data.model.GoalContribution
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.ui.format.MoneyFormatter
import com.artemkhateev.finance.ui.format.amountInputText
import com.artemkhateev.finance.ui.format.parseAmount
import java.time.LocalDate

/** Форма цели. Суммы хранятся текстом — так, как их набирают. */
data class GoalDraft(
    /** Пустой — новая цель. */
    val id: String = "",
    val name: String = "",
    val emoji: String = "🎯",
    val tone: CategoryTone = CategoryTone.Teal,
    val targetText: String = "",
    /** Уже отложено — только у новой цели: сумма станет её первым взносом. */
    val savedText: String = "",
    val targetDate: LocalDate? = null,
    /** Начало плана существующей цели; у новой план начинается в день создания. */
    val startDate: LocalDate? = null,
) {
    val target: Money? get() = parseAmount(targetText)?.takeIf { it.minor > 0 }

    /** Пустое поле — ничего не отложено; неразборчивый текст — null. */
    val saved: Money? get() = if (savedText.isBlank()) Money.Zero else parseAmount(savedText)

    /** Почему сохранить нельзя; null — можно. */
    fun problem(): String? = when {
        name.isBlank() -> "Name the goal"
        emoji.isBlank() -> "Pick an emoji"
        target == null -> "Target should be more than zero"
        saved == null -> "Saved amount should be an amount"
        else -> null
    }

    /** [newId] нужен новой цели заранее: к нему привязывается первый взнос. Существующая цель свой id сохраняет. */
    fun toGoal(newId: String, today: LocalDate): Goal = Goal(
        id = id.ifBlank { newId },
        name = name.trim(),
        emoji = emoji,
        tone = tone,
        target = requireNotNull(target) { "Goal draft is not valid: ${problem()}" },
        targetDate = targetDate,
        startDate = startDate ?: today,
    )

    companion object {
        fun from(goal: Goal) = GoalDraft(
            id = goal.id,
            name = goal.name,
            emoji = goal.emoji,
            tone = goal.tone,
            targetText = amountInputText(goal.target),
            targetDate = goal.targetDate,
            startDate = goal.startDate,
        )
    }
}

enum class ContributionKind { Add, Withdraw }

/** Взнос или снятие в карточке цели. */
data class ContributionDraft(
    val kind: ContributionKind = ContributionKind.Add,
    val amountText: String = "",
) {
    val amount: Money? get() = parseAmount(amountText)?.takeIf { it.minor > 0 }

    /** Почему записать нельзя; null — можно. Снять больше отложенного не даём. */
    fun problem(saved: Money): String? {
        val value = amount ?: return "Enter an amount"
        return if (kind == ContributionKind.Withdraw && value > saved) "Only ${MoneyFormatter.format(saved)} is saved" else null
    }

    fun toContribution(goalId: String, today: LocalDate): GoalContribution {
        val value = requireNotNull(amount) { "Contribution has no amount" }
        return GoalContribution(
            id = "",
            goalId = goalId,
            amount = if (kind == ContributionKind.Withdraw) -value else value,
            date = today,
        )
    }
}
