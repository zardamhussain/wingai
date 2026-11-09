package com.wingai.keyboard.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.provider.Settings
import android.widget.Toast
import com.wingai.keyboard.capture.ScreenCaptureActivity
import com.wingai.keyboard.capture.ScreenCaptureManager
import com.wingai.keyboard.capture.WingAiScreenshotService
import com.wingai.keyboard.data.AiHubMode
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
                val nowOpen = !InputConnectionBridge.sessionState.value.isPanelOpen
                InputConnectionBridge.updateState { it.copy(isPanelOpen = nowOpen) }
                if (nowOpen) {
                    syncHostDraftOnOpen()
                }
            }
            ShortcutAction.PASTE_CONTEXT -> {
                InputConnectionBridge.updateState { it.copy(isPanelOpen = true, hubMode = AiHubMode.REPLY) }
                pasteFromClipboard()
            }
            ShortcutAction.NONE -> Unit
        }
    }

    /**
     * SwiftKey-style auto draft detection (`g60/f.p`):
     * Reads the host input field when the panel opens or switches hub mode.
     * If the user has already typed >= 3 words and has no screenshot/pasted context,
     * automatically switches to Tone Rewrite / Polish mode.
     */
    fun syncHostDraftOnOpen() {
        val draft = InputConnectionBridge.readHostFieldText(preferSelection = true)
        val state = InputConnectionBridge.sessionState.value

        InputConnectionBridge.updateState {
            it.copy(
                hubMode = AiHubMode.REWRITE,
                hostDraftText = draft,
                errorMessage = null
            )
        }

        if (draft.isNotBlank() && state.suggestions.isEmpty()) {
            val action = if (state.selectedTone != null) RewriteAction.TONE else RewriteAction.REWRITE
            rewriteReply(action, draft)
        }
    }

    fun setHubMode(mode: AiHubMode) {
        val draft = InputConnectionBridge.readHostFieldText(preferSelection = true)
        InputConnectionBridge.updateState {
            it.copy(
                hubMode = mode,
                hostDraftText = draft,
                errorMessage = null
            )
        }
        val wordCount = draft.split(Regex("\\s+")).count { it.isNotBlank() }
        when (mode) {
            AiHubMode.REWRITE -> {
                if (wordCount >= 3) {
                    val state = InputConnectionBridge.sessionState.value
                    val action = if (state.selectedTone != null) RewriteAction.TONE else RewriteAction.REWRITE
                    rewriteReply(action, draft)
                }
            }
            AiHubMode.POLISH -> {
                if (wordCount >= 2) {
                    rewriteWithCustomPolish(draft)
                }
            }
            AiHubMode.REPLY -> {
                val state = InputConnectionBridge.sessionState.value
                if (!state.selectedImageBase64.isNullOrBlank() || state.pastedContext.isNotBlank()) {
                    generateReply()
                }
            }
        }
    }

    private fun rewriteWithCustomPolish(draft: String) {
        if (draft.isBlank()) return
        val state = InputConnectionBridge.sessionState.value
        activeJob?.cancel()
        activeJob = scope.launch {
            InputConnectionBridge.updateState { it.copy(isLoading = true, errorMessage = null, lastReplacedText = null) }
            val userId = settingsRepository.getOrCreateUserId()
            val settings = settingsRepository.settingsFlow.first()
            val client = WingAiApiClient({ settings.backendUrl }, { userId })
            val result = client.rewriteReply(
                replyText = draft,
                action = RewriteAction.REWRITE,
                tone = state.selectedTone,
                customInstruction = "Fix grammar, spelling, and punctuation while keeping the original meaning natural and concise.",
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
                        it.copy(isLoading = false, errorMessage = err.message ?: "Editor polish failed.")
                    }
                    refreshUserProfile()
                }
            )
        }
    }

    /**
     * Launches the Circle-to-Search / Select & Search region selector using an instant
     * in-memory screenshot (never saved to disk/gallery and never triggers the MediaProjection
     * screen-recording dialog).
     */
    fun startScreenAnalysis() {
        if (InputConnectionBridge.isPasswordFieldActive()) {
            InputConnectionBridge.updateState {
                it.copy(
                    isPanelOpen = true,
                    errorMessage = "Screen selection is disabled while a password field is active."
                )
            }
            return
        }

        InputConnectionBridge.updateState { it.copy(hubMode = AiHubMode.REPLY) }

        // Instant in-memory screenshot (Circle to Search style — no screen share dialog, no file saved)
        if (WingAiScreenshotService.captureForSelectAndSearch()) {
            return
        }

        // Prompt user once to enable "WingAI Select & Search" if not yet enabled
        Toast.makeText(
            context,
            "Enable 'WingAI Select & Search' once for instant Circle-to-Search snapshots (no screen share popup).",
            Toast.LENGTH_LONG
        ).show()
        val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(settingsIntent) }
    }

    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val item = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)

        // If clipboard holds a copied screenshot/image, open the Select & Search region selector on it!
        val clipUri = item?.uri
        val clipboardBitmap = clipUri?.let { uri ->
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
        if (clipboardBitmap != null) {
            ScreenCaptureManager.setCapturedBitmap(clipboardBitmap)
            val cropIntent = Intent(context, ScreenCaptureActivity::class.java).apply {
                action = ScreenCaptureActivity.ACTION_SHOW_CROP
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(cropIntent)
            return
        }

        val text = item
            ?.coerceToText(context)
            ?.toString()
            ?.trim()
            .orEmpty()

        if (text.isBlank()) {
            InputConnectionBridge.updateState {
                it.copy(errorMessage = "Clipboard is empty. Copy conversation text or a screenshot first.")
            }
        } else {
            InputConnectionBridge.updateState {
                it.copy(pastedContext = text, hubMode = AiHubMode.REPLY, errorMessage = null)
            }
        }
    }

    fun selectTone(tone: ReplyTone?) {
        InputConnectionBridge.updateState { it.copy(selectedTone = tone, errorMessage = null) }
        val state = InputConnectionBridge.sessionState.value
        val draft = InputConnectionBridge.readHostFieldText(preferSelection = true)
            .ifBlank { state.hostDraftText }
        val hasChatContext = state.structuredContext != null ||
            !state.selectedImageBase64.isNullOrBlank() ||
            !state.pendingImageBase64.isNullOrBlank() ||
            state.pastedContext.isNotBlank()

        when {
            draft.isNotBlank() && (state.hubMode == AiHubMode.REWRITE || state.hubMode == AiHubMode.POLISH || !hasChatContext) -> {
                rewriteReply(if (tone != null) RewriteAction.TONE else RewriteAction.REWRITE, draft)
            }
            hasChatContext -> {
                generateReply()
            }
            state.suggestions.isNotEmpty() -> {
                val baseText = state.editingReplyText.ifBlank { state.suggestions.first().text }
                if (baseText.isNotBlank()) {
                    rewriteReply(if (tone != null) RewriteAction.TONE else RewriteAction.REWRITE, baseText)
                }
            }
        }
    }

    fun updateCustomInstruction(instruction: String) {
        InputConnectionBridge.updateState { it.copy(customInstruction = instruction) }
    }

    fun updatePastedContext(contextText: String) {
        InputConnectionBridge.updateState { it.copy(pastedContext = contextText) }
    }

    fun clearSelectedImage() {
        InputConnectionBridge.updateState { it.copy(selectedImageBase64 = null) }
    }

    fun selectSuggestion(text: String) {
        InputConnectionBridge.updateState { it.copy(editingReplyText = text, preservedDraft = text) }
    }

    fun updateEditingReply(text: String) {
        InputConnectionBridge.updateState { it.copy(editingReplyText = text, preservedDraft = text) }
    }

    fun confirmUploadPendingCapture() {
        val state = InputConnectionBridge.sessionState.value
        val structured = state.structuredContext
        val base64 = state.pendingImageBase64 ?: state.selectedImageBase64
        if (structured == null && base64.isNullOrBlank()) return
        activeJob?.cancel()
        activeJob = scope.launch {
            InputConnectionBridge.updateState { it.copy(isLoading = true, errorMessage = null, lastReplacedText = null) }
            val userId = settingsRepository.getOrCreateUserId()
            val settings = settingsRepository.settingsFlow.first()
            val client = WingAiApiClient({ settings.backendUrl }, { userId })
            val result = client.analyzeContext(
                imageBase64 = if (structured == null) base64 else null,
                conversationText = state.pastedContext.ifBlank { structured?.formattedTranscript.orEmpty() },
                tone = state.selectedTone,
                customInstruction = state.customInstruction,
                structuredContext = structured
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
            it.copy(pendingImageBase64 = null, selectedImageBase64 = null, errorMessage = "Selected image cleared.")
        }
    }

    fun generateReply() {
        val state = InputConnectionBridge.sessionState.value
        val hostDraft = InputConnectionBridge.readHostFieldText(preferSelection = true)
            .ifBlank { state.hostDraftText }
        val hasChatContext = state.structuredContext != null ||
            !state.selectedImageBase64.isNullOrBlank() ||
            !state.pendingImageBase64.isNullOrBlank() ||
            state.pastedContext.isNotBlank()

        if ((state.hubMode == AiHubMode.REWRITE || state.hubMode == AiHubMode.POLISH) && hostDraft.isNotBlank()) {
            if (state.hubMode == AiHubMode.POLISH) {
                rewriteWithCustomPolish(hostDraft)
            } else {
                val action = if (state.selectedTone != null) RewriteAction.TONE else RewriteAction.REWRITE
                rewriteReply(action, hostDraft)
            }
            return
        }

        if (!hasChatContext) {
            if (hostDraft.isNotBlank()) {
                val action = if (state.selectedTone != null) RewriteAction.TONE else RewriteAction.REWRITE
                rewriteReply(action, hostDraft)
                return
            }
            // No image, no pasted chat, and no typed draft -> launch screen selection directly!
            startScreenAnalysis()
            return
        }
        if (state.structuredContext != null || !state.pendingImageBase64.isNullOrBlank() || !state.selectedImageBase64.isNullOrBlank()) {
            confirmUploadPendingCapture()
            return
        }

        activeJob?.cancel()
        activeJob = scope.launch {
            InputConnectionBridge.updateState { it.copy(isLoading = true, errorMessage = null, lastReplacedText = null) }
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
            InputConnectionBridge.updateState { it.copy(isLoading = true, errorMessage = null, lastReplacedText = null) }
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
            it.copy(errorMessage = null)
        }
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun undoLastReplacement() {
        InputConnectionBridge.undoLastReplacement()
    }

    fun clearContext() {
        activeJob?.cancel()
        InputConnectionBridge.clearContext()
    }
}
