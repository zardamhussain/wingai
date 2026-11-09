package com.wingai.keyboard.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class WingAiAuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var gateway: FakeAccountGateway
    private lateinit var vm: WingAiAuthViewModel
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        gateway = FakeAccountGateway()
        vm = WingAiAuthViewModel(gateway)
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun signedOutInitially() { assertNull(vm.state.value.user) }
    @Test fun missingConfigIsNonFatal() {
        val unavailable = WingAiAuthViewModel(null)
        assertFalse(unavailable.state.value.available)
        unavailable.submit("a@example.com", "secret", "", "")
        assertFalse(unavailable.state.value.busy)
    }
    @Test fun badEmailNeverReachesGateway() = runTest(dispatcher) {
        vm.submit("bad", "secret", "", ""); advanceUntilIdle()
        assertEquals(0, gateway.signIns); assertNotNull(vm.state.value.emailError)
    }
    @Test fun existingSixCharacterPasswordsStillWork() = runTest(dispatcher) {
        vm.submit("a@example.com", "123456", "", ""); advanceUntilIdle()
        assertEquals(1, gateway.signIns); assertNotNull(vm.state.value.user)
        assertEquals(1, gateway.syncs)
    }
    @Test fun passwordMismatchPreventsRegistration() = runTest(dispatcher) {
        vm.mode(AuthMode.REGISTER)
        vm.submit("a@example.com", "12345678", "Ada", "not the same")
        advanceUntilIdle(); assertEquals(0, gateway.registrations); assertNotNull(vm.state.value.confirmError)
    }
    @Test fun registrationCreatesProfileAndSendsVerification() = runTest(dispatcher) {
        vm.mode(AuthMode.REGISTER)
        vm.submit("a@example.com", "long-password", "Ada", "long-password")
        runCurrent()
        assertEquals(1, gateway.registrations); assertEquals(1, gateway.verifications)
        assertEquals(1, gateway.syncs); assertNotNull(vm.state.value.user)
        assertEquals(60, vm.state.value.verificationCooldown)
        advanceUntilIdle()
    }
    @Test fun duplicateSubmissionSuppressed() = runTest(dispatcher) {
        gateway.delayMillis = 2_000
        repeat(3) { vm.submit("a@example.com", "123456", "", "") }
        advanceUntilIdle(); assertEquals(1, gateway.signIns)
    }
    @Test fun firestoreFailureDoesNotUndoLogin() = runTest(dispatcher) {
        gateway.failSync = true
        vm.submit("a@example.com", "123456", "", ""); advanceUntilIdle()
        assertNotNull(vm.state.value.user); assertNotNull(vm.state.value.profileWarning)
        assertNull(vm.state.value.error); assertFalse(vm.state.value.busy)
    }
    @Test fun failedProfileSyncCanBeRetried() = runTest(dispatcher) {
        gateway.failSync = true
        vm.submit("a@example.com", "123456", "", ""); advanceUntilIdle()
        gateway.failSync = false
        vm.retryProfile(); advanceUntilIdle()
        assertNull(vm.state.value.profileWarning); assertEquals(2, gateway.syncs)
    }
    @Test fun verificationFailureKeepsCreatedAccount() = runTest(dispatcher) {
        gateway.failVerification = true
        vm.mode(AuthMode.REGISTER)
        vm.submit("a@example.com", "12345678", "Ada", "12345678"); advanceUntilIdle()
        assertNotNull(vm.state.value.user); assertTrue(vm.state.value.notice!!.contains("Account created"))
    }
    @Test fun resetDoesNotRequirePasswordOrRevealUserExistence() = runTest(dispatcher) {
        vm.mode(AuthMode.RESET); vm.submit("a@example.com", "", "", ""); advanceUntilIdle()
        assertEquals(1, gateway.resets); assertTrue(vm.state.value.notice!!.startsWith("If an account exists"))
        assertNull(vm.state.value.user)
    }
    @Test fun cooldownBlocksRepeatVerification() = runTest(dispatcher) {
        vm.sendVerification(); runCurrent(); vm.sendVerification(); runCurrent()
        assertEquals(1, gateway.verifications)
        advanceTimeBy(60_000); runCurrent(); vm.sendVerification(); runCurrent()
        assertEquals(2, gateway.verifications); advanceUntilIdle()
    }
    @Test fun verificationCanBeRefreshed() = runTest(dispatcher) {
        vm.submit("a@example.com", "123456", "", ""); advanceUntilIdle()
        vm.refreshVerification(); advanceUntilIdle(); assertTrue(vm.state.value.user!!.verified)
    }
    @Test fun failedSignInShowsSafeErrorAndReenablesForm() = runTest(dispatcher) {
        gateway.failSignIn = true
        vm.submit("a@example.com", "123456", "", ""); advanceUntilIdle()
        assertNull(vm.state.value.user); assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.error!!.contains("private-token")); assertFalse(vm.state.value.busy)
    }
    @Test fun signOutRemovesSession() = runTest(dispatcher) {
        vm.submit("a@example.com", "123456", "", ""); advanceUntilIdle()
        vm.signOut(); advanceUntilIdle(); assertNull(vm.state.value.user); assertEquals(1, gateway.signOuts)
    }
    @Test fun deletionRequiresPassword() = runTest(dispatcher) {
        vm.deleteAccount(""); advanceUntilIdle(); assertEquals(0, gateway.deletions)
    }
    @Test fun failedDeletionKeepsSession() = runTest(dispatcher) {
        vm.submit("a@example.com", "123456", "", ""); advanceUntilIdle()
        gateway.failDeletion = true
        vm.deleteAccount("wrong-password"); advanceUntilIdle()
        assertNotNull(vm.state.value.user); assertNotNull(vm.state.value.error)
    }
    @Test fun successfulDeletionClearsSession() = runTest(dispatcher) {
        vm.submit("a@example.com", "123456", "", ""); advanceUntilIdle()
        vm.deleteAccount("123456"); advanceUntilIdle()
        assertNull(vm.state.value.user); assertEquals(1, gateway.deletions)
    }
    @Test fun timeoutDoesNotLeaveFormBusy() = runTest(dispatcher) {
        gateway.delayMillis = 50_000
        vm.submit("a@example.com", "123456", "", ""); advanceUntilIdle()
        assertFalse(vm.state.value.busy); assertTrue(vm.state.value.error!!.contains("taking too long"))
    }
    @Test fun modeChangeClearsValidation() {
        vm.submit("bad", "", "", ""); vm.mode(AuthMode.RESET)
        assertNull(vm.state.value.emailError); assertNull(vm.state.value.passwordError)
    }

    @Test fun googleSignInCreatesSessionAndSyncsProfile() = runTest(dispatcher) {
        vm.googleAction { "test-token" }; runCurrent()
        assertEquals(1, gateway.googleSignIns); assertEquals(1, gateway.syncs)
        assertTrue(vm.state.value.user!!.usesGoogle); assertFalse(vm.state.value.busy)
        assertFalse(vm.state.value.toString().contains("test-token"))
    }
    @Test fun googleChooserCancellationIsNotAnError() = runTest(dispatcher) {
        vm.googleAction { null }
        assertEquals(0, gateway.googleSignIns); assertFalse(vm.state.value.busy)
        assertNull(vm.state.value.error); assertNull(vm.state.value.user)
    }
    @Test fun googleProviderErrorKeepsEmailLoginAvailable() = runTest(dispatcher) {
        vm.googleAction { throw GoogleSignInProblem("Update Google Play services.") }
        assertEquals("Update Google Play services.", vm.state.value.error)
        assertTrue(vm.state.value.available); assertFalse(vm.state.value.busy)
    }
    @Test fun googleProfileFailureDoesNotUndoAuthentication() = runTest(dispatcher) {
        gateway.failSync = true
        vm.googleAction { "test-token" }; runCurrent()
        assertNotNull(vm.state.value.user); assertNotNull(vm.state.value.profileWarning)
        assertNull(vm.state.value.error)
    }
    @Test fun duplicateGoogleTapsAndEmailSubmissionBlockedWhileChoosing() = runTest(dispatcher) {
        var choosers = 0
        val job = launch { vm.googleAction { choosers++; awaitCancellation() } }
        runCurrent()
        vm.googleAction { choosers++; "test-token" }
        vm.submit("a@example.com", "123456", "", "")
        assertEquals(1, choosers); assertEquals(0, gateway.signIns)
        job.cancel(); runCurrent(); assertFalse(vm.state.value.busy)
    }
    @Test fun googlePickerSurvivesHumanDelayWithoutNetworkTimeout() = runTest(dispatcher) {
        val job = launch { vm.googleAction { delay(90_000); "test-token" } }
        advanceTimeBy(60_000); assertTrue(vm.state.value.busy); assertEquals(0, gateway.googleSignIns)
        advanceUntilIdle(); job.join(); assertTrue(vm.state.value.user!!.usesGoogle)
    }
    @Test fun googleNetworkTimeoutReenablesButtons() = runTest(dispatcher) {
        gateway.delayMillis = 50_000
        vm.googleAction { "test-token" }
        assertFalse(vm.state.value.busy); assertNull(vm.state.value.user)
        assertTrue(vm.state.value.error!!.contains("taking too long"))
    }
    @Test fun googleDeletionReauthenticatesCapturedUserWithoutPassword() = runTest(dispatcher) {
        vm.googleAction { "test-token" }
        vm.googleAction(delete = true) { "fresh-token" }; runCurrent()
        assertEquals("google-uid", gateway.deletedGoogleUid)
        assertEquals(1, gateway.googleDeletions); assertEquals(0, gateway.deletions)
        assertNull(vm.state.value.user)
    }
    @Test fun cancelledGoogleDeletionKeepsAccount() = runTest(dispatcher) {
        vm.googleAction { "test-token" }
        vm.googleAction(delete = true) { null }
        assertEquals(0, gateway.googleDeletions); assertNotNull(vm.state.value.user)
    }
    @Test fun mismatchedGoogleAccountDoesNotDeleteOrSwitchSession() = runTest(dispatcher) {
        vm.googleAction { "test-token" }
        gateway.failDeletion = true
        vm.googleAction(delete = true) { "other-account-token" }
        assertEquals("google-uid", vm.state.value.user!!.uid)
        assertEquals(0, gateway.googleDeletions); assertNotNull(vm.state.value.error)
    }
    @Test fun googleDeletionCannotLaunchForPasswordOnlyUser() = runTest(dispatcher) {
        vm.submit("a@example.com", "123456", "", ""); advanceUntilIdle()
        var launched = false
        vm.googleAction(delete = true) { launched = true; "test-token" }
        assertFalse(launched); assertEquals(0, gateway.googleDeletions)
    }
    @Test fun missingGoogleConfigurationDoesNotLaunchPicker() = runTest(dispatcher) {
        gateway.googleAvailable = false
        val disabled = WingAiAuthViewModel(gateway)
        disabled.googleAction { fail("Must not launch"); null }
        assertFalse(disabled.state.value.googleAvailable); assertFalse(disabled.state.value.busy)
    }

    @Test fun slowFirestoreDoesNotHoldGoogleLoginBusy() = runTest(dispatcher) {
        gateway.syncDelayMillis = 8_000
        vm.googleAction { "test-token" }; runCurrent()
        assertNotNull(vm.state.value.user); assertFalse(vm.state.value.busy)
        assertTrue(vm.state.value.profileSyncing); assertNull(vm.state.value.wingaiDeviceId)
        advanceUntilIdle()
        assertEquals("wing_a1b2c3d4e5", vm.state.value.wingaiDeviceId)
        assertFalse(vm.state.value.profileSyncing)
    }
    @Test fun profileTimeoutIsNotAnAuthenticationError() = runTest(dispatcher) {
        gateway.syncDelayMillis = 50_000
        vm.googleAction { "test-token" }; advanceUntilIdle()
        assertNotNull(vm.state.value.user); assertNull(vm.state.value.error)
        assertTrue(vm.state.value.profileWarning!!.contains("PROFILE_TIMEOUT"))
        assertNull(vm.state.value.wingaiDeviceId); assertFalse(vm.state.value.profileSyncing)
    }
    @Test fun restoredSessionBackfillsDeviceIdWithoutAnotherLogin() = runTest(dispatcher) {
        gateway.users.value = AccountUser("restored", "ada@example.com", "Ada", true)
        val restored = WingAiAuthViewModel(gateway)
        advanceUntilIdle()
        assertEquals(0, gateway.signIns); assertEquals("wing_a1b2c3d4e5", restored.state.value.wingaiDeviceId)
    }
    @Test fun cancelledSyncCannotPublishDeviceIdIntoSignedOutScreen() = runTest(dispatcher) {
        gateway.syncDelayMillis = 8_000
        vm.googleAction { "test-token" }; runCurrent()
        vm.signOut(); advanceUntilIdle()
        assertNull(vm.state.value.user); assertNull(vm.state.value.wingaiDeviceId)
        assertFalse(vm.state.value.profileSyncing)
    }
    @Test fun providerThatNeverReturnsHasBoundedChooserWait() = runTest(dispatcher) {
        vm.googleAction { awaitCancellation() }
        assertTrue(vm.state.value.error!!.contains("GOOGLE_CHOOSER_TIMEOUT"))
        assertFalse(vm.state.value.busy); assertFalse(vm.state.value.isChoosingGoogle)
    }
    @Test fun explicitChooserCancelReleasesBusyState() = runTest(dispatcher) {
        val operation = launch { vm.googleAction { awaitCancellation() } }
        runCurrent(); assertTrue(vm.state.value.isChoosingGoogle)
        vm.cancelGoogleSignIn(); runCurrent()
        assertTrue(operation.isCancelled); assertFalse(vm.state.value.busy)
        assertFalse(vm.state.value.isChoosingGoogle); assertNull(vm.state.value.error)
    }
}

