package com.wingai.keyboard.data

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.SurroundingText
import android.os.Bundle
import android.os.Handler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WingAiBridgeTest {

    private class FakeInputConnection(
        initialText: String = "",
        var selectedText: String? = null
    ) : InputConnection {
        val buffer = StringBuilder(initialText)
        var selectionStart = 0
        var selectionEnd = initialText.length

        override fun getSelectedText(flags: Int): CharSequence? = selectedText

        override fun getExtractedText(
            request: ExtractedTextRequest?,
            flags: Int
        ): ExtractedText? = null

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = buffer.toString()

        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = ""

        override fun getSurroundingText(beforeLength: Int, afterLength: Int, flags: Int): SurroundingText? = null

        override fun beginBatchEdit(): Boolean = true

        override fun endBatchEdit(): Boolean = true

        override fun finishComposingText(): Boolean = true

        override fun setSelection(start: Int, end: Int): Boolean {
            selectionStart = start.coerceIn(0, buffer.length)
            selectionEnd = end.coerceIn(0, buffer.length)
            return true
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val replacement = text?.toString().orEmpty()
            if (selectedText != null) {
                buffer.clear()
                buffer.append(replacement)
                selectedText = null
            } else {
                buffer.append(replacement)
            }
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (buffer.isNotEmpty() && beforeLength > 0) {
                val deleteCount = beforeLength.coerceAtMost(buffer.length)
                buffer.delete(buffer.length - deleteCount, buffer.length)
            }
            return true
        }

        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean = false
        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean = false
        override fun setComposingRegion(start: Int, end: Int): Boolean = false
        override fun commitCompletion(text: CompletionInfo?): Boolean = false
        override fun commitCorrection(correctionInfo: CorrectionInfo?): Boolean = false
        override fun performEditorAction(editorAction: Int): Boolean = false
        override fun performContextMenuAction(id: Int): Boolean = false
        override fun clearMetaKeyStates(states: Int): Boolean = false
        override fun reportFullscreenMode(enabled: Boolean): Boolean = false
        override fun performPrivateCommand(action: String?, data: Bundle?): Boolean = false
        override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = false
        override fun getHandler(): Handler? = null
        override fun closeConnection() {}
        override fun sendKeyEvent(event: KeyEvent?): Boolean = false
        override fun getCursorCapsMode(reqModes: Int): Int = 0
        override fun commitContent(inputContentInfo: InputContentInfo, flags: Int, opts: Bundle?): Boolean = false
    }

    @Before
    fun resetBridge() {
        InputConnectionBridge.clearContext()
        InputConnectionBridge.updateState {
            AiSessionState()
        }
    }

    @Test
    fun readHostFieldTextPrefersSelectedTextOverExtractedDraft() {
        val fakeIc = FakeInputConnection(
            initialText = "Full paragraph in the chat box",
            selectedText = "highlighted phrase"
        )
        InputConnectionBridge.bindInputMethod(null, { fakeIc }, InputType.TYPE_CLASS_TEXT)

        assertEquals("highlighted phrase", InputConnectionBridge.readHostFieldText(preferSelection = true))
        assertEquals("Full paragraph in the chat box", InputConnectionBridge.readHostFieldText(preferSelection = false))
    }

    @Test
    fun replaceHostFieldTextReplacesInPlaceAndSupportsOneTapUndo() {
        val fakeIc = FakeInputConnection(initialText = "i will be late 10 min")
        InputConnectionBridge.bindInputMethod(null, { fakeIc }, InputType.TYPE_CLASS_TEXT)

        val replaceResult = InputConnectionBridge.replaceHostFieldText("I'll be about 10 minutes late!")
        assertTrue(replaceResult.isSuccess)
        assertEquals("I'll be about 10 minutes late!", fakeIc.buffer.toString())

        val stateAfterReplace = InputConnectionBridge.sessionState.value
        assertEquals("i will be late 10 min", stateAfterReplace.undoPreviousText)
        assertEquals("I'll be about 10 minutes late!", stateAfterReplace.lastReplacedText)

        val undone = InputConnectionBridge.undoLastReplacement()
        assertTrue(undone)
        assertEquals("i will be late 10 min", fakeIc.buffer.toString())
        assertNull(InputConnectionBridge.sessionState.value.undoPreviousText)
    }

    @Test
    fun passwordFieldsDisableHostReadAndReplace() {
        val fakeIc = FakeInputConnection(initialText = "secret_password_123")
        InputConnectionBridge.bindInputMethod(
            null,
            { fakeIc },
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )

        assertTrue(InputConnectionBridge.isPasswordFieldActive())
        assertEquals("", InputConnectionBridge.readHostFieldText())
        assertFalse(InputConnectionBridge.replaceHostFieldText("hacked").isSuccess)
    }
}
