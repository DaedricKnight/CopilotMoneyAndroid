package com.artemkhateev.finance.feature.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artemkhateev.finance.data.auth.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SignInMode { SignIn, CreateAccount }

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val mode: SignInMode = SignInMode.SignIn,
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

class SignInViewModel(private val auth: AuthRepository) : ViewModel() {

    private val mutableState = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = mutableState.asStateFlow()

    fun onEmailChange(value: String) = mutableState.update { it.copy(email = value, error = null, info = null) }

    fun onPasswordChange(value: String) = mutableState.update { it.copy(password = value, error = null, info = null) }

    fun toggleMode() = mutableState.update {
        val next = if (it.mode == SignInMode.SignIn) SignInMode.CreateAccount else SignInMode.SignIn
        it.copy(mode = next, error = null, info = null)
    }

    fun submit() {
        val current = mutableState.value
        val email = current.email.trim()
        if (email.isEmpty() || current.password.isEmpty()) {
            mutableState.update { it.copy(error = "Enter email and password") }
            return
        }
        launchAuth {
            when (current.mode) {
                SignInMode.SignIn -> auth.signInWithEmail(email, current.password)
                SignInMode.CreateAccount -> auth.createAccount(email, current.password)
            }
        }
    }

    fun resetPassword() {
        val email = mutableState.value.email.trim()
        if (email.isEmpty()) {
            mutableState.update { it.copy(error = "Enter your email first") }
            return
        }
        launchAuth(successMessage = "Password reset link sent to $email") { auth.sendPasswordReset(email) }
    }

    fun signInWithGoogle(activity: Activity) = launchAuth { auth.signInWithGoogle(activity) }

    private fun launchAuth(successMessage: String? = null, block: suspend () -> Unit) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            val outcome = try {
                block()
                // Пароль в памяти после входа не нужен.
                mutableState.value.copy(password = "", info = successMessage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value.copy(error = e.message ?: "Something went wrong")
            }
            mutableState.value = outcome.copy(busy = false)
        }
    }
}
