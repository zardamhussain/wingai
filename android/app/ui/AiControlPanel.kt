package com.wingai.keyboard.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wingai.keyboard.data.ActiveInputTarget
import com.wingai.keyboard.data.AiHubMode
import com.wingai.keyboard.data.AiSessionState
import com.wingai.keyboard.data.InputConnectionBridge
import com.wingai.keyboard.data.ReplySuggestion
import com.wingai.keyboard.data.ReplyTone
import com.wingai.keyboard.data.RewriteAction
import com.wingai.keyboard.data.UserProfile
import kotlinx.coroutines.delay

/**
 * Replica of Microsoft SwiftKey's Writing Assistance Hub (`res/layout/toolbar_panel_layout.xml`,
 * `res/layout/hub_view.xml`, `res/layout/toolbar_tone_change_panel.xml`, `res/layout/toolbar_tone_change_tone.xml`,
 * and `res/layout/toolbar_panel_bottom_bar.xml`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AiControlPanel(
    state: AiSessionState,
    panelHeight: Dp = 250.dp,
    isFloatingOverlay: Boolean = false,
    onAnalyzeScreen: () -> Unit,
    onPasteFromClipboard: () -> Unit,
    onToneSelect: (ReplyTone?) -> Unit,
    onHubModeSelect: (AiHubMode) -> Unit = {},
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
    val view = LocalView.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(panelHeight)
            .background(WingAiTokens.BgCanvas)
    ) {
        // 1. SWIFTKEY WRITING ASSISTANCE HUB TOP BAR (`res/layout/hub_view.xml` + `toolbar_panel_layout.xml`)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .padding(horizontal = 4.dp)
        ) {
            // Center-top expand grabber (`res/drawable/expand_grabber.xml`: 32dp x 4dp)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 3.dp)
                    .width(32.dp)
                    .height(3.5.dp)
                    .clip(CircleShape)
                    .background(WingAiTokens.TextPrimary.copy(alpha = 0.25f))
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Back Chevron + Single `Editor` Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // `id/toolbar_panel_back` (`ic_chevron_left_small`)
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable(
                                role = Role.Button,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onClose()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        SwiftKeyVectorIcon(
                            imageVector = SwiftKeyIcons.ChevronLeftSmall,
                            modifier = Modifier.size(18.dp),
                            tint = WingAiTokens.TextPrimary
                        )
                    }

                    SwiftKeyHubTabItem(
                        label = "Editor",
                        icon = SwiftKeyIcons.EditorPen,
                        selected = true,
                        onClick = {}
                    )
                }

                // Right: Single Outlined CTA Pill (`id/action_button` in `hub_view.xml`)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!state.selectedImageBase64.isNullOrBlank() || state.pastedContext.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .height(26.dp)
                                .clip(RoundedCornerShape(50))
                                .border(0.8.dp, WingAiTokens.BorderStrong, RoundedCornerShape(50))
                                .clickable(
                                    role = Role.Button,
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        onClearContext()
                                    }
                                )
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Reset",
                                color = WingAiTokens.TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .height(26.dp)
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, WingAiTokens.AccentPrimary, RoundedCornerShape(50))
                            .clickable(
                                role = Role.Button,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onAnalyzeScreen()
                                }
                            )
                            .padding(horizontal = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            SwiftKeyVectorIcon(
                                imageVector = SwiftKeyIcons.WingAiLogo,
                                modifier = Modifier.size(14.dp),
                                gradientBrush = WingAiTokens.WingAiBrandBrush
                            )
                            Text(
                                text = "Analyse Chat",
                                color = WingAiTokens.AccentPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Optional Floating Custom Instruction Input
        if (isFloatingOverlay && state.activeInputTarget == ActiveInputTarget.CUSTOM_INSTRUCTION) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.customInstruction,
                    onValueChange = onCustomInstructionChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "e.g. Tell them I am busy today",
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

        // 2. MAIN CONTENT CONTAINER (`id/toolbar_panel_content_container` -> `toolbar_tone_change_panel.xml`)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.isLoading -> {
                    // Exact `res/layout/panel_loading_layout.xml`: centered 28dp spinner + cycling text
                    SwiftKeyLoadingPanel(onCancelRequest = onCancelRequest)
                }
                !state.errorMessage.isNullOrBlank() && state.suggestions.isEmpty() -> {
                    SwiftKeyMessagingDialogCard(
                        title = "Couldn't generate text",
                        message = state.errorMessage,
                        primaryLabel = "Try again",
                        secondaryLabel = null,
                        onPrimaryClick = onGenerateReply,
                        onSecondaryClick = {}
                    )
                }
                state.suggestions.isNotEmpty() -> {
                    // Exact `yd0/a` & `toolbar_tone_change_panel.xml`:
                    // Horizontal PagerSnapHelper with 32dp end padding so the next card peeks on the right
                    val pagerState = rememberPagerState(pageCount = { state.suggestions.size })
                    var copiedPageIndex by remember(state.suggestions) { mutableStateOf<Int?>(null) }

                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = if (state.suggestions.size > 1) 32.dp else 12.dp
                        ),
                        pageSpacing = 8.dp,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val item = state.suggestions[page]
                        val isReplaced = state.lastReplacedText == item.text
                        val isCopied = copiedPageIndex == page

                        SwiftKeyToneCard(
                            item = item,
                            hubMode = state.hubMode,
                            isReplaced = isReplaced,
                            isCopied = isCopied,
                            onReplace = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onInsertReply(item.text)
                            },
                            onCopy = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                copiedPageIndex = page
                                onCopyReply(item.text)
                            }
                        )
                    }
                }
                else -> {
                    // Exact `res/layout/toolbar_dialog_messaging_view.xml` Empty State Card
                    SwiftKeyEmptyHubCard(
                        hubMode = state.hubMode,
                        hostDraftText = state.hostDraftText,
                        onAnalyzeScreen = onAnalyzeScreen,
                        onPasteFromClipboard = onPasteFromClipboard,
                        onRewriteDraft = onGenerateReply
                    )
                }
            }
        }

        // 2.5. COMPACT TONE / STYLE SELECTOR PILL BAR (Flirty, Casual, Friendly, Professional, Funny, Short, Detailed)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .padding(horizontal = 8.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            WingAiToneChip(
                label = "Rewrite",
                selected = state.selectedTone == null,
                accentColor = WingAiTokens.AccentPrimary,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onToneSelect(null)
                }
            )

            ReplyTone.entries.forEach { tone ->
                WingAiToneChip(
                    label = tone.label,
                    selected = state.selectedTone == tone,
                    accentColor = WingAiTokens.AccentPrimary,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onToneSelect(tone)
                    }
                )
            }
        }

        // 3. SWIFTKEY BOTTOM BAR (`res/layout/toolbar_panel_bottom_bar.xml` - elevated above Android gesture nav bar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // `id/toolbar_panel_abc_button` ("abc" button to return to keyboard)
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(WingAiTokens.Surface2)
                    .clickable(
                        role = Role.Button,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onClose()
                        }
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "abc",
                    color = WingAiTokens.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // `id/toolbar_panel_delete_key` (1:1 aspect ratio hold-to-repeat Backspace icon button)
            RepeatableBackspaceButton(
                onBackspace = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    InputConnectionBridge.sendBackspaceToHost()
                }
            )
        }
    }
}

/**
 * Replica of `res/layout/hub_item.xml`:
 * 14sp text + optional leading icon + 1.5dp bottom indicator bar (`id/hub_selected_border`).
 */
