package com.sidekick.watch.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sidekick.watch.data.AgentBackends
import com.sidekick.watch.data.AgentSettings
import com.sidekick.watch.data.OpenAIMessage
import com.sidekick.watch.data.OpenAIRepository
import com.sidekick.watch.data.ResponseNotifier
import com.sidekick.watch.data.PersistedChatMessage
import com.sidekick.watch.data.PersistedConversationState
import com.sidekick.watch.data.PersistedConversationSummary
import com.sidekick.watch.data.SettingsRepository
import com.sidekick.watch.data.SpacebotMessage
import com.sidekick.watch.data.SpacebotRepository
import java.util.UUID
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val settingsRepository: SettingsRepository,
    private val spacebotRepository: SpacebotRepository,
    private val openAIRepository: OpenAIRepository,
    private val responseNotifier: ResponseNotifier,
) : ViewModel() {
    private val logTag = "SidekickInput"

    private val _uiState =
        MutableStateFlow(
            ChatUiState(
                conversations = emptyList(),
                selectedConversationId = null,
                messagesByConversation = emptyMap(),
                backendConversationIds = emptyMap(),
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
                        modelInput = if (it.modelInput.isEmpty()) settings.model else it.modelInput,
                    )
                }
            }
        }

        viewModelScope.launch {
            val persisted = settingsRepository.loadConversationState() ?: return@launch
            _uiState.update { state ->
                state.copy(
                    conversations = persisted.conversations.map { it.toConversationSummary() },
                    selectedConversationId = persisted.selectedConversationId ?: persisted.conversations.firstOrNull()?.id,
                    messagesByConversation = persisted.messagesByConversation.mapValues { (_, messages) -> messages.mapNotNull { it.toChatMessageOrNull() } },
                    backendConversationIds = persisted.backendConversationIds,
                )
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
        persistConversationState()
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
                backendConversationIds = state.backendConversationIds + (newConversation.id to UUID.randomUUID().toString()),
                errorMessage = null,
            )
        }
        persistConversationState()

        return newConversation.id
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
            val nextModel =
                when {
                    state.modelInput.isBlank() -> nextBackend.defaultModel.orEmpty()
                    state.modelInput.trim() == currentBackend.defaultModel.orEmpty() -> nextBackend.defaultModel.orEmpty()
                    else -> state.modelInput
                }

            state.copy(
                agentFlavorInput = nextBackend.id,
                baseUrlInput = nextBaseUrl,
                modelInput = nextModel,
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

    fun saveModel(model: String) {
        _uiState.update { it.copy(modelInput = model) }
        persistCurrentSettings()
    }

    private fun persistCurrentSettings() {
        val state = _uiState.value
        viewModelScope.launch {
            settingsRepository.saveSettings(
                backendId = state.agentFlavorInput,
                baseUrl = state.baseUrlInput,
                authToken = state.authTokenInput,
                model = state.modelInput,
            )
            _uiState.update { it.copy(errorMessage = null) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
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
        Log.i(logTag, "Using backend=${backend.id} baseUrl=${settings.baseUrl}")
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
                isSending = true,
                errorMessage = null,
            )
        }
        persistConversationState()

        when (backend.id) {
            "openclaw" -> sendViaOpenAI(localConversationId, backendConversationId, settings)
            else -> sendViaSpacebot(localConversationId, backendConversationId, trimmed, settings)
        }
    }

    private fun sendViaSpacebot(
        localConversationId: String,
        backendConversationId: String,
        content: String,
        settings: AgentSettings,
    ) {
        viewModelScope.launch {
            val sendResult =
                spacebotRepository.sendMessage(
                    baseUrl = settings.baseUrl,
                    authToken = settings.authToken,
                    conversationId = backendConversationId,
                    senderId = senderId,
                    content = content,
                )

            if (sendResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isSending = false,
                        errorMessage = formatNetworkError(sendResult.exceptionOrNull(), "send"),
                    )
                }
                persistConversationState()
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
                        errorMessage = formatNetworkError(pollResult.exceptionOrNull(), "poll"),
                    )
                }
                persistConversationState()
                return@launch
            }

            val replyMessages = mapSpacebotMessages(pollResult.getOrNull().orEmpty())
            val latestReplyText = replyMessages.lastOrNull()?.text.orEmpty()
            if (latestReplyText.isNotBlank()) {
                responseNotifier.notifyIfInBackground(latestReplyText)
            }
            _uiState.update {
                val existingMessages = it.messagesByConversation[localConversationId].orEmpty()
                val updatedMessages = if (replyMessages.isEmpty()) existingMessages else existingMessages + replyMessages
                it.copy(
                    messagesByConversation = it.messagesByConversation + (localConversationId to updatedMessages),
                    conversations =
                        if (latestReplyText.isBlank()) it.conversations
                        else {
                            updateConversationMeta(
                                conversations = it.conversations,
                                conversationId = localConversationId,
                                inputText = latestReplyText,
                                allowInitialPromptUpdate = false,
                            )
                        },
                    isPolling = false,
                )
            }
            persistConversationState()
        }
    }

    private fun sendViaOpenAI(localConversationId: String, backendConversationId: String, settings: AgentSettings) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = false, isPolling = true) }

            val history = _uiState.value.messagesByConversation[localConversationId].orEmpty()
            val openAIMessages = history.map { msg ->
                OpenAIMessage(
                    role = when (msg.role) {
                        MessageRole.USER -> "user"
                        MessageRole.BOT -> "assistant"
                    },
                    content = msg.text,
                )
            }

            try {
                val botMessageId = UUID.randomUUID().toString()
                val buffer = StringBuilder()

                openAIRepository.sendMessageStreaming(
                    baseUrl = settings.baseUrl,
                    authToken = settings.authToken,
                    model = settings.model,
                    messages = openAIMessages,
                    user = backendConversationId,
                ).collect { chunk ->
                    buffer.append(chunk)
                    val snapshot = buffer.toString()
                    _uiState.update { state ->
                        val existing = state.messagesByConversation[localConversationId].orEmpty()
                        val withoutStreaming = existing.filter { it.id != botMessageId }
                        val streamingMessage = ChatMessage(id = botMessageId, role = MessageRole.BOT, text = snapshot)
                        state.copy(
                            messagesByConversation = state.messagesByConversation + (localConversationId to (withoutStreaming + streamingMessage)),
                        )
                    }
                }

                val finalText = buffer.toString()
                if (finalText.isNotBlank()) {
                    responseNotifier.notifyIfInBackground(finalText)
                }
                _uiState.update { state ->
                    state.copy(
                        conversations =
                            if (finalText.isBlank()) state.conversations
                            else updateConversationMeta(
                                conversations = state.conversations,
                                conversationId = localConversationId,
                                inputText = finalText,
                                allowInitialPromptUpdate = false,
                            ),
                        isPolling = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isPolling = false,
                        errorMessage = formatNetworkError(e, "send"),
                    )
                }
            }
            persistConversationState()
        }
    }

    private fun persistConversationState() {
        val state = _uiState.value
        viewModelScope.launch {
            settingsRepository.saveConversationState(state.toPersistedConversationState())
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

    private fun formatNetworkError(error: Throwable?, phase: String): String {
        val root = rootCause(error)
        val suffix = if (phase == "send") "while sending" else "while waiting for reply"
        return when (root) {
            is UnknownHostException ->
                "Couldn't resolve server host $suffix. Check the Base URL and network."
            is ConnectException ->
                "Couldn't connect to server $suffix. If this is a private/Tailnet host, make sure the watch can reach it."
            is SocketTimeoutException ->
                "Server timed out $suffix. Check connectivity and try again."
            is SSLException ->
                "TLS/SSL handshake failed $suffix. Check server certificate/HTTPS configuration."
            else ->
                error?.message ?: "Request failed $suffix."
        }
    }

    private fun rootCause(error: Throwable?): Throwable? {
        var current = error
        while (current?.cause != null) {
            current = current.cause
        }
        return current
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val spacebotRepository: SpacebotRepository,
        private val openAIRepository: OpenAIRepository,
        private val responseNotifier: ResponseNotifier,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(settingsRepository, spacebotRepository, openAIRepository, responseNotifier) as T
        }
    }

}

