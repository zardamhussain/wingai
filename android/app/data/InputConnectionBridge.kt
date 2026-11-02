package com.wingai.keyboard.data

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.wingai.keyboard.privacy.PrivacyGuard
import helium314.keyboard.latin.LatinIME
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.lang.ref.WeakReference

object InputConnectionBridge {
    @Volatile
    private var imeServiceRef: WeakReference<InputMethodService>? = null

    @Volatile
    private var connectionProvider: (() -> InputConnection?)? = null

    @Volatile
    private var activeInputType: Int = 0

    @Volatile
    private var collapsedForSelection: Boolean = false

    private val mainHandler: Handler? by lazy {
        runCatching { Handler(Looper.getMainLooper()) }.getOrNull()
    }

    private val _sessionState = MutableStateFlow(AiSessionState())
    val sessionState: StateFlow<AiSessionState> = _sessionState.asStateFlow()

    fun isCollapsedForSelection(): Boolean = collapsedForSelection

    @JvmOverloads
    fun bindInputMethod(
        imeService: InputMethodService? = null,
        provider: () -> InputConnection?,
        inputType: Int
    ) {
        if (imeService != null) {
            imeServiceRef = WeakReference(imeService)
        }
        connectionProvider = provider
        activeInputType = inputType
        _sessionState.update { it.copy(isKeyboardVisible = true) }
    }

    fun unbindInputMethod() {
        _sessionState.update {
            it.copy(
                isKeyboardVisible = false,
                activeInputTarget = ActiveInputTarget.HOST_APP
            )
        }
    }

    /**
     * Collapses the keyboard's visible insets to 0px without calling requestHideSelf(0),
     * so the underlying chat app expands to full screen height while keeping the IME bound
     * and the text field focused.
     */
    fun hideKeyboardForSelection() {
        collapsedForSelection = true
        mainHandler?.post {
            runCatching {
                val ime = imeServiceRef?.get()
                if (ime is LatinIME) {
                    ime.setSelectionCollapseMode(true)
                } else {
                    ime?.requestHideSelf(0)
                }
            }
        }
    }

    /**
     * Restores the keyboard's full height and visibility immediately when selection finishes
     * or is canceled.
     */
    fun restoreKeyboardAfterSelection() {
        collapsedForSelection = false
        val delays = longArrayOf(0L, 100L, 260L)
        for (delayMs in delays) {
            mainHandler?.postDelayed({
                collapsedForSelection = false
                val ime = imeServiceRef?.get() ?: return@postDelayed
                runCatching {
                    if (ime is LatinIME) {
                        ime.setSelectionCollapseMode(false)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ime.requestShowSelf(InputMethodManager.SHOW_IMPLICIT)
                    }
                }
            }, delayMs)
        }
    }

    fun isPasswordFieldActive(): Boolean = PrivacyGuard.isPasswordInputType(activeInputType)

    fun hasActiveInputConnection(): Boolean = connectionProvider?.invoke() != null

    /**
     * SwiftKey-style host text reader (`g60/f.p`):
     * 1. Reads highlighted selection first via `getSelectedText(0)`.
     * 2. Falls back to `getExtractedText()` or `getTextBeforeCursor()` for the full draft.
     */
    fun readHostFieldText(preferSelection: Boolean = true): String {
        if (isPasswordFieldActive()) return ""
        val ic = connectionProvider?.invoke() ?: return ""
        if (preferSelection) {
            val selected = runCatching { ic.getSelectedText(0)?.toString()?.trim() }.getOrNull()
            if (!selected.isNullOrBlank()) {
                return selected
            }
        }
        val extracted = runCatching {
            ic.getExtractedText(ExtractedTextRequest(), 0)?.text?.toString()?.trim()
        }.getOrNull()
        if (!extracted.isNullOrBlank()) {
            return extracted
        }
        val before = runCatching { ic.getTextBeforeCursor(4000, 0)?.toString() }.getOrNull().orEmpty()
        val after = runCatching { ic.getTextAfterCursor(4000, 0)?.toString() }.getOrNull().orEmpty()
        return (before + after).trim()
    }

    fun consumeHostFieldTextAsPrompt(): String {
        if (isPasswordFieldActive()) return ""
        val ic = connectionProvider?.invoke() ?: return ""
        val extracted = readHostFieldText(preferSelection = true)
        if (extracted.isNotBlank()) {
            ic.deleteSurroundingText(extracted.length + 10, 10)
            _sessionState.update { it.copy(customInstruction = extracted) }
        }
        return extracted
    }