@Composable
private fun SwiftKeyHubTabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val textColor by animateColorAsState(
        targetValue = if (selected) WingAiTokens.TextPrimary else WingAiTokens.TextSecondary,
        animationSpec = tween(140),
        label = "hubItemText"
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) WingAiTokens.AccentPrimary else Color.Transparent,
        animationSpec = tween(140),
        label = "hubItemBorder"
    )

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            SwiftKeyVectorIcon(
                imageVector = icon,
                modifier = Modifier.size(15.dp),
                tint = if (selected) WingAiTokens.AccentPrimary else WingAiTokens.TextSecondary
            )
            Text(
                text = label,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1
            )
        }
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(1.8.dp)
                .background(indicatorColor)
        )
    }
}

/**
 * Exact replica of `res/layout/toolbar_tone_change_tone.xml` (`0x7f0d0112`) and `yd0/e`:
 * - Card background: `toolbar_panel_card_background_dark` (`#404040` dark / `#FFFFFF` light, `12dp` radius, `0.5dp` stroke)
 * - Top `ScrollView` (`paddingHorizontal = 18dp`, `marginTop = 14dp`):
 *   1. `id/tone_change_tone_type`: `14sp`, `toolbar_panel_card_label_background` (`16dp` radius pill, `alpha = 0.16f`, `paddingHorizontal = 10dp`, `paddingVertical = 1dp`)
 *   2. `id/tone_change_tone_title`: auto-sized `15sp..20sp` (`marginTop = 8dp`)
 * - Bottom left-aligned `Widget.Material3.Button.TextButton`s (`style/ToneChangeToneButton`, `paddingHorizontal = 18dp`):
 *   1. `id/tone_change_card_accept`: `ic_accept_and_replace` (`20dp`) + `"Replace"` (or `"Replaced"` when selected)
 *   2. `id/tone_change_card_copy`: `ic_copy` (`20dp`) + `"Copy"` (or `"Copied"` when clicked)
 */
