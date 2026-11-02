package com.wingai.keyboard.ui

import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.wingai.keyboard.ai.AiSessionController
import com.wingai.keyboard.data.AiHubMode
import com.wingai.keyboard.data.InputConnectionBridge
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay

/**
 * LEVEL 1: Microsoft SwiftKey Top Features / Toolbar Row (`res/layout/toolbar_view.xml`, `id/toolbar_frame`, `40dp` height).
 *
 * From SwiftKey v9.13.18.4 (`td0/c1`, `td0/d`, `toolbar_recycler_item.xml`):
 * - Height: `40dp` (`dimen/toolbar_height = 40.0dip`)
 * - Contains evenly spaced `40dp x 40dp` feature buttons (`dimen/toolbar_button_side = 40.0dip`, `20dp` icon size):
 *   1. Copilot / Analyse Chat (`ic_copilot_unselected` / `SparkleAi`, opens Screen Selection or Reply Hub)
 *   2. Tone (`ic_tone`, opens Writing Assistance Hub in Tone mode)
 *   3. Editor (`ic_editor`, opens Writing Assistance Hub in Editor mode)
 *   4. Clipboard (`ic_clipboard`, opens Clipboard History)
 *   5. Emoji / GIF (`ic_emoji_tab`, opens Emoji Palette)
 *   6. Voice Typing (`ic_voice_typing`, triggers Voice Input)
 *   7. Settings / More (`ic_settings`, opens Settings)
 */
class WingAiFeaturesRowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)
    private var scope: CoroutineScope? = null

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    init {
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val currentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = currentScope
        val aiController = AiSessionController(context.applicationContext, currentScope)

        rootView?.let { root ->
            root.setViewTreeLifecycleOwner(this)
            root.setViewTreeViewModelStoreOwner(this)
            root.setViewTreeSavedStateRegistryOwner(this)
        }
        setViewTreeLifecycleOwner(this)
        setViewTreeViewModelStoreOwner(this)
        setViewTreeSavedStateRegistryOwner(this)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        removeAllViews()
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@WingAiFeaturesRowView)
            setViewTreeViewModelStoreOwner(this@WingAiFeaturesRowView)
            setViewTreeSavedStateRegistryOwner(this@WingAiFeaturesRowView)
            setContent {
                WingAiTheme {
                    val state by InputConnectionBridge.sessionState.collectAsState()
                    val isPassword = InputConnectionBridge.isPasswordFieldActive()
                    val view = LocalView.current

                    AnimatedVisibility(
                        visible = state.isFeaturesRowOpen && !state.isPanelOpen,
                        enter = expandVertically(tween(180)) + fadeIn(tween(150)),
                        exit = shrinkVertically(tween(160)) + fadeOut(tween(120))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .background(WingAiTokens.BgCanvas)
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. WingAI Editor (`WingAiLogo` on the far left of Level 1)
                            SwiftKeyFeatureItemButton(
                                icon = SwiftKeyIcons.WingAiLogo,
                                contentDescription = "WingAI Editor",
                                isActive = state.isPanelOpen,
                                enabled = !isPassword,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    InputConnectionBridge.updateState {
                                        it.copy(
                                            isPanelOpen = true,
                                            hubMode = AiHubMode.REWRITE
                                        )
                                    }
                                    aiController.syncHostDraftOnOpen()
                                }
                            )

                            // 2. Analyse Chat Pill (`WingAiLogo` gradient + "Analyse Chat")
                            Box(
                                modifier = Modifier
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(WingAiTokens.Surface2)
                                    .border(
                                        width = 1.dp,
                                        brush = WingAiTokens.WingAiBrandBrush,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable(
                                        enabled = !isPassword,
                                        role = Role.Button,
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            aiController.startScreenAnalysis()
                                        }
                                    )
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    SwiftKeyVectorIcon(
                                        imageVector = SwiftKeyIcons.WingAiLogo,
                                        modifier = Modifier.size(16.dp),
                                        gradientBrush = WingAiTokens.WingAiBrandBrush
                                    )
                                    Text(
                                        text = "Analyse Chat",
                                        color = WingAiTokens.TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }

                            // 3. Editor (`EditorPen` - opens Editor panel for input or generated text)
                            SwiftKeyFeatureItemButton(
                                icon = SwiftKeyIcons.EditorPen,
                                contentDescription = "Editor",
                                isActive = state.isPanelOpen,
                                enabled = !isPassword,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    InputConnectionBridge.updateState {
                                        it.copy(
                                            isPanelOpen = true,
                                            hubMode = AiHubMode.REWRITE
                                        )
                                    }
                                    aiController.syncHostDraftOnOpen()
                                }
                            )

                            // 4. Stickers / Emoji (`ic_sticker_sparkle_unselected`)
                            SwiftKeyFeatureItemButton(
                                icon = SwiftKeyIcons.StickerSparkle,
                                contentDescription = "Emoji & Stickers",
                                isActive = false,
                                enabled = true,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    InputConnectionBridge.triggerKeyboardKeyCode(KeyCode.EMOJI)
                                }
                            )

                            // 5. Clipboard (`ic_clipboard`)
                            SwiftKeyFeatureItemButton(
                                icon = SwiftKeyIcons.ClipboardBoard,
                                contentDescription = "Clipboard",
                                isActive = false,
                                enabled = true,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    InputConnectionBridge.triggerKeyboardKeyCode(KeyCode.CLIPBOARD)
                                }
                            )

                            // 6. Voice Typing (`ic_voice_typing`)
                            SwiftKeyFeatureItemButton(
                                icon = SwiftKeyIcons.VoiceMic,
                                contentDescription = "Voice typing",
                                isActive = false,
                                enabled = true,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    InputConnectionBridge.triggerKeyboardKeyCode(KeyCode.VOICE_INPUT)
                                }
                            )

                            // 7. More / Settings (`...`)
                            SwiftKeyFeatureItemButton(
                                icon = SwiftKeyIcons.MoreHorizontalDots,
                                contentDescription = "Settings",
                                isActive = false,
                                enabled = true,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    InputConnectionBridge.triggerKeyboardKeyCode(KeyCode.SETTINGS)
                                }
                            )
                        }
                    }
                }
            }
        }
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }
}

