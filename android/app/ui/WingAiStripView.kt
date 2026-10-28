package com.wingai.keyboard.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
import com.wingai.keyboard.data.InputConnectionBridge
import helium314.keyboard.latin.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Compact action buttons embedded directly inside HeliBoard's 40dp SuggestionStripView
 * so no extra top bar overlaps the host app's message field.
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
                MaterialTheme(colorScheme = darkColorScheme()) {
                    val state by InputConnectionBridge.sessionState.collectAsState()
                    val isPassword = InputConnectionBridge.isPasswordFieldActive()

                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isPassword) {
                            CompactPillButton(
                                text = "\uD83D\uDCF8 Read",
                                backgroundColor = Color(0xFF5C6BC0),
                                textColor = Color.White,
                                onClick = { aiController.startScreenAnalysis() }
                            )
                            CompactPillButton(
                                text = "\u2728 Ask",
                                backgroundColor = Color(0xFF28334D),
                                onClick = {
                                    val prompt = InputConnectionBridge.consumeHostFieldTextAsPrompt()
                                    if (prompt.isBlank()) {
                                        Toast.makeText(
                                            context,
                                            "Type your instruction in the message box first, then tap Ask.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        aiController.startScreenAnalysis()
                                    }
                                }
                            )
                        }

                        CompactPillButton(
                            text = if (isPassword) {
                                "\uD83D\uDD12"
                            } else if (state.isPanelOpen) {
                                "\u2328\uFE0F Keys"
                            } else {
                                "\uD83E\uDEBD AI"
                            },
                            backgroundColor = if (state.isPanelOpen) Color(0xFF3949AB) else Color(0xFF232C42),
                            textColor = Color.White,
                            onClick = {
                                if (!isPassword) {
                                    InputConnectionBridge.updateState {
                                        it.copy(isPanelOpen = !it.isPanelOpen)
                                    }
                                }
                            }
                        )
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
 * Fixed-height WingAI Reply Panel placed inside KeyboardWrapperView.
 * Uses the exact measured pixel height of MainKeyboardView (never MATCH_PARENT / fillMaxSize)
 * so the keyboard never stretches to fill the phone screen.
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
                MaterialTheme(colorScheme = darkColorScheme()) {
                    val state by InputConnectionBridge.sessionState.collectAsState()
                    val isPassword = InputConnectionBridge.isPasswordFieldActive()
                    val showAiPanel = state.isPanelOpen && !isPassword
                    var measuredKbHeightPx by remember { mutableIntStateOf(0) }
                    val density = LocalDensity.current

                    LaunchedEffect(showAiPanel) {
                        val kbView = rootView?.findViewById<View>(R.id.keyboard_view)
                        if (kbView != null && kbView.height > 100) {
                            measuredKbHeightPx = kbView.height
                        }
                        this@WingAiStripView.visibility = if (showAiPanel) View.VISIBLE else View.GONE
                        kbView?.visibility = if (showAiPanel) View.INVISIBLE else View.VISIBLE
                    }

                    if (showAiPanel) {
                        val targetHeightDp = if (measuredKbHeightPx > 100) {
                            with(density) { measuredKbHeightPx.toDp() }
                        } else {
                            250.dp
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(targetHeightDp)
                                .background(Color(0xFF10141F))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
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
