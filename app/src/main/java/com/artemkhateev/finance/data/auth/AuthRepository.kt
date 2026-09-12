package com.artemkhateev.finance.data.auth

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

data class AuthUser(val uid: String, val email: String?, val displayName: String?)

/** Ошибка входа с текстом, который можно показать пользователю. */
class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Пользователь сам закрыл окно выбора аккаунта — это не ошибка. */
class AuthCancelledException : Exception()

interface AuthRepository {
    /** Текущий пользователь; null — вход не выполнен. */
    val user: StateFlow<AuthUser?>

    suspend fun signInWithEmail(email: String, password: String)
    suspend fun createAccount(email: String, password: String)
    suspend fun sendPasswordReset(email: String)

    /** Окно выбора Google-аккаунта системное, поэтому нужна Activity, а не любой Context. */
    suspend fun signInWithGoogle(activity: Activity)
    suspend fun signOut()
}
