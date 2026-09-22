package com.artemkhateev.finance.data.firebase

import android.util.Log
import com.artemkhateev.finance.data.FinanceRepository
import com.artemkhateev.finance.data.auth.AuthRepository
import com.artemkhateev.finance.data.balanceChanges
import com.artemkhateev.finance.data.demo.DemoData
import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.AccountByName
import com.artemkhateev.finance.data.model.AccountType
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryByName
import com.artemkhateev.finance.data.model.ContributionsNewestFirst
import com.artemkhateev.finance.data.model.Goal
import com.artemkhateev.finance.data.model.GoalContribution
import com.artemkhateev.finance.data.model.Holding
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.NewestFirst
import com.artemkhateev.finance.data.model.PortfolioSnapshot
import com.artemkhateev.finance.data.model.Recurring
import com.artemkhateev.finance.data.model.Transaction
import com.artemkhateev.finance.data.model.newAccountId
import com.artemkhateev.finance.data.model.newCategoryId
import com.artemkhateev.finance.data.model.newContributionId
import com.artemkhateev.finance.data.model.newGoalId
import com.artemkhateev.finance.data.model.newHoldingId
import com.artemkhateev.finance.data.model.newRecurringId
import com.artemkhateev.finance.data.model.newTransactionId
import com.artemkhateev.finance.data.transactionsWindowStart
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.util.concurrent.Executor
import java.util.concurrent.Executors

private const val TAG = "CloudFinance"

/** Предел записей в одной пачке Firestore. */
private const val BATCH_LIMIT = 500

/** Сколько слушатель коллекции живёт после ухода последнего экрана: вкладки переключают чаще. */
private const val LISTENER_KEEP_ALIVE_MILLIS = 60_000L

