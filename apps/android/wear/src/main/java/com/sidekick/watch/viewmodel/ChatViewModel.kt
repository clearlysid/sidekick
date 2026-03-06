package com.sidekick.watch.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sidekick.watch.data.AgentBackends
import com.sidekick.watch.data.AgentSettings
import com.sidekick.watch.data.SettingsRepository
import com.sidekick.watch.data.SpacebotMessage
import com.sidekick.watch.data.SpacebotRepository
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

    private val _uiState =
        MutableStateFlow(
            ChatUiState(
                conversations = INITIAL_CONVERSATIONS,
                selectedConversationId = INITIAL_CONVERSATIONS.firstOrNull()?.id,
                messagesByConversation = INITIAL_CONVERSATIONS.associate { it.id to emptyList() },
                backendConversationIds = INITIAL_CONVERSATIONS.associate { it.id to UUID.randomUUID().toString() },
            ),
        )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val senderId = "wear-user"

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _uiState.update {
                    it.copy(
                        savedSettings = settings,
                        agentFlavorInput = if (it.agentFlavorInput.isEmpty()) settings.backendId else it.agentFlavorInput,
                        baseUrlInput = if (it.baseUrlInput.isEmpty()) settings.baseUrl else it.baseUrlInput,
                        authTokenInput = if (it.authTokenInput.isEmpty()) settings.authToken else it.authTokenInput,
                    )
                }
            }
        }
    }

    fun openConversation(conversationId: String) {
        _uiState.update { state ->
            if (state.conversations.none { it.id == conversationId }) {
                state
            } else {
                state.copy(
                    selectedConversationId = conversationId,
                    errorMessage = null,
                )
            }
        }
    }

    fun startNewConversation(): String {
        val newConversation =
            ConversationSummary(
                id = "conversation-${UUID.randomUUID().toString().take(8)}",
                initialPrompt = null,
                lastUpdatedEpochMs = System.currentTimeMillis(),
            )

        _uiState.update { state ->
            state.copy(
                conversations = listOf(newConversation) + state.conversations,
                selectedConversationId = newConversation.id,
                messagesByConversation = state.messagesByConversation + (newConversation.id to emptyList()),
                draftByConversation = state.draftByConversation + (newConversation.id to ""),
                backendConversationIds = state.backendConversationIds + (newConversation.id to UUID.randomUUID().toString()),
                errorMessage = null,
            )
        }

        return newConversation.id
    }

    fun onBaseUrlChanged(value: String) {
        Log.d(logTag, "onBaseUrlChanged length=${value.length}")
        _uiState.update { it.copy(baseUrlInput = value) }
    }

    fun onAuthTokenChanged(value: String) {
        Log.d(logTag, "onAuthTokenChanged length=${value.length}")
        _uiState.update { it.copy(authTokenInput = value) }
    }

    fun cycleAgentFlavor() {
        _uiState.update { state ->
            val all = AgentBackends.supported
            val currentIndex = all.indexOfFirst { it.id == state.agentFlavorInput }.coerceAtLeast(0)
            val next = all[(currentIndex + 1) % all.size]
            val current = AgentBackends.fromId(state.agentFlavorInput)
            val normalizedInputBaseUrl = state.baseUrlInput.trim().trimEnd('/')
            val nextBaseUrl =
                when {
                    state.baseUrlInput.isBlank() -> next.defaultBaseUrl
                    normalizedInputBaseUrl == current.defaultBaseUrl -> next.defaultBaseUrl
                    else -> state.baseUrlInput
                }

            state.copy(
                agentFlavorInput = next.id,
                baseUrlInput = nextBaseUrl,
            )
        }
    }

    fun saveAgentFlavor(backendId: String) {
        _uiState.update { state ->
            val currentBackend = AgentBackends.fromId(state.agentFlavorInput)
            val nextBackend = AgentBackends.fromId(backendId)
            val normalizedBaseUrl = state.baseUrlInput.trim().trimEnd('/')
            val nextBaseUrl =
                when {
                    state.baseUrlInput.isBlank() -> nextBackend.defaultBaseUrl
                    normalizedBaseUrl == currentBackend.defaultBaseUrl -> nextBackend.defaultBaseUrl
                    else -> state.baseUrlInput
                }

            state.copy(
                agentFlavorInput = nextBackend.id,
                baseUrlInput = nextBaseUrl,
            )
        }
        persistCurrentSettings()
    }

    fun saveBaseUrl(baseUrl: String) {
        _uiState.update { it.copy(baseUrlInput = baseUrl) }
        persistCurrentSettings()
    }

    fun saveAuthToken(authToken: String) {
        _uiState.update { it.copy(authTokenInput = authToken) }
        persistCurrentSettings()
    }

    fun saveSettings() {
        persistCurrentSettings()
    }

    private fun persistCurrentSettings() {
        val state = _uiState.value
        viewModelScope.launch {
            settingsRepository.saveSettings(
                backendId = state.agentFlavorInput,
                baseUrl = state.baseUrlInput,
                authToken = state.authTokenInput,
            )
            _uiState.update { it.copy(errorMessage = null) }
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
        _uiState.update { state ->
            val conversationId = state.selectedConversationId ?: return@update state
            state.copy(draftByConversation = state.draftByConversation + (conversationId to value))
        }
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

        val localConversationId = state.selectedConversationId
        if (localConversationId == null) {
            _uiState.update { it.copy(errorMessage = "Choose a conversation first.") }
            return
        }

        val settings = state.savedSettings
        val backend = AgentBackends.fromId(settings.backendId)
        if (settings.baseUrl.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = "Set ${backend.displayName} URL in Settings first.")
            }
            return
        }

        val backendConversationId = state.backendConversationIds[localConversationId] ?: UUID.randomUUID().toString()
        if (!state.backendConversationIds.containsKey(localConversationId)) {
            _uiState.update {
                it.copy(backendConversationIds = it.backendConversationIds + (localConversationId to backendConversationId))
            }
        }

        val userMessage = ChatMessage(role = MessageRole.USER, text = trimmed)
        _uiState.update {
            val existingMessages = it.messagesByConversation[localConversationId].orEmpty()
            it.copy(
                messagesByConversation = it.messagesByConversation + (localConversationId to (existingMessages + userMessage)),
                conversations =
                    updateConversationMeta(
                        conversations = it.conversations,
                        conversationId = localConversationId,
                        inputText = trimmed,
                        allowInitialPromptUpdate = true,
                    ),
                draftByConversation = it.draftByConversation + (localConversationId to ""),
                isSending = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            val sendResult =
                spacebotRepository.sendMessage(
                    baseUrl = settings.baseUrl,
                    authToken = settings.authToken,
                    conversationId = backendConversationId,
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
                    conversationId = backendConversationId,
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
                val existingMessages = it.messagesByConversation[localConversationId].orEmpty()
                val updatedMessages = if (replyMessages.isEmpty()) existingMessages else existingMessages + replyMessages
                val latestReply = replyMessages.lastOrNull()?.text.orEmpty()
                it.copy(
                    messagesByConversation = it.messagesByConversation + (localConversationId to updatedMessages),
                    conversations =
                        if (latestReply.isBlank()) it.conversations
                        else {
                            updateConversationMeta(
                                conversations = it.conversations,
                                conversationId = localConversationId,
                                inputText = latestReply,
                                allowInitialPromptUpdate = false,
                            )
                        },
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

    private fun updateConversationMeta(
        conversations: List<ConversationSummary>,
        conversationId: String,
        inputText: String,
        allowInitialPromptUpdate: Boolean,
    ): List<ConversationSummary> {
        val normalized = inputText.trim().replace("\n", " ")
        val now = System.currentTimeMillis()
        return conversations.map { conversation ->
            if (conversation.id != conversationId) {
                conversation
            } else {
                val nextPrompt =
                    if (allowInitialPromptUpdate && conversation.initialPrompt.isNullOrBlank()) normalized
                    else conversation.initialPrompt
                conversation.copy(
                    initialPrompt = nextPrompt,
                    lastUpdatedEpochMs = now,
                )
            }
        }
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

    private companion object {
        val INITIAL_PROMPTS =
            listOf(
                "Summarize yesterday's standup action items",
                "Draft a quick release note for v1.2",
                "What did we decide about offline mode?",
                "Create a test plan for speech recognition",
                "List follow-ups from the design review",
            )

        val INITIAL_CONVERSATIONS: List<ConversationSummary> =
            INITIAL_PROMPTS.mapIndexed { index, prompt ->
                ConversationSummary(
                    id = "conversation-${index + 1}",
                    initialPrompt = prompt,
                    lastUpdatedEpochMs = System.currentTimeMillis() - (index * 60_000L),
                )
            }
    }
}

data class ChatUiState(
    val conversations: List<ConversationSummary> = emptyList(),
    val selectedConversationId: String? = null,
    val messagesByConversation: Map<String, List<ChatMessage>> = emptyMap(),
    val draftByConversation: Map<String, String> = emptyMap(),
    val backendConversationIds: Map<String, String> = emptyMap(),
    val savedSettings: AgentSettings = AgentSettings(),
    val agentFlavorInput: String = "",
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
    val errorMessage: String? = null,
) {
    val selectedAgentFlavorName: String
        get() = AgentBackends.fromId(agentFlavorInput).displayName

    val activeAgentName: String
        get() = AgentBackends.fromId(savedSettings.backendId).displayName

    val currentConversation: ConversationSummary?
        get() = conversations.firstOrNull { it.id == selectedConversationId }

    val currentConversationTitle: String
        get() = currentConversation?.initialPrompt?.take(40)?.ifBlank { "New conversation" } ?: "Conversation"

    val messages: List<ChatMessage>
        get() = selectedConversationId?.let { messagesByConversation[it].orEmpty() }.orEmpty()

    val draftMessage: String
        get() = selectedConversationId?.let { draftByConversation[it].orEmpty() }.orEmpty()
}

data class ConversationSummary(
    val id: String,
    val initialPrompt: String?,
    val lastUpdatedEpochMs: Long,
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
