package com.wingai.keyboard.auth

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AuthContentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(
        state: AccountState = AccountState(), light: Boolean = false, fontScale: Float = 1f,
        mode: (AuthMode) -> Unit = {}, submit: (String, String, String, String) -> Unit = { _, _, _, _ -> },
        close: () -> Unit = {}, google: () -> Unit = {}, googleDelete: () -> Unit = {}, cancelGoogle: () -> Unit = {}
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MaterialTheme(colorScheme = if (light) lightColorScheme() else darkColorScheme()) {
                    AuthContent(state, mode, submit, {}, {}, {}, {}, {}, close, google, googleDelete, cancelGoogle)
                }
            }
        }
    }

    @Test fun signInRendersAndCanBeSkipped() {
        var closed = false
        show(close = { closed = true })
        compose.onNodeWithText("Log in for better\nconversations.").assertIsDisplayed()
        screenshot("sign-in-reference.png")
        compose.onNodeWithText("Continue without signing in").performScrollTo().performClick()
        assertTrue(closed)
    }

    @Test fun signInSubmitsEnteredCredentials() {
        var received: List<String>? = null
        show(submit = { e, p, n, c -> received = listOf(e, p, n, c) })
        compose.onNodeWithText("Email address").performScrollTo().performTextInput("ada@example.com")
        compose.onNodeWithText("Password").performScrollTo().performTextInput("passphrase123")
        compose.onAllNodesWithText("Login").onLast().performScrollTo().performClick()
        assertEquals(listOf("ada@example.com", "passphrase123", "", ""), received)
    }

    @Test fun registrationShowsNameAndConfirmationInLightTheme() {
        show(AccountState(mode = AuthMode.REGISTER), light = true)
        compose.onNodeWithText("Your name").assertExists()
        screenshot("signup-reference.png")
        compose.onNodeWithText("Confirm password").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Create account").onLast().performScrollTo().assertIsDisplayed()
    }

    @Test fun forgotPasswordOpensResetMode() {
        var mode: AuthMode? = null
        show(mode = { mode = it })
        compose.onNodeWithText("Forgot password?").performScrollTo().performClick()
        assertEquals(AuthMode.RESET, mode)
    }

    @Test fun resetHasNoPasswordField() {
        show(AccountState(mode = AuthMode.RESET))
        compose.onNodeWithText("Password").assertDoesNotExist()
        compose.onNodeWithText("Send reset link").performScrollTo().assertIsDisplayed()
        screenshot("reset-password-reference.png")
    }

    @Test fun signedInAccountShowsVerificationAndDeletionConfirmation() {
        show(AccountState(user = AccountUser("uid", "ada@example.com", "Ada", false)))
        compose.onNodeWithText("ada@example.com").assertExists()
        screenshot("account-dark.png")
        compose.onNodeWithText("Delete account").performScrollTo().performClick()
        compose.onNodeWithText("Current password").assertIsDisplayed()
        compose.onNodeWithText("Delete permanently").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
    }

    @Test fun busyStateDisablesSubmission() {
        show(AccountState(busy = true))
        compose.onNodeWithText("Continue with Google").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Please wait...").performScrollTo().assertIsNotEnabled()
    }

    @Test fun googleButtonLaunchesWithoutEmailOrPassword() {
        var launched = false
        show(google = { launched = true })
        compose.onNodeWithText("Continue with Google").performScrollTo().performClick()
        assertTrue(launched)
    }

    @Test fun referenceLayoutHasFullWidthHeaderAndRoundedFormSheet() {
        show()
        compose.onNodeWithTag("auth-turquoise-header").assertWidthIsEqualTo(390.dp)
        compose.onNodeWithTag("auth-white-sheet").assertWidthIsEqualTo(390.dp)
        compose.onNodeWithText("Or continue with").assertExists()
    }

    @Test fun signUpTextLinkReplacesTabsAndChangesMode() {
        var selected: AuthMode? = null
        show(mode = { selected = it })
        compose.onNodeWithText("Sign up").performClick()
        assertEquals(AuthMode.REGISTER, selected)
    }

    @Test fun existingAccountLinkReturnsToLogin() {
        var selected: AuthMode? = null
        show(AccountState(mode = AuthMode.REGISTER), mode = { selected = it })
        compose.onNodeWithText("Log in").performClick()
        assertEquals(AuthMode.SIGN_IN, selected)
    }

    @Test fun validationAndNoticesRemainVisibleOnReferenceForm() {
        show(AccountState(error = "Please check your connection.", emailError = "Enter a valid email address."))
        compose.onNodeWithText("Please check your connection.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Enter a valid email address.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Continue with Google").performScrollTo().assertIsEnabled()
    }

    @Test
    @Config(qualifiers = "w844dp-h390dp-mdpi")
    fun landscapeFormScrollsWithoutLosingActions() {
        show(AccountState(mode = AuthMode.REGISTER))
        compose.onNodeWithTag("auth-field:Confirm password").performScrollTo().performTextInput("example-passphrase")
        compose.onNodeWithText("Create account").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Continue with Google").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Continue without signing in").performScrollTo().assertIsDisplayed()
    }

    @Test fun googleButtonAvailableOnCreateAccount() {
        var launched = false
        show(AccountState(mode = AuthMode.REGISTER), google = { launched = true })
        compose.onNodeWithText("Continue with Google").performScrollTo().performClick()
        assertTrue(launched)
    }

    @Test fun googleIsNotShownOnPasswordReset() {
        show(AccountState(mode = AuthMode.RESET))
        compose.onNodeWithText("Continue with Google").assertDoesNotExist()
    }

    @Test fun googleAccountDeletionUsesGoogleNotPassword() {
        var confirmed = false
        show(AccountState(user = AccountUser("google-uid", "ada@example.com", "Ada", true, setOf("google.com"))),
            googleDelete = { confirmed = true })
        screenshot("google-account-dark.png")
        compose.onNodeWithText("Connected with Google").assertExists()
        compose.onNodeWithText("Delete account").performScrollTo().performClick()
        compose.onNodeWithText("Current password").assertDoesNotExist()
        compose.onNodeWithText("Confirm with Google").assertIsEnabled().performClick()
        assertTrue(confirmed)
    }

    @Test fun waitingGoogleChooserHasAnEnabledCancelAction() {
        var cancelled = false
        show(AccountState(busy = true, isChoosingGoogle = true, authProgress = "Choose your Google account to continue."),
            cancelGoogle = { cancelled = true })
        compose.onNodeWithText("Cancel Google sign-in").performScrollTo().assertIsEnabled().performClick()
        assertTrue(cancelled)
    }

    @Test fun slowProfileSyncLeavesSignOutAndBackAvailable() {
        show(AccountState(user = AccountUser("uid", "ada@example.com", "Ada", true), profileSyncing = true))
        compose.onNodeWithText("Saving device ID...").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Sign out").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Back to WingAI").performScrollTo().assertIsEnabled()
    }

    @Test fun savedDeviceIdIsDisplayedOnlyAfterConfirmation() {
        show(AccountState(user = AccountUser("uid", "ada@example.com", "Ada", true), wingaiDeviceId = "wing_a1b2c3d4e5"))
        compose.onNodeWithText("wing_a1b2c3d4e5").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Device ID saved to your private cloud profile.").assertExists()
        screenshot("device-id-saved.png")
    }

    @Test fun liveLoginToAccountTransitionAndSignOutRemainResponsive() {
        val state = mutableStateOf(AccountState())
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                AuthContent(state.value, {}, { _, _, _, _ -> }, {}, {}, {}, {}, {}, {},
                    onGoogleSignIn = { state.value = AccountState(busy = true, isChoosingGoogle = true) })
            }
        }
        compose.onNodeWithText("Continue with Google").performScrollTo().performClick()
        compose.runOnIdle {
            state.value = AccountState(user = AccountUser("google", "ada@example.com", "Ada", true, setOf("google.com")),
                profileSyncing = true)
        }
        compose.onNodeWithText("Connected with Google").assertExists()
        compose.onNodeWithText("Back to WingAI").performScrollTo().assertIsEnabled()
        compose.runOnIdle { state.value = state.value.copy(profileSyncing = false, wingaiDeviceId = "wing_a1b2c3d4e5") }
        compose.onNodeWithText("wing_a1b2c3d4e5").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { state.value = AccountState() }
        compose.onNodeWithText("Continue with Google").performScrollTo().assertIsEnabled()
    }

    @Test fun compactFieldsKeepUsable48DpTargets() {
        show()
        compose.onNodeWithTag("auth-field:Email address").assertHeightIsEqualTo(48.dp)
        compose.onNodeWithTag("auth-field:Password").assertHeightIsEqualTo(48.dp)
    }

    @Test fun passwordVisibilityIconHasAccessibleState() {
        show()
        compose.onNodeWithTag("auth-field:Password").performTextInput("passphrase123")
        compose.onNodeWithContentDescription("Show password").performClick()
        compose.onNodeWithContentDescription("Hide password").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Show password").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun compactFieldsExpandForLargeTextOnSmallScreens() {
        show(AccountState(mode = AuthMode.REGISTER), fontScale = 2f)
        compose.onNodeWithTag("auth-field:Email address").performScrollTo().assertHeightIsAtLeast(52.dp)
        compose.onNodeWithTag("auth-field:Confirm password").performScrollTo().performTextInput("long-password")
        compose.onAllNodesWithText("Create account").onLast().performScrollTo().assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val file = File("../../artifacts/auth-ui", name)
        requireNotNull(file.parentFile).mkdirs()
        // PixelCopy has no real display in Robolectric. Draw the native test view instead.
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
