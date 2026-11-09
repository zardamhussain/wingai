package com.wingai.keyboard.data

enum class ReplyTone(val wireName: String, val label: String) {
    FLIRTY("flirty", "Flirty"),
    CASUAL("casual", "Casual"),
    FRIENDLY("friendly", "Friendly"),
    FUNNY("funny", "Funny"),
    PROFESSIONAL("professional", "Professional"),
    SHORT("short", "Short"),
    DETAILED("detailed", "Detailed");

    companion object {
        fun fromWireName(value: String?): ReplyTone =
            entries.firstOrNull { it.wireName.equals(value, ignoreCase = true) } ?: FRIENDLY
    }
}

enum class ShortcutAction(val wireName: String, val label: String) {
    READ_SCREEN("read_screen", "Select & Analyze Screen Area"),
    OPEN_PANEL("open_panel", "Open AI Control Panel"),
    PASTE_CONTEXT("paste_context", "Paste Conversation Context"),
    NONE("none", "Disabled");

    companion object {
        fun fromWireName(value: String?): ShortcutAction =
            entries.firstOrNull { it.wireName.equals(value, ignoreCase = true) } ?: READ_SCREEN
    }
}

enum class RewriteAction(val wireName: String, val label: String) {
    REWRITE("rewrite", "Rewrite"),
    SHORTEN("shorten", "Shorten"),
    EXPAND("expand", "Expand"),
    TONE("tone", "Tone")
}

enum class ActiveInputTarget {
    HOST_APP,
    CUSTOM_INSTRUCTION,
    PASTED_CONTEXT,
    EDIT_REPLY
}

enum class AiHubMode(val label: String) {
    REPLY("Reply"),
    REWRITE("Tone"),
    POLISH("Editor")
}

data class UserProfile(
    val userId: String = "",
    val tier: String = "free",
    val dailyUsed: Int = 0,
    val dailyLimit: Int? = 5,
    val remaining: Int? = 5
) {
    val isPaid: Boolean get() = tier.equals("paid", ignoreCase = true)
}

data class ReplySuggestion(
    val tone: String,
    val text: String
)

data class StructuredChatMessage(
    val index: Int,
    val sender: String, // "them" | "me" | "system"
    val text: String,
    val alignment: String // "left" | "right" | "center"
)

data class StructuredChatContext(
    val source: String = "ocr_select_and_search",
    val languageHint: String = "english",
    val replyLanguageDirective: String = "Write all 3 replies in natural conversational English.",
    val lastSpeaker: String = "them",
    val messageIntent: String = "general_statement",
    val userStyleHint: String = "casual_conversational",
    val targetWordCountRange: String = "4 to 12 words",
    val replySlotPlans: List<String> = emptyList(),
    val latestIncomingMessage: String = "",
    val messages: List<StructuredChatMessage> = emptyList(),
    val formattedTranscript: String = ""
)

data class AiReplyResult(
    val status: String,
    val contextStatus: String,
    val message: String? = null,
    val replies: List<ReplySuggestion> = emptyList(),
    val user: UserProfile? = null
)

data class AiSessionState(
    val isPanelOpen: Boolean = false,
    val isFeaturesRowOpen: Boolean = true,
    val isKeyboardVisible: Boolean = false,
    val hubMode: AiHubMode = AiHubMode.REPLY,
    val activeInputTarget: ActiveInputTarget = ActiveInputTarget.HOST_APP,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val pendingImageBase64: String? = null,
    val selectedImageBase64: String? = null,
    val structuredContext: StructuredChatContext? = null,
    val pastedContext: String = "",
    val hostDraftText: String = "",
    val undoPreviousText: String? = null,
    val lastReplacedText: String? = null,
    val selectedTone: ReplyTone? = null,
    val customInstruction: String = "",
    val editingReplyText: String = "",
    val preservedDraft: String = "",
    val suggestions: List<ReplySuggestion> = emptyList(),
    val userProfile: UserProfile? = null
)
