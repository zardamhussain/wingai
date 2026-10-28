package com.wingai.keyboard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wingai.keyboard.ai.WingAiApiClient
import com.wingai.keyboard.data.InputConnectionBridge
import com.wingai.keyboard.data.UserProfile
import com.wingai.keyboard.settings.SettingsRepository
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.preferences.Preference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun UserAccountScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SettingsRepository(context.applicationContext) }
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun loadProfile() {
        scope.launch {
            isLoading = true
            statusMessage = null
            val userId = repo.getOrCreateUserId()
            val settings = repo.settingsFlow.first()
            val client = WingAiApiClient({ settings.backendUrl }, { userId })
            client.fetchUserProfile(userId).fold(
                onSuccess = { loaded ->
                    profile = loaded
                    InputConnectionBridge.updateState { it.copy(userProfile = loaded) }
                    isLoading = false
                },
                onFailure = { err ->
                    statusMessage = err.message ?: "Failed to load user profile."
                    isLoading = false
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        loadProfile()
    }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = "WingAI Account & Plan",
        settings = emptyList()
    ) {
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)) { innerPadding ->
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
            ) {
                val currentProfile = profile
                val planTitle = when {
                    isLoading && currentProfile == null -> "Loading plan..."
                    currentProfile?.isPaid == true -> "Paid Plan (Unlimited)"
                    else -> "Free Plan (5 requests / day)"
                }
                val usageDesc = when {
                    isLoading && currentProfile == null -> "Fetching account details..."
                    currentProfile?.isPaid == true ->
                        "${currentProfile.dailyUsed} requests used today • Unlimited remaining"
                    currentProfile != null -> {
                        val limit = currentProfile.dailyLimit ?: 5
                        val rem = currentProfile.remaining ?: 0
                        "${currentProfile.dailyUsed} / $limit requests used today ($rem remaining)"
                    }
                    else -> statusMessage ?: "Tap to refresh account status"
                }

                Preference(
                    name = planTitle,
                    description = usageDesc,
                    onClick = { loadProfile() }
                )

                if (currentProfile != null && !currentProfile.isPaid) {
                    val limit = (currentProfile.dailyLimit ?: 5).coerceAtLeast(1)
                    val progress = (currentProfile.dailyUsed.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                Preference(
                    name = "WingAI User ID",
                    description = currentProfile?.userId?.ifBlank { "Loading..." }
                        ?.let { "$it (Tap to copy)" } ?: "Loading...",
                    onClick = {
                        val id = currentProfile?.userId.orEmpty()
                        if (id.isNotBlank()) {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("WingAI User ID", id))
                            Toast.makeText(context, "User ID copied!", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                Preference(
                    name = "Refresh Account Status",
                    description = if (isLoading) "Refreshing..." else "Tap to sync latest plan and daily usage",
                    onClick = { if (!isLoading) loadProfile() }
                )

                if (!statusMessage.isNullOrBlank()) {
                    Text(
                        text = statusMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
