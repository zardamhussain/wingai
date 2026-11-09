package com.wingai.keyboard.auth

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import com.wingai.keyboard.ui.WingAiTheme
import com.wingai.keyboard.ui.WingAiTokens
import com.wingai.keyboard.ui.LocalWingAiPalette

/** Separate optional screen: never gates the IME, onboarding or existing settings navigation. */
class WingAiAuthActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Password screens must not appear in screenshots or the recent-apps thumbnail.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val repository = runCatching { WingAiAuthRepository(applicationContext) }.getOrNull()
        val googleController = GoogleCredentialController(this)
        setContent {
            WingAiTheme {
                val vm: WingAiAuthViewModel = viewModel(factory = WingAiAuthViewModel.Factory(repository))
                val state by vm.state.collectAsState()
                val accountIsLight = LocalWingAiPalette.current.isLight
                // Update window flags only when the screen palette actually changes.
                LaunchedEffect(state.user == null, accountIsLight) {
                    val bars = WindowCompat.getInsetsController(window, window.decorView)
                    bars.isAppearanceLightStatusBars = state.user != null && accountIsLight
                    bars.isAppearanceLightNavigationBars = state.user == null || accountIsLight
                }
                AuthContent(state, vm::mode, vm::submit, vm::sendVerification,
                    vm::refreshVerification, vm::retryProfile, vm::signOut, vm::deleteAccount, ::finish,
                    onGoogleSignIn = { lifecycleScope.launch { vm.googleAction(requestToken = googleController::requestIdToken) } },
                    onGoogleDelete = { lifecycleScope.launch { vm.googleAction(delete = true, requestToken = googleController::requestIdToken) } },
                    onCancelGoogle = vm::cancelGoogleSignIn)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AuthContent(
    state: AccountState,
    onMode: (AuthMode) -> Unit,
    onSubmit: (String, String, String, String) -> Unit,
    onVerify: () -> Unit,
    onRefreshVerification: () -> Unit,
    onSync: () -> Unit,
    onSignOut: () -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
    onGoogleSignIn: () -> Unit = {},
    onGoogleDelete: () -> Unit = {},
    onCancelGoogle: () -> Unit = {}
) {
    // Match the supplied reference only on authentication pages. The existing signed-in
    // account controls and the keyboard retain their current theme and behavior.
    if (state.user == null) {
        WingAiLoginPage(mode = state.mode, onClose = onClose) {
            LoginForm(state, onMode, onSubmit, onGoogleSignIn, onCancelGoogle)
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("Continue without signing in", fontSize = 12.sp)
            }
            Text(
                "Login saves your name, email and WingAI device ID to your private profile. It never uploads typed text, chats or clipboard content. Your keyboard and device plan are unchanged.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            )
        }
        return
    }
    val colors = MaterialTheme.colorScheme
    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("WingAI Account", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    TextButton(onClick = onClose) { Text("Close") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        Modifier.size(64.dp).clip(RoundedCornerShape(22.dp))
                            .background(WingAiTokens.WingAiBrandBrush),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("W", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text("YOUR WORDS. YOUR WORLD.", color = colors.primary,
                        style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp)
                    Text("Make yourself\nat home.", style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold, color = colors.onBackground)
                    Text(
                        "Your WingAI login, all in one place.",
                        color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge
                    )
                }
                state.error?.let { StatusMessage(it, error = true) }
                state.notice?.let { StatusMessage(it) }
                state.profileWarning?.let { StatusMessage(it) }
                state.authProgress?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (state.isChoosingGoogle) TextButton(onClick = onCancelGoogle) { Text("Cancel Google sign-in") }
                if (state.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().semantics {
                        liveRegion = LiveRegionMode.Polite
                    })
                }
                AccountDetails(state, onVerify, onRefreshVerification, onSync, onSignOut, onDelete, onGoogleDelete)
                OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("Back to WingAI")
                }
                HorizontalDivider(color = colors.outlineVariant)
                Text(
                    "Private by design. Login stores your name, email and existing WingAI device ID in your private profile. " +
                        "It does not upload typed text, chats, screenshots or clipboard content. " +
                        "Your existing AI requests, device plan and keyboard settings are unchanged.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoginForm(
    state: AccountState,
    onMode: (AuthMode) -> Unit,
    onSubmit: (String, String, String, String) -> Unit,
    onGoogleSignIn: () -> Unit,
    onCancelGoogle: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    // Passwords never enter saved instance state, preferences, analytics or logs.
    var password by remember(state.mode) { mutableStateOf("") }
    var confirm by remember(state.mode) { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val enabled = !state.busy && state.available
    val submit = {
        if (enabled) {
            focus.clearFocus()
            onSubmit(email, password, name, confirm)
        }
    }
    Column(Modifier.fillMaxWidth().animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(when (state.mode) {
            AuthMode.SIGN_IN -> "Login"
            AuthMode.REGISTER -> "Sign up"
            AuthMode.RESET -> "Reset password"
        }, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)
        if (state.mode != AuthMode.RESET) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.Center) {
                Text(if (state.mode == AuthMode.SIGN_IN) "Don't have an account?" else "Already have an account?",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically))
                TextButton(onClick = {
                    focus.clearFocus()
                    onMode(if (state.mode == AuthMode.SIGN_IN) AuthMode.REGISTER else AuthMode.SIGN_IN)
                }, enabled = !state.busy, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text(if (state.mode == AuthMode.SIGN_IN) "Sign up" else "Log in", fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            Text("Enter your email to receive a password reset link.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        state.error?.let { StatusMessage(it, error = true) }
        state.notice?.let { StatusMessage(it) }
        state.profileWarning?.let { StatusMessage(it) }
        state.authProgress?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (state.isChoosingGoogle) TextButton(onClick = onCancelGoogle) { Text("Cancel Google sign-in") }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.mode == AuthMode.REGISTER) {
            CompactAuthField(value = name, onValueChange = { name = it }, label = "Your name",
                icon = AuthFieldIcon.PERSON, autofillType = ContentType.PersonFullName,
                enabled = enabled, error = state.nameError,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
        }
        CompactAuthField(value = email, onValueChange = { email = it }, label = "Email address",
            icon = AuthFieldIcon.EMAIL, autofillType = ContentType.EmailAddress,
            enabled = enabled, error = state.emailError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email,
                autoCorrectEnabled = false, imeAction = if (state.mode == AuthMode.RESET) ImeAction.Done else ImeAction.Next),
            keyboardActions = KeyboardActions(onDone = { submit() }))
        if (state.mode != AuthMode.RESET) {
            PasswordField(password, { password = it }, "Password", state.passwordError, enabled,
                newPassword = state.mode == AuthMode.REGISTER,
                done = state.mode == AuthMode.SIGN_IN, onDone = submit)
            if (state.mode == AuthMode.REGISTER) {
                PasswordField(confirm, { confirm = it }, "Confirm password", state.confirmError, enabled,
                    newPassword = true, done = true, onDone = submit)
                Text("Use at least 8 characters for your password.", modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            } else {
                TextButton(onClick = { onMode(AuthMode.RESET) }, enabled = !state.busy,
                    modifier = Modifier.align(Alignment.End), contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("Forgot password?", fontSize = 12.sp)
                }
            }
        }
        Button(onClick = submit, enabled = enabled, shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            Text(if (state.busy) "Please wait..." else when (state.mode) {
                AuthMode.SIGN_IN -> "Login"
                AuthMode.REGISTER -> "Create account"
                AuthMode.RESET -> "Send reset link"
            }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        if (state.mode == AuthMode.RESET) {
            TextButton(onClick = { onMode(AuthMode.SIGN_IN) }, enabled = !state.busy) { Text("Back to login") }
        } else {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(Modifier.weight(1f))
                Text("Or continue with", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.weight(1f))
            }
            GoogleSignInButton(enabled = enabled && state.googleAvailable, onClick = {
                focus.clearFocus()
                onGoogleSignIn()
            })
        }
    }
}

@Composable
private fun PasswordField(
    value: String, onValue: (String) -> Unit, label: String, error: String?, enabled: Boolean,
    newPassword: Boolean = false, done: Boolean = false, onDone: () -> Unit = {}
) {
    var visible by remember(newPassword, label) { mutableStateOf(false) }
    CompactAuthField(value = value, onValueChange = onValue, label = label,
        icon = AuthFieldIcon.LOCK,
        autofillType = if (newPassword) ContentType.NewPassword else ContentType.Password,
        enabled = enabled, error = error,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        passwordVisible = visible, onTogglePassword = { visible = !visible },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false,
            imeAction = if (done) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onDone = { onDone() }))
}

@Composable
private fun AccountDetails(
    state: AccountState, onVerify: () -> Unit, onRefresh: () -> Unit,
    onSync: () -> Unit, onSignOut: () -> Unit, onDelete: (String) -> Unit, onGoogleDelete: () -> Unit
) {
    val user = state.user ?: return
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deletionPassword by remember { mutableStateOf("") }
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(user.name.ifBlank { "Your WingAI account" }, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Text(user.email, style = MaterialTheme.typography.bodyLarge)
            if (user.usesGoogle) Text("Connected with Google", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(if (user.verified) "Email verified" else "Email verification pending",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.labelLarge)
            }
            if (!user.verified) {
                Text("Verify your address using the link in your inbox. You can keep using your keyboard meanwhile.",
                    style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onVerify, enabled = !state.busy && state.verificationCooldown == 0,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.verificationCooldown > 0) "Resend in ${state.verificationCooldown}s" else "Send verification email")
                }
                TextButton(onClick = onRefresh, enabled = !state.busy) { Text("I've verified my email") }
            }
            HorizontalDivider()
            Text("WingAI device ID", style = MaterialTheme.typography.titleSmall)
            state.wingaiDeviceId?.let { savedId ->
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(savedId, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(when {
                state.profileSyncing -> "Saving profile and device ID... You can keep using the app."
                state.wingaiDeviceId != null -> "Device ID saved to your private cloud profile."
                else -> "Device ID save not confirmed. Use Sync account profile to retry."
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Your plan stays on this device", style = MaterialTheme.typography.titleSmall)
            Text("Signing in doesn't transfer or change your existing paid plan or daily quota. Manage them on Account & Plan.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onSync, enabled = !state.busy && !state.profileSyncing, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.profileSyncing) "Saving device ID..." else "Sync account profile")
            }
            OutlinedButton(onClick = { confirmSignOut = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Sign out")
            }
            TextButton(onClick = { confirmDelete = true }, enabled = !state.busy) {
                Text("Delete account", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmSignOut) AlertDialog(onDismissRequest = { confirmSignOut = false },
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(0.9f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Sign out of WingAI?") },
        text = { Text("Your keyboard, saved settings and device plan stay available.") },
        confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text("Sign out") } },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false; deletionPassword = "" },
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(0.9f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Delete your account?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("This permanently deletes your WingAI login and cloud profile. It does not cancel a device plan or erase local keyboard data. " +
                    if (user.usesGoogle) "Confirm with the same Google account (${user.email}). Your Google account itself will not be deleted."
                    else "Enter your current password to confirm.")
                if (!user.usesGoogle) PasswordField(deletionPassword, { deletionPassword = it }, "Current password", null, true)
            }
        },
        confirmButton = { TextButton(enabled = !state.busy && (user.usesGoogle || deletionPassword.isNotEmpty()), onClick = {
            if (user.usesGoogle) onGoogleDelete() else onDelete(deletionPassword)
            deletionPassword = ""; confirmDelete = false
        }) { Text(if (user.usesGoogle) "Confirm with Google" else "Delete permanently", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false; deletionPassword = "" }) { Text("Cancel") } })
}

@Composable
private fun StatusMessage(message: String, error: Boolean = false) {
    Surface(shape = RoundedCornerShape(16.dp),
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(message, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SignInPreview() {
    WingAiTheme { AuthContent(AccountState(), {}, { _, _, _, _ -> }, {}, {}, {}, {}, {}, {}) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 1000)
@Composable
private fun RegisterPreview() {
    WingAiTheme { AuthContent(AccountState(mode = AuthMode.REGISTER), {}, { _, _, _, _ -> }, {}, {}, {}, {}, {}, {}) }
}
