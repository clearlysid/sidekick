package com.sidekick.watch.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class OpenAIMessage(
    val role: String,
    val content: String,
)

class OpenAIRepository(
    private val client: OkHttpClient,
) {

    suspend fun sendMessage(
        baseUrl: String,
        authToken: String,
        conversationId: String,
        messages: List<OpenAIMessage>,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val messagesArray = JSONArray()
                for (message in messages) {
                    messagesArray.put(
                        JSONObject()
                            .put("role", message.role)
                            .put("content", message.content),
                    )
                }

                val payload =
                    JSONObject()
                        .put("model", "openclaw:main")
                        .put("user", conversationId)
                        .put("messages", messagesArray)

                val request =
                    Request.Builder()
                        .url("${normalizeBaseUrl(baseUrl)}/v1/chat/completions")
                        .addHeader("Authorization", "Bearer $authToken")
                        .addHeader("Content-Type", "application/json")
                        .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                        .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("Request failed (${response.code}): ${response.body?.string().orEmpty()}")
                    }

                    val body = response.body?.string().orEmpty()
                    val root = JSONObject(body)
                    val choices = root.optJSONArray("choices")
                        ?: error("Response missing 'choices' field")
                    val firstChoice = choices.optJSONObject(0)
                        ?: error("Response has empty 'choices' array")
                    val message = firstChoice.optJSONObject("message")
                        ?: error("Response missing 'message' in first choice")
                    message.optString("content", "")
                }
            }
        }

    private fun normalizeBaseUrl(baseUrl: String): String = baseUrl.trim().trimEnd('/')

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
