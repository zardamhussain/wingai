// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wingai.keyboard.capture.WingAiScreenshotService
import com.wingai.keyboard.ui.CompactPillButton
import com.wingai.keyboard.ui.WingAiSurfaceCard
import com.wingai.keyboard.ui.WingAiTheme
import com.wingai.keyboard.ui.WingAiTokens
import helium314.keyboard.latin.utils.UncachedInputMethodManagerUtils
import kotlinx.coroutines.delay

private fun isSelectAndSearchEnabled(context: Context): Boolean {
    if (WingAiScreenshotService.isAvailable()) return true
    val enabledServices = runCatching {
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
    }.getOrNull().orEmpty()
    return enabledServices.contains(WingAiScreenshotService::class.java.name, ignoreCase = true)
}

@Composable
fun WelcomeWizard(
    close: () -> Unit,
    finish: () -> Unit
) {
    val ctx = LocalContext.current
    val imm = remember { ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager }

    var isImeEnabled by remember {
        mutableStateOf(UncachedInputMethodManagerUtils.isThisImeEnabled(ctx, imm))
    }
    var isImeCurrent by remember {
        mutableStateOf(UncachedInputMethodManagerUtils.isThisImeCurrent(ctx, imm))
    }
    var isSnapshotEnabled by remember {
        mutableStateOf(isSelectAndSearchEnabled(ctx))
    }
    var testText by remember { mutableStateOf("") }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isImeEnabled = UncachedInputMethodManagerUtils.isThisImeEnabled(ctx, imm)
        isImeCurrent = UncachedInputMethodManagerUtils.isThisImeCurrent(ctx, imm)
        isSnapshotEnabled = isSelectAndSearchEnabled(ctx)
    }

    // Continuously poll while onboarding is visible so toggling any system setting updates live
    LaunchedEffect(Unit) {
        while (true) {
            isImeEnabled = UncachedInputMethodManagerUtils.isThisImeEnabled(ctx, imm)
            isImeCurrent = UncachedInputMethodManagerUtils.isThisImeCurrent(ctx, imm)
            isSnapshotEnabled = isSelectAndSearchEnabled(ctx)
            delay(250L)
        }
    }

    WingAiTheme {
        WelcomeWizardContent(
            isImeEnabled = isImeEnabled,
            isImeCurrent = isImeCurrent,
            isSnapshotEnabled = isSnapshotEnabled,
            testText = testText,
            onTestTextChange = { testText = it },
            onEnableIme = {
                val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                }
                settingsLauncher.launch(intent)
            },
            onSelectIme = {
                imm.showInputMethodPicker()
            },
            onEnableSnapshot = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                settingsLauncher.launch(intent)
            },
            onContinue = close,
            onFinish = finish
        )
    }
}

