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
