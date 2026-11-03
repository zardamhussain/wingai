package com.wingai.keyboard.auth

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.os.UserManager
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseOptions
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wingai.keyboard.settings.SettingsRepository
import java.util.concurrent.ConcurrentHashMap

internal data class AccountUser(
    val uid: String, val email: String, val name: String, val verified: Boolean,
    val providers: Set<String> = setOf("password")
) {
    val usesGoogle: Boolean get() = "google.com" in providers
}

/** No changes to SettingsRepository, the device ID, AI transport or backend entitlements. */
internal interface AccountGateway {
    val users: Flow<AccountUser?>
    val currentUser: AccountUser?
    val googleAvailable: Boolean
    suspend fun signIn(email: String, password: String)
    suspend fun signInGoogle(idToken: String)
    suspend fun register(email: String, password: String, name: String)
    suspend fun resetPassword(email: String)
    suspend fun sendVerification()
    suspend fun reload()
    /** Returns the device ID only after the server acknowledges the profile transaction. */
    suspend fun syncProfile(): String
    suspend fun deleteAccount(password: String)
    suspend fun deleteGoogleAccount(idToken: String, expectedUid: String)
    suspend fun signOut()
}

internal object WingAiFirebase {
    private const val APP_NAME = "wingai-account"
    // SDK Tasks can outlive a coroutine timeout or Activity. Drain them before deletion.
    val profileWrites = ConcurrentHashMap<String, Task<Void?>>()

    @SuppressLint("WrongConstant") // AOSP credential-storage flag; asserted at runtime and covered on API 28/35.
    @Synchronized
    fun app(context: Context): FirebaseApp {
        check(context.getSystemService(UserManager::class.java).isUserUnlocked) {
            "Unlock your device to access your account."
        }
        FirebaseApp.getApps(context).firstOrNull { it.name == APP_NAME }?.let { return it }
        // Keep the legacy application's default DEVICE storage unchanged. Android's
        // ContextImpl uses flag 0x10 for credential storage (API 24+); its convenience
        // method is not in the public SDK. createPackageContext accepts the storage flag.
        // Assert the result rather than ever persisting auth tokens in device storage.
        val storageContext = context.createPackageContext(context.packageName, 0x10)
        check(!storageContext.isDeviceProtectedStorage) { "Credential storage unavailable" }
        // Firebase internally calls getApplicationContext(), which must not switch back.
        val credentialContext = object : ContextWrapper(
            storageContext
        ) {
            override fun getApplicationContext(): Context = this
        }
        val options = requireNotNull(FirebaseOptions.fromResource(credentialContext)) {
            "Firebase configuration is missing from this build."
        }
        return FirebaseApp.initializeApp(credentialContext, options, APP_NAME)
    }

    fun auth(context: Context): FirebaseAuth = FirebaseAuth.getInstance(app(context))

    @SuppressLint("StaticFieldLeak") // Only the application-scoped credential Context, never an Activity.
    private var database: FirebaseFirestore? = null
    @Synchronized
    fun firestore(context: Context): FirebaseFirestore = database ?: FirebaseFirestore.getInstance(app(context)).apply {
        // Account metadata must not be queued to disk across account switches/offline sessions.
        firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            .build()
        database = this
    }
}

