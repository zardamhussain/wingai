package com.wingai.keyboard.capture

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
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
import com.wingai.keyboard.ai.WingAiApiClient
import com.wingai.keyboard.data.AiHubMode
import com.wingai.keyboard.data.InputConnectionBridge
import com.wingai.keyboard.settings.SettingsRepository
import com.wingai.keyboard.ui.WingAiTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * On-demand in-memory screenshot provider and TYPE_ACCESSIBILITY_OVERLAY host for Select & Search.
 * Never launches a separate Activity during screen selection—so the underlying chat app never
 * loses focus and the keyboard reopens 100% reliably after Analyze or Cancel.
 */
class WingAiScreenshotService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var overlayHostView: OverlayComposeContainer? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ChatOcrExtractor.warmUp()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op: background event monitoring is disabled in wingai_screenshot_service.xml.
    }

    override fun onInterrupt() {
        dismissOverlayAndRestoreKeyboard()
    }

    override fun onDestroy() {
        dismissOverlayAndRestoreKeyboard()
        serviceScope.cancel()
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    private fun performInMemoryCaptureAfterKeyboardHide() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            InputConnectionBridge.updateState {
                it.copy(
                    isPanelOpen = true,
                    errorMessage = "Instant screenshot selection requires Android 11 or newer."
                )
            }
            return
        }

        // Remove any existing overlay before starting a new snapshot
        removeOverlayViewOnly()

        // Step 1: Collapse keyboard insets so messages behind the keyboard are uncovered
        InputConnectionBridge.hideKeyboardForSelection()

        // Step 2: Wait 240ms for the chat app to expand to full height, then capture in RAM
        mainHandler.postDelayed({
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val hardwareBuffer = screenshot.hardwareBuffer
                        val colorSpace = screenshot.colorSpace
                        val bitmap = try {
                            val hwBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            hwBitmap?.copy(Bitmap.Config.ARGB_8888, false).also {
                                hwBitmap?.recycle()
                            }
                        } catch (_: Throwable) {
                            null
                        } finally {
                            runCatching { hardwareBuffer.close() }
                        }

                        if (bitmap != null) {
                            ScreenCaptureManager.setCapturedBitmap(bitmap)
                            showSelectOverlayWindow(bitmap)
                        } else {
                            InputConnectionBridge.updateState {
                                it.copy(
                                    isPanelOpen = true,
                                    isLoading = false,
                                    errorMessage = "Screen content is protected and cannot be captured."
                                )
                            }
                            InputConnectionBridge.restoreKeyboardAfterSelection()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        val reason = when (errorCode) {
                            ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT ->
                                "Please wait a moment before taking another snapshot."
                            ERROR_TAKE_SCREENSHOT_SECURE_WINDOW ->
                                "This screen is protected (FLAG_SECURE) and blocks screenshots. Use Paste Chat instead."
                            else ->
                                "Could not capture screen (code $errorCode). Try again or use Paste Chat."
                        }
                        InputConnectionBridge.updateState {
                            it.copy(
                                isPanelOpen = true,
                                isLoading = false,
                                errorMessage = reason
                            )
                        }
                        InputConnectionBridge.restoreKeyboardAfterSelection()
                    }
                }
            )
        }, 75L)
    }

    private fun showSelectOverlayWindow(bitmap: Bitmap) {
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: run {
            InputConnectionBridge.restoreKeyboardAfterSelection()
            return
        }

        removeOverlayViewOnly()

        val container = OverlayComposeContainer(
            context = this,
            onBackPressed = {
                ScreenCaptureManager.clearCapturedBitmap()
                dismissOverlayAndRestoreKeyboard()
            }
        )
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(container)
            setViewTreeViewModelStoreOwner(container)
            setViewTreeSavedStateRegistryOwner(container)
            setContent {
                WingAiTheme {
                    SelectAndSearchCropOverlay(
                        bitmap = bitmap,
                        onPickFromGallery = null,
                        onCancel = {
                            ScreenCaptureManager.clearCapturedBitmap()
                            dismissOverlayAndRestoreKeyboard()
                        },
                        onConfirmSelection = { left, top, right, bottom ->
                            val croppedBitmap = ScreenCaptureManager.cropBitmap(
                                bitmap,
                                left,
                                top,
                                right,
                                bottom
                            )
                            ScreenCaptureManager.clearCapturedBitmap()
                            dismissOverlayAndRestoreKeyboard()
                            analyzeSelectedCropBitmap(croppedBitmap)
                        }
                    )
                }
            }
        }
        container.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        runCatching {
            wm.addView(container, params)
            container.onShown()
            overlayHostView = container
        }.onFailure {
            InputConnectionBridge.restoreKeyboardAfterSelection()
        }
    }

    private fun removeOverlayViewOnly() {
        val existing = overlayHostView ?: return
        overlayHostView = null
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        runCatching {
            existing.onRemoved()
            wm?.removeViewImmediate(existing)
        }
    }

    private fun dismissOverlayAndRestoreKeyboard() {
        removeOverlayViewOnly()
        InputConnectionBridge.restoreKeyboardAfterSelection()
    }

    private fun analyzeSelectedCropBitmap(croppedBitmap: Bitmap) {
        InputConnectionBridge.updateState {
            it.copy(
                isPanelOpen = true,
                hubMode = AiHubMode.REPLY,
                isLoading = true,
                selectedImageBase64 = null,
                pendingImageBase64 = null,
                errorMessage = null
            )
        }
        val appContext = applicationContext
        serviceScope.launch {
            val ocrResult = try {
                ChatOcrExtractor.extractStructuredChat(croppedBitmap)
            } finally {
                if (!croppedBitmap.isRecycled) {
                    runCatching { croppedBitmap.recycle() }
                }
            }

            val structured = ocrResult.getOrElse { err ->
                InputConnectionBridge.updateState {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Could not extract text from selected region."
                    )
                }
                return@launch
            }

            InputConnectionBridge.updateState {
                it.copy(
                    structuredContext = structured,
                    pastedContext = structured.formattedTranscript
                )
            }

            val repo = SettingsRepository(appContext)
            val userId = repo.getOrCreateUserId()
            val settings = repo.settingsFlow.first()
            val client = WingAiApiClient({ settings.backendUrl }, { userId })
            val state = InputConnectionBridge.sessionState.value
            val result = client.analyzeContext(
                conversationText = structured.formattedTranscript,
                tone = state.selectedTone,
                customInstruction = state.customInstruction,
                structuredContext = structured
            )
            result.fold(
                onSuccess = { res ->
                    InputConnectionBridge.updateState {
                        it.copy(
                            isLoading = false,
                            suggestions = res.replies,
                            userProfile = res.user ?: it.userProfile,
                            editingReplyText = res.replies.firstOrNull()?.text.orEmpty(),
                            errorMessage = if (res.contextStatus == "insufficient") {
                                res.message ?: "Conversation is not clear. Select a clearer chat section."
                            } else null
                        )
                    }
                },
                onFailure = { err ->
                    InputConnectionBridge.updateState {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "AI request failed."
                        )
                    }
                }
            )
        }
    }

    companion object {
        @Volatile
        private var instance: WingAiScreenshotService? = null

        fun isAvailable(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && instance != null

        fun captureForSelectAndSearch(): Boolean {
            val svc = instance ?: return false
            svc.performInMemoryCaptureAfterKeyboardHide()
            return true
        }
    }
}

private class OverlayComposeContainer(
    context: Context,
    private val onBackPressed: () -> Unit
) : FrameLayout(context),
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
        isFocusable = true
        isFocusableInTouchMode = true
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        setViewTreeLifecycleOwner(this)
        setViewTreeViewModelStoreOwner(this)
        setViewTreeSavedStateRegistryOwner(this)
    }

    fun onShown() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onRemoved() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event?.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            onBackPressed()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
