package com.wingai.keyboard.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.wingai.keyboard.capture.ScreenCaptureActivity
import com.wingai.keyboard.capture.ScreenCaptureManager
import com.wingai.keyboard.capture.ScreenCaptureService
import com.wingai.keyboard.data.InputConnectionBridge
import com.wingai.keyboard.data.ReplyTone
import com.wingai.keyboard.data.RewriteAction
import com.wingai.keyboard.data.ShortcutAction
import com.wingai.keyboard.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AiSessionController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val settingsRepository = SettingsRepository(context)
    private var activeJob: Job? = null

    init {
        refreshUserProfile()
    }

    fun refreshUserProfile() {
        scope.launch {
            val userId = settingsRepository.getOrCreateUserId()
            val settings = settingsRepository.settingsFlow.first()
            val client = WingAiApiClient({ settings.backendUrl }, { userId })
            client.fetchUserProfile(userId).onSuccess { profile ->
                InputConnectionBridge.updateState { it.copy(userProfile = profile) }
            }
        }
    }

    fun executeShortcut(action: ShortcutAction) {
        when (action) {
            ShortcutAction.READ_SCREEN -> startScreenAnalysis()
            ShortcutAction.OPEN_PANEL -> {
                InputConnectionBridge.updateState { it.copy(isPanelOpen = !it.isPanelOpen) }
            }
            ShortcutAction.PASTE_CONTEXT -> {
                InputConnectionBridge.updateState { it.copy(isPanelOpen = true) }
                pasteFromClipboard()
            }
            ShortcutAction.NONE -> Unit
        }
    }

    fun startScreenAnalysis() {
        if (InputConnectionBridge.isPasswordFieldActive()) {
            InputConnectionBridge.updateState {
                it.copy(
                    isPanelOpen = true,
                    errorMessage = "Screen analysis is disabled while a password field is active."
                )
            }
            return
        }
        if (ScreenCaptureManager.hasActiveSession()) {
            val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_CAPTURE_FRAME
            }
            context.startForegroundService(serviceIntent)
        } else {
            val intent = Intent(context, ScreenCaptureActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(ScreenCaptureService.EXTRA_CAPTURE_IMMEDIATELY, true)
            }
            context.startActivity(intent)
        }
    }

    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = clipboard?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            ?.trim()
            .orEmpty()

        if (text.isBlank()) {
            InputConnectionBridge.updateState {
                it.copy(errorMessage = "Clipboard is empty. Copy conversation text first.")
            }
        } else {
            InputConnectionBridge.updateState {
                it.copy(pastedContext = text, errorMessage = null)
            }
        }
    }

    fun selectTone(tone: ReplyTone?) {
        InputConnectionBridge.updateState { it.copy(selectedTone = tone) }
        val state = InputConnectionBridge.sessionState.value
        if (state.pastedContext.isNotBlank()) {
            generateReply()
        } else if (state.suggestions.isNotEmpty() || ScreenCaptureManager.hasActiveSession()) {
            startScreenAnalysis()
        }
    }

    fun updateCustomInstruction(instruction: String) {
        InputConnectionBridge.updateState { it.copy(customInstruction = instruction) }
    }

    fun updatePastedContext(contextText: String) {
        InputConnectionBridge.updateState { it.copy(pastedContext = contextText) }
    }

    fun selectSuggestion(text: String) {
        InputConnectionBridge.updateState { it.copy(editingReplyText = text, preservedDraft = text) }
    }

    fun updateEditingReply(text: String) {
        InputConnectionBridge.updateState { it.copy(editingReplyText = text, preservedDraft = text) }
    }

    fun confirmUploadPendingCapture() {
        val state = InputConnectionBridge.sessionState.value
        val base64 = state.pendingImageBase64 ?: return
        activeJob?.cancel()
        activeJob = scope.launch {
            InputConnectionBridge.updateState { it.copy(isLoading = true, errorMessage = null) }
            val userId = settingsRepository.getOrCreateUserId()
            val settings = settingsRepository.settingsFlow.first()
            val client = WingAiApiClient({ settings.backendUrl }, { userId })
            val result = client.analyzeContext(
                imageBase64 = base64,
                conversationText = state.pastedContext,
                tone = state.selectedTone,
                customInstruction = state.customInstruction
            )
            result.fold(
                onSuccess = { res ->
                    InputConnectionBridge.updateState {
                        it.copy(
                            isLoading = false,
                            pendingImageBase64 = null,
                            suggestions = res.replies,
                            userProfile = res.user ?: it.userProfile,
                            editingReplyText = res.replies.firstOrNull()?.text.orEmpty(),
                            errorMessage = if (res.contextStatus == "insufficient") {
                                res.message ?: "Conversation is not clear. Add more context."
                            } else null
                        )
                    }
                },
                onFailure = { err ->
                    InputConnectionBridge.updateState {
                        it.copy(isLoading = false, errorMessage = err.message ?: "AI request failed.")
                    }
                    refreshUserProfile()
                }
            )
        }
    }

    fun cancelPendingCapture() {
        InputConnectionBridge.updateState {
            it.copy(pendingImageBase64 = null, errorMessage = "Captured image discarded.")
        }
    }

    fun generateReply() {
        val state = InputConnectionBridge.sessionState.value
        if (state.pastedContext.isBlank() && state.pendingImageBase64.isNullOrBlank()) {
            InputConnectionBridge.updateState {
                it.copy(errorMessage = "Tap Read or Paste Chat before generating replies.")
            }
            return
        }
        if (!state.pendingImageBase64.isNullOrBlank()) {
            confirmUploadPendingCapture()
            return
        }

        activeJob?.cancel()
        activeJob = scope.launch {
            InputConnectionBridge.updateState { it.copy(isLoading = true, errorMessage = null) }
            val userId = settingsRepository.getOrCreateUserId()
            val settings = settingsRepository.settingsFlow.first()
            val client = WingAiApiClient({ settings.backendUrl }, { userId })
            val result = client.generateReplies(
                conversationText = state.pastedContext,
                tone = state.selectedTone,
                customInstruction = state.customInstruction
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
                                res.message ?: "Conversation is not clear. Add more context."
                            } else null
                        )
                    }
                },
                onFailure = { err ->
                    InputConnectionBridge.updateState {
                        it.copy(isLoading = false, errorMessage = err.message ?: "AI request failed.")
                    }
                    refreshUserProfile()
                }
            )
        }
    }

    fun rewriteReply(action: RewriteAction, replyText: String) {
        if (replyText.isBlank()) return
        val state = InputConnectionBridge.sessionState.value
        activeJob?.cancel()
        activeJob = scope.launch {
            InputConnectionBridge.updateState { it.copy(isLoading = true, errorMessage = null) }
            val userId = settingsRepository.getOrCreateUserId()
            val settings = settingsRepository.settingsFlow.first()
            val client = WingAiApiClient({ settings.backendUrl }, { userId })
            val result = client.rewriteReply(
                replyText = replyText,
                action = action,
                tone = state.selectedTone,
                customInstruction = state.customInstruction,
                conversationText = state.pastedContext
            )
            result.fold(
                onSuccess = { res ->
                    InputConnectionBridge.updateState {
                        it.copy(
                            isLoading = false,
                            suggestions = res.replies,
                            userProfile = res.user ?: it.userProfile,
                            editingReplyText = res.replies.firstOrNull()?.text.orEmpty(),
                            errorMessage = null
                        )
                    }
                },
                onFailure = { err ->
                    InputConnectionBridge.updateState {
                        it.copy(isLoading = false, errorMessage = err.message ?: "Rewrite failed.")
                    }
                    refreshUserProfile()
                }
            )
        }
    }

    fun cancelInFlightRequest() {
        activeJob?.cancel()
        activeJob = null
        InputConnectionBridge.updateState {
            it.copy(isLoading = false, errorMessage = "Request canceled.")
        }
    }

    fun insertReply(text: String) {
        val result = InputConnectionBridge.insertReply(text)
        result.onFailure {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("WingAI Reply", text))
            InputConnectionBridge.updateState {
                it.copy(isPanelOpen = false, errorMessage = null)
            }
            Toast.makeText(context, "Copied! Tap the suggestion on your keyboard to paste.", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyReply(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("WingAI Reply", text))
        InputConnectionBridge.updateState {
            it.copy(errorMessage = "Reply copied to clipboard.")
        }
    }

    fun clearContext() {
        activeJob?.cancel()
        InputConnectionBridge.clearContext()
    }
}
