package com.wingai.keyboard.auth

import android.app.Application
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AccountProfileDataTest {
    @Test fun createsProfileWithExactExistingWingAiDeviceId() {
        val data = accountProfileData("Ada", "ada@example.com", "wing_a1b2c3d4e5", true)
        assertEquals("wing_a1b2c3d4e5", data["wingaiDeviceId"])
        assertEquals(setOf("displayName", "email", "wingaiDeviceId", "createdAt", "updatedAt"), data.keys)
        assertEquals(FieldValue.serverTimestamp(), data["createdAt"])
        assertEquals(FieldValue.serverTimestamp(), data["updatedAt"])
    }
    @Test fun updateBackfillsDeviceIdWithoutReplacingCreatedAt() {
        val data = accountProfileData("Ada", "ada@example.com", "wing_0123456789", false)
        assertFalse(data.containsKey("createdAt"))
        assertEquals("wing_0123456789", data["wingaiDeviceId"])
    }
    @Test fun firebaseUidOrHardwareIdCannotBeMistakenForWingAiId() {
        for (id in listOf("firebaseUid", "", "0123456789abcdef", "wing_short", "wing_ABCDEF1234")) {
            try { accountProfileData("Ada", "ada@example.com", id, true); fail("Must reject $id") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun noEntitlementOrCredentialFieldsAreIncluded() {
        val data = accountProfileData("Ada", "ada@example.com", "wing_0123456789", false)
        for (field in listOf("tier", "quota", "role", "password", "token", "emailVerified", "deviceOwner")) {
            assertFalse(data.containsKey(field))
        }
    }
    @Test fun firestoreRulesErrorIsClearlyNotAGoogleLoginError() {
        val message = profileSyncError(FirebaseFirestoreException("private backend detail", FirebaseFirestoreException.Code.PERMISSION_DENIED))
        assertTrue(message.contains("You're signed in"))
        assertTrue(message.contains("PROFILE_PERMISSION_DENIED"))
        assertFalse(message.contains("private backend detail"))
    }
    @Test fun offlineDatabaseDoesNotClaimIdWasSaved() {
        val message = profileSyncError(FirebaseFirestoreException("private backend detail", FirebaseFirestoreException.Code.UNAVAILABLE))
        assertTrue(message.contains("hasn't been confirmed saved"))
        assertTrue(message.contains("PROFILE_UNAVAILABLE"))
    }
}
