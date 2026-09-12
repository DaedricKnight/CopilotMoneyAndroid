package com.artemkhateev.finance.data.firebase

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.artemkhateev.finance.data.auth.AuthCancelledException
import com.artemkhateev.finance.data.auth.AuthException
import com.artemkhateev.finance.data.auth.AuthRepository
import com.artemkhateev.finance.data.auth.AuthUser
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(
    private val context: Context,
    private val auth: FirebaseAuth,
    /** Web client ID из google-services.json; null — вход через Google в проекте не включён. */
    private val webClientId: String?,
) : AuthRepository {

    private val currentUser = MutableStateFlow(auth.currentUser?.toAuthUser())
    override val user: StateFlow<AuthUser?> = currentUser.asStateFlow()

    init {
        auth.addAuthStateListener { currentUser.value = it.currentUser?.toAuthUser() }
    }

    override suspend fun signInWithEmail(email: String, password: String) {
        authCall { auth.signInWithEmailAndPassword(email, password).await() }
    }

    override suspend fun createAccount(email: String, password: String) {
        authCall { auth.createUserWithEmailAndPassword(email, password).await() }
    }

    override suspend fun sendPasswordReset(email: String) {
        authCall { auth.sendPasswordResetEmail(email).await() }
    }

    override suspend fun signInWithGoogle(activity: Activity) {
        authCall {
            val clientId = webClientId
                ?: throw AuthException("Google sign-in isn't enabled for this Firebase project")
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
                .build()
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                throw AuthException("Google returned an unexpected credential")
            }
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        }
    }

    override suspend fun signOut() {
        auth.signOut()
        // Иначе при следующем входе окно выбора молча подставит тот же аккаунт.
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    private suspend fun <T> authCall(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: AuthException) {
        throw e
    } catch (e: GetCredentialCancellationException) {
        throw AuthCancelledException()
    } catch (e: Exception) {
        throw AuthException(e.toUserMessage(), e)
    }
}

private fun FirebaseUser.toAuthUser() = AuthUser(uid = uid, email = email, displayName = displayName)

private fun Exception.toUserMessage(): String = when (this) {
    is FirebaseAuthWeakPasswordException -> "Password must be at least 6 characters"
    is FirebaseAuthUserCollisionException -> "An account with this email already exists"
    is FirebaseAuthInvalidUserException -> "No account with this email"
    is FirebaseAuthInvalidCredentialsException ->
        if (errorCode == "ERROR_INVALID_EMAIL") "This email doesn't look right" else "Wrong email or password"
    is FirebaseNetworkException -> "No connection. Check the network and try again"
    is FirebaseTooManyRequestsException -> "Too many attempts. Try again later"
    is NoCredentialException -> "No Google account on this device"
    is GetCredentialException -> "Google sign-in failed: ${errorMessage ?: type}"
    else -> message ?: "Something went wrong"
}
