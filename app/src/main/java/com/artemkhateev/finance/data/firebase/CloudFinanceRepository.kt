package com.artemkhateev.finance.data.firebase

import android.util.Log
import com.artemkhateev.finance.data.FinanceRepository
import com.artemkhateev.finance.data.auth.AuthRepository
import com.artemkhateev.finance.data.demo.DemoData
import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.AccountType
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.NewestFirst
import com.artemkhateev.finance.data.model.Recurring
import com.artemkhateev.finance.data.model.Transaction
import com.artemkhateev.finance.data.model.newTransactionId
import com.artemkhateev.finance.data.transactionsWindowStart
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

private const val TAG = "CloudFinance"

/**
 * Данные текущего пользователя в Firestore: users/{uid}/categories|accounts|transactions|recurrings.
 * Потоки следуют за входом и выходом, поэтому экранам не нужно пересоздаваться при смене аккаунта.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CloudFinanceRepository(
    private val auth: AuthRepository,
    private val db: FirebaseFirestore,
    scope: CoroutineScope,
    private val today: () -> LocalDate = { LocalDate.now() },
) : FinanceRepository {

    init {
        scope.launch {
            auth.user.map { it?.uid }.distinctUntilChanged().collect { uid ->
                if (uid != null) {
                    runCatching { ensureDefaults(uid) }.onFailure { Log.w(TAG, "Default data not written", it) }
                }
            }
        }
    }

    override val categories: Flow<List<Category>> = perUser({ it.collection(CATEGORIES) }, ::categoryFrom)

    override val accounts: Flow<List<Account>> = perUser({ it.collection(ACCOUNTS) }, ::accountFrom)

    // Более старые документы не читаем, чтобы не тратить квоту: экраны их всё равно не показывают.
    override val transactions: Flow<List<Transaction>> = perUser(
        { user ->
            val since = transactionsWindowStart(today()).toString()
            user.collection(TRANSACTIONS).whereGreaterThanOrEqualTo("date", since)
        },
        ::transactionFrom,
    ).map { list -> list.sortedWith(NewestFirst) }

    override val recurrings: Flow<List<Recurring>> = perUser({ it.collection(RECURRINGS) }, ::recurringFrom)

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

    override suspend fun saveTransaction(transaction: Transaction) {
        val user = currentUserDoc() ?: return
        val saved = if (transaction.id.isBlank()) transaction.copy(id = newTransactionId()) else transaction
        user.collection(TRANSACTIONS).document(saved.id).set(saved.toMap())
            .addOnFailureListener { Log.w(TAG, "saveTransaction failed", it) }
    }

    override suspend fun deleteTransaction(transactionId: String) {
        val user = currentUserDoc() ?: return
        user.collection(TRANSACTIONS).document(transactionId).delete()
            .addOnFailureListener { Log.w(TAG, "deleteTransaction failed", it) }
    }

    /** Заливает демо-данные. Идентификаторы постоянные: повторный вызов перезаписывает те же документы. */
    suspend fun importDemoData() {
        val user = currentUserDoc() ?: return
        val batch = db.batch()
        DemoData.categories.forEach { batch.set(user.collection(CATEGORIES).document(it.id), it.toMap()) }
        DemoData.accounts.forEach { batch.set(user.collection(ACCOUNTS).document(it.id), it.toMap()) }
        DemoData.recurrings.forEach { batch.set(user.collection(RECURRINGS).document(it.id), it.toMap()) }
        DemoData.transactions(today()).forEach {
            batch.set(user.collection(TRANSACTIONS).document(it.id), it.toMap())
        }
        batch.commit().await()
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
        if (uid == null) flowOf(emptyList()) else query(userDoc(uid)).observe(parse)
    }

    private companion object {
        const val CATEGORIES = "categories"
        const val ACCOUNTS = "accounts"
        const val TRANSACTIONS = "transactions"
        const val RECURRINGS = "recurrings"
        val CASH = Account("cash", "Cash", "", AccountType.Checking, Money.Zero)
    }
}

private fun <T> Query.observe(parse: (String, Map<String, Any?>) -> T?): Flow<List<T>> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
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
