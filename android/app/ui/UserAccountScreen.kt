package com.wingai.keyboard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wingai.keyboard.ai.WingAiApiClient
import com.wingai.keyboard.data.InputConnectionBridge
import com.wingai.keyboard.data.UserProfile
import com.wingai.keyboard.settings.SettingsRepository
import com.wingai.keyboard.settings.WingAiAppearance
import helium314.keyboard.settings.SearchScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Account data remains live; previews below inject synthetic profiles and never call the API.
data class UserAccountUiState(
    val profile: UserProfile? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class UserAccountViewModel(private val repository: SettingsRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(UserAccountUiState())
    val uiState: StateFlow<UserAccountUiState> = _uiState.asStateFlow()

    init { refreshProfile() }

    fun refreshProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val userId = repository.getOrCreateUserId()
            val settings = repository.settingsFlow.first()
            val client = WingAiApiClient({ settings.backendUrl }, { userId })
            client.fetchUserProfile(userId).fold(
                onSuccess = { loaded ->
                    InputConnectionBridge.updateState { it.copy(userProfile = loaded) }
                    _uiState.update { it.copy(profile = loaded, isLoading = false, errorMessage = null) }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = err.message ?: "Couldn't refresh your plan. Try again in a moment.")
                    }
                }
            )
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            UserAccountViewModel(SettingsRepository(context.applicationContext)) as T
    }
}

@Composable
fun UserAccountScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { SettingsRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val vm: UserAccountViewModel = viewModel(factory = UserAccountViewModel.Factory(context))
    val state by vm.uiState.collectAsState()

    // Theme the complete screen, including SearchScreen's app bar (previously outside WingAiTheme).
    WingAiTheme {
        SearchScreen(
            onClickBack = onClickBack,
            title = { Text("Your WingAI") },
            filteredItems = { emptyList<Unit>() },
            itemContent = {},
            icon = {},
            content = {
                Scaffold(
                    containerColor = WingAiTokens.BgCanvas,
                    contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                ) { innerPadding ->
                    UserAccountContent(
                        state = state,
                        modifier = Modifier.padding(innerPadding),
                        onRefresh = { vm.refreshProfile() },
                        onAppearanceChange = { appearance ->
                            scope.launch {
                                try {
                                    repository.updateAppearance(appearance)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Couldn't save the theme. Please try again.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onCopyUserId = { userId ->
                            if (userId.isNotBlank()) {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("WingAI User ID", userId))
                                Toast.makeText(context, "User ID copied", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        )
    }
}

@Composable
fun UserAccountContent(
    state: UserAccountUiState,
    modifier: Modifier = Modifier,
    appearance: WingAiAppearance = LocalWingAiAppearance.current,
    onAppearanceChange: (WingAiAppearance) -> Unit = {},
    onRefresh: () -> Unit,
    onCopyUserId: (String) -> Unit
) {
    val profile = state.profile
    val isPaid = profile?.isPaid == true

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(WingAiTokens.BgCanvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // Theme choices work even while the account service is offline.
        WingAiThemePicker(selected = appearance, onSelect = onAppearanceChange)

        WingAiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Text("YOUR PLAN", color = WingAiTokens.TextSecondary, fontSize = 11.sp, letterSpacing = 1.sp)
            Text(
                text = when {
                    profile == null -> "Your daily inspiration"
                    isPaid -> "More room for your words."
                    else -> "A little help, every day."
                },
                color = WingAiTokens.TextPrimary, fontSize = 22.sp,
                lineHeight = 28.sp, fontWeight = FontWeight.Bold
            )
            if (profile == null) {
                if (state.isLoading) {
                    WingAiSkeletonBox(Modifier.fillMaxWidth(0.7f).height(16.dp))
                    WingAiSkeletonBox(Modifier.fillMaxWidth().height(6.dp))
                } else {
                    Text("Connect to check your plan and daily usage.", color = WingAiTokens.TextSecondary, fontSize = 13.sp)
                }
            } else if (isPaid) {
                Text("WingAI Pro · Unlimited AI requests", color = WingAiTokens.AccentEmerald, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("${profile.dailyUsed} requests used today", color = WingAiTokens.TextSecondary, fontSize = 13.sp)
            } else {
                val limit = (profile.dailyLimit ?: 5).coerceAtLeast(0)
                val used = profile.dailyUsed.coerceAtLeast(0)
                val remaining = (profile.remaining ?: (limit - used)).coerceIn(0, limit)
                Text("WingAI Free · $remaining of $limit requests left today", color = WingAiTokens.TextPrimary, fontSize = 14.sp)
                LinearProgressIndicator(
                    progress = { if (limit > 0) (used.toFloat() / limit).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = WingAiTokens.AccentPrimary,
                    trackColor = WingAiTokens.Surface3
                )
                Text("Your allowance refreshes at 00:00 UTC.", color = WingAiTokens.TextSecondary, fontSize = 12.sp)
            }
            CompactPillButton(
                text = if (state.isLoading) "Refreshing..." else "Refresh usage",
                height = 48.dp,
                enabled = !state.isLoading,
                onClick = onRefresh
            )
        }

        if (!state.errorMessage.isNullOrBlank()) {
            WingAiSurfaceCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = WingAiTokens.AccentRoseContainer,
                borderColor = WingAiTokens.AccentRose
            ) {
                Text("Let's try that again.", color = WingAiTokens.TextPrimary, fontWeight = FontWeight.SemiBold)
                // Use theme-aware text: the old white error text was unreadable on light surfaces.
                Text(state.errorMessage, color = WingAiTokens.TextPrimary, fontSize = 13.sp)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("You're in control.", color = WingAiTokens.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Review your reply before sending. Normal keyboard typing works offline; AI features use the configured online service.",
                color = WingAiTokens.TextSecondary, fontSize = 13.sp, lineHeight = 19.sp
            )
        }

        WingAiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Text("DEVICE USER ID", color = WingAiTokens.TextSecondary, fontSize = 11.sp, letterSpacing = 0.7.sp)
            Text(
                profile?.userId?.takeIf { it.isNotBlank() } ?: "Available when your account connects",
                color = WingAiTokens.TextPrimary, fontSize = 13.sp
            )
            CompactPillButton(
                text = "Copy ID", height = 48.dp,
                enabled = !profile?.userId.isNullOrBlank(),
                onClick = { onCopyUserId(profile?.userId.orEmpty()) }
            )
        }
    }
}

@Preview(name = "Aurora / free", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun UserAccountFreePreview() {
    WingAiTheme(appearance = WingAiAppearance.AURORA) {
        UserAccountContent(
            state = UserAccountUiState(UserProfile("wing_demo_preview", "free", 2, 5, 3), false),
            onRefresh = {}, onCopyUserId = {}
        )
    }
}

@Preview(name = "Daylight / Pro", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun UserAccountPaidPreview() {
    WingAiTheme(appearance = WingAiAppearance.DAYLIGHT) {
        UserAccountContent(
            state = UserAccountUiState(UserProfile("wing_demo_pro", "paid", 28, null, null), false),
            onRefresh = {}, onCopyUserId = {}
        )
    }
}
