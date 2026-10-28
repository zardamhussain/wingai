package com.wingai.keyboard.ui

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wingai.keyboard.data.ActiveInputTarget
import com.wingai.keyboard.data.AiSessionState
import com.wingai.keyboard.data.InputConnectionBridge
import com.wingai.keyboard.data.ReplyTone
import com.wingai.keyboard.data.RewriteAction
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination

@Composable
fun AiControlPanel(
    state: AiSessionState,
    panelHeight: Dp = 225.dp,
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp, max = panelHeight)
            .background(Color(0xFF131824), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF28324B), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // If inside the floating overlay (no keyboard toolbar), show a slim top action bar
        if (isFloatingOverlay) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompactPillButton(
                        text = "\uD83D\uDCF8 Read Screen",
                        backgroundColor = Color(0xFF5C6BC0),
                        textColor = Color.White,
                        onClick = onAnalyzeScreen
                    )
                    CompactPillButton(
                        text = "Paste",
                        backgroundColor = Color(0xFF232C42),
                        onClick = onPasteFromClipboard
                    )
                    CompactPillButton(
                        text = "Generate",
                        backgroundColor = Color(0xFF2E3B59),
                        onClick = onGenerateReply
                    )
                }
                CompactPillButton(
                    text = "\u2715",
                    backgroundColor = Color(0xFF232C42),
                    onClick = onClose
                )
            }
        }

        // Row 1: Account Plan badge + Paste Chat + Tone selector + Reset
        val context = LocalContext.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val user = state.userProfile
            val planLabel = when {
                user == null -> "\uD83D\uDC64 Plan"
                user.isPaid -> "\uD83D\uDC51 Paid"
                else -> "\uD83D\uDC64 Free (${user.remaining ?: 5}/${user.dailyLimit ?: 5})"
            }
            CompactPillButton(
                text = planLabel,
                backgroundColor = if (user?.isPaid == true) Color(0xFF2E7D32) else Color(0xFF283593),
                textColor = Color.White,
                onClick = {
                    val intent = Intent(context, SettingsActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    SettingsDestination.navigateTo(SettingsDestination.WingAiUser)
                }
            )
            CompactPillButton(
                text = "Paste Chat",
                backgroundColor = Color(0xFF232C42),
                textColor = Color.White,
                onClick = onPasteFromClipboard
            )
            ToneChip(
                label = "All 3 Tones",
                selected = state.selectedTone == null,
                onClick = { onToneSelect(null) }
            )
            ReplyTone.entries.forEach { tone ->
                ToneChip(
                    label = tone.label,
                    selected = state.selectedTone == tone,
                    onClick = { onToneSelect(tone) }
                )
            }
            if (state.suggestions.isNotEmpty() || state.customInstruction.isNotBlank() || !state.errorMessage.isNullOrBlank()) {
                CompactPillButton(
                    text = "Reset",
                    backgroundColor = Color(0xFF262F45),
                    textColor = Color(0xFFB0BEC5),
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
                    placeholder = { Text("e.g. Tell them I am busy today", fontSize = 11.sp, color = Color(0xFF90A4AE)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF1E273C),
                        unfocusedContainerColor = Color(0xFF1E273C),
                        focusedBorderColor = Color(0xFF42A5F5),
                        unfocusedBorderColor = Color(0xFF546E7A),
                        cursorColor = Color.White
                    ),
                    singleLine = true
                )
                CompactPillButton(
                    text = "Apply",
                    backgroundColor = Color(0xFF5C6BC0),
                    textColor = Color.White,
                    onClick = {
                        InputConnectionBridge.updateState {
                            it.copy(activeInputTarget = ActiveInputTarget.HOST_APP)
                        }
                        onAnalyzeScreen()
                    }
                )
            }
        }

        // Scrollable content area for status, errors, and the 3 reply cards
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (state.pastedContext.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A253B), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF3949AB), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                        Text(
                            text = "\uD83D\uDCCB Pasted Chat Context:",
                            color = Color(0xFF90CAF9),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.pastedContext,
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    CompactPillButton(
                        text = "\u2715",
                        backgroundColor = Color(0xFF28334D),
                        onClick = { onPastedContextChange("") }
                    )
                }
            }

            if (state.isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1C2438), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF82B1FF)
                        )
                        Text(
                            text = "Reading conversation & crafting 3 replies...",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                    CompactPillButton(
                        text = "Cancel",
                        backgroundColor = Color(0xFF4A252C),
                        textColor = Color(0xFFFF8A80),
                        onClick = onCancelRequest
                    )
                }
            }

            if (!state.errorMessage.isNullOrBlank() && !state.isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF3B1D26), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF6E303E), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.errorMessage,
                        color = Color(0xFFFFCDD2),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    CompactPillButton(
                        text = "Retry",
                        backgroundColor = Color(0xFF5C6BC0),
                        textColor = Color.White,
                        onClick = onAnalyzeScreen
                    )
                }
            }

            if (state.suggestions.isEmpty() && !state.isLoading && state.errorMessage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1B2234), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tap \"Analyze Screen\" above (or double-tap \uD83E\uDEBD) to read the chat and generate 3 replies.",
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.sp
                    )
                }
            }

            state.suggestions.forEach { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E273C))
                        .border(1.dp, Color(0xFF2F3D5C), RoundedCornerShape(10.dp))
                        .clickable { onInsertReply(item.text) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.tone.uppercase(),
                            color = Color(0xFF80CBC4),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CompactPillButton(
                                text = "Shorten",
                                backgroundColor = Color(0xFF28334D),
                                onClick = { onRewriteAction(RewriteAction.SHORTEN, item.text) }
                            )
                            CompactPillButton(
                                text = "Expand",
                                backgroundColor = Color(0xFF28334D),
                                onClick = { onRewriteAction(RewriteAction.EXPAND, item.text) }
                            )
                            if (isFloatingOverlay) {
                                CompactPillButton(
                                    text = "Copy",
                                    backgroundColor = Color(0xFF28334D),
                                    onClick = { onCopyReply(item.text) }
                                )
                            }
                            CompactPillButton(
                                text = "Insert \u2197",
                                backgroundColor = Color(0xFF5C6BC0),
                                textColor = Color.White,
                                onClick = { onInsertReply(item.text) }
                            )
                        }
                    }
                    Text(
                        text = item.text,
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CompactPillButton(
    text: String,
    backgroundColor: Color = Color(0xFF232C42),
    textColor: Color = Color(0xFFE3F2FD),
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ToneChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0xFF3949AB) else Color(0xFF1F273A))
            .border(
                width = 1.dp,
                color = if (selected) Color(0xFF7986CB) else Color(0xFF34415E),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color(0xFFCFD8DC),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}
