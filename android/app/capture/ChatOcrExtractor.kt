package com.wingai.keyboard.capture

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.wingai.keyboard.data.StructuredChatContext
import com.wingai.keyboard.data.StructuredChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

data class OcrDetectedLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

object ChatOcrExtractor {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun warmUp() {
        runCatching { recognizer }
    }

    private val STANDALONE_NOISE_REGEX = Regex(
        """^(?:today|yesterday|unread messages|typing\.{0,3}|online|delivered|read|seen|sent|voice call.*|missed voice call.*|video call.*|\d{1,2}[:.]\d{2}(?:\s?[ap]m)?(?:\s*[vV✓✔/\\|!]{1,3})?|\u2713{1,2})$""",
        RegexOption.IGNORE_CASE
    )

    private val TRAILING_TIMESTAMP_REGEX = Regex(
        """\s+\d{1,2}[:.]\d{2}(?:\s?[ap]m)?(?:\s*[\u2713✓✔vV/\\|!]{1,3})?$""",
        RegexOption.IGNORE_CASE
    )

    private val DEVANAGARI_REGEX = Regex("""[\u0900-\u097F]""")

    private val HINGLISH_MARKERS = setOf(
        "haan", "han", "nahi", "nhi", "na", "kya", "kaise", "kaisa", "kaisi", "bhai", "yaar",
        "acha", "accha", "theek", "thik", "hai", "hain", "h", "ho", "hu", "hoon", "milte",
        "kal", "aaj", "abhi", "chal", "chalo", "chl", "batao", "bata", "bta", "dekhte",
        "pakka", "zaroor", "bilkul", "shukriya", "karo", "karna", "kr", "krde", "krdia",
        "raha", "rahi", "rahe", "rha", "rhi", "baad", "pehle", "kaam", "kahan", "kaha",
        "kab", "kyun", "kyu", "tum", "tu", "aap", "mera", "meri", "mere", "tera", "teri",
        "sahi", "baje", "sham", "subah", "raat", "dopahar", "aja", "aaja", "ao", "aao",
        "wala", "wali", "wale", "koi", "kuch", "bohot", "bahut", "thoda", "zyada", "jaldi",
        "mushkil", "scene", "plan", "bdi", "bada", "chota", "mast", "badhiya", "bas", "aur",
        "lekin", "par", "toh", "to", "bhi", "hi", "se", "ko", "mein", "me", "pe", "par",
        "gayab", "gaya", "gayi", "gaye", "jaunga", "jaungi", "aunga", "ayega", "chalega"
    )

    private val SLANG_BRO_MARKERS = setOf(
        "bhai", "bro", "yaar", "abe", "abey", "scene", "chill", "pakka", "mast", "bdi", "aja", "chl", "kr"
    )

    private val POLITE_MARKERS = setOf(
        "aap", "ji", "please", "thank", "thanks", "shukriya", "regards", "sir", "ma'am", "kindly"
    )

    private val TIME_PLACE_KEYWORDS = setOf(
        "baje", "pm", "am", "kal", "aaj", "sham", "subah", "raat", "tomorrow", "today", "tonight",
        "milte", "chalte", "meet", "coffee", "dinner", "lunch", "office", "call", "time", "when", "where", "kahan", "kab"
    )

    private val GREETING_KEYWORDS = setOf(
        "hey", "hi", "hello", "wassup", "sup", "morning", "evening", "haal", "kaise", "kaisa"
    )

    private val APOLOGY_DELAY_KEYWORDS = setOf(
        "sorry", "late", "busy", "stuck", "delay", "traffic", "wait", "ruk", "thoda"
    )

    private val ACK_SHORT_KEYWORDS = setOf(
        "ok", "okay", "acha", "accha", "haan", "han", "theek", "thik", "done", "cool", "nice", "aja", "aaja", "hmm", "hm"
    )

