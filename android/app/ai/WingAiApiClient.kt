package com.wingai.keyboard.ai

import com.wingai.keyboard.data.AiReplyResult
import com.wingai.keyboard.data.ReplySuggestion
import com.wingai.keyboard.data.ReplyTone
import com.wingai.keyboard.data.RewriteAction
import com.wingai.keyboard.data.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class WingAiApiClient(
    private val baseUrlProvider: () -> String,
    private val userIdProvider: (() -> String)? = null
) {

    suspend fun fetchUserProfile(userId: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val encodedId = URLEncoder.encode(userId, "UTF-8")
            val raw = getJson("/v1/user?user_id=$encodedId", userId)
            parseUserProfile(JSONObject(raw))
        }
    }

    suspend fun analyzeContext(
        imageBase64: String? = null,
        conversationText: String? = null,
        tone: ReplyTone? = null,
        customInstruction: String? = null
    ): Result<AiReplyResult> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = userIdProvider?.invoke().orEmpty()
            val payload = JSONObject().apply {
                if (uid.isNotBlank()) put("user_id", uid)
                if (!imageBase64.isNullOrBlank()) {
                    put("image_base64", imageBase64)
                    put("mime_type", "image/jpeg")
                }
                if (!conversationText.isNullOrBlank()) {
                    put("conversation_text", conversationText.trim())
                }
                if (tone != null) {
                    put("tone", tone.wireName)
                }
                if (!customInstruction.isNullOrBlank()) {
                    put("custom_instruction", customInstruction.trim())
                }
            }
            val raw = postJson("/v1/analyze", payload.toString(), uid)
            parseReplyResponse(raw)
        }
    }

    suspend fun generateReplies(
        conversationText: String,
        tone: ReplyTone? = null,
        customInstruction: String? = null
    ): Result<AiReplyResult> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = userIdProvider?.invoke().orEmpty()
            val payload = JSONObject().apply {
                if (uid.isNotBlank()) put("user_id", uid)
                put("conversation_text", conversationText.trim())
                if (tone != null) put("tone", tone.wireName)
                if (!customInstruction.isNullOrBlank()) {
                    put("custom_instruction", customInstruction.trim())
                }
            }
            val raw = postJson("/v1/replies", payload.toString(), uid)
            parseReplyResponse(raw)
        }
    }

    suspend fun rewriteReply(
        replyText: String,
        action: RewriteAction,
        tone: ReplyTone? = null,
        customInstruction: String? = null,
        conversationText: String? = null
    ): Result<AiReplyResult> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = userIdProvider?.invoke().orEmpty()
            val payload = JSONObject().apply {
                if (uid.isNotBlank()) put("user_id", uid)
                put("reply_text", replyText.trim())
                put("action", action.wireName)
                if (tone != null) put("tone", tone.wireName)
                if (!customInstruction.isNullOrBlank()) {
                    put("custom_instruction", customInstruction.trim())
                }
                if (!conversationText.isNullOrBlank()) {
                    put("conversation_text", conversationText.trim())
                }
            }
            val raw = postJson("/v1/rewrite", payload.toString(), uid)
            parseReplyResponse(raw)
        }
    }

    private fun getJson(path: String, userId: String): String {
        val base = resolvePrimaryBaseUrl()
        val conn = (URL("$base$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            if (userId.isNotBlank()) {
                setRequestProperty("X-User-Id", userId)
            }
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use(BufferedReader::readText)
            }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException(extractErrorDetail(body, code))
            }
            return body
        } finally {
            conn.disconnect()
        }
    }

    private fun postJson(path: String, jsonBody: String, userId: String = ""): String {
        val base = resolvePrimaryBaseUrl()
        val conn = (URL("$base$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 25_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (userId.isNotBlank()) {
                setRequestProperty("X-User-Id", userId)
            }
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonBody)
                writer.flush()
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use(BufferedReader::readText)
            }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException(extractErrorDetail(body, code))
            }
            if (body.isBlank()) {
                throw IllegalStateException("AI response is empty.")
            }
            return body
        } finally {
            conn.disconnect()
        }
    }

    private fun resolvePrimaryBaseUrl(): String {
        val configured = baseUrlProvider().trim().trimEnd('/')
        return if (configured.isNotBlank()) {
            configured
        } else {
            "https://wingai-backend.zardamhussain.workers.dev"
        }
    }

    private fun extractErrorDetail(body: String, code: Int): String {
        val detail = runCatching {
            JSONObject(body).optString("detail", "")
        }.getOrDefault("")
        return if (detail.isNotBlank()) {
            detail
        } else {
            "AI request failed (HTTP $code). Check connection or retry."
        }
    }

    companion object {
        fun parseUserProfile(obj: JSONObject): UserProfile {
            return UserProfile(
                userId = obj.optString("user_id", ""),
                tier = obj.optString("tier", "free"),
                dailyUsed = obj.optInt("daily_used", 0),
                dailyLimit = if (obj.isNull("daily_limit")) null else obj.optInt("daily_limit", 5),
                remaining = if (obj.isNull("remaining")) null else obj.optInt("remaining", 5)
            )
        }

        fun parseReplyResponse(rawJson: String): AiReplyResult {
            val root = JSONObject(rawJson)
            val status = root.optString("status", "success")
            val contextStatus = root.optString("context_status", "sufficient")
            val message = root.optString("message", "").takeIf { it.isNotBlank() }
            val userObj = root.optJSONObject("user")?.let { parseUserProfile(it) }
            val repliesArray = root.optJSONArray("replies")
                ?: throw IllegalStateException("AI response did not include replies.")

            val list = mutableListOf<ReplySuggestion>()
            for (i in 0 until repliesArray.length()) {
                val item = repliesArray.getJSONObject(i)
                val text = item.optString("text", "").trim()
                if (text.isNotEmpty()) {
                    list.add(
                        ReplySuggestion(
                            tone = item.optString("tone", "friendly").lowercase(),
                            text = text
                        )
                    )
                }
            }
            if (list.isEmpty()) {
                throw IllegalStateException("AI response contained no usable replies.")
            }
            return AiReplyResult(
                status = status,
                contextStatus = contextStatus,
                message = message,
                replies = list,
                user = userObj
            )
        }
    }
}
