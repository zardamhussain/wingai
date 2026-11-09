package com.wingai.keyboard.auth

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GoogleCredentialControllerTest {
    private suspend fun withController(block: suspend (GoogleCredentialController, FakeManager, Activity) -> Unit) {
        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = activityController.get()
            val manager = FakeManager()
            block(GoogleCredentialController(activity, manager), manager, activity)
        } finally { activityController.pause().stop().destroy() }
    }

    @Test fun usesGeneratedWebClientIdAndExplicitGoogleOption() = runTest {
        withController { controller, manager, activity ->
            assertEquals("test-google-token", controller.requestIdToken())
            assertSame(activity, manager.context)
            val option = manager.request!!.credentialOptions.single() as GetSignInWithGoogleOption
            assertEquals(googleWebClientId(activity), option.serverClientId)
            assertTrue(option.serverClientId.endsWith(".apps.googleusercontent.com"))
            assertTrue(option.nonce!!.length >= 40)
        }
    }
    @Test fun freshNonceForEveryChooser() = runTest {
        withController { controller, manager, _ ->
            controller.requestIdToken()
            val first = (manager.request!!.credentialOptions.single() as GetSignInWithGoogleOption).nonce
            controller.requestIdToken()
            val second = (manager.request!!.credentialOptions.single() as GetSignInWithGoogleOption).nonce
            assertNotEquals(first, second)
        }
    }
    @Test fun dismissalReturnsNull() = runTest {
        withController { controller, manager, _ ->
            manager.failure = GetCredentialCancellationException()
            assertNull(controller.requestIdToken())
        }
    }
    @Test fun missingAccountExplainsEmailFallback() = runTest {
        withController { controller, manager, _ ->
            manager.failure = NoCredentialException()
            try { controller.requestIdToken(); fail("Expected fallback") }
            catch (error: GoogleSignInProblem) { assertTrue(error.message!!.contains("Add one in your device settings")) }
        }
    }
    @Test fun unavailablePlayServicesExplainedSafely() = runTest {
        withController { controller, manager, _ ->
            manager.failure = GetCredentialProviderConfigurationException("secret SDK detail")
            try { controller.requestIdToken(); fail("Expected fallback") }
            catch (error: GoogleSignInProblem) {
                assertTrue(error.message!!.contains("Google Play services"))
                assertFalse(error.message!!.contains("secret"))
            }
        }
    }
    @Test fun unsupportedDeviceKeepsEmailFallback() = runTest {
        withController { controller, manager, _ ->
            manager.failure = GetCredentialUnsupportedException()
            try { controller.requestIdToken(); fail("Expected fallback") }
            catch (error: GoogleSignInProblem) { assertTrue(error.message!!.contains("email")) }
        }
    }
    @Test fun rawProviderExceptionNotExposed() = runTest {
        withController { controller, manager, _ ->
            manager.failure = GetCredentialUnknownException("private-token")
            try { controller.requestIdToken(); fail("Expected failure") }
            catch (error: GoogleSignInProblem) { assertFalse(error.message!!.contains("private-token")) }
        }
    }
    @Test fun malformedGoogleCredentialRejected() = runTest {
        withController { controller, manager, _ ->
            manager.response = GetCredentialResponse(CustomCredential(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL, Bundle()))
            try { controller.requestIdToken(); fail("Expected failure") }
            catch (error: GoogleSignInProblem) { assertTrue(error.message!!.contains("response")) }
        }
    }
    @Test fun unrelatedCredentialRejected() = runTest {
        withController { controller, manager, _ ->
            manager.response = GetCredentialResponse(PasswordCredential("user", "secret"))
            try { controller.requestIdToken(); fail("Expected failure") }
            catch (error: GoogleSignInProblem) { assertTrue(error.message!!.contains("unsupported credential")) }
        }
    }
    @Test fun lifecycleCancellationPropagates() = runTest {
        withController { controller, manager, _ ->
            manager.failure = CancellationException("Activity destroyed")
            try { controller.requestIdToken(); fail("Expected cancellation") }
            catch (_: CancellationException) { }
        }
    }
    @Test fun unexpectedProviderRuntimeExceptionIsContained() = runTest {
        withController { controller, manager, _ ->
            manager.failure = IllegalStateException("private SDK details")
            try { controller.requestIdToken(); fail("Expected a safe error") }
            catch (error: GoogleSignInProblem) {
                assertTrue(error.message!!.contains("GOOGLE_START"))
                assertFalse(error.message!!.contains("private SDK details"))
            }
        }
    }
    @Test fun firebaseCollisionMessageDoesNotSuggestUnsafeAutomaticMerge() {
        val message = accountError(FirebaseAuthException("ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL", "private detail"))
        assertTrue(message.contains("another sign-in method")); assertFalse(message.contains("private detail"))
    }
    @Test fun wrongGoogleIdentityHasActionableDeletionError() {
        val message = accountError(FirebaseAuthException("ERROR_USER_MISMATCH", "private detail"))
        assertTrue(message.contains("same Google account")); assertFalse(message.contains("private detail"))
    }
}

private class FakeManager : CredentialManager by mock(CredentialManager::class.java) {
    var request: GetCredentialRequest? = null
    var context: Context? = null
    var failure: Exception? = null
    var response = GetCredentialResponse(GoogleIdTokenCredential.Builder()
        .setId("ada@example.com").setIdToken("test-google-token").build())
    override suspend fun getCredential(context: Context, request: GetCredentialRequest): GetCredentialResponse {
        this.context = context; this.request = request
        failure?.let { throw it }
        return response
    }
}