    /**
     * Runs on-device ML Kit OCR on the cropped selection bitmap and converts the detected
     * spatial text lines into a fully pre-analyzed `StructuredChatContext`.
     */
    suspend fun extractStructuredChat(croppedBitmap: Bitmap): Result<StructuredChatContext> =
        withContext(Dispatchers.Default) {
            runCatching {
                val maxDim = max(croppedBitmap.width, croppedBitmap.height)
                val ocrBitmap = if (maxDim > 900) {
                    val scale = 900f / maxDim.toFloat()
                    Bitmap.createScaledBitmap(
                        croppedBitmap,
                        (croppedBitmap.width * scale).toInt().coerceAtLeast(1),
                        (croppedBitmap.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    croppedBitmap
                }
                val width = ocrBitmap.width.coerceAtLeast(1)
                val height = ocrBitmap.height.coerceAtLeast(1)
                val visionText = try {
                    recognizer.process(InputImage.fromBitmap(ocrBitmap, 0)).await()
                } finally {
                    if (ocrBitmap !== croppedBitmap && !ocrBitmap.isRecycled) {
                        runCatching { ocrBitmap.recycle() }
                    }
                }

                val detectedLines = mutableListOf<OcrDetectedLine>()
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val box = line.boundingBox
                        val raw = line.text.trim()
                        if (raw.isNotEmpty() && box != null) {
                            detectedLines.add(
                                OcrDetectedLine(
                                    text = raw,
                                    left = box.left.coerceAtLeast(0),
                                    top = box.top.coerceAtLeast(0),
                                    right = box.right.coerceAtMost(width),
                                    bottom = box.bottom.coerceAtMost(height)
                                )
                            )
                        }
                    }
                }

                val structured = buildStructuredChatFromLines(
                    lines = detectedLines,
                    cropWidth = width,
                    cropHeight = height
                )
                if (structured.messages.isEmpty()) {
                    throw IllegalStateException("No readable chat text found in the selected area. Select a clearer message region.")
                }
                structured
            }
        }

    /**
     * Pure deterministic layout-aware parser that:
     * 1. Classifies OCR lines into `them` (left), `me` (right), or `system` (center)
     * 2. Merges multi-line bubbles & trims to the most recent 6 turns
     * 3. Pre-computes conversation language, user style/length profile, message intent, and 3 reply slot plans
     *    so the lightweight backend model does zero input analysis.
     */
    fun buildStructuredChatFromLines(
        lines: List<OcrDetectedLine>,
        cropWidth: Int,
        cropHeight: Int
    ): StructuredChatContext {
        val safeW = cropWidth.coerceAtLeast(1).toFloat()
        val sorted = lines
            .mapNotNull { line ->
                val cleaned = cleanLineText(line.text)
                if (cleaned.isEmpty()) null else line.copy(text = cleaned)
            }
            .sortedWith(compareBy<OcrDetectedLine> { it.top }.thenBy { it.left })

        data class MutableBubble(
            val sender: String,
            val alignment: String,
            var text: String,
            var left: Int,
            var top: Int,
            var right: Int,
            var bottom: Int
        )

        val bubbles = mutableListOf<MutableBubble>()
        for (line in sorted) {
            val (sender, alignment) = classifySenderAndAlignment(line, safeW)
            val lineHeight = (line.bottom - line.top).coerceAtLeast(12)
            val prev = bubbles.lastOrNull()
            if (prev != null && prev.sender == sender && sender != "system") {
                val prevHeight = (prev.bottom - prev.top).coerceAtLeast(12)
                val verticalGap = line.top - prev.bottom
                val maxMergeGap = (max(prevHeight, lineHeight) * 1.45f).toInt().coerceAtLeast(24)
                if (verticalGap <= maxMergeGap) {
                    prev.text = "${prev.text} ${line.text}".trim()
                    prev.left = minOf(prev.left, line.left)
                    prev.right = maxOf(prev.right, line.right)
                    prev.bottom = maxOf(prev.bottom, line.bottom)
                    continue
                }
            }
            bubbles.add(
                MutableBubble(
                    sender = sender,
                    alignment = alignment,
                    text = line.text,
                    left = line.left,
                    top = line.top,
                    right = line.right,
                    bottom = line.bottom
                )
            )
        }

        val recentBubbles = bubbles
            .filter { it.text.isNotBlank() }

        val structuredMessages = recentBubbles.mapIndexed { idx, b ->
            StructuredChatMessage(
                index = idx + 1,
                sender = b.sender,
                text = b.text,
                alignment = b.alignment
            )
        }

        return enrichStructuredChat(
            source = "ocr_select_and_search",
            messages = structuredMessages
        )
    }

