package com.sidekick.watch.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader

data class OpenAIMessage(val role: String, val content: String)

class OpenAIRepository(
    private val client: OkHttpClient,
) {

    fun sendMessageStreaming(
        baseUrl: String,
        authToken: String,
        model: String,
        messages: List<OpenAIMessage>,
        user: String? = null,
    ): Flow<String> = flow {
        val messagesArray = JSONArray().apply {
            messages.forEach { msg ->
                put(JSONObject().put("role", msg.role).put("content", msg.content))
            }
        }
        val payload = JSONObject()
            .put("model", model)
            .put("messages", messagesArray)
            .put("stream", true)
        if (!user.isNullOrBlank()) payload.put("user", user)

        val url = "${normalizeBaseUrl(baseUrl)}/v1/chat/completions"
        val requestBuilder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
        if (authToken.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $authToken")
        }

        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            val body = response.body?.string().orEmpty()
            response.close()
            error("OpenAI request failed (${response.code}): $body")
        }

        val reader: BufferedReader = response.body?.byteStream()?.bufferedReader()
            ?: run { response.close(); error("Empty response body") }

        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (!l.startsWith("data: ")) continue
                val data = l.removePrefix("data: ").trim()
                if (data == "[DONE]") break
                val delta = runCatching {
                    JSONObject(data)
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("delta")
                        ?.optString("content")
                }.getOrNull()
                if (!delta.isNullOrEmpty()) {
                    emit(delta)
                }
            }
        } finally {
            reader.close()
            response.close()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun sendMessage(
        baseUrl: String,
        authToken: String,
        model: String,
        messages: List<OpenAIMessage>,
        user: String? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val messagesArray = JSONArray().apply {
                messages.forEach { msg ->
                    put(JSONObject().put("role", msg.role).put("content", msg.content))
                }
            }
            val payload = JSONObject()
                .put("model", model)
                .put("messages", messagesArray)
                .put("stream", false)
            if (!user.isNullOrBlank()) payload.put("user", user)

            val url = "${normalizeBaseUrl(baseUrl)}/v1/chat/completions"
            val requestBuilder = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            if (authToken.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $authToken")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    error("OpenAI request failed (${response.code}): ${response.body?.string().orEmpty()}")
                }
                val body = response.body?.string().orEmpty()
                JSONObject(body)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
            }
        }
    }

    private fun normalizeBaseUrl(baseUrl: String): String = baseUrl.trim().trimEnd('/')

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
