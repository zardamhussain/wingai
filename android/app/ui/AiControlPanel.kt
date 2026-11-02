package com.wingai.keyboard.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wingai.keyboard.data.ActiveInputTarget
import com.wingai.keyboard.data.AiSessionState
import com.wingai.keyboard.data.InputConnectionBridge
import com.wingai.keyboard.data.ReplySuggestion
import com.wingai.keyboard.data.ReplyTone
import com.wingai.keyboard.data.RewriteAction
import com.wingai.keyboard.data.UserProfile
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination

@Composable
fun AiControlPanel(
    state: AiSessionState,
    panelHeight: Dp = 250.dp,
    isFloatingOverlay: Boolean = false,
    onAnalyzeScreen: () -> Unit,
    onPasteFromClipboard: () -> Unit,
    onToneSelect: (ReplyTone?) -> Unit,
    onCustomInstructionChange: (String) -> Unit,
    onPastedContextChange: (String) -> Unit,
    onGenerateReply: () -> Unit,
    onConfirmUploadCapture: () -> Unit,
    onCancelPendingCapture: () -> Unit,
    onCancelRequest: () -> Unit,
    onSelectSuggestion: (String) -> Unit,
    onEditingReplyChange: (String) -> Unit,
    onInsertReply: (String) -> Unit,
    onCopyReply: (String) -> Unit,
    onRewriteAction: (RewriteAction, String) -> Unit,
    onClearContext: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp, max = panelHeight)
            .background(WingAiTokens.BgCanvas)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Your reply, your way",
            color = WingAiTokens.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (isFloatingOverlay) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompactPillButton(
                        text = "Analyze chat",
                        backgroundColor = WingAiTokens.AccentPrimary,
                        borderColor = WingAiTokens.AccentPrimary,
                        textColor = WingAiTokens.OnAccentPrimary,
                        onClick = onAnalyzeScreen
                    )
                    CompactPillButton(
                        text = "Paste",
                        backgroundColor = WingAiTokens.Surface1,
                        onClick = onPasteFromClipboard
                    )
                }
                CompactPillButton(
                    text = "\u2715",
                    backgroundColor = WingAiTokens.Surface2,
                    onClick = onClose
                )
            }
        }

        // Top Key-Cap Strip: Plan + Paste + Tone Chips + Reset
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val user = state.userProfile
            val isPaid = user?.isPaid == true
            val planLabel = when {
                user == null -> "My plan"
                isPaid -> "Pro"
                else -> "${user.remaining ?: 5} left today"
            }
            CompactPillButton(
                text = planLabel,
                backgroundColor = WingAiTokens.Surface2,
                borderColor = WingAiTokens.BorderSubtle,
                textColor = if (isPaid) WingAiTokens.AccentEmerald else WingAiTokens.TextSecondary,
                onClick = {
                    runCatching {
                        val intent = Intent(context, SettingsActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        SettingsDestination.navigateTo(SettingsDestination.WingAiUser)
                    }
                }
            )

            CompactPillButton(
                text = "Paste chat",
                backgroundColor = WingAiTokens.Surface1,
                borderColor = WingAiTokens.BorderSubtle,
                onClick = onPasteFromClipboard
            )

            WingAiToneChip(
                label = "Mix it up",
                selected = state.selectedTone == null,
                accentColor = WingAiTokens.AccentPrimary,
                onClick = { onToneSelect(null) }
            )

            ReplyTone.entries.forEach { tone ->
                WingAiToneChip(
                    label = tone.label,
                    selected = state.selectedTone == tone,
                    accentColor = WingAiTokens.toneColor(tone.wireName),
                    onClick = { onToneSelect(tone) }
                )
            }

            if (
                state.suggestions.isNotEmpty() ||
                state.customInstruction.isNotBlank() ||
                !state.selectedImageBase64.isNullOrBlank() ||
                state.pastedContext.isNotBlank() ||
                !state.errorMessage.isNullOrBlank()
            ) {
                CompactPillButton(
                    text = "Reset",
                    backgroundColor = WingAiTokens.Surface2,
                    textColor = WingAiTokens.TextSecondary,
                    onClick = onClearContext
                )
            }
        }

        if (isFloatingOverlay && state.activeInputTarget == ActiveInputTarget.CUSTOM_INSTRUCTION) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.customInstruction,
                    onValueChange = onCustomInstructionChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "e.g. Suggest coffee tomorrow instead",
                            fontSize = 11.sp,
                            color = WingAiTokens.TextMuted
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = WingAiTokens.TextPrimary,
                        unfocusedTextColor = WingAiTokens.TextPrimary,
                        focusedContainerColor = WingAiTokens.Surface1,
                        unfocusedContainerColor = WingAiTokens.Surface1,
                        focusedBorderColor = WingAiTokens.AccentPrimary,
                        unfocusedBorderColor = WingAiTokens.BorderSubtle,
                        cursorColor = WingAiTokens.AccentPrimary
                    ),
                    singleLine = true
                )
                CompactPillButton(
                    text = "Apply",
                    backgroundColor = WingAiTokens.AccentPrimary,
                    borderColor = WingAiTokens.AccentPrimary,
                    textColor = WingAiTokens.OnAccentPrimary,
                    onClick = {
                        InputConnectionBridge.updateState {
                            it.copy(activeInputTarget = ActiveInputTarget.HOST_APP)
                        }
                        onAnalyzeScreen()
                    }
                )
            }
        }

        // Scrollable Workspace: Context Badges, Loading/Error States, and 3 Reply Key-Cards
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Active Custom Instruction Pill
            AnimatedVisibility(
                visible = state.customInstruction.isNotBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(WingAiTokens.RadiusSm)
                        .background(WingAiTokens.Surface2)
                        .border(1.dp, WingAiTokens.BorderSubtle, WingAiTokens.RadiusSm)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Prompt: ${state.customInstruction}",
                        color = WingAiTokens.TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                    )
                    CompactPillButton(
                        text = "\u2715",
                        height = 24.dp,
                        backgroundColor = WingAiTokens.Surface1,
                        onClick = { onCustomInstructionChange("") }
                    )
                }
            }

            // Selected Image Region Preview Banner
            AnimatedVisibility(
                visible = !state.selectedImageBase64.isNullOrBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val previewBitmap = remember(state.selectedImageBase64) {
                    runCatching {
                        val bytes = Base64.decode(state.selectedImageBase64, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }.getOrNull()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(WingAiTokens.RadiusSm)
                        .background(WingAiTokens.Surface1)
                        .border(1.dp, WingAiTokens.BorderSubtle, WingAiTokens.RadiusSm)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap,
                                contentDescription = "Selected chat region",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(50.dp)
                                    .height(32.dp)
                                    .clip(WingAiTokens.RadiusSm)
                                    .border(1.dp, WingAiTokens.BorderStrong, WingAiTokens.RadiusSm)
                            )
                        }
                        Column {
                            Text(
                                text = "Selected Screen Area",
                                color = WingAiTokens.TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Reusing cropped selection",
                                color = WingAiTokens.TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        CompactPillButton(
                            text = "Re-select",
                            height = 24.dp,
                            fontSize = 11.sp,
                            backgroundColor = WingAiTokens.Surface2,
                            onClick = onAnalyzeScreen
                        )
                        CompactPillButton(
                            text = "\u2715",
                            height = 24.dp,
                            fontSize = 11.sp,
                            backgroundColor = WingAiTokens.Surface2,
                            onClick = onCancelPendingCapture
                        )
                    }
                }
            }

            // Pasted Chat Context Preview Banner
            AnimatedVisibility(
                visible = state.pastedContext.isNotBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(WingAiTokens.RadiusSm)
                        .background(WingAiTokens.Surface1)
                        .border(1.dp, WingAiTokens.BorderSubtle, WingAiTokens.RadiusSm)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Pasted Chat",
                            color = WingAiTokens.TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = state.pastedContext,
                            color = WingAiTokens.TextPrimary,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    CompactPillButton(
                        text = "\u2715",
                        height = 24.dp,
                        fontSize = 11.sp,
                        backgroundColor = WingAiTokens.Surface2,
                        onClick = { onPastedContextChange("") }
                    )
                }
            }

            // Skeleton Loading State (3 Reply Card Skeletons)
            if (state.isLoading) {
                repeat(3) { index ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(WingAiTokens.RadiusSm)
                            .background(WingAiTokens.Surface1)
                            .border(1.dp, WingAiTokens.BorderSubtle, WingAiTokens.RadiusSm)
                            .padding(horizontal = 11.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                WingAiSkeletonBox(
                                    modifier = Modifier.size(6.dp),
                                    shape = CircleShape
                                )
                                WingAiSkeletonBox(
                                    modifier = Modifier
                                        .width(64.dp)
                                        .height(10.dp)
                                )
                            }
                            if (index == 0) {
                                CompactPillButton(
                                    text = "Cancel",
                                    height = 22.dp,
                                    fontSize = 10.5.sp,
                                    backgroundColor = WingAiTokens.Surface2,
                                    textColor = WingAiTokens.TextSecondary,
                                    onClick = onCancelRequest
                                )
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    WingAiSkeletonBox(
                                        modifier = Modifier
                                            .width(48.dp)
                                            .height(22.dp)
                                    )
                                    WingAiSkeletonBox(
                                        modifier = Modifier
                                            .width(48.dp)
                                            .height(22.dp)
                                    )
                                }
                            }
                        }
                        WingAiSkeletonBox(
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .height(13.dp)
                        )
                        WingAiSkeletonBox(
                            modifier = Modifier
                                .fillMaxWidth(if (index == 1) 0.72f else 0.60f)
                                .height(13.dp)
                        )
                    }
                }
            }

            // Error State Banner
            if (!state.errorMessage.isNullOrBlank() && !state.isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(WingAiTokens.RadiusMd)
                        .background(WingAiTokens.AccentRoseContainer)
                        .border(1.dp, WingAiTokens.AccentRose, WingAiTokens.RadiusMd)
                        .padding(horizontal = 11.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.errorMessage,
                        color = WingAiTokens.TextPrimary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    CompactPillButton(
                        text = "Try again",
                        height = 26.dp,
                        fontSize = 11.sp,
                        backgroundColor = WingAiTokens.AccentPrimary,
                        borderColor = WingAiTokens.AccentPrimary,
                        textColor = WingAiTokens.OnAccentPrimary,
                        onClick = onAnalyzeScreen
                    )
                }
            }

            // Minimal Empty State Card
            if (state.suggestions.isEmpty() && !state.isLoading && state.errorMessage.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(WingAiTokens.RadiusMd)
                        .background(WingAiTokens.Surface1)
                        .border(1.dp, WingAiTokens.BorderSubtle, WingAiTokens.RadiusMd)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Find your next words.",
                        color = WingAiTokens.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Choose a chat area or paste a conversation. Get a few ways to say it, then make one yours.",
                        color = WingAiTokens.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompactPillButton(
                            text = "Analyze chat",
                            backgroundColor = WingAiTokens.AccentPrimary,
                            borderColor = WingAiTokens.AccentPrimary,
                            textColor = WingAiTokens.OnAccentPrimary,
                            onClick = onAnalyzeScreen
                        )
                        CompactPillButton(
                            text = "Paste chat",
                            backgroundColor = WingAiTokens.Surface2,
                            borderColor = WingAiTokens.BorderSubtle,
                            onClick = onPasteFromClipboard
                        )
                    }
                }
            }

            // 3 Reply Key-Cards
            if (!state.isLoading) {
                state.suggestions.forEach { item ->
                    ReplySuggestionCard(
                        item = item,
                        isFloatingOverlay = isFloatingOverlay,
                        onInsert = { onInsertReply(item.text) },
                        onCopy = { onCopyReply(item.text) },
                        onShorten = { onRewriteAction(RewriteAction.SHORTEN, item.text) },
                        onExpand = { onRewriteAction(RewriteAction.EXPAND, item.text) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplySuggestionCard(
    item: ReplySuggestion,
    isFloatingOverlay: Boolean,
    onInsert: () -> Unit,
    onCopy: () -> Unit,
    onShorten: () -> Unit,
    onExpand: () -> Unit
) {
    val badgeColor = WingAiTokens.toneColor(item.tone)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(WingAiTokens.RadiusMd)
            .background(WingAiTokens.Surface1)
            .border(1.dp, WingAiTokens.BorderSubtle, WingAiTokens.RadiusMd)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                )
                Text(
                    text = item.tone.uppercase(),
                    color = WingAiTokens.TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
            }
        }
        Text(
            text = item.text,
            color = WingAiTokens.TextPrimary,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        // Explicit insertion avoids accidental taps while scrolling. Actions stay reachable
        // on narrow keyboards and at increased font scales without shrinking the reply text.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
                CompactPillButton(
                    text = "Shorten",
                    height = 36.dp,
                    fontSize = 11.sp,
                    backgroundColor = WingAiTokens.Surface2,
                    textColor = WingAiTokens.TextSecondary,
                    onClick = onShorten
                )
                CompactPillButton(
                    text = "Expand",
                    height = 36.dp,
                    fontSize = 11.sp,
                    backgroundColor = WingAiTokens.Surface2,
                    textColor = WingAiTokens.TextSecondary,
                    onClick = onExpand
                )
                if (isFloatingOverlay) {
                    CompactPillButton(
                        text = "Copy",
                        height = 36.dp,
                        fontSize = 11.sp,
                        backgroundColor = WingAiTokens.Surface2,
                        onClick = onCopy
                    )
                }
                CompactPillButton(
                    text = "Insert",
                    height = 36.dp,
                    fontSize = 11.sp,
                    backgroundColor = WingAiTokens.AccentPrimary,
                    borderColor = WingAiTokens.AccentPrimary,
                    textColor = WingAiTokens.OnAccentPrimary,
                    onClick = onInsert
                )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 250)
@Composable
private fun AiControlPanelRepliesPreview() {
    WingAiTheme {
        AiControlPanel(
            state = AiSessionState(
                isPanelOpen = true,
                pastedContext = "Hey! Are we still on for coffee at 4pm today?",
                userProfile = UserProfile(userId = "wing_9f8a7b6c", tier = "free", dailyUsed = 2, dailyLimit = 5, remaining = 3),
                suggestions = listOf(
                    ReplySuggestion("casual", "Yep, 4pm works for me! See you there."),
                    ReplySuggestion("friendly", "Yes! Really looking forward to catching up at 4."),
                    ReplySuggestion("professional", "Yes, 4:00 PM still works well for me. See you soon.")
                )
            ),
            onAnalyzeScreen = {},
            onPasteFromClipboard = {},
            onToneSelect = {},
            onCustomInstructionChange = {},
            onPastedContextChange = {},
            onGenerateReply = {},
            onConfirmUploadCapture = {},
            onCancelPendingCapture = {},
            onCancelRequest = {},
            onSelectSuggestion = {},
            onEditingReplyChange = {},
            onInsertReply = {},
            onCopyReply = {},
            onRewriteAction = { _, _ -> },
            onClearContext = {},
            onClose = {}
        )
    }
}