/**
 * Данные текущего пользователя в Firestore: users/{uid}/categories|accounts|transactions|recurrings|
 * holdings|portfolioHistory|goals|goalContributions. Потоки следуют за входом и выходом, поэтому экранам
 * не нужно пересоздаваться при смене аккаунта.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CloudFinanceRepository(
    private val auth: AuthRepository,
    private val db: FirebaseFirestore,
    private val scope: CoroutineScope,
    private val today: () -> LocalDate = { LocalDate.now() },
) : FinanceRepository {

    /** Снимки Firestore разбираются здесь, а не на главном потоке: там разбор тормозил переключение вкладок. */
    private val listenerExecutor: Executor = Executors.newSingleThreadExecutor()

    init {
        scope.launch {
            auth.user.map { it?.uid }.distinctUntilChanged().collect { uid ->
                if (uid != null) {
                    runCatching { ensureDefaults(uid) }.onFailure { Log.w(TAG, "Default data not written", it) }
                }
            }
        }
    }

    // Firestore отдаёт документы по id, а экраны показывают категории и счета по имени.
    override val categories: Flow<List<Category>> =
        perUser({ it.collection(CATEGORIES) }, ::categoryFrom).map { it.sortedWith(CategoryByName) }.shared()

    override val accounts: Flow<List<Account>> =
        perUser({ it.collection(ACCOUNTS) }, ::accountFrom).map { it.sortedWith(AccountByName) }.shared()

    // Более старые документы не читаем, чтобы не тратить квоту: экраны их всё равно не показывают.
    override val transactions: Flow<List<Transaction>> = perUser(
        { user ->
            val since = transactionsWindowStart(today()).toString()
            user.collection(TRANSACTIONS).whereGreaterThanOrEqualTo("date", since)
        },
        ::transactionFrom,
    ).map { list -> list.sortedWith(NewestFirst) }.shared()

    // Отдельный слушатель на год: подписка появляется, только когда на экране трат выбран длинный период.
    override val transactionsYear: Flow<List<Transaction>> = perUser(
        { user ->
            val since = today().minusYears(1).toString()
            user.collection(TRANSACTIONS).whereGreaterThanOrEqualTo("date", since)
        },
        ::transactionFrom,
    ).map { list -> list.sortedWith(NewestFirst) }.shared()

    override val recurrings: Flow<List<Recurring>> = perUser({ it.collection(RECURRINGS) }, ::recurringFrom).shared()

    override val holdings: Flow<List<Holding>> = perUser({ it.collection(HOLDINGS) }, ::holdingFrom).shared()

    override val portfolioHistory: Flow<List<PortfolioSnapshot>> = perUser(
        { user ->
            val since = today().minusYears(1).toString()
            user.collection(PORTFOLIO_HISTORY).whereGreaterThanOrEqualTo("date", since)
        },
        ::snapshotFrom,
    ).map { list -> list.sortedBy { it.date.toEpochDay() } }.shared()

    override val goals: Flow<List<Goal>> = perUser({ it.collection(GOALS) }, ::goalFrom).shared()

    // Взносы не ограничены окном транзакций: прогресс цели складывается из всех.
    override val goalContributions: Flow<List<GoalContribution>> =
        perUser({ it.collection(GOAL_CONTRIBUTIONS) }, ::contributionFrom)
            .map { list -> list.sortedWith(ContributionsNewestFirst) }
            .shared()

    override suspend fun markReviewed(transactionIds: Collection<String>) {
        val user = currentUserDoc() ?: return
        val batch = db.batch()
        transactionIds.forEach { batch.update(user.collection(TRANSACTIONS).document(it), "reviewed", true) }
        // Сервер не ждём: Firestore сразу применяет запись локально, и экран обновляется даже офлайн.
        batch.commit().addOnFailureListener { Log.w(TAG, "markReviewed failed", it) }
    }

    override suspend fun setCategory(transactionId: String, categoryId: String?) {
        val user = currentUserDoc() ?: return
        user.collection(TRANSACTIONS).document(transactionId).update("categoryId", categoryId)
            .addOnFailureListener { Log.w(TAG, "setCategory failed", it) }
    }

    override suspend fun saveTransaction(transaction: Transaction, previous: Transaction?) {
        val user = currentUserDoc() ?: return
        val saved = if (transaction.id.isBlank()) transaction.copy(id = newTransactionId()) else transaction
        user.collection(TRANSACTIONS).document(saved.id).set(saved.toMap())
            .addOnFailureListener { Log.w(TAG, "saveTransaction failed", it) }
        adjustBalances(user, balanceChanges(saved, previous))
    }

    override suspend fun deleteTransaction(transaction: Transaction) {
        val user = currentUserDoc() ?: return
        user.collection(TRANSACTIONS).document(transaction.id).delete()
            .addOnFailureListener { Log.w(TAG, "deleteTransaction failed", it) }
        adjustBalances(user, balanceChanges(saved = null, previous = transaction))
    }

    override suspend fun saveCategory(category: Category) {
        val user = currentUserDoc() ?: return
        val saved = if (category.id.isBlank()) category.copy(id = newCategoryId()) else category
        user.collection(CATEGORIES).document(saved.id).set(saved.toMap())
            .addOnFailureListener { Log.w(TAG, "saveCategory failed", it) }
    }

    override suspend fun deleteCategory(categoryId: String) {
        val user = currentUserDoc() ?: return
        user.collection(CATEGORIES).document(categoryId).delete()
            .addOnFailureListener { Log.w(TAG, "deleteCategory failed", it) }
    }

    override suspend fun saveAccount(account: Account) {
        val user = currentUserDoc() ?: return
        val saved = if (account.id.isBlank()) account.copy(id = newAccountId()) else account
        user.collection(ACCOUNTS).document(saved.id).set(saved.toMap())
            .addOnFailureListener { Log.w(TAG, "saveAccount failed", it) }
    }

    override suspend fun deleteAccount(accountId: String) {
        val user = currentUserDoc() ?: return
        user.collection(ACCOUNTS).document(accountId).delete()
            .addOnFailureListener { Log.w(TAG, "deleteAccount failed", it) }
        deleteWhere(user.collection(HOLDINGS), "accountId", accountId)
    }

    override suspend fun saveRecurring(recurring: Recurring) {
        val user = currentUserDoc() ?: return
        val saved = if (recurring.id.isBlank()) recurring.copy(id = newRecurringId()) else recurring
        user.collection(RECURRINGS).document(saved.id).set(saved.toMap())
            .addOnFailureListener { Log.w(TAG, "saveRecurring failed", it) }
    }

    override suspend fun deleteRecurring(recurringId: String) {
        val user = currentUserDoc() ?: return
        user.collection(RECURRINGS).document(recurringId).delete()
            .addOnFailureListener { Log.w(TAG, "deleteRecurring failed", it) }
    }

    override suspend fun saveHolding(holding: Holding) {
        val user = currentUserDoc() ?: return
        val saved = if (holding.id.isBlank()) holding.copy(id = newHoldingId()) else holding
        user.collection(HOLDINGS).document(saved.id).set(saved.toMap())
            .addOnFailureListener { Log.w(TAG, "saveHolding failed", it) }
    }

    override suspend fun deleteHolding(holdingId: String) {
        val user = currentUserDoc() ?: return
        user.collection(HOLDINGS).document(holdingId).delete()
            .addOnFailureListener { Log.w(TAG, "deleteHolding failed", it) }
    }

    override suspend fun recordPortfolioValue(snapshot: PortfolioSnapshot) {
        val user = currentUserDoc() ?: return
        // Id документа — дата: второй снимок за день просто перезаписывает первый.
        user.collection(PORTFOLIO_HISTORY).document(snapshot.date.toString()).set(snapshot.toMap())
            .addOnFailureListener { Log.w(TAG, "recordPortfolioValue failed", it) }
    }

    override suspend fun saveGoal(goal: Goal) {
        val user = currentUserDoc() ?: return
        val saved = if (goal.id.isBlank()) goal.copy(id = newGoalId()) else goal
        user.collection(GOALS).document(saved.id).set(saved.toMap())
            .addOnFailureListener { Log.w(TAG, "saveGoal failed", it) }
    }

    override suspend fun deleteGoal(goalId: String) {
        val user = currentUserDoc() ?: return
        user.collection(GOALS).document(goalId).delete()
            .addOnFailureListener { Log.w(TAG, "deleteGoal failed", it) }
        deleteWhere(user.collection(GOAL_CONTRIBUTIONS), "goalId", goalId)
    }

    override suspend fun saveContribution(contribution: GoalContribution) {
        val user = currentUserDoc() ?: return
        val saved = if (contribution.id.isBlank()) contribution.copy(id = newContributionId()) else contribution
        user.collection(GOAL_CONTRIBUTIONS).document(saved.id).set(saved.toMap())
            .addOnFailureListener { Log.w(TAG, "saveContribution failed", it) }
    }

    override suspend fun deleteContribution(contributionId: String) {
        val user = currentUserDoc() ?: return
        user.collection(GOAL_CONTRIBUTIONS).document(contributionId).delete()
            .addOnFailureListener { Log.w(TAG, "deleteContribution failed", it) }
    }

    /**
     * Удаляет все документы пользователя и ждёт подтверждения сервера. Сам документ пользователя
     * остаётся: иначе при следующем входе снова создались бы стартовые категории.
     */
    override suspend fun deleteAllData() {
        val user = currentUserDoc() ?: return
        for (name in USER_COLLECTIONS) {
            val documents = user.collection(name).get().await().documents
            for (chunk in documents.chunked(BATCH_LIMIT)) {
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        }
    }

    override suspend fun importTransactions(transactions: List<Transaction>) {
        val user = currentUserDoc() ?: return
        for (chunk in transactions.chunked(BATCH_LIMIT)) {
            val batch = db.batch()
            chunk.forEach { batch.set(user.collection(TRANSACTIONS).document(it.id), it.toMap()) }
            batch.commit().await()
        }
    }

    /** Заливает демо-данные. Идентификаторы постоянные: повторный вызов перезаписывает те же документы. */
    suspend fun importDemoData() {
        val user = currentUserDoc() ?: return
        val day = today()
        val batch = db.batch()
        DemoData.categories.forEach { batch.set(user.collection(CATEGORIES).document(it.id), it.toMap()) }
        DemoData.accounts.forEach { batch.set(user.collection(ACCOUNTS).document(it.id), it.toMap()) }
        DemoData.recurrings(day).forEach { batch.set(user.collection(RECURRINGS).document(it.id), it.toMap()) }
        DemoData.transactions(day).forEach { batch.set(user.collection(TRANSACTIONS).document(it.id), it.toMap()) }
        DemoData.holdings(day).forEach { batch.set(user.collection(HOLDINGS).document(it.id), it.toMap()) }
        DemoData.portfolioHistory(day).forEach {
            batch.set(user.collection(PORTFOLIO_HISTORY).document(it.date.toString()), it.toMap())
        }
        DemoData.goals(day).forEach { batch.set(user.collection(GOALS).document(it.id), it.toMap()) }
        DemoData.goalContributions(day).forEach { batch.set(user.collection(GOAL_CONTRIBUTIONS).document(it.id), it.toMap()) }
        batch.commit().await()
    }

    /**
     * Атомарный increment отдельной записью, а не в одной пачке с транзакцией: если счёт уже удалён,
     * падает только сдвиг остатка, а сама транзакция сохраняется.
     */
    private fun adjustBalances(user: DocumentReference, changes: Map<String, Long>) {
        changes.forEach { (accountId, delta) ->
            user.collection(ACCOUNTS).document(accountId).update("balance", FieldValue.increment(delta))
                .addOnFailureListener { Log.w(TAG, "Balance of $accountId not adjusted", it) }
        }
    }

    /**
     * Удаляет документы, у которых [field] равно [value]: позиции удалённого счёта, взносы удалённой цели.
     * Без сети get() отвечает из локального кэша, так что удаление работает и офлайн.
     */
    private fun deleteWhere(collection: CollectionReference, field: String, value: String) {
        collection.whereEqualTo(field, value).get()
            .addOnSuccessListener { snapshot ->
                snapshot.documents.chunked(BATCH_LIMIT).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { batch.delete(it.reference) }
                    batch.commit().addOnFailureListener { Log.w(TAG, "${collection.id} of $value not deleted", it) }
                }
            }
            .addOnFailureListener { Log.w(TAG, "${collection.id} of $value not deleted", it) }
    }

    /** Новому пользователю — стартовые категории с бюджетами и счёт наличных. */
    private suspend fun ensureDefaults(uid: String) {
        val user = userDoc(uid)
        if (user.get().await().exists()) return
        val batch = db.batch()
        batch.set(user, mapOf("createdAt" to FieldValue.serverTimestamp()))
        DemoData.categories.forEach { batch.set(user.collection(CATEGORIES).document(it.id), it.toMap()) }
        batch.set(user.collection(ACCOUNTS).document(CASH.id), CASH.toMap())
        batch.commit().await()
    }

    private fun userDoc(uid: String): DocumentReference = db.collection("users").document(uid)

    private fun currentUserDoc(): DocumentReference? = auth.user.value?.uid?.let { userDoc(it) }

    private fun <T> perUser(
        query: (DocumentReference) -> Query,
        parse: (String, Map<String, Any?>) -> T?,
    ): Flow<List<T>> = auth.user.map { it?.uid }.distinctUntilChanged().flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else query(userDoc(uid)).observe(listenerExecutor, parse)
    }

    /**
     * Один слушатель на коллекцию для всех экранов: вкладка открывается с уже загруженным списком, а не
     * заводит свой слушатель. Когда слушатель останавливается, последний список забывается — после смены
     * аккаунта не мелькнут данные прежнего.
     */
    private fun <T> Flow<T>.shared(): Flow<T> = flowOn(Dispatchers.Default).shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = LISTENER_KEEP_ALIVE_MILLIS, replayExpirationMillis = 0),
        replay = 1,
    )

    private companion object {
        const val CATEGORIES = "categories"
        const val ACCOUNTS = "accounts"
        const val TRANSACTIONS = "transactions"
        const val RECURRINGS = "recurrings"
        const val HOLDINGS = "holdings"
        const val PORTFOLIO_HISTORY = "portfolioHistory"
        const val GOALS = "goals"
        const val GOAL_CONTRIBUTIONS = "goalContributions"
        val USER_COLLECTIONS = listOf(
            CATEGORIES, ACCOUNTS, TRANSACTIONS, RECURRINGS, HOLDINGS, PORTFOLIO_HISTORY, GOALS, GOAL_CONTRIBUTIONS,
        )
        val CASH = Account("cash", "Cash", "", AccountType.Checking, Money.Zero)
    }
}

private fun <T> Query.observe(executor: Executor, parse: (String, Map<String, Any?>) -> T?): Flow<List<T>> = callbackFlow {
    val registration = addSnapshotListener(executor) { snapshot, error ->
        if (error != null) {
            // Например, PERMISSION_DENIED сразу после выхода: слушатель уже не работает, завершаем поток.
            Log.w(TAG, "Snapshot listener failed", error)
            close()
            return@addSnapshotListener
        }
        if (snapshot != null) {
            trySend(snapshot.documents.mapNotNull { doc -> doc.data?.let { parse(doc.id, it) } })
        }
    }
    awaitClose { registration.remove() }
}