    /**
     * Also pre-analyzes pasted plain text conversations so `/v1/replies` and `/v1/analyze`
     * always benefit from frontend language, intent, and slot-plan extraction.
     */
    fun buildStructuredChatFromRawText(rawText: String): StructuredChatContext {
        val rawLines = rawText.lines()
            .map { cleanLineText(it) }
            .filter { it.isNotBlank() }

        val messages = rawLines.mapIndexed { idx, line ->
            val lower = line.lowercase()
            val (sender, cleanedText, align) = when {
                lower.startsWith("[me]:") || lower.startsWith("me:") || lower.startsWith("you:") -> {
                    val stripped = line.substringAfter(":").trim()
                    Triple("me", stripped, "right")
                }
                lower.startsWith("[them]:") || lower.startsWith("them:") || lower.startsWith("partner:") -> {
                    val stripped = line.substringAfter(":").trim()
                    Triple("them", stripped, "left")
                }
                else -> Triple("them", line, "left")
            }
            StructuredChatMessage(
                index = idx + 1,
                sender = sender,
                text = cleanedText,
                alignment = align
            )
        }

        return enrichStructuredChat(
            source = "text_pre_analyzer",
            messages = messages
        )
    }

    private fun enrichStructuredChat(
        source: String,
        messages: List<StructuredChatMessage>
    ): StructuredChatContext {
        val nonSystem = messages.filter { it.sender != "system" }
        val lastSpeaker = nonSystem.lastOrNull()?.sender ?: "them"
        val latestIncoming = messages.lastOrNull { it.sender == "them" }?.text
            ?: nonSystem.lastOrNull()?.text
            ?: messages.lastOrNull()?.text.orEmpty()

        val combinedText = messages.joinToString(" ") { it.text }
        val hasDevanagari = DEVANAGARI_REGEX.containsMatchIn(combinedText)
        val allWords = messages
            .flatMap { it.text.lowercase().split(Regex("[^a-z']+")) }
            .filter { it.isNotBlank() }

        val hinglishHits = allWords.count { word ->
            word in HINGLISH_MARKERS ||
                word.endsWith("unga") ||
                word.endsWith("ungi") ||
                word.endsWith("ega") ||
                word.endsWith("egi") ||
                word.endsWith("raha") ||
                word.endsWith("rahi")
        }

        val languageHint = when {
            hasDevanagari -> "hindi_devanagari"
            hinglishHits >= 1 -> "hinglish"
            else -> "english"
        }

        val slangHits = allWords.count { it in SLANG_BRO_MARKERS }
        val politeHits = allWords.count { it in POLITE_MARKERS }
        val meMessages = messages.filter { it.sender == "me" }
        val avgMeWords = if (meMessages.isNotEmpty()) {
            meMessages.sumOf { it.text.split(Regex("\\s+")).size } / meMessages.size
        } else {
            6
        }

        val userStyleHint = when {
            politeHits >= 1 -> "polite_respectful"
            slangHits >= 1 && avgMeWords <= 8 -> "short_casual_bro_slang"
            slangHits >= 1 -> "casual_bro_slang"
            avgMeWords <= 5 -> "short_punchy_casual"
            else -> "casual_conversational"
        }

        val targetWordCountRange = when {
            avgMeWords <= 5 -> "3 to 8 words"
            avgMeWords <= 10 -> "5 to 12 words"
            else -> "8 to 16 words"
        }

        val replyLanguageDirective = when (languageHint) {
            "hindi_devanagari" ->
                "Users are chatting in Hindi (Devanagari). Write all 3 replies in natural Hindi."
            "hinglish" ->
                if (slangHits >= 1) {
                    "Users are chatting in casual Hinglish (Hindi in Latin script with casual slang like bhai/yaar/aja/kr). Write all 3 replies in natural Hinglish matching this exact vibe."
                } else {
                    "Users are chatting in Hinglish (Hindi written in English/Latin alphabet). Write all 3 replies in natural conversational Hinglish."
                }
            else ->
                "Users are chatting in English. Write all 3 replies in natural conversational English."
        }

        val (intent, slotPlans) = classifyIntentAndBuildSlotPlans(
            targetMessage = latestIncoming,
            lastSpeaker = lastSpeaker
        )

        val formattedTranscript = messages.joinToString("\n") { msg ->
            val tag = when (msg.sender) {
                "me" -> "[ME]"
                "system" -> "[CONTEXT]"
                else -> "[THEM]"
            }
            "$tag: ${msg.text}"
        }

        return StructuredChatContext(
            source = source,
            languageHint = languageHint,
            replyLanguageDirective = replyLanguageDirective,
            lastSpeaker = lastSpeaker,
            messageIntent = intent,
            userStyleHint = userStyleHint,
            targetWordCountRange = targetWordCountRange,
            replySlotPlans = slotPlans,
            latestIncomingMessage = latestIncoming,
            messages = messages,
            formattedTranscript = formattedTranscript
        )
    }

