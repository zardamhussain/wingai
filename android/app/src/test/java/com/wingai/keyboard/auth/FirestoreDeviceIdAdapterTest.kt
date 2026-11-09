package com.wingai.keyboard.auth

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Transaction
import com.wingai.keyboard.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercise the real production transaction adapter without accessing any live user/database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FirestoreDeviceIdAdapterTest {
    private class Fixture(val exists: Boolean, val uid: String) {
        val context: Context = ApplicationProvider.getApplicationContext()
        val auth = mock(FirebaseAuth::class.java)
        val user = mock(FirebaseUser::class.java)
        val db = mock(FirebaseFirestore::class.java)
        val users = mock(CollectionReference::class.java)
        val document = mock(DocumentReference::class.java)
        val transaction = mock(Transaction::class.java)
        val snapshot = mock(DocumentSnapshot::class.java)
        var offMainThread = false
        var loads = 0
        init {
            `when`(auth.currentUser).thenReturn(user)
            `when`(user.uid).thenReturn(uid)
            `when`(user.email).thenReturn("ada@example.com")
            `when`(user.displayName).thenReturn("Ada")
            `when`(db.collection("users")).thenReturn(users)
            `when`(users.document(uid)).thenReturn(document)
            `when`(transaction.get(document)).thenReturn(snapshot)
            `when`(snapshot.exists()).thenReturn(exists)
            doAnswer { invocation ->
                offMainThread = Thread.currentThread() != Looper.getMainLooper().thread
                @Suppress("UNCHECKED_CAST")
                val function = invocation.arguments[0] as Transaction.Function<Void?>
                function.apply(transaction)
                Tasks.forResult<Void?>(null)
            }.`when`(db).runTransaction(any<Transaction.Function<Void?>>())
        }
        fun repository() = WingAiAuthRepository(context, auth,
            loadDeviceId = { loads++; "wing_a1b2c3d4e5" }, database = { db }, clearCredentials = {})
    }

    @Test fun createsUsersUidDocumentWithTheExistingIdOffMainThread() = runBlocking {
        val f = Fixture(false, "device-create")
        assertEquals("wing_a1b2c3d4e5", f.repository().syncProfile())
        val data = ArgumentCaptor.forClass(Map::class.java)
        verify(f.transaction).set(eq(f.document), data.capture())
        assertEquals("wing_a1b2c3d4e5", data.value["wingaiDeviceId"])
        assertTrue(data.value.containsKey("createdAt")); assertTrue(f.offMainThread)
        assertEquals(1, f.loads)
        verify(f.users).document("device-create")
        Unit
    }
    @Test fun existingProfileUsesUpdateAndPreservesCreationTime() = runBlocking {
        val f = Fixture(true, "device-update")
        f.repository().syncProfile()
        @Suppress("UNCHECKED_CAST")
        val data = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, Any>>
        verify(f.transaction).update(eq(f.document), data.capture())
        assertEquals("wing_a1b2c3d4e5", data.value["wingaiDeviceId"])
        assertFalse(data.value.containsKey("createdAt"))
    }
    @Test fun rulesFailureDoesNotReturnAFalseSavedDeviceId() = runBlocking {
        val f = Fixture(false, "device-denied")
        doReturn(Tasks.forException<Void?>(FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)))
            .`when`(f.db).runTransaction(any<Transaction.Function<Void?>>())
        try { f.repository().syncProfile(); fail("Must not report saved") }
        catch (error: FirebaseFirestoreException) { assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error.code) }
    }
    @Test fun signedOutClientNeverRequestsADeviceIdOrWritesFirestore() = runBlocking {
        val f = Fixture(false, "device-signed-out")
        `when`(f.auth.currentUser).thenReturn(null)
        try { f.repository().syncProfile(); fail("Must reject") }
        catch (_: IllegalArgumentException) { }
        assertEquals(0, f.loads)
        verify(f.db, never()).collection("users")
        Unit
    }
    @Test fun defaultDeviceIdLoaderReusesTheKeyboardDataStoreIdentity() = runBlocking {
        val f = Fixture(false, "device-existing-datastore")
        val existing = SettingsRepository(f.context).getOrCreateUserId()
        val repository = WingAiAuthRepository(f.context, f.auth, database = { f.db }, clearCredentials = {})
        assertEquals(existing, repository.syncProfile())
        assertEquals(existing, SettingsRepository(f.context).getOrCreateUserId())
        val data = ArgumentCaptor.forClass(Map::class.java)
        verify(f.transaction).set(eq(f.document), data.capture())
        assertEquals(existing, data.value["wingaiDeviceId"])
    }
}