private class FakeAccountGateway : AccountGateway {
    override val users = MutableStateFlow<AccountUser?>(null)
    override val currentUser get() = users.value
    override var googleAvailable = true
    var googleSignIns = 0; var googleDeletions = 0; var deletedGoogleUid: String? = null
    var signIns = 0; var registrations = 0; var syncs = 0; var verifications = 0
    var resets = 0; var signOuts = 0; var deletions = 0
    var delayMillis = 0L
    var syncDelayMillis = 0L
    var failSync = false; var failSignIn = false; var failDeletion = false; var failVerification = false
    override suspend fun signIn(email: String, password: String) {
        signIns++; delay(delayMillis)
        if (failSignIn) throw IOException("private-token must never reach UI")
        users.value = AccountUser("uid", email, "Ada", false)
    }
    override suspend fun register(email: String, password: String, name: String) {
        registrations++; users.value = AccountUser("uid", email, name, false)
    }
    override suspend fun signInGoogle(idToken: String) {
        googleSignIns++; delay(delayMillis)
        if (failSignIn) throw IOException("private-token")
        users.value = AccountUser("google-uid", "ada@example.com", "Ada", true, setOf("google.com"))
    }
    override suspend fun deleteGoogleAccount(idToken: String, expectedUid: String) {
        check(currentUser?.uid == expectedUid)
        if (failDeletion) throw GoogleSignInProblem("Choose the same Google account.")
        deletedGoogleUid = expectedUid; googleDeletions++; users.value = null
    }
    override suspend fun resetPassword(email: String) { resets++ }
    override suspend fun sendVerification() {
        verifications++; if (failVerification) throw IOException("offline")
    }
    override suspend fun reload() { users.value = users.value?.copy(verified = true) }
    override suspend fun syncProfile(): String {
        syncs++; delay(syncDelayMillis)
        if (failSync) throw IOException("permission denied")
        return "wing_a1b2c3d4e5"
    }
    override suspend fun deleteAccount(password: String) {
        if (failDeletion) throw IOException("reauth failed")
        deletions++; users.value = null
    }
    override suspend fun signOut() { signOuts++; users.value = null }
}
