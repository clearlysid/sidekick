package com.sidekick.watch.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "spacebot_settings")

data class AgentSettings(
    val backendId: String = AgentBackends.spark.id,
    val baseUrl: String = AgentBackends.spark.defaultBaseUrl,
    val authToken: String = "",
)

class SettingsRepository(private val context: Context) {

    val settingsFlow: Flow<AgentSettings> =
        context.dataStore.data
            .catch { ex ->
                if (ex is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw ex
                }
            }
            .map { prefs ->
                val backend = AgentBackends.fromId(prefs[BACKEND_ID_KEY])
                AgentSettings(
                    backendId = backend.id,
                    baseUrl = prefs[BASE_URL_KEY]?.ifBlank { backend.defaultBaseUrl } ?: backend.defaultBaseUrl,
                    authToken = prefs[AUTH_TOKEN_KEY].orEmpty(),
                )
            }

    suspend fun saveSettings(backendId: String, baseUrl: String, authToken: String) {
        context.dataStore.edit { prefs ->
            val backend = AgentBackends.fromId(backendId)
            val normalizedBaseUrl = baseUrl.trim().trimEnd('/').ifBlank { backend.defaultBaseUrl }
            prefs[BACKEND_ID_KEY] = backend.id
            prefs[BASE_URL_KEY] = normalizedBaseUrl
            prefs[AUTH_TOKEN_KEY] = authToken.trim()
        }
    }

    suspend fun saveConversationState(state: PersistedConversationState) {
        context.dataStore.edit { prefs ->
            prefs[CONVERSATION_STATE_KEY] = serializeConversationState(state)
        }
    }

    suspend fun loadConversationState(): PersistedConversationState? {
        val prefs =
            context.dataStore.data
                .catch { ex ->
                    if (ex is IOException) {
                        emit(emptyPreferences())
                    } else {
                        throw ex
                    }
                }
                .first()
        val raw = prefs[CONVERSATION_STATE_KEY].orEmpty()
        if (raw.isBlank()) return null
        return deserializeConversationState(raw)
    }

    private fun serializeConversationState(state: PersistedConversationState): String {
        val root = JSONObject()
            .put("version", 1)
            .put("selected_conversation_id", state.selectedConversationId ?: JSONObject.NULL)

        val conversationsArray = JSONArray()
        state.conversations.forEach { conversation ->
            conversationsArray.put(
                JSONObject()
                    .put("id", conversation.id)
                    .put("initial_prompt", conversation.initialPrompt ?: JSONObject.NULL)
                    .put("last_updated_epoch_ms", conversation.lastUpdatedEpochMs),
            )
        }
        root.put("conversations", conversationsArray)

        val messagesObject = JSONObject()
        state.messagesByConversation.forEach { (conversationId, messages) ->
            val messagesArray = JSONArray()
            messages.forEach { message ->
                messagesArray.put(
                    JSONObject()
                        .put("id", message.id)
                        .put("role", message.role)
                        .put("text", message.text),
                )
            }
            messagesObject.put(conversationId, messagesArray)
        }
        root.put("messages_by_conversation", messagesObject)

        val backendIdsObject = JSONObject()
        state.backendConversationIds.forEach { (localId, backendId) ->
            backendIdsObject.put(localId, backendId)
        }
        root.put("backend_conversation_ids", backendIdsObject)

        return root.toString()
    }

    private fun deserializeConversationState(raw: String): PersistedConversationState? =
        runCatching {
            val root = JSONObject(raw)

            val conversations =
                mutableListOf<PersistedConversationSummary>().apply {
                    val array = root.optJSONArray("conversations") ?: JSONArray()
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val id = item.optString("id").trim()
                        if (id.isBlank()) continue
                        val prompt = item.optString("initial_prompt", "").ifBlank { null }
                        val lastUpdated = item.optLong("last_updated_epoch_ms", System.currentTimeMillis())
                        add(
                            PersistedConversationSummary(
                                id = id,
                                initialPrompt = prompt,
                                lastUpdatedEpochMs = lastUpdated,
                            ),
                        )
                    }
                }

            val messagesByConversation =
                buildMap<String, List<PersistedChatMessage>> {
                    val messagesObject = root.optJSONObject("messages_by_conversation") ?: JSONObject()
                    val keys = messagesObject.keys()
                    while (keys.hasNext()) {
                        val conversationId = keys.next().orEmpty()
                        if (conversationId.isBlank()) continue
                        val array = messagesObject.optJSONArray(conversationId) ?: JSONArray()
                        val messages =
                            mutableListOf<PersistedChatMessage>().apply {
                                for (index in 0 until array.length()) {
                                    val item = array.optJSONObject(index) ?: continue
                                    val id = item.optString("id").ifBlank { continue }
                                    val role = item.optString("role").ifBlank { continue }
                                    val text = item.optString("text")
                                    add(PersistedChatMessage(id = id, role = role, text = text))
                                }
                            }
                        put(conversationId, messages)
                    }
                }

            val backendConversationIds =
                buildMap<String, String> {
                    val backendObject = root.optJSONObject("backend_conversation_ids") ?: JSONObject()
                    val keys = backendObject.keys()
                    while (keys.hasNext()) {
                        val localId = keys.next().orEmpty()
                        val backendId = backendObject.optString(localId).trim()
                        if (localId.isNotBlank() && backendId.isNotBlank()) {
                            put(localId, backendId)
                        }
                    }
                }

            PersistedConversationState(
                selectedConversationId = root.optString("selected_conversation_id").ifBlank { null },
                conversations = conversations,
                messagesByConversation = messagesByConversation,
                backendConversationIds = backendConversationIds,
            )
        }.getOrNull()

    private companion object {
        val BACKEND_ID_KEY = stringPreferencesKey("backend_id")
        val BASE_URL_KEY = stringPreferencesKey("base_url")
        val AUTH_TOKEN_KEY = stringPreferencesKey("auth_token")
        val CONVERSATION_STATE_KEY = stringPreferencesKey("conversation_state_json")
    }
}

data class PersistedConversationState(
    val selectedConversationId: String?,
    val conversations: List<PersistedConversationSummary>,
    val messagesByConversation: Map<String, List<PersistedChatMessage>>,
    val backendConversationIds: Map<String, String>,
)

data class PersistedConversationSummary(
    val id: String,
    val initialPrompt: String?,
    val lastUpdatedEpochMs: Long,
)

data class PersistedChatMessage(
    val id: String,
    val role: String,
    val text: String,
)
