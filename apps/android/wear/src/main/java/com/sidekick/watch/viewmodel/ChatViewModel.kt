package com.sidekick.watch.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sidekick.watch.data.SettingsRepository
import com.sidekick.watch.data.SpacebotMessage
import com.sidekick.watch.data.SpacebotRepository
import com.sidekick.watch.data.SpacebotSettings
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val settingsRepository: SettingsRepository,
    private val spacebotRepository: SpacebotRepository,
) : ViewModel() {
    private val logTag = "SidekickInput"
    private var micNoiseFloorDb = Float.NaN

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val conversationId = UUID.randomUUID().toString()
    private val senderId = "wear-user"

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _uiState.update {
                    it.copy(
                        savedSettings = settings,
                        baseUrlInput = if (it.baseUrlInput.isEmpty()) settings.baseUrl else it.baseUrlInput,
                        authTokenInput = if (it.authTokenInput.isEmpty()) settings.authToken else it.authTokenInput,
                    )
                }
            }
        }
    }

    fun openSettings() {
        _uiState.update {
            it.copy(
                screen = Screen.Settings,
                baseUrlInput = it.savedSettings.baseUrl,
                authTokenInput = it.savedSettings.authToken,
                errorMessage = null,
            )
        }
    }

    fun closeSettings() {
        _uiState.update { it.copy(screen = Screen.Chat) }
    }

    fun onBaseUrlChanged(value: String) {
        Log.d(logTag, "onBaseUrlChanged length=${value.length}")
        _uiState.update { it.copy(baseUrlInput = value) }
    }

    fun onAuthTokenChanged(value: String) {
        Log.d(logTag, "onAuthTokenChanged length=${value.length}")
        _uiState.update { it.copy(authTokenInput = value) }
    }

    fun saveSettings() {
        val state = _uiState.value
        viewModelScope.launch {
            settingsRepository.saveSettings(
                baseUrl = state.baseUrlInput,
                authToken = state.authTokenInput,
            )
            _uiState.update { it.copy(screen = Screen.Chat, errorMessage = null) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun setListening(isListening: Boolean) {
        _uiState.update {
            it.copy(
                isListening = isListening,
                liveTranscript = if (isListening) it.liveTranscript else "",
                micLevel = if (isListening) it.micLevel else 0f,
                micPeakLevel = if (isListening) it.micPeakLevel else 0f,
                micRawDb = if (isListening) it.micRawDb else 0f,
                isVoiceDetected = if (isListening) it.isVoiceDetected else false,
            )
        }
        if (!isListening) {
            micNoiseFloorDb = Float.NaN
        }
    }

    fun updateLiveTranscript(value: String) {
        _uiState.update { it.copy(liveTranscript = value) }
    }

    fun updateMicLevel(rmsDb: Float) {
        val clamped = rmsDb.coerceIn(-2f, 12f)
        micNoiseFloorDb =
            if (micNoiseFloorDb.isNaN()) clamped else (micNoiseFloorDb * 0.92f) + (clamped * 0.08f)

        val voiceDetected = clamped > micNoiseFloorDb + 1.5f
        val normalized = ((clamped - micNoiseFloorDb + 2f) / 8f).coerceIn(0f, 1f)

        _uiState.update {
            it.copy(
                micRawDb = clamped,
                micLevel = normalized,
                micPeakLevel = max(normalized, it.micPeakLevel * 0.9f),
                isVoiceDetected = voiceDetected,
            )
        }
    }

    fun onVoiceTextReceived(text: String) {
        sendMessage(text)
    }

    fun onDraftChanged(value: String) {
        Log.d(logTag, "onDraftChanged length=${value.length}")
        _uiState.update { it.copy(draftMessage = value) }
    }

    fun sendDraftMessage() {
        val draft = _uiState.value.draftMessage
        sendMessage(draft)
    }

    fun sendMessage(content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return

        val state = _uiState.value
        if (state.isSending || state.isPolling) return

        val settings = state.savedSettings
        if (settings.baseUrl.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = "Set URL in Settings first.")
            }
            return
        }

        val userMessage = ChatMessage(role = MessageRole.USER, text = trimmed)
        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                isSending = true,
                errorMessage = null,
                draftMessage = "",
            )
        }

        viewModelScope.launch {
            val sendResult =
                spacebotRepository.sendMessage(
                    baseUrl = settings.baseUrl,
                    authToken = settings.authToken,
                    conversationId = conversationId,
                    senderId = senderId,
                    content = trimmed,
                )

            if (sendResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isSending = false,
                        errorMessage = sendResult.exceptionOrNull()?.message ?: "Send failed.",
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(isSending = false, isPolling = true) }

            val pollResult =
                spacebotRepository.pollReplies(
                    baseUrl = settings.baseUrl,
                    authToken = settings.authToken,
                    conversationId = conversationId,
                )

            if (pollResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isPolling = false,
                        errorMessage = pollResult.exceptionOrNull()?.message ?: "Polling failed.",
                    )
                }
                return@launch
            }

            val replyMessages = mapSpacebotMessages(pollResult.getOrNull().orEmpty())
            _uiState.update {
                it.copy(
                    messages = if (replyMessages.isEmpty()) it.messages else it.messages + replyMessages,
                    isPolling = false,
                )
            }
        }
    }

    private fun mapSpacebotMessages(items: List<SpacebotMessage>): List<ChatMessage> {
        val mapped = mutableListOf<ChatMessage>()
        val streamingBuffer = StringBuilder()

        fun flushStreamingBuffer() {
            if (streamingBuffer.isNotEmpty()) {
                mapped += ChatMessage(role = MessageRole.BOT, text = streamingBuffer.toString())
                streamingBuffer.clear()
            }
        }

        for (item in items) {
            when (item.type) {
                SpacebotRepository.TYPE_STREAM_CHUNK -> streamingBuffer.append(item.content)
                SpacebotRepository.TYPE_STREAM_END -> flushStreamingBuffer()
                SpacebotRepository.TYPE_TEXT,
                SpacebotRepository.TYPE_FILE,
                -> {
                    flushStreamingBuffer()
                    if (item.content.isNotBlank()) {
                        mapped += ChatMessage(role = MessageRole.BOT, text = item.content)
                    }
                }
            }
        }

        flushStreamingBuffer()
        return mapped
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val spacebotRepository: SpacebotRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(settingsRepository, spacebotRepository) as T
        }
    }
}

data class ChatUiState(
    val screen: Screen = Screen.Chat,
    val messages: List<ChatMessage> = emptyList(),
    val savedSettings: SpacebotSettings = SpacebotSettings(),
    val baseUrlInput: String = "",
    val authTokenInput: String = "",
    val isSending: Boolean = false,
    val isPolling: Boolean = false,
    val isListening: Boolean = false,
    val liveTranscript: String = "",
    val micLevel: Float = 0f,
    val micPeakLevel: Float = 0f,
    val micRawDb: Float = 0f,
    val isVoiceDetected: Boolean = false,
    val draftMessage: String = "",
    val errorMessage: String? = null,
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val text: String,
)

enum class MessageRole {
    USER,
    BOT,
}

enum class Screen {
    Chat,
    Settings,
}