private fun ChatUiState.toPersistedConversationState(): PersistedConversationState =
    PersistedConversationState(
        selectedConversationId = selectedConversationId,
        conversations = conversations.map { it.toPersistedConversationSummary() },
        messagesByConversation =
            messagesByConversation.mapValues { (_, messages) ->
                messages.map { it.toPersistedChatMessage() }
            },
        backendConversationIds = backendConversationIds,
    )

private fun ConversationSummary.toPersistedConversationSummary(): PersistedConversationSummary =
    PersistedConversationSummary(
        id = id,
        initialPrompt = initialPrompt,
        lastUpdatedEpochMs = lastUpdatedEpochMs,
    )

private fun PersistedConversationSummary.toConversationSummary(): ConversationSummary =
    ConversationSummary(
        id = id,
        initialPrompt = initialPrompt,
        lastUpdatedEpochMs = lastUpdatedEpochMs,
    )

private fun ChatMessage.toPersistedChatMessage(): PersistedChatMessage =
    PersistedChatMessage(
        id = id,
        role = role.name,
        text = text,
    )

private fun PersistedChatMessage.toChatMessageOrNull(): ChatMessage? {
    val parsedRole = MessageRole.entries.firstOrNull { it.name == role } ?: return null
    return ChatMessage(
        id = id,
        role = parsedRole,
        text = text,
    )
}

data class ChatUiState(
    val conversations: List<ConversationSummary> = emptyList(),
    val selectedConversationId: String? = null,
    val messagesByConversation: Map<String, List<ChatMessage>> = emptyMap(),
    val backendConversationIds: Map<String, String> = emptyMap(),
    val savedSettings: AgentSettings = AgentSettings(),
    val agentFlavorInput: String = "",
    val baseUrlInput: String = "",
    val authTokenInput: String = "",
    val modelInput: String = "",
    val isSending: Boolean = false,
    val isPolling: Boolean = false,
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
