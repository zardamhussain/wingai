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
import com.wingai.keyboard.ai.WingAiApiClient
import com.wingai.keyboard.capture.ChatOcrExtractor
import com.wingai.keyboard.capture.OcrDetectedLine
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

    @Test
    fun chatOcrExtractorBuildsSystematicStructuredPayloadWithoutImageBytes() {
        val lines = listOf(
            OcrDetectedLine("Today", left = 420, top = 20, right = 580, bottom = 50),
            OcrDetectedLine("Hey kal sham ko 7 baje", left = 40, top = 100, right = 480, bottom = 140),
            OcrDetectedLine("coffee pe milte hain? 6:42 PM", left = 40, top = 148, right = 500, bottom = 188),
            OcrDetectedLine("Haan sounds good, kahan? 6:43 PM", left = 460, top = 240, right = 940, bottom = 280),
            OcrDetectedLine("Blue Tokai Indiranagar chalte hain!", left = 45, top = 340, right = 620, bottom = 380)
        )

        val structured = ChatOcrExtractor.buildStructuredChatFromLines(
            lines = lines,
            cropWidth = 1000,
            cropHeight = 600
        )

        assertEquals("hinglish", structured.languageHint)
        assertEquals("time_or_place_proposal", structured.messageIntent)
        assertEquals(3, structured.replySlotPlans.size)
        assertTrue(structured.replyLanguageDirective.contains("Hinglish"))
        assertEquals(3, structured.messages.size)
        assertEquals("them", structured.messages[0].sender)
        assertEquals("Hey kal sham ko 7 baje coffee pe milte hain?", structured.messages[0].text)
        assertEquals("me", structured.messages[1].sender)
        assertEquals("Haan sounds good, kahan?", structured.messages[1].text)
        assertEquals("them", structured.messages[2].sender)
        assertEquals("Blue Tokai Indiranagar chalte hain!", structured.latestIncomingMessage)

        val payloadJson = WingAiApiClient.buildAnalyzePayloadJson(
            userId = "wing_b6cef3b37f",
            imageBase64 = "should_not_be_sent",
            tone = ReplyTone.CASUAL,
            structuredContext = structured
        )

        assertFalse(payloadJson.has("image_base64"))
        assertTrue(payloadJson.has("structured_context"))
        assertEquals(
            "Blue Tokai Indiranagar chalte hain!",
            payloadJson.getJSONObject("structured_context").getString("latest_incoming_message")
        )
    }
}
