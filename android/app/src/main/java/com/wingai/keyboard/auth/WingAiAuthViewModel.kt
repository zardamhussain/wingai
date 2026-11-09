package com.wingai.keyboard.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.currentCoroutineContext

internal enum class AuthMode { SIGN_IN, REGISTER, RESET }

internal data class AccountState(
    val user: AccountUser? = null,
    val mode: AuthMode = AuthMode.SIGN_IN,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val profileWarning: String? = null,
    val profileSyncing: Boolean = false,
    val wingaiDeviceId: String? = null,
    val authProgress: String? = null,
    val isChoosingGoogle: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val nameError: String? = null,
    val confirmError: String? = null,
    val verificationCooldown: Int = 0,
    val available: Boolean = true,
    val googleAvailable: Boolean = true
)

internal class WingAiAuthViewModel(private val gateway: AccountGateway?) : ViewModel() {
    private val mutableState = MutableStateFlow(AccountState(
        user = gateway?.currentUser,
        available = gateway != null,
        googleAvailable = gateway?.googleAvailable == true,
        error = if (gateway == null) "Account sign-in is unavailable in this build. You can still use your keyboard." else null
    ))
    val state = mutableState.asStateFlow()
    private var cooldownJob: Job? = null
    private var profileJob: Job? = null
    private var googlePickerJob: Job? = null

    init {
        if (gateway != null) viewModelScope.launch {
            gateway.users.collect { user ->
                if (user?.uid != state.value.user?.uid) {
                    profileJob?.cancel()
                    mutableState.update { it.copy(user = user, profileSyncing = false, wingaiDeviceId = null, profileWarning = null) }
                } else mutableState.update { it.copy(user = user) }
            }
        }
        // Backfill the device ID for an existing restored login without forcing a sign-out.
        if (gateway?.currentUser != null) startProfileSync()
    }

    fun mode(mode: AuthMode) {
        if (state.value.busy) return
        mutableState.update { it.copy(mode = mode, error = null, notice = null,
            emailError = null, passwordError = null, nameError = null, confirmError = null) }
    }

    fun submit(email: String, password: String, name: String, confirm: String) {
        if (state.value.busy || gateway == null) return
        val mode = state.value.mode
        val emailError = AuthValidation.email(email)
        val passwordError = when (mode) {
            AuthMode.REGISTER -> AuthValidation.newPassword(password)
            AuthMode.SIGN_IN -> if (password.isEmpty()) "Enter your password." else null
            AuthMode.RESET -> null
        }
        val nameError = if (mode == AuthMode.REGISTER) AuthValidation.name(name) else null
        val confirmError = if (mode == AuthMode.REGISTER && password != confirm) "Passwords don't match." else null
        mutableState.update { it.copy(emailError = emailError, passwordError = passwordError,
            nameError = nameError, confirmError = confirmError, error = null, notice = null) }
        if (listOf(emailError, passwordError, nameError, confirmError).any { it != null }) return
        perform {
            when (mode) {
                AuthMode.SIGN_IN -> {
                    gateway.signIn(email, password)
                    mutableState.update { it.copy(user = gateway.currentUser, notice = "Welcome back. You're signed in.") }
                    startProfileSync()
                }
                AuthMode.REGISTER -> {
                    gateway.register(email, password, name)
                    mutableState.update { it.copy(user = gateway.currentUser) }
                    try {
                        gateway.sendVerification()
                        startVerificationCooldown()
                        mutableState.update { it.copy(notice = "Account created. Check your inbox to verify your email.") }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        mutableState.update { it.copy(notice = "Account created. Tap Send verification email to verify your address.") }
                    }
                    startProfileSync()
                }
                AuthMode.RESET -> {
                    gateway.resetPassword(email)
                    mutableState.update { it.copy(notice = "If an account exists for this email, you'll receive a password reset link. Check your inbox and spam folder.") }
                }
            }
        }
    }

    fun sendVerification() {
        if (state.value.verificationCooldown > 0) return
        perform {
            requireNotNull(gateway).sendVerification()
            startVerificationCooldown()
            mutableState.update { it.copy(notice = "Verification email sent. Check your inbox and spam folder.") }
        }
    }

    fun refreshVerification() = perform {
        requireNotNull(gateway).reload()
        mutableState.update { it.copy(user = gateway.currentUser, notice =
            if (gateway.currentUser?.verified == true) "Email verified. You're all set."
            else "Your email isn't verified yet. Open the link in your inbox, then check again.") }
    }

    fun retryProfile() {
        if (!state.value.busy && !state.value.profileSyncing) startProfileSync()
    }

