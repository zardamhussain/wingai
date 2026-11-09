package com.wingai.keyboard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.wingai.keyboard.auth.WingAiAccountEntry
import com.wingai.keyboard.data.InputConnectionBridge
import com.wingai.keyboard.data.UserProfile
import com.wingai.keyboard.settings.SettingsRepository
import helium314.keyboard.settings.SearchScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserAccountUiState(
    val profile: UserProfile? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class UserAccountViewModel(
    private val repository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserAccountUiState())
    val uiState: StateFlow<UserAccountUiState> = _uiState.asStateFlow()

    init {
        refreshProfile()
    }

    fun refreshProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val userId = repository.getOrCreateUserId()
            val settings = repository.settingsFlow.first()
            val client = WingAiApiClient({ settings.backendUrl }, { userId })
            client.fetchUserProfile(userId).fold(
                onSuccess = { loaded ->
                    InputConnectionBridge.updateState { it.copy(userProfile = loaded) }
                    _uiState.update {
                        it.copy(profile = loaded, isLoading = false, errorMessage = null)
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Unable to reach WingAI backend."
                        )
                    }
                }
            )
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return UserAccountViewModel(SettingsRepository(context.applicationContext)) as T
        }
    }
}

@Composable
fun UserAccountScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    val vm: UserAccountViewModel = viewModel(factory = UserAccountViewModel.Factory(context))
    val state by vm.uiState.collectAsState()

    LaunchedEffect(Unit) {
        vm.refreshProfile()
    }

    SearchScreen(
        onClickBack = onClickBack,
        title = { Text("WingAI Account & Plan") },
        filteredItems = { emptyList<Unit>() },
        itemContent = {},
        icon = {},
        content = {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
            ) { innerPadding ->
                UserAccountContent(
                    state = state,
                    modifier = Modifier.padding(innerPadding),
                    onRefresh = { vm.refreshProfile() },
                    accountEntry = { WingAiAccountEntry() },
                    onCopyUserId = { userId ->
                        if (userId.isNotBlank()) {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("WingAI User ID", userId))
                            Toast.makeText(context, "User ID copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    )
}

@Composable
fun UserAccountContent(
    state: UserAccountUiState,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
    onCopyUserId: (String) -> Unit,
    accountEntry: @Composable () -> Unit = {}
) {
    var playgroundText by remember { mutableStateOf("") }
    val profile = state.profile
    val isPaid = profile?.isPaid == true

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Optional login is additive; all existing device plan UI and behavior stays intact.
        accountEntry()

        // 1. Plan & Daily Quota Hero Card
        WingAiSurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = WingAiTokens.Surface1,
            borderColor = if (isPaid) WingAiTokens.AccentEmerald else WingAiTokens.BorderAccent
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "CURRENT SUBSCRIPTION",
                        color = WingAiTokens.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = if (isPaid) "WingAI Pro" else "WingAI Free Tier",
                        color = WingAiTokens.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(WingAiTokens.RadiusPill)
                        .background(
                            if (isPaid) WingAiTokens.AccentEmeraldContainer else WingAiTokens.AccentPrimaryContainer
                        )
                        .border(
                            1.dp,
                            if (isPaid) WingAiTokens.AccentEmerald else WingAiTokens.AccentPrimary,
                            WingAiTokens.RadiusPill
                        )
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isPaid) "\uD83D\uDC51 PAID • UNLIMITED" else "\u26A1 FREE • 5 / DAY",
                        color = if (isPaid) WingAiTokens.AccentEmerald else WingAiTokens.TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (state.isLoading && profile == null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    WingAiSkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(14.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        repeat(5) {
                            WingAiSkeletonBox(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(7.dp),
                                shape = WingAiTokens.RadiusPill
                            )
                        }
                    }
                }
            } else if (profile != null) {
                val limit = profile.dailyLimit ?: 5
                val used = profile.dailyUsed
                val remaining = profile.remaining ?: (limit - used).coerceAtLeast(0)

                Text(
                    text = if (isPaid) {
                        "$used AI requests processed today • Unlimited daily replies active"
                    } else {
                        "$used of $limit daily replies used today • $remaining remaining (resets 00:00 UTC)"
                    },
                    color = WingAiTokens.TextSecondary,
                    fontSize = 13.sp
                )

                // 5-Step Segmented Quota Bar for Free Tier
                if (!isPaid) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (slot in 1..limit) {
                            val consumed = slot <= used
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(7.dp)
                                    .clip(WingAiTokens.RadiusPill)
                                    .background(
                                        when {
                                            used >= limit -> WingAiTokens.AccentRose
                                            consumed -> WingAiTokens.AccentPrimary
                                            else -> WingAiTokens.Surface3
                                        }
                                    )
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                CompactPillButton(
                    text = if (state.isLoading) "Syncing" else "Refresh Quota",
                    backgroundColor = WingAiTokens.Surface2,
                    borderColor = WingAiTokens.BorderStrong,
                    enabled = !state.isLoading,
                    onClick = onRefresh
                )
            }
        }

        // 2. Error Banner if Offline
        if (!state.errorMessage.isNullOrBlank()) {
            WingAiSurfaceCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = WingAiTokens.AccentRoseContainer,
                borderColor = WingAiTokens.AccentRose
            ) {
                Text(
                    text = state.errorMessage,
                    color = Color(0xFFFFE4E8),
                    fontSize = 12.sp
                )
            }
        }

        // 3. Copyable Device User ID Card
        WingAiSurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onCopyUserId(profile?.userId.orEmpty()) }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "WINGAI DEVICE USER ID",
                        color = WingAiTokens.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp
                    )
                    if (profile?.userId.isNullOrBlank()) {
                        WingAiSkeletonBox(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(16.dp)
                        )
                    } else {
                        Text(
                            text = profile?.userId.orEmpty(),
                            color = WingAiTokens.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                CompactPillButton(
                    text = "Copy ID",
                    backgroundColor = WingAiTokens.AccentPrimaryContainer,
                    borderColor = WingAiTokens.BorderAccent,
                    onClick = { onCopyUserId(profile?.userId.orEmpty()) }
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0E17)
@Composable
private fun UserAccountFreePreview() {
    WingAiTheme {
        UserAccountContent(
            state = UserAccountUiState(
                profile = UserProfile(
                    userId = "wing_8f42c19a0b",
                    tier = "free",
                    dailyUsed = 3,
                    dailyLimit = 5,
                    remaining = 2
                ),
                isLoading = false
            ),
            onRefresh = {},
            onCopyUserId = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0E17)
@Composable
private fun UserAccountPaidPreview() {
    WingAiTheme {
        UserAccountContent(
            state = UserAccountUiState(
                profile = UserProfile(
                    userId = "wing_pro998877",
                    tier = "paid",
                    dailyUsed = 28,
                    dailyLimit = null,
                    remaining = null
                ),
                isLoading = false
            ),
            onRefresh = {},
            onCopyUserId = {}
        )
    }
}