/**
 * Exact replica of `res/layout/toolbar_recycler_item.xml`:
 * `40dp x 40dp` FrameLayout button with `20dp x 20dp` centered icon (`dimen/toolbar_button_icon_height = 20dp`).
 */
@Composable
private fun SwiftKeyFeatureItemButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isActive) WingAiTokens.AccentPrimary.copy(alpha = 0.18f)
                else Color.Transparent
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        SwiftKeyVectorIcon(
            imageVector = icon,
            modifier = Modifier.size(20.dp),
            tint = when {
                !enabled -> WingAiTokens.TextMuted.copy(alpha = 0.4f)
                isActive -> WingAiTokens.AccentPrimary
                else -> WingAiTokens.TextPrimary
            }
        )
    }
}

/**
 * LEVEL 2 LEFT: Microsoft SwiftKey Candidate Row Chevron Toggle Button (`rb0/y0` + `rb0/a1`):
 * - Wrapper width: `40dp` (`dimen/candidate_bar_height = 40dp`)
 * - Circular button: `28dp x 28dp` (`0x41e00000 = 28.0f`), `1dp` stroke, `15%` fill alpha, `12dp` chevron icon (`ic_toolbar_open` / `ic_toolbar_closed`).
 * - Toggles Level 1 (`WingAiFeaturesRowView`) open or closed.
 */
class WingAiChevronToggleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    init {
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        rootView?.let { root ->
            root.setViewTreeLifecycleOwner(this)
            root.setViewTreeViewModelStoreOwner(this)
            root.setViewTreeSavedStateRegistryOwner(this)
        }
        setViewTreeLifecycleOwner(this)
        setViewTreeViewModelStoreOwner(this)
        setViewTreeSavedStateRegistryOwner(this)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        removeAllViews()
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@WingAiChevronToggleView)
            setViewTreeViewModelStoreOwner(this@WingAiChevronToggleView)
            setViewTreeSavedStateRegistryOwner(this@WingAiChevronToggleView)
            setContent {
                WingAiTheme {
                    val state by InputConnectionBridge.sessionState.collectAsState()
                    val view = LocalView.current

                    // 40dp x 40dp wrapper (`id/toolbar_button_wrapper` in `rb0/a1`)
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .fillMaxHeight()
                            .clickable(
                                role = Role.Button,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    InputConnectionBridge.updateState {
                                        if (it.isPanelOpen) {
                                            it.copy(isPanelOpen = false, isFeaturesRowOpen = true)
                                        } else {
                                            it.copy(isFeaturesRowOpen = !it.isFeaturesRowOpen)
                                        }
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // 28dp x 28dp circular MaterialButton (`id/toolbar_button` in `rb0/y0`)
                        // Stroke = 1dp, Fill = iconColor.copy(alpha = 0.15f), Icon = 12dp chevron
                        val iconColor = WingAiTokens.TextPrimary
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(iconColor.copy(alpha = 0.15f))
                                .border(1.dp, iconColor.copy(alpha = 0.75f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            SwiftKeyVectorIcon(
                                imageVector = if (state.isFeaturesRowOpen) {
                                    SwiftKeyIcons.ToolbarChevronDown
                                } else {
                                    SwiftKeyIcons.ToolbarChevronUp
                                },
                                modifier = Modifier.size(12.dp),
                                tint = iconColor
                            )
                        }
                    }
                }
            }
        }
        addView(composeView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        super.onDetachedFromWindow()
    }
}

/**
 * LEVEL 2 RIGHT: Conditional 1-Tap Undo Chip (`res/layout/quick_delete_ribbon_view.xml`: `id/quick_delete_undo_chip`).
 * Leaves the 3 prediction slots across Level 2 full-width and unobstructed when no undo is pending.
 */
class WingAiToolbarActionsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)
    private var scope: CoroutineScope? = null

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    init {
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val currentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = currentScope
        val aiController = AiSessionController(context.applicationContext, currentScope)

        rootView?.let { root ->
            root.setViewTreeLifecycleOwner(this)
            root.setViewTreeViewModelStoreOwner(this)
            root.setViewTreeSavedStateRegistryOwner(this)
        }
        setViewTreeLifecycleOwner(this)
        setViewTreeViewModelStoreOwner(this)
        setViewTreeSavedStateRegistryOwner(this)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        removeAllViews()
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@WingAiToolbarActionsView)
            setViewTreeViewModelStoreOwner(this@WingAiToolbarActionsView)
            setViewTreeSavedStateRegistryOwner(this@WingAiToolbarActionsView)
            setContent {
                WingAiTheme {
                    val state by InputConnectionBridge.sessionState.collectAsState()
                    val isPassword = InputConnectionBridge.isPasswordFieldActive()
                    val view = LocalView.current

                    // Auto-expire the 1-tap Undo chip after 10 seconds (`quick_delete_undo_chip` behavior)
                    LaunchedEffect(state.undoPreviousText) {
                        if (state.undoPreviousText != null) {
                            delay(10_000L)
                            InputConnectionBridge.updateState { it.copy(undoPreviousText = null) }
                        }
                    }

                    AnimatedVisibility(
                        visible = !isPassword && state.undoPreviousText != null,
                        enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.9f),
                        exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.9f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(end = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(WingAiTokens.Surface1)
                                    .border(1.dp, WingAiTokens.BorderStrong, RoundedCornerShape(50))
                                    .clickable(
                                        role = Role.Button,
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            aiController.undoLastReplacement()
                                        }
                                    )
                                    .padding(horizontal = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                SwiftKeyVectorIcon(
                                    imageVector = SwiftKeyIcons.ArrowUndo,
                                    modifier = Modifier.size(13.dp),
                                    tint = WingAiTokens.AccentPrimary
                                )
                                Text(
                                    text = "Undo",
                                    color = WingAiTokens.TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
        addView(composeView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }
}

/**
 * Fixed-height WingAI Writing Assistance Hub placed inside KeyboardWrapperView.
 */
class WingAiStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)
    private var scope: CoroutineScope? = null

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    init {
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val currentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = currentScope
        val aiController = AiSessionController(context.applicationContext, currentScope)

        rootView?.let { root ->
            root.setViewTreeLifecycleOwner(this)
            root.setViewTreeViewModelStoreOwner(this)
            root.setViewTreeSavedStateRegistryOwner(this)
        }
        setViewTreeLifecycleOwner(this)
        setViewTreeViewModelStoreOwner(this)
        setViewTreeSavedStateRegistryOwner(this)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        removeAllViews()
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@WingAiStripView)
            setViewTreeViewModelStoreOwner(this@WingAiStripView)
            setViewTreeSavedStateRegistryOwner(this@WingAiStripView)
            setContent {
                WingAiTheme {
                    val state by InputConnectionBridge.sessionState.collectAsState()
                    val isPassword = InputConnectionBridge.isPasswordFieldActive()
                    val showAiPanel = state.isPanelOpen && !isPassword
                    var measuredKbHeightPx by remember { mutableIntStateOf(0) }
                    var measuredCandidateHeightPx by remember { mutableIntStateOf(0) }
                    val density = LocalDensity.current

                    LaunchedEffect(showAiPanel) {
                        val kbView = rootView?.findViewById<View>(R.id.keyboard_view)
                        val candidateRow = rootView?.findViewById<View>(R.id.candidate_row_frame)
                        if (kbView != null && kbView.height > 100) {
                            measuredKbHeightPx = kbView.height
                        }
                        if (candidateRow != null && candidateRow.height > 10) {
                            measuredCandidateHeightPx = candidateRow.height
                        }
                        if (showAiPanel) {
                            candidateRow?.visibility = View.GONE
                            this@WingAiStripView.alpha = 0f
                            this@WingAiStripView.visibility = View.VISIBLE
                            this@WingAiStripView.animate()
                                .alpha(1f)
                                .setDuration(150L)
                                .start()
                            kbView?.visibility = View.GONE
                        } else {
                            candidateRow?.visibility = View.VISIBLE
                            kbView?.alpha = 0f
                            kbView?.visibility = View.VISIBLE
                            kbView?.animate()
                                ?.alpha(1f)
                                ?.setDuration(140L)
                                ?.start()
                            this@WingAiStripView.visibility = View.GONE
                        }
                    }

                    if (showAiPanel) {
                        val candidateHeightDp = if (measuredCandidateHeightPx > 10) {
                            with(density) { measuredCandidateHeightPx.toDp() }
                        } else {
                            40.dp
                        }
                        val targetHeightDp = if (measuredKbHeightPx > 100) {
                            with(density) { measuredKbHeightPx.toDp() } + candidateHeightDp
                        } else {
                            246.dp
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(targetHeightDp)
                                .background(WingAiTokens.BgCanvas)
                        ) {
                            AiControlPanel(
                                state = state,
                                panelHeight = targetHeightDp,
                                isFloatingOverlay = false,
                                onAnalyzeScreen = { aiController.startScreenAnalysis() },
                                onPasteFromClipboard = {
                                    aiController.pasteFromClipboard()
                                    aiController.generateReply()
                                },
                                onToneSelect = { aiController.selectTone(it) },
                                onHubModeSelect = { aiController.setHubMode(it) },
                                onCustomInstructionChange = { aiController.updateCustomInstruction(it) },
                                onPastedContextChange = { aiController.updatePastedContext(it) },
                                onGenerateReply = { aiController.generateReply() },
                                onConfirmUploadCapture = { aiController.confirmUploadPendingCapture() },
                                onCancelPendingCapture = { aiController.cancelPendingCapture() },
                                onCancelRequest = { aiController.cancelInFlightRequest() },
                                onSelectSuggestion = { aiController.selectSuggestion(it) },
                                onEditingReplyChange = { aiController.updateEditingReply(it) },
                                onInsertReply = { aiController.insertReply(it) },
                                onCopyReply = { aiController.copyReply(it) },
                                onRewriteAction = { action, text -> aiController.rewriteReply(action, text) },
                                onClearContext = { aiController.clearContext() },
                                onClose = {
                                    InputConnectionBridge.updateState { it.copy(isPanelOpen = false) }
                                }
                            )
                        }
                    }
                }
            }
        }
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }
}