internal class WingAiAuthRepository(
    context: Context,
    private val auth: FirebaseAuth = WingAiFirebase.auth(context),
    private val loadDeviceId: suspend () -> String = { SettingsRepository(context.applicationContext).getOrCreateUserId() },
    private val database: () -> FirebaseFirestore = { WingAiFirebase.firestore(context.applicationContext) },
    private val clearCredentials: suspend () -> Unit = { clearGoogleCredentialState(context.applicationContext) }
) : AccountGateway {
    private val context = context.applicationContext
    private val db get() = database()
    override val currentUser get() = auth.currentUser?.snapshot()
    override val googleAvailable get() = googleWebClientId(context).isNotBlank()

    override val users: Flow<AccountUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.snapshot()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    override suspend fun signInGoogle(idToken: String) {
        require(idToken.isNotBlank())
        // Firebase validates signature, audience and provider. Never trust decoded client claims.
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    override suspend fun register(email: String, password: String, name: String) {
        val user = requireNotNull(auth.createUserWithEmailAndPassword(email.trim(), password).await().user)
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await()
    }

    override suspend fun resetPassword(email: String) {
        try {
            auth.sendPasswordResetEmail(email.trim()).await()
        } catch (error: FirebaseAuthException) {
            // Same public result whether the email exists or not.
            if (error.errorCode != "ERROR_USER_NOT_FOUND") throw error
        }
    }

    override suspend fun sendVerification() { requireNotNull(auth.currentUser).sendEmailVerification().await() }

    override suspend fun reload() {
        val user = requireNotNull(auth.currentUser)
        user.reload().await()
        user.getIdToken(true).await() // Refresh verified-email claims for security rules.
    }

    override suspend fun syncProfile(): String = withContext(Dispatchers.IO) {
        val user = requireNotNull(auth.currentUser)
        // Read the same application-context DataStore used by the keyboard/Worker account.
        // Login must never replace that saved ID or create a second credential-storage ID.
        val deviceId = loadDeviceId()
        check(auth.currentUser?.uid == user.uid) { "Account changed during profile sync" }
        val document = db.collection("users").document(user.uid)
        val name = user.displayName.orEmpty().take(60)
        val email = user.email.orEmpty()
        // Transactions require a server connection; no persistent offline write queue.
        val write = requireNotNull(WingAiFirebase.profileWrites.compute(user.uid) { _, pending ->
            if (pending != null && !pending.isComplete) pending else db.runTransaction<Void?> { transaction ->
                val existing = transaction.get(document)
                val profile = accountProfileData(name, email, deviceId, isNew = !existing.exists())
                if (existing.exists()) transaction.update(document, profile) else transaction.set(document, profile)
                null
            }
        })
        write.addOnCompleteListener { WingAiFirebase.profileWrites.remove(user.uid, write) }
        write.await()
        deviceId
    }

    override suspend fun deleteAccount(password: String) {
        val user = requireNotNull(auth.currentUser)
        user.reauthenticate(EmailAuthProvider.getCredential(requireNotNull(user.email), password)).await()
        deleteReauthenticatedUser(user)
    }

    override suspend fun deleteGoogleAccount(idToken: String, expectedUid: String) {
        val user = requireNotNull(auth.currentUser)
        check(user.uid == expectedUid) { "Account changed during confirmation" }
        // Reauthenticate the SAME FirebaseUser; choosing another Google account must not
        // switch identities or delete that other account. Firebase rejects a UID mismatch.
        user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
        check(auth.currentUser?.uid == expectedUid) { "Account changed during confirmation" }
        deleteReauthenticatedUser(user)
    }

    private suspend fun deleteReauthenticatedUser(user: FirebaseUser) {
        WingAiFirebase.profileWrites[user.uid]?.let { pending ->
            try { pending.await() } catch (error: Exception) {
                if (error is CancellationException) throw error
                // A completed failed sync is safe to discard before deleting the profile.
            }
        }
        val document = db.collection("users").document(user.uid)
        db.runTransaction { transaction ->
            transaction.get(document)
            transaction.delete(document)
        }.await()
        // Delete profile first so an unauthenticated client is never needed for cleanup.
        // If Auth deletion fails, the account remains usable and deletion can be retried.
        user.delete().await()
        clearCredentialSession()
    }

    override suspend fun signOut() {
        auth.signOut()
        clearCredentialSession()
    }

    private suspend fun clearCredentialSession() {
        // Local Firebase sign-out/deletion must not be undone by an unavailable provider.
        // The next explicit button tap uses a chooser, never automatic sign-in.
        try { withTimeoutOrNull(5_000) { clearCredentials() } }
        catch (error: Exception) { if (error is CancellationException) throw error }
    }

    private fun FirebaseUser.snapshot() = AccountUser(uid, email.orEmpty(), displayName.orEmpty(), isEmailVerified,
        providerData.map { it.providerId }.toSet())
}

/** Authentication and database failures are distinct: never present a rules denial as a bad Google login. */
internal fun profileSyncError(error: Throwable): String = when (error) {
    is FirebaseFirestoreException -> when (error.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "You're signed in, but Firestore rejected saving your profile/device ID. Publish the updated users rules for this build. [PROFILE_PERMISSION_DENIED]"
        FirebaseFirestoreException.Code.UNAUTHENTICATED ->
            "Your login session needs refreshing before saving the device ID. Sign out and sign in again. [PROFILE_UNAUTHENTICATED]"
        FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            "You're signed in. The database is unavailable; your device ID hasn't been confirmed saved. Check your connection and retry. [PROFILE_UNAVAILABLE]"
        else -> "You're signed in, but the device ID hasn't been confirmed saved. Retry profile sync. [PROFILE_${error.code.name}]"
    }
    is IllegalArgumentException -> "You're signed in, but the saved device ID could not be synced. Your existing ID was not changed. [PROFILE_DEVICE_ID]"
    else -> "You're signed in, but your profile/device ID hasn't been confirmed saved. Check your connection and retry. [PROFILE_SYNC]"
}

/** Never expose SDK exception text, tokens, backend configuration or account existence. */
internal fun accountError(error: Throwable): String = when (error) {
    is GoogleSignInProblem -> error.message ?: "Google sign-in couldn't finish. Please try again."
    is FirebaseNetworkException -> "No connection. Check your internet and try again."
    is FirebaseTooManyRequestsException -> "Too many attempts. Please wait a moment and try again."
    is FirebaseAuthException -> when (error.errorCode) {
        "ERROR_WEAK_PASSWORD" -> "Choose a stronger password that meets the account password policy."
        "ERROR_INVALID_EMAIL" -> "Enter a valid email address."
        "ERROR_OPERATION_NOT_ALLOWED", "ERROR_CONFIGURATION_NOT_FOUND" ->
            "This sign-in provider isn't enabled yet. Please contact WingAI support."
        "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
            "An account already uses this email with another sign-in method. Use that method to sign in; no accounts have been merged."
        "ERROR_USER_MISMATCH" -> "Choose the same Google account you're currently signed in with to confirm deletion."
        "ERROR_REQUIRES_RECENT_LOGIN" -> "Please sign in again before making this change."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "Unable to create this account. Try signing in or resetting your password."
        "ERROR_USER_DISABLED", "ERROR_USER_NOT_FOUND", "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_INVALID_LOGIN_CREDENTIALS" ->
            "Couldn't sign in. Check your email and password, or reset your password."
        else -> "We couldn't complete that request. Please try again."
    }
    else -> "We couldn't complete that request. Check your connection and try again."
}
