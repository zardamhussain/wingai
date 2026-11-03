package com.wingai.keyboard.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import helium314.keyboard.latin.R
import java.security.SecureRandom
import kotlinx.coroutines.CancellationException
import android.util.Base64

/** Only launches after an explicit tap. The Activity owns chooser lifetime, never the ViewModel. */
internal class GoogleCredentialController(
    private val activity: Activity,
    private val manager: CredentialManager? = null
) {

    suspend fun requestIdToken(): String? {
        return try {
        // Some providers fail during initialization. Create them only after a tap and
        // inside the guarded request, never in Activity.onCreate where they can close the app.
        val credentialManager = manager ?: CredentialManager.create(activity)
        val webClientId = googleWebClientId(activity)
        if (webClientId.isBlank()) throw GoogleSignInProblem("Google sign-in isn't configured in this build.")
        // An independent nonce per request; tokens are passed directly to Firebase, not saved/logged.
        val nonceBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val nonce = Base64.encodeToString(nonceBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val option = GetSignInWithGoogleOption.Builder(webClientId).setNonce(nonce).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val credential = credentialManager.getCredential(activity, request).credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                throw GoogleSignInProblem("Google returned an unsupported credential. Please try again.")
            }
            GoogleIdTokenCredential.createFrom(credential.data).idToken.also {
                if (it.isBlank()) throw GoogleSignInProblem("Google didn't return a sign-in token. Please try again.")
            }
        } catch (_: GetCredentialCancellationException) {
            null // Closing the chooser is not an authentication failure.
        } catch (_: NoCredentialException) {
            throw GoogleSignInProblem("No Google account is available. Add one in your device settings and try again, or sign in with email.")
        } catch (_: GetCredentialProviderConfigurationException) {
            throw GoogleSignInProblem("Google sign-in is unavailable. Update Google Play services, or sign in with email.")
        } catch (_: GetCredentialUnsupportedException) {
            throw GoogleSignInProblem("This device doesn't support Google sign-in. You can still sign in with email.")
        } catch (_: GoogleIdTokenParsingException) {
            throw GoogleSignInProblem("We couldn't read the Google sign-in response. Please try again.")
        } catch (_: GetCredentialException) {
            throw GoogleSignInProblem("Google sign-in couldn't finish. Check your connection and try again. If it persists, check the Firebase Google provider and signing certificate. [GOOGLE_PROVIDER]")
        } catch (error: CancellationException) {
            throw error
        } catch (error: GoogleSignInProblem) {
            throw error
        } catch (_: Exception) {
            // Safe category only: do not expose provider exception text, account details or tokens.
            throw GoogleSignInProblem("Google sign-in couldn't start or process the response. Update Google Play services and try again, or use email login. [GOOGLE_START]")
        }
    }
}

internal class GoogleSignInProblem(message: String) : Exception(message)

internal fun googleWebClientId(context: Context): String =
    context.getString(R.string.default_web_client_id).trim()

internal suspend fun clearGoogleCredentialState(context: Context) {
    CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
}