@Composable
private fun WelcomeWizardContent(
    isImeEnabled: Boolean,
    isImeCurrent: Boolean,
    isSnapshotEnabled: Boolean,
    testText: String,
    onTestTextChange: (String) -> Unit,
    onEnableIme: () -> Unit,
    onSelectIme: () -> Unit,
    onEnableSnapshot: () -> Unit,
    onContinue: () -> Unit,
    onFinish: () -> Unit
) {
    val completedCount = listOf(isImeEnabled, isImeCurrent, isSnapshotEnabled).count { it }

    Scaffold(
        containerColor = WingAiTokens.BgCanvas,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Header Badge & Skip Link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(WingAiTokens.Surface1)
                        .border(1.dp, WingAiTokens.BorderSubtle, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(WingAiTokens.AccentPrimary)
                    )
                    Text(
                        text = "WINGAI BOARD SETUP",
                        color = WingAiTokens.TextSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }

                CompactPillButton(
                    text = if (isImeEnabled && isImeCurrent) "Done" else "Skip",
                    backgroundColor = WingAiTokens.Surface2,
                    textColor = WingAiTokens.TextSecondary,
                    height = 28.dp,
                    onClick = onContinue
                )
            }

            // Hero Title & Subtitle
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Good conversations start here.",
                    color = WingAiTokens.TextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 36.sp
                )
                Text(
                    text = "Make room for your words. Set up WingAI, then add optional screen replies when you need a hand.",
                    color = WingAiTokens.TextSecondary,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                )
            }

            // Segmented 3-Step Progress Bar
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$completedCount of 3 steps completed",
                        color = WingAiTokens.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (completedCount == 3) {
                        Text(
                            text = "Ready to use",
                            color = WingAiTokens.AccentEmerald,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val states = listOf(isImeEnabled, isImeCurrent, isSnapshotEnabled)
                    states.forEach { done ->
                        val barColor by animateColorAsState(
                            targetValue = if (done) WingAiTokens.AccentEmerald else WingAiTokens.Surface3,
                            animationSpec = tween(250),
                            label = "stepProgressBar"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(barColor)
                        )
                    }
                }
            }

            // Step 1 Card: Enable Keyboard
            OnboardingStepCard(
                stepNumber = "01",
                title = "Enable WingAI",
                description = "Add WingAI Board in Android's on-screen keyboard settings.",
                actionLabel = "Open keyboard settings",
                isCompleted = isImeEnabled,
                isActive = !isImeEnabled,
                onAction = onEnableIme
            )

            // Step 2 Card: Switch Active Input Method
            OnboardingStepCard(
                stepNumber = "02",
                title = "Make it your keyboard",
                description = "Choose WingAI Board to start typing in your favorite apps.",
                actionLabel = "Choose WingAI",
                isCompleted = isImeCurrent,
                isActive = isImeEnabled && !isImeCurrent,
                onAction = onSelectIme
            )

            // Step 3 Card: Enable Instant Select & Search
            OnboardingStepCard(
                stepNumber = "03",
                title = "Add screen replies",
                description = "Optional: enable the accessibility service to select chat areas for AI replies. Selected content is sent to your configured AI service when you request replies.",
                actionLabel = "Review permission",
                isCompleted = isSnapshotEnabled,
                isActive = isImeEnabled && isImeCurrent && !isSnapshotEnabled,
                onAction = onEnableSnapshot
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Primary Bottom CTA
            val primaryCtaLabel = when {
                !isImeEnabled -> "Step 1: Enable WingAI Board"
                !isImeCurrent -> "Step 2: Switch to WingAI Board"
                else -> "Open WingAI Settings"
            }
            val primaryCtaAction = when {
                !isImeEnabled -> onEnableIme
                !isImeCurrent -> onSelectIme
                else -> onContinue
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(WingAiTokens.RadiusMd)
                    .background(WingAiTokens.AccentPrimary)
                    .clickable(role = Role.Button, onClick = primaryCtaAction),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = primaryCtaLabel,
                    color = WingAiTokens.OnAccentPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun OnboardingStepCard(
    stepNumber: String,
    title: String,
    description: String,
    actionLabel: String,
    isCompleted: Boolean,
    isActive: Boolean,
    onAction: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            isCompleted -> WingAiTokens.AccentEmerald
            isActive -> WingAiTokens.AccentPrimary
            else -> WingAiTokens.BorderSubtle
        },
        animationSpec = tween(200),
        label = "stepBorder"
    )

    WingAiSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = WingAiTokens.Surface1,
        borderColor = borderColor,
        onClick = if (!isCompleted) onAction else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(WingAiTokens.RadiusSm)
                        .background(
                            when {
                                isCompleted -> WingAiTokens.AccentEmeraldContainer
                                isActive -> WingAiTokens.AccentPrimaryContainer
                                else -> WingAiTokens.Surface2
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isCompleted) "\u2713" else stepNumber,
                        color = when {
                            isCompleted -> WingAiTokens.AccentEmerald
                            isActive -> WingAiTokens.AccentPrimary
                            else -> WingAiTokens.TextSecondary
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = title,
                    color = WingAiTokens.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isCompleted) WingAiTokens.AccentEmeraldContainer else WingAiTokens.Surface2
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isCompleted) "Completed" else if (isActive) "Next Step" else "Pending",
                    color = if (isCompleted) WingAiTokens.AccentEmerald else WingAiTokens.TextSecondary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Text(
            text = description,
            color = WingAiTokens.TextSecondary,
            fontSize = 12.5.sp,
            lineHeight = 17.sp
        )

        if (!isCompleted) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                CompactPillButton(
                    text = actionLabel,
                    backgroundColor = if (isActive) WingAiTokens.AccentPrimary else WingAiTokens.Surface2,
                    borderColor = if (isActive) WingAiTokens.AccentPrimary else WingAiTokens.BorderSubtle,
                    textColor = if (isActive) WingAiTokens.OnAccentPrimary else WingAiTokens.TextPrimary,
                    height = 48.dp,
                    onClick = onAction
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomeWizardPreview() {
    WingAiTheme {
        WelcomeWizardContent(
            isImeEnabled = true,
            isImeCurrent = false,
            isSnapshotEnabled = false,
            testText = "",
            onTestTextChange = {},
            onEnableIme = {},
            onSelectIme = {},
            onEnableSnapshot = {},
            onContinue = {},
            onFinish = {}
        )
    }
}