    /**
     * SwiftKey-style in-place replacement (`cc0/n.K`):
     * Replaces either the highlighted selection or the entire host text field atomically,
     * storing the previous text for 1-tap Undo in the keyboard strip.
     */
    fun replaceHostFieldText(newText: String, closePanel: Boolean = false): Result<Unit> {
        val ic = connectionProvider?.invoke()
            ?: return Result.failure(IllegalStateException("Select a text field before replacing text."))
        if (isPasswordFieldActive()) {
            return Result.failure(IllegalStateException("WingAI is disabled in password fields."))
        }

        val previousFullText = readHostFieldText(preferSelection = false)
        val selected = runCatching { ic.getSelectedText(0)?.toString() }.getOrNull()

        val success = runCatching {
            ic.beginBatchEdit()
            try {
                ic.finishComposingText()
                if (!selected.isNullOrEmpty()) {
                    ic.commitText(newText, 1)
                } else {
                    val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)?.text
                    if (extracted != null && extracted.isNotEmpty()) {
                        ic.setSelection(0, extracted.length)
                        ic.commitText(newText, 1)
                    } else {
                        val beforeLen = ic.getTextBeforeCursor(4000, 0)?.length ?: 0
                        val afterLen = ic.getTextAfterCursor(4000, 0)?.length ?: 0
                        if (beforeLen > 0 || afterLen > 0) {
                            ic.deleteSurroundingText(beforeLen, afterLen)
                        }
                        ic.commitText(newText, 1)
                    }
                }
            } finally {
                ic.endBatchEdit()
            }
        }.isSuccess

        return if (success) {
            _sessionState.update {
                it.copy(
                    preservedDraft = newText,
                    hostDraftText = newText,
                    lastReplacedText = newText,
                    undoPreviousText = previousFullText.takeIf { prev -> prev.isNotBlank() && prev != newText },
                    errorMessage = null,
                    isPanelOpen = if (closePanel) false else it.isPanelOpen,
                    activeInputTarget = ActiveInputTarget.HOST_APP
                )
            }
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Select a text field before replacing text."))
        }
    }

    /**
     * Inserts or replaces the reply in the host field and saves undo state.
     */
    fun insertReply(text: String): Result<Unit> {
        val currentState = _sessionState.value
        val existingDraft = readHostFieldText(preferSelection = false)
        val shouldReplaceInPlace = currentState.hubMode != AiHubMode.REPLY && existingDraft.isNotBlank()

        return if (shouldReplaceInPlace) {
            replaceHostFieldText(newText = text, closePanel = false)
        } else {
            val ic = connectionProvider?.invoke()
                ?: return Result.failure(IllegalStateException("Select a text field before inserting a reply."))
            if (isPasswordFieldActive()) {
                return Result.failure(IllegalStateException("WingAI is disabled in password fields."))
            }
            val committed = if (existingDraft.isNotBlank()) {
                replaceHostFieldText(newText = text, closePanel = true).isSuccess
            } else {
                ic.commitText(text, 1)
            }
            if (committed) {
                _sessionState.update {
                    it.copy(
                        preservedDraft = text,
                        lastReplacedText = text,
                        undoPreviousText = existingDraft,
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
    }

    /**
     * SwiftKey-style 1-tap Undo chip (`quick_delete_ribbon_view` / `cd0/g`):
     * Restores the text field to its state prior to the last AI insert/replace.
     */
    fun undoLastReplacement(): Boolean {
        val prev = _sessionState.value.undoPreviousText ?: return false
        val ic = connectionProvider?.invoke() ?: return false
        if (isPasswordFieldActive()) return false

        val restored = runCatching {
            ic.beginBatchEdit()
            try {
                ic.finishComposingText()
                val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)?.text
                if (extracted != null) {
                    ic.setSelection(0, extracted.length)
                } else {
                    val beforeLen = ic.getTextBeforeCursor(4000, 0)?.length ?: 0
                    val afterLen = ic.getTextAfterCursor(4000, 0)?.length ?: 0
                    ic.deleteSurroundingText(beforeLen, afterLen)
                }
                ic.commitText(prev, 1)
            } finally {
                ic.endBatchEdit()
            }
        }.isSuccess

        if (restored) {
            _sessionState.update {
                it.copy(
                    undoPreviousText = null,
                    lastReplacedText = null,
                    hostDraftText = prev
                )
            }
        }
        return restored
    }

    /**
     * Sends a single backspace operation to the host app (used by the SwiftKey-style bottom bar delete key).
     */
    fun sendBackspaceToHost() {
        if (isPasswordFieldActive()) return
        val ic = connectionProvider?.invoke() ?: return
        runCatching {
            val selected = ic.getSelectedText(0)
            if (!selected.isNullOrEmpty()) {
                ic.commitText("", 1)
            } else {
                ic.deleteSurroundingText(1, 0)
            }
        }.onFailure {
            runCatching {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            }
        }
    }

    fun updateState(transform: (AiSessionState) -> AiSessionState) {
        _sessionState.update(transform)
    }

    fun clearContext() {
        _sessionState.update { current ->
            AiSessionState(
                isPanelOpen = current.isPanelOpen,
                isFeaturesRowOpen = current.isFeaturesRowOpen,
                isKeyboardVisible = current.isKeyboardVisible,
                hubMode = current.hubMode,
                preservedDraft = current.preservedDraft,
                userProfile = current.userProfile
            )
        }
    }

    /**
     * Dispatches a HeliBoard `KeyCode` to `LatinIME.mKeyboardActionListener` so SwiftKey
     * Level 1 feature buttons (Clipboard, Emoji, Voice, Settings, Undo) can trigger native
     * keyboard panels directly.
     */
    fun triggerKeyboardKeyCode(code: Int) {
        mainHandler?.post {
            runCatching {
                val ime = imeServiceRef?.get() as? LatinIME ?: return@post
                ime.mKeyboardActionListener.onCodeInput(code, -1, -1, false)
            }
        }
    }
}
