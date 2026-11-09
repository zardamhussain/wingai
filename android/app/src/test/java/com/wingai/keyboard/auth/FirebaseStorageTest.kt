package com.wingai.keyboard.auth

import android.app.Application
import android.content.Context
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
class FirebaseStorageTest {
    @Test fun firebaseUsesCredentialStorageWithoutChangingTheKeyboardContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        val keyboardContext = context.createDeviceProtectedStorageContext()
        assertTrue(keyboardContext.isDeviceProtectedStorage)
        val app = WingAiFirebase.app(keyboardContext)
        assertFalse(app.applicationContext.isDeviceProtectedStorage)
        assertFalse(app.applicationContext.applicationContext.isDeviceProtectedStorage)
        assertTrue(keyboardContext.isDeviceProtectedStorage)
        assertEquals("wingai-account", app.name)
    }

    @Test fun firebaseInitializationIsRefusedBeforeUnlock() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.getSystemService(UserManager::class.java)).setUserUnlocked(false)
        try {
            WingAiFirebase.app(context)
            fail("Firebase should not be initialized before device unlock")
        } catch (_: IllegalStateException) {
            // Existing keyboard path has no dependency on account initialization.
        }
    }
}
