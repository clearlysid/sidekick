package com.sidekick.watch.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Headers
import org.json.JSONArray
import org.json.JSONObject

class SpacebotRepository(
    private val client: OkHttpClient,
) {

    suspend fun sendMessage(
        baseUrl: String,
        authToken: String,
        conversationId: String,
        senderId: String,
        content: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload =
                    JSONObject()
                        .put("conversation_id", conversationId)
                        .put("sender_id", senderId)
                        .put("content", content)

                val request =
                    Request.Builder()
                        .url("${normalizeBaseUrl(baseUrl)}/send")
                        .headers(authHeaders(authToken))
                        .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                        .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("Send failed (${response.code}): ${response.body?.string().orEmpty()}")
                    }
                }
            }
        }

    suspend fun pollReplies(
        baseUrl: String,
        authToken: String,
        conversationId: String,
        timeoutMs: Long = 30_000,
        intervalMs: Long = 2_000,
    ): Result<List<SpacebotMessage>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val deadline = System.currentTimeMillis() + timeoutMs
                val buffered = mutableListOf<SpacebotMessage>()
                var sawStreamChunk = false

                while (System.currentTimeMillis() < deadline) {
                    val messages = pollOnce(baseUrl, authToken, conversationId)
                    buffered.addAll(messages)

                    if (messages.any { it.type == TYPE_STREAM_CHUNK }) {
                        sawStreamChunk = true
                    }

                    if (messages.any { it.type == TYPE_STREAM_END }) {
                        break
                    }

                    // Non-streaming replies should be shown immediately instead of waiting full timeout.
                    if (!sawStreamChunk && messages.any { it.type == TYPE_TEXT || it.type == TYPE_FILE }) {
                        break
                    }

                    delay(intervalMs)
                }

                buffered
            }
        }

    private fun pollOnce(
        baseUrl: String,
        authToken: String,
        conversationId: String,
    ): List<SpacebotMessage> {
        val request =
            Request.Builder()
                .url("${normalizeBaseUrl(baseUrl)}/poll/$conversationId")
                .headers(authHeaders(authToken))
                .get()
                .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Poll failed (${response.code}): ${response.body?.string().orEmpty()}")
            }

            val body = response.body?.string().orEmpty()
            if (body.isBlank()) {
                return emptyList()
            }

            val root = JSONObject(body)
            val messageArray: JSONArray = root.optJSONArray("messages") ?: JSONArray()

            return buildList {
                for (index in 0 until messageArray.length()) {
                    val obj = messageArray.optJSONObject(index) ?: continue
                    add(
                        SpacebotMessage(
                            type = obj.optString("type"),
                            content = obj.optString("content"),
                        ),
                    )
                }
            }
        }
    }

    private fun authHeaders(token: String): Headers {
        if (token.isBlank()) {
            return Headers.headersOf()
        }
        return Headers.Builder()
            .add("Authorization", "Bearer $token")
            .add("x-webhook-token", token)
            .build()
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val TYPE_TEXT = "text"
        const val TYPE_STREAM_CHUNK = "stream_chunk"
        const val TYPE_STREAM_END = "stream_end"
        const val TYPE_FILE = "file"
    }
}

data class SpacebotMessage(
    val type: String,
    val content: String,
)
