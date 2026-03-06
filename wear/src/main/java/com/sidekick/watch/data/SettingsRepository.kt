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
const val DEFAULT_SPACEBOT_BASE_URL = "https://debian.finch-kelvin.ts.net"

data class SpacebotSettings(
    val baseUrl: String = DEFAULT_SPACEBOT_BASE_URL,
    val authToken: String = "",
)

class SettingsRepository(private val context: Context) {

    val settingsFlow: Flow<SpacebotSettings> =
        context.dataStore.data
            .catch { ex ->
                if (ex is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw ex
                }
            }
            .map { prefs ->
                SpacebotSettings(
                    baseUrl = prefs[BASE_URL_KEY] ?: DEFAULT_SPACEBOT_BASE_URL,
                    authToken = prefs[AUTH_TOKEN_KEY].orEmpty(),
                )
            }

    suspend fun saveSettings(baseUrl: String, authToken: String) {
        context.dataStore.edit { prefs ->
            val normalizedBaseUrl = baseUrl.trim().trimEnd('/').ifBlank { DEFAULT_SPACEBOT_BASE_URL }
            prefs[BASE_URL_KEY] = normalizedBaseUrl
            prefs[AUTH_TOKEN_KEY] = authToken.trim()
        }
    }

    private companion object {
        val BASE_URL_KEY = stringPreferencesKey("base_url")
        val AUTH_TOKEN_KEY = stringPreferencesKey("auth_token")
    }
}