@Composable
private fun SwiftKeyToneCard(
    item: ReplySuggestion,
    hubMode: AiHubMode,
    isReplaced: Boolean,
    isCopied: Boolean,
    onReplace: () -> Unit,
    onCopy: () -> Unit
) {
    val cardShape = RoundedCornerShape(12.dp)

    // Auto-size text between 15sp and 20sp in 1sp increments like SwiftKey's yd0/b OnLayoutChangeListener
    val titleFontSize = when {
        item.text.length <= 48 -> 19.sp
        item.text.length <= 85 -> 17.sp
        item.text.length <= 135 -> 16.sp
        else -> 15.sp
    }
    val titleLineHeight = when {
        item.text.length <= 48 -> 24.sp
        item.text.length <= 85 -> 22.sp
        else -> 20.sp
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(cardShape)
            .background(WingAiTokens.Surface1)
            .border(0.5.dp, WingAiTokens.BorderSubtle, cardShape),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Scrollable top section (`id/tone_change_card_scroll_view`)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .clickable(role = Role.Button, onClick = onReplace)
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // `id/tone_change_tone_type` (`toolbar_panel_card_label_background`: 16dp radius, 10dp horizontal, 1dp vertical)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(WingAiTokens.cardPillBackground())
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (hubMode == AiHubMode.POLISH) {
                        "Grammar, Spelling, Punctuation"
                    } else {
                        item.tone.replaceFirstChar { it.uppercase() }
                    },
                    color = WingAiTokens.TextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            // `id/tone_change_tone_title`
            Text(
                text = item.text,
                color = WingAiTokens.TextPrimary,
                fontSize = titleFontSize,
                lineHeight = titleLineHeight,
                fontWeight = FontWeight.Normal
            )
        }

        // Bottom left-aligned Material3 TextButtons (`id/tone_change_card_accept` & `id/tone_change_card_copy`)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .padding(start = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // `id/tone_change_card_accept`: Replace / Replaced
            val replaceTint = if (isReplaced) WingAiTokens.AccentPrimary else WingAiTokens.TextPrimary
            Row(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(role = Role.Button, onClick = onReplace)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SwiftKeyVectorIcon(
                    imageVector = SwiftKeyIcons.ReplaceUnselected,
                    modifier = Modifier.size(18.dp),
                    tint = replaceTint
                )
                Text(
                    text = if (isReplaced) "Replaced" else "Replace",
                    color = replaceTint,
                    fontSize = 14.sp,
                    fontWeight = if (isReplaced) FontWeight.Medium else FontWeight.Normal
                )
            }

            // `id/tone_change_card_copy`: Copy / Copied
            val copyTint = if (isCopied) WingAiTokens.AccentPrimary else WingAiTokens.TextPrimary
            Row(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(role = Role.Button, onClick = onCopy)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SwiftKeyVectorIcon(
                    imageVector = SwiftKeyIcons.CopyUnselected,
                    modifier = Modifier.size(18.dp),
                    tint = copyTint
                )
                Text(
                    text = if (isCopied) "Copied" else "Copy",
                    color = copyTint,
                    fontSize = 14.sp,
                    fontWeight = if (isCopied) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Exact replica of `res/layout/panel_loading_layout.xml`:
 * Centered 28dp progress spinner + cycling status text (`"Analysing text…"` -> `"Generating alternative versions…"` -> `"Suggesting new tones…"`).
 */
@Composable
private fun SwiftKeyLoadingPanel(onCancelRequest: () -> Unit) {
    val messages = remember {
        listOf(
            "Analysing text\u2026",
            "Generating alternative versions\u2026",
            "Suggesting new tones\u2026"
        )
    }
    var step by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1150L)
            step = (step + 1) % messages.size
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(WingAiTokens.Surface1)
            .border(0.5.dp, WingAiTokens.BorderSubtle, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.5.dp,
                color = WingAiTokens.AccentPrimary
            )
            AnimatedContent(
                targetState = messages[step],
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                label = "swiftKeyLoadingStatus"
            ) { statusText ->
                Text(
                    text = statusText,
                    color = WingAiTokens.TextPrimary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = onCancelRequest)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Cancel",
                color = WingAiTokens.TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Unified Editor empty state card:
 * - No Microsoft mention
 * - No Paste or Analyse buttons inside the Editor card (`Analyse Chat` stays in the top bar for screen capture; Editor works on typed input or generated text)
 */
@Composable
private fun SwiftKeyEmptyHubCard(
    hubMode: AiHubMode,
    hostDraftText: String,
    onAnalyzeScreen: () -> Unit,
    onPasteFromClipboard: () -> Unit,
    onRewriteDraft: () -> Unit
) {
    val liveDraft = remember(hubMode, hostDraftText) {
        InputConnectionBridge.readHostFieldText(preferSelection = true).ifBlank { hostDraftText }
    }
    val hasText = liveDraft.isNotBlank()

    SwiftKeyMessagingDialogCard(
        icon = SwiftKeyIcons.EditorPen,
        title = "WingAI Editor",
        message = if (hasText) {
            "\"$liveDraft\""
        } else {
            "Type in the text box to rewrite or change tone, or tap Analyse Chat above to capture a conversation."
        },
        primaryLabel = if (hasText) "Rewrite text" else null,
        secondaryLabel = null,
        onPrimaryClick = onRewriteDraft,
        onSecondaryClick = {}
    )
}

@Composable
private fun SwiftKeyMessagingDialogCard(
    icon: ImageVector = SwiftKeyIcons.EditorPen,
    title: String,
    message: String,
    primaryLabel: String? = null,
    secondaryLabel: String? = null,
    onPrimaryClick: () -> Unit = {},
    onSecondaryClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(WingAiTokens.Surface1)
            .border(0.5.dp, WingAiTokens.BorderSubtle, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (primaryLabel != null || secondaryLabel != null) {
            Arrangement.SpaceBetween
        } else {
            Arrangement.Center
        }
    ) {
        // `id/title_and_side_image_wrapper` + `id/message_view`
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SwiftKeyVectorIcon(
                    imageVector = icon,
                    modifier = Modifier.size(20.dp),
                    tint = WingAiTokens.AccentPrimary
                )
                Text(
                    text = title,
                    color = WingAiTokens.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = message,
                color = WingAiTokens.TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (primaryLabel != null || secondaryLabel != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (secondaryLabel != null) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, WingAiTokens.BorderStrong, RoundedCornerShape(20.dp))
                            .clickable(role = Role.Button, onClick = onSecondaryClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = secondaryLabel,
                            color = WingAiTokens.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (primaryLabel != null) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(WingAiTokens.AccentPrimary)
                            .clickable(role = Role.Button, onClick = onPrimaryClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = primaryLabel,
                            color = WingAiTokens.OnAccentPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * SwiftKey-style Hold-to-Repeat Delete Key (`id/toolbar_panel_delete_key` in `toolbar_panel_bottom_bar.xml`):
 * 1:1 ratio flat icon button on the bottom-right.
 */
@Composable
private fun RepeatableBackspaceButton(onBackspace: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "deleteKeyScale"
    )

    LaunchedEffect(isPressed) {
        if (isPressed) {
            onBackspace()
            delay(380L)
            while (isPressed) {
                onBackspace()
                delay(65L)
            }
        }
    }

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "\u232B",
            color = WingAiTokens.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 250)
@Composable
private fun AiControlPanelRepliesPreview() {
    WingAiTheme {
        AiControlPanel(
            state = AiSessionState(
                isPanelOpen = true,
                hubMode = AiHubMode.REWRITE,
                pastedContext = "Hey! Are we still on for coffee at 4pm today?",
                userProfile = UserProfile(userId = "wing_9f8a7b6c", tier = "free", dailyUsed = 2, dailyLimit = 5, remaining = 3),
                suggestions = listOf(
                    ReplySuggestion("Professional", "Yes, 4:00 PM still works well for me. Looking forward to meeting."),
                    ReplySuggestion("Casual", "Yep, 4pm works for me! See you there."),
                    ReplySuggestion("Polite", "Yes, I would love to meet at 4pm today. Thank you for checking!")
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
