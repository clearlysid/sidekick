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
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "spacebot_settings")

data class AgentSettings(
    val backendId: String = AgentBackends.spacebot.id,
    val baseUrl: String = AgentBackends.spacebot.defaultBaseUrl,
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

    private companion object {
        val BACKEND_ID_KEY = stringPreferencesKey("backend_id")
        val BASE_URL_KEY = stringPreferencesKey("base_url")
        val AUTH_TOKEN_KEY = stringPreferencesKey("auth_token")
    }
}