    private fun startProfileSync() {
        val expectedUid = gateway?.currentUser?.uid ?: return
        if (profileJob?.isActive == true) return
        mutableState.update { it.copy(profileSyncing = true, profileWarning = null) }
        profileJob = viewModelScope.launch {
            try {
                val deviceId = withTimeout(12_000) { gateway.syncProfile() }
                if (gateway.currentUser?.uid == expectedUid) mutableState.update {
                    it.copy(wingaiDeviceId = deviceId, profileWarning = null)
                }
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                if (gateway.currentUser?.uid == expectedUid) mutableState.update {
                    it.copy(profileWarning = if (error is TimeoutCancellationException)
                        "You're signed in. Saving your device ID took too long; the save isn't confirmed. Retry when connected. [PROFILE_TIMEOUT]"
                        else profileSyncError(error))
                }
            } finally {
                if (gateway.currentUser?.uid == expectedUid) mutableState.update { it.copy(profileSyncing = false) }
            }
        }
    }

    fun signOut() = perform {
        profileJob?.cancel()
        requireNotNull(gateway).signOut()
        cooldownJob?.cancel()
        mutableState.value = AccountState(busy = true, googleAvailable = gateway.googleAvailable,
            notice = "Signed out. Your keyboard, settings and device plan are unchanged.")
    }

    fun deleteAccount(password: String) {
        if (password.isEmpty()) {
            mutableState.update { it.copy(error = "Enter your current password to confirm deletion.") }
            return
        }
        perform {
            profileJob?.cancel()
            requireNotNull(gateway).deleteAccount(password)
            cooldownJob?.cancel()
            mutableState.value = AccountState(busy = true, googleAvailable = gateway.googleAvailable, notice =
                "Your login account and cloud profile were deleted. Device settings and the existing device plan were not changed.")
        }
    }

    /** The caller's Activity lifecycle owns the picker. No Activity or ID token is retained in state. */
    suspend fun googleAction(delete: Boolean = false, requestToken: suspend () -> String?) {
        if (state.value.busy || gateway == null || !gateway.googleAvailable) return
        val expectedUid = if (delete) gateway.currentUser?.uid ?: return else null
        if (delete && gateway.currentUser?.usesGoogle != true) return
        mutableState.update { it.copy(busy = true, error = null, notice = null,
            emailError = null, passwordError = null, nameError = null, confirmError = null,
            isChoosingGoogle = true, authProgress = "Choose your Google account to continue.") }
        try {
            googlePickerJob = currentCoroutineContext()[Job]
            // Bounded provider wait plus a visible Cancel action; no indefinite frozen form.
            val token = withTimeout(120_000) { requestToken() } ?: return
            googlePickerJob = null
            mutableState.update { it.copy(isChoosingGoogle = false,
                authProgress = if (delete) "Confirming your Google account..." else "Signing in with Google...") }
            withTimeout(40_000) {
                if (delete) {
                    profileJob?.cancel()
                    gateway.deleteGoogleAccount(token, requireNotNull(expectedUid))
                    cooldownJob?.cancel()
                    mutableState.value = AccountState(busy = true, googleAvailable = gateway.googleAvailable,
                        notice = "Your WingAI login and cloud profile were deleted. Your Google account, keyboard and device plan are unchanged.")
                } else {
                    gateway.signInGoogle(token)
                    mutableState.update { it.copy(user = gateway.currentUser, notice = "You're signed in with Google.") }
                    startProfileSync()
                }
            }
        } catch (error: TimeoutCancellationException) {
            mutableState.update { it.copy(error = if (it.isChoosingGoogle)
                "Google's account chooser didn't respond. Update Google Play services and try again. [GOOGLE_CHOOSER_TIMEOUT]"
                else "Sign-in is taking too long. Check your connection and try again. [GOOGLE_AUTH_TIMEOUT]") }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.update { it.copy(error = accountError(error)) }
        } finally {
            googlePickerJob = null
            mutableState.update { it.copy(busy = false, user = gateway.currentUser, isChoosingGoogle = false, authProgress = null) }
        }
    }

    fun cancelGoogleSignIn() {
        if (state.value.isChoosingGoogle) googlePickerJob?.cancel()
    }

    private fun startVerificationCooldown() {
        cooldownJob?.cancel()
        mutableState.update { it.copy(verificationCooldown = 60) }
        cooldownJob = viewModelScope.launch {
            repeat(60) {
                delay(1_000)
                mutableState.update { it.copy(verificationCooldown = (it.verificationCooldown - 1).coerceAtLeast(0)) }
            }
        }
    }

    private fun perform(block: suspend () -> Unit) {
        if (state.value.busy || gateway == null) return
        mutableState.update { it.copy(busy = true, error = null, notice = null) }
        viewModelScope.launch {
            try {
                withTimeout(40_000) { block() }
            } catch (error: TimeoutCancellationException) {
                mutableState.update { it.copy(error = "This is taking too long. Check your connection and try again.") }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(user = gateway.currentUser, error = accountError(error)) }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    class Factory(private val gateway: AccountGateway?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = WingAiAuthViewModel(gateway) as T
    }
}
