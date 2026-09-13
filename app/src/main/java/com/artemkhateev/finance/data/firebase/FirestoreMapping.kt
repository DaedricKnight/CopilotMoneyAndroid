package com.artemkhateev.finance.data.firebase

import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.AccountType
import com.artemkhateev.finance.data.model.AssetClass
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryKind
import com.artemkhateev.finance.data.model.CategoryTone
import com.artemkhateev.finance.data.model.Goal
import com.artemkhateev.finance.data.model.GoalContribution
import com.artemkhateev.finance.data.model.Holding
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.PortfolioSnapshot
import com.artemkhateev.finance.data.model.Recurring
import com.artemkhateev.finance.data.model.Transaction
import java.time.LocalDate

/*
 * Документы Firestore ↔ модели. Суммы хранятся целыми центами, даты — строками ISO
 * ("2026-09-12"): такие строки сортируются как даты и не зависят от часового пояса.
 * Нераспознанный документ пропускается, а не роняет весь список.
 */

internal fun Category.toMap(): Map<String, Any?> = mapOf(
    "name" to name,
    "emoji" to emoji,
    "tone" to tone.name,
    "kind" to kind.name,
    "monthlyBudget" to monthlyBudget?.minor,
)

internal fun categoryFrom(id: String, data: Map<String, Any?>): Category? = runCatching {
    Category(
        id = id,
        name = data["name"] as String,
        emoji = data["emoji"] as? String ?: "❔",
        tone = enumOr(data["tone"], CategoryTone.Gray),
        kind = enumOr(data["kind"], CategoryKind.Expense),
        monthlyBudget = (data["monthlyBudget"] as? Number)?.let { Money(it.toLong()) },
    )
}.getOrNull()

internal fun Account.toMap(): Map<String, Any?> = mapOf(
    "name" to name,
    "institution" to institution,
    "type" to type.name,
    "balance" to balance.minor,
    "mask" to mask,
)

internal fun accountFrom(id: String, data: Map<String, Any?>): Account? = runCatching {
    Account(
        id = id,
        name = data["name"] as String,
        institution = data["institution"] as? String ?: "",
        type = enumOr(data["type"], AccountType.Checking),
        balance = Money((data["balance"] as? Number)?.toLong() ?: 0L),
        mask = data["mask"] as? String,
    )
}.getOrNull()

internal fun Transaction.toMap(): Map<String, Any?> = mapOf(
    "accountId" to accountId,
    "merchant" to merchant,
    "amount" to amount.minor,
    "date" to date.toString(),
    "categoryId" to categoryId,
    "note" to note,
    "reviewed" to reviewed,
)

internal fun transactionFrom(id: String, data: Map<String, Any?>): Transaction? = runCatching {
    Transaction(
        id = id,
        accountId = data["accountId"] as? String ?: "",
        merchant = data["merchant"] as String,
        amount = Money((data["amount"] as Number).toLong()),
        date = LocalDate.parse(data["date"] as String),
        categoryId = data["categoryId"] as? String,
        note = data["note"] as? String,
        reviewed = data["reviewed"] as? Boolean ?: false,
    )
}.getOrNull()

internal fun Recurring.toMap(): Map<String, Any?> = mapOf(
    "name" to name,
    "emoji" to emoji,
    "amount" to amount.minor,
    "dayOfMonth" to dayOfMonth,
    "categoryId" to categoryId,
)

internal fun recurringFrom(id: String, data: Map<String, Any?>): Recurring? = runCatching {
    Recurring(
        id = id,
        name = data["name"] as String,
        emoji = data["emoji"] as? String ?: "🔁",
        amount = Money((data["amount"] as Number).toLong()),
        dayOfMonth = (data["dayOfMonth"] as Number).toInt(),
        categoryId = data["categoryId"] as? String,
    )
}.getOrNull()

internal fun Holding.toMap(): Map<String, Any?> = mapOf(
    "accountId" to accountId,
    "symbol" to symbol,
    "name" to name,
    "assetClass" to assetClass.name,
    "quantityMicros" to quantityMicros,
    "costPerUnit" to costPerUnit.minor,
    "price" to price.minor,
    "priceUpdated" to priceUpdated.toString(),
)

internal fun holdingFrom(id: String, data: Map<String, Any?>): Holding? = runCatching {
    val symbol = data["symbol"] as String
    Holding(
        id = id,
        accountId = data["accountId"] as? String ?: "",
        symbol = symbol,
        name = data["name"] as? String ?: symbol,
        assetClass = enumOr(data["assetClass"], AssetClass.Other),
        quantityMicros = (data["quantityMicros"] as Number).toLong(),
        costPerUnit = Money((data["costPerUnit"] as Number).toLong()),
        price = Money((data["price"] as Number).toLong()),
        priceUpdated = LocalDate.parse(data["priceUpdated"] as String),
    )
}.getOrNull()

internal fun PortfolioSnapshot.toMap(): Map<String, Any?> = mapOf(
    "date" to date.toString(),
    "value" to value.minor,
)

/** Id снимка — его дата, поэтому она же служит запасным источником даты. */
internal fun snapshotFrom(id: String, data: Map<String, Any?>): PortfolioSnapshot? = runCatching {
    PortfolioSnapshot(
        date = LocalDate.parse(data["date"] as? String ?: id),
        value = Money((data["value"] as Number).toLong()),
    )
}.getOrNull()

internal fun Goal.toMap(): Map<String, Any?> = mapOf(
    "name" to name,
    "emoji" to emoji,
    "tone" to tone.name,
    "target" to target.minor,
    "targetDate" to targetDate?.toString(),
    "startDate" to startDate.toString(),
)

internal fun goalFrom(id: String, data: Map<String, Any?>): Goal? = runCatching {
    Goal(
        id = id,
        name = data["name"] as String,
        emoji = data["emoji"] as? String ?: "🎯",
        tone = enumOr(data["tone"], CategoryTone.Teal),
        target = Money((data["target"] as Number).toLong()),
        targetDate = (data["targetDate"] as? String)?.let { LocalDate.parse(it) },
        startDate = LocalDate.parse(data["startDate"] as String),
    )
}.getOrNull()

internal fun GoalContribution.toMap(): Map<String, Any?> = mapOf(
    "goalId" to goalId,
    "amount" to amount.minor,
    "date" to date.toString(),
)

internal fun contributionFrom(id: String, data: Map<String, Any?>): GoalContribution? = runCatching {
    GoalContribution(
        id = id,
        goalId = data["goalId"] as String,
        amount = Money((data["amount"] as Number).toLong()),
        date = LocalDate.parse(data["date"] as String),
    )
}.getOrNull()

private inline fun <reified E : Enum<E>> enumOr(value: Any?, default: E): E =
    enumValues<E>().firstOrNull { it.name == value } ?: default
