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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "spacebot_settings")

private val json = Json { ignoreUnknownKeys = true }

data class AgentSettings(
    val backendId: String = AgentBackends.openclaw.id,
    val baseUrl: String = AgentBackends.openclaw.defaultBaseUrl,
    val authToken: String = "",
    val model: String = AgentBackends.openclaw.defaultModel.orEmpty(),
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
                    model = prefs[MODEL_KEY]?.ifBlank { backend.defaultModel.orEmpty() } ?: backend.defaultModel.orEmpty(),
                )
            }

    val themeFlow: Flow<String> =
        context.dataStore.data
            .catch { ex ->
                if (ex is IOException) emit(emptyPreferences()) else throw ex
            }
            .map { prefs -> prefs[THEME_KEY] ?: "default" }

    suspend fun saveTheme(themeId: String) {
        context.dataStore.edit { prefs -> prefs[THEME_KEY] = themeId }
    }

    suspend fun saveSettings(backendId: String, baseUrl: String, authToken: String, model: String) {
        context.dataStore.edit { prefs ->
            val backend = AgentBackends.fromId(backendId)
            val normalizedBaseUrl = normalizeBaseUrl(baseUrl).ifBlank { backend.defaultBaseUrl }
            prefs[BACKEND_ID_KEY] = backend.id
            prefs[BASE_URL_KEY] = normalizedBaseUrl
            prefs[AUTH_TOKEN_KEY] = authToken.trim()
            prefs[MODEL_KEY] = model.trim().ifBlank { backend.defaultModel.orEmpty() }
        }
    }

    suspend fun saveConversationState(state: PersistedConversationState) {
        context.dataStore.edit { prefs ->
            prefs[CONVERSATION_STATE_KEY] = json.encodeToString(state)
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
        return runCatching { json.decodeFromString<PersistedConversationState>(raw) }.getOrNull()
    }

    private companion object {
        val BACKEND_ID_KEY = stringPreferencesKey("backend_id")
        val BASE_URL_KEY = stringPreferencesKey("base_url")
        val AUTH_TOKEN_KEY = stringPreferencesKey("auth_token")
        val MODEL_KEY = stringPreferencesKey("model")
        val CONVERSATION_STATE_KEY = stringPreferencesKey("conversation_state_json")
        val THEME_KEY = stringPreferencesKey("color_theme")
    }
}

@Serializable
data class PersistedConversationState(
    val selectedConversationId: String? = null,
    val conversations: List<PersistedConversationSummary> = emptyList(),
    val messagesByConversation: Map<String, List<PersistedChatMessage>> = emptyMap(),
    val backendConversationIds: Map<String, String> = emptyMap(),
)

@Serializable
data class PersistedConversationSummary(
    val id: String,
    val initialPrompt: String? = null,
    val lastUpdatedEpochMs: Long = 0L,
)

@Serializable
data class PersistedChatMessage(
    val id: String,
    val role: String,
    val text: String,
)