    private fun classifyIntentAndBuildSlotPlans(
        targetMessage: String,
        lastSpeaker: String
    ): Pair<String, List<String>> {
        val lower = targetMessage.lowercase().trim()
        val words = lower.split(Regex("[^a-z0-9:]+")).filter { it.isNotBlank() }.toSet()
        val hasQuestionMark = lower.contains("?")
        val hasTimeDigits = Regex("""\b\d{1,2}(?::\d{2})?\s*(?:am|pm|baje)?\b""").containsMatchIn(lower)

        return when {
            words.intersect(TIME_PLACE_KEYWORDS).isNotEmpty() || hasTimeDigits -> {
                "time_or_place_proposal" to listOf(
                    "Slot 1: Confirm and agree to the time/place/plan naturally.",
                    "Slot 2: Suggest a slight time adjustment (e.g. 15-30 mins later) or ask a quick detail.",
                    "Slot 3: Say you will check and confirm shortly."
                )
            }
            words.intersect(APOLOGY_DELAY_KEYWORDS).isNotEmpty() -> {
                "apology_or_delay" to listOf(
                    "Slot 1: Reassure them warmly (no worries / take your time).",
                    "Slot 2: Ask casually when they will be free/ready.",
                    "Slot 3: Short, chill acknowledgment."
                )
            }
            words.size <= 3 && words.intersect(ACK_SHORT_KEYWORDS).isNotEmpty() -> {
                "acknowledgment_or_short_ping" to listOf(
                    "Slot 1: Match their energy with a short, direct follow-up.",
                    "Slot 2: Ask a quick question to move the plan/chat forward.",
                    "Slot 3: Brief, natural confirmation."
                )
            }
            words.size <= 5 && words.intersect(GREETING_KEYWORDS).isNotEmpty() -> {
                "greeting_or_checkin" to listOf(
                    "Slot 1: Warm, upbeat greeting + ask how things are going.",
                    "Slot 2: Chill casual greeting + brief status.",
                    "Slot 3: Playful, friendly check-in."
                )
            }
            hasQuestionMark || lower.startsWith("kya ") || lower.startsWith("can ") ||
                lower.startsWith("are ") || lower.startsWith("do ") || lower.startsWith("will ") -> {
                if (words.contains("kahan") || words.contains("kaha") || words.contains("kab") ||
                    words.contains("kaise") || words.contains("kyun") || words.contains("kyu") ||
                    words.contains("where") || words.contains("when") || words.contains("what") ||
                    words.contains("how") || words.contains("why")
                ) {
                    "open_question" to listOf(
                        "Slot 1: Direct, helpful conversational answer.",
                        "Slot 2: Casual answer + ask their preference.",
                        "Slot 3: Say you will check and let them know in a bit."
                    )
                } else {
                    "yes_no_question" to listOf(
                        "Slot 1: Clear affirmative ('Yes / Haan') response.",
                        "Slot 2: Conditional / soft ('Maybe / Let me check') response.",
                        "Slot 3: Polite decline or alternative suggestion."
                    )
                }
            }
            lastSpeaker == "me" -> {
                "follow_up_after_self" to listOf(
                    "Slot 1: Natural follow-up to nudging/continuing the thread.",
                    "Slot 2: Add a quick clarifying detail or question.",
                    "Slot 3: Short, low-pressure check-in."
                )
            }
            else -> {
                "general_statement" to listOf(
                    "Slot 1: Agreeable, natural reaction matching their vibe.",
                    "Slot 2: Curious follow-up question to keep the conversation flowing.",
                    "Slot 3: Concise, high-signal reply."
                )
            }
        }
    }

    private fun cleanLineText(raw: String): String {
        val singleLine = raw.replace(Regex("\\s+"), " ").trim()
        if (singleLine.isEmpty() || STANDALONE_NOISE_REGEX.matches(singleLine)) {
            return ""
        }
        val stripped = singleLine.replace(TRAILING_TIMESTAMP_REGEX, "").trim()
        return if (STANDALONE_NOISE_REGEX.matches(stripped)) "" else stripped
    }

    private fun classifySenderAndAlignment(
        line: OcrDetectedLine,
        cropWidth: Float
    ): Pair<String, String> {
        val normLeft = (line.left / cropWidth).coerceIn(0f, 1f)
        val normRight = (line.right / cropWidth).coerceIn(0f, 1f)
        val normCenter = (normLeft + normRight) / 2f

        return when {
            normLeft > 0.24f && normRight < 0.76f && abs(normCenter - 0.5f) <= 0.08f && line.text.length <= 36 ->
                "system" to "center"
            normCenter >= 0.53f || (normRight >= 0.78f && normLeft >= 0.18f) ->
                "me" to "right"
            else ->
                "them" to "left"
        }
    }
}
