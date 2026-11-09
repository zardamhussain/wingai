package com.wingai.keyboard.auth

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/** Test the real Firebase adapter with mocked SDK endpoints, never contact production. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GoogleFirebaseAdapterTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun googleTokenIsExchangedWithFirebaseGoogleProvider() = runTest {
        val auth = mock(FirebaseAuth::class.java)
        `when`(auth.signInWithCredential(any(AuthCredential::class.java))).thenReturn(Tasks.forResult(mock(AuthResult::class.java)))
        WingAiAuthRepository(context, auth).signInGoogle("test-token")
        val credential = ArgumentCaptor.forClass(AuthCredential::class.java)
        verify(auth).signInWithCredential(credential.capture())
        assertEquals(GoogleAuthProvider.PROVIDER_ID, credential.value.provider)
        verify(auth, never()).signInWithEmailAndPassword(anyString(), anyString())
    }
    @Test fun blankGoogleTokenNeverReachesFirebase() = runTest {
        val auth = mock(FirebaseAuth::class.java)
        try { WingAiAuthRepository(context, auth).signInGoogle(""); fail("Expected rejection") }
        catch (_: IllegalArgumentException) { }
        verifyNoInteractions(auth)
    }
    @Test fun wrongGoogleIdentityFailsReauthBeforeAnyDeletionOrSignIn() = runTest {
        val auth = mock(FirebaseAuth::class.java)
        val user = mock(FirebaseUser::class.java)
        `when`(auth.currentUser).thenReturn(user)
        `when`(user.uid).thenReturn("current-user")
        `when`(user.reauthenticate(any(AuthCredential::class.java))).thenReturn(
            Tasks.forException(FirebaseAuthException("ERROR_USER_MISMATCH", "Wrong user")))
        try {
            WingAiAuthRepository(context, auth).deleteGoogleAccount("other-user-token", "current-user")
            fail("Must reject before any delete")
        } catch (error: FirebaseAuthException) { assertEquals("ERROR_USER_MISMATCH", error.errorCode) }
        verify(user).reauthenticate(any(AuthCredential::class.java))
        verify(user, never()).delete()
        verify(auth, never()).signInWithCredential(any(AuthCredential::class.java))
    }
    @Test fun changedCurrentAccountRejectedBeforeReauthentication() = runTest {
        val auth = mock(FirebaseAuth::class.java)
        val user = mock(FirebaseUser::class.java)
        `when`(auth.currentUser).thenReturn(user); `when`(user.uid).thenReturn("different-user")
        try { WingAiAuthRepository(context, auth).deleteGoogleAccount("token", "original-user"); fail("Must reject") }
        catch (_: IllegalStateException) { }
        verify(user, never()).reauthenticate(any(AuthCredential::class.java))
        verify(user, never()).delete()
    }
    @Test fun providerSnapshotIdentifiesGoogleOnlyAccounts() {
        val auth = mock(FirebaseAuth::class.java)
        val user = mock(FirebaseUser::class.java)
        val provider = mock(UserInfo::class.java)
        `when`(provider.providerId).thenReturn("google.com")
        `when`(user.uid).thenReturn("uid")
        `when`(user.providerData).thenReturn(listOf(provider))
        `when`(auth.currentUser).thenReturn(user)
        assertTrue(WingAiAuthRepository(context, auth).currentUser!!.usesGoogle)
    }
    @Test fun signOutClearsFirebaseBeforeCredentialManager() = runTest {
        val auth = mock(FirebaseAuth::class.java)
        var cleared = false
        val repository = WingAiAuthRepository(context, auth) {
            verify(auth).signOut(); cleared = true
        }
        repository.signOut()
        assertTrue(cleared)
    }
    @Test fun providerClearFailureDoesNotPreventLocalSignOut() = runTest {
        val auth = mock(FirebaseAuth::class.java)
        WingAiAuthRepository(context, auth) { throw IOException("No credential provider") }.signOut()
        verify(auth).signOut()
    }
    @Test fun credentialClearIsBounded() = runTest {
        val auth = mock(FirebaseAuth::class.java)
        WingAiAuthRepository(context, auth) { delay(30_000) }.signOut()
        verify(auth).signOut()
        assertEquals(5_000, testScheduler.currentTime)
    }
}
