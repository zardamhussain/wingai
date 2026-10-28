package com.wingai.keyboard.data

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import com.wingai.keyboard.privacy.PrivacyGuard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object InputConnectionBridge {
    @Volatile
    private var connectionProvider: (() -> InputConnection?)? = null

    @Volatile
    private var activeInputType: Int = 0

    private val _sessionState = MutableStateFlow(AiSessionState())
    val sessionState: StateFlow<AiSessionState> = _sessionState.asStateFlow()

    fun bindInputMethod(provider: () -> InputConnection?, inputType: Int) {
        connectionProvider = provider
        activeInputType = inputType
        _sessionState.update { it.copy(isKeyboardVisible = true) }
    }

    fun unbindInputMethod() {
        connectionProvider = null
        activeInputType = 0
        _sessionState.update {
            it.copy(
                isKeyboardVisible = false,
                activeInputTarget = ActiveInputTarget.HOST_APP
            )
        }
    }

    fun isPasswordFieldActive(): Boolean = PrivacyGuard.isPasswordInputType(activeInputType)

    fun hasActiveInputConnection(): Boolean = connectionProvider?.invoke() != null

    fun consumeHostFieldTextAsPrompt(): String {
        if (isPasswordFieldActive()) return ""
        val ic = connectionProvider?.invoke() ?: return ""
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)?.text?.toString()?.trim()
            ?: ic.getTextBeforeCursor(250, 0)?.toString()?.trim()
            ?: ""
        if (extracted.isNotBlank()) {
            ic.deleteSurroundingText(extracted.length + 10, 10)
            _sessionState.update { it.copy(customInstruction = extracted) }
        }
        return extracted
    }

    fun insertReply(text: String): Result<Unit> {
        val ic = connectionProvider?.invoke()
            ?: return Result.failure(IllegalStateException("Select a text field before inserting a reply."))
        if (isPasswordFieldActive()) {
            return Result.failure(IllegalStateException("WingAI is disabled in password fields."))
        }
        // Commit the reply text without sending or submitting the form (AT-08, AT-09).
        val committed = ic.commitText(text, 1)
        return if (committed) {
            _sessionState.update {
                it.copy(
                    preservedDraft = text,
                    errorMessage = null,
                    isPanelOpen = false,
                    activeInputTarget = ActiveInputTarget.HOST_APP
                )
            }
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Select a text field before inserting a reply."))
        }
    }

    fun updateState(transform: (AiSessionState) -> AiSessionState) {
        _sessionState.update(transform)
    }

    fun clearContext() {
        _sessionState.update { current ->
            AiSessionState(
                isPanelOpen = current.isPanelOpen,
                isKeyboardVisible = current.isKeyboardVisible,
                preservedDraft = current.preservedDraft
            )
        }
    }
}
