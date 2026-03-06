package com.sidekick.watch.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.app.RemoteInput
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.input.RemoteInputIntentHelper
import com.sidekick.watch.data.SettingsRepository
import com.sidekick.watch.data.SpacebotRepository
import com.sidekick.watch.presentation.theme.SidekickTheme
import com.sidekick.watch.ui.ChatScreen
import com.sidekick.watch.ui.SettingsScreen
import com.sidekick.watch.viewmodel.ChatViewModel
import com.sidekick.watch.viewmodel.Screen
import java.util.Locale
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {
    private val logTag = "SidekickSpeech"

    private val okHttpClient by lazy { OkHttpClient.Builder().build() }
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }
    private val spacebotRepository by lazy { SpacebotRepository(okHttpClient) }

    private val viewModel: ChatViewModel by viewModels {
        ChatViewModel.Factory(
            settingsRepository = settingsRepository,
            spacebotRepository = spacebotRepository,
        )
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var pendingSpeechResult: ((String) -> Unit)? = null
    private var isSpeechSessionActive = false
    private var hasRetriedAfterClientError = false

    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startSpeechCapture()
            } else {
                pendingSpeechResult = null
                viewModel.setListening(false)
                viewModel.showError("Microphone permission denied.")
            }
        }

    private val textInputLauncher =
        registerForActivityResult(StartActivityForResult()) { result ->
            val data = result.data ?: return@registerForActivityResult
            val results = RemoteInput.getResultsFromIntent(data) ?: return@registerForActivityResult
            val enteredText = results.getCharSequence(CHAT_TEXT_RESULT_KEY)?.toString().orEmpty().trim()
            if (enteredText.isNotEmpty()) {
                viewModel.onDraftChanged(enteredText)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSpeechRecognizer()
        handleAssistIntent(intent)

        setContent {
            val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

            SidekickTheme {
                when (uiState.screen) {
                    Screen.Chat -> {
                        ChatScreen(
                            uiState = uiState,
                            onOpenTextInput = ::launchRemoteTextInput,
                            onSendText = viewModel::sendDraftMessage,
                            onMicClick = {
                                pendingSpeechResult = viewModel::onVoiceTextReceived
                                startSpeechCapture()
                            },
                            onSettingsClick = viewModel::openSettings,
                            onDismissError = viewModel::clearError,
                        )
                    }

                    Screen.Settings -> {
                        SettingsScreen(
                            baseUrl = uiState.baseUrlInput,
                            authToken = uiState.authTokenInput,
                            onBaseUrlChange = viewModel::onBaseUrlChanged,
                            onAuthTokenChange = viewModel::onAuthTokenChanged,
                            onSave = viewModel::saveSettings,
                            onBack = viewModel::closeSettings,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAssistIntent(intent)
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer?.destroy()
        speechRecognizer = null

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            viewModel.setListening(false)
            viewModel.showError("Speech recognition is unavailable on this watch.")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(logTag, "onReadyForSpeech")
                        viewModel.setListening(true)
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(logTag, "onBeginningOfSpeech")
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        viewModel.updateMicLevel(rmsdB)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        Log.d(logTag, "onEndOfSpeech")
                        isSpeechSessionActive = false
                        viewModel.setListening(false)
                        viewModel.updateMicLevel(0f)
                    }

                    override fun onError(error: Int) {
                        Log.w(logTag, "onError=$error")
                        isSpeechSessionActive = false
                        viewModel.setListening(false)
                        viewModel.updateMicLevel(0f)

                        if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                            val partialFallback = viewModel.uiState.value.liveTranscript.trim()
                            if (partialFallback.isNotEmpty()) {
                                pendingSpeechResult?.invoke(partialFallback)
                                pendingSpeechResult = null
                                viewModel.updateLiveTranscript("")
                                hasRetriedAfterClientError = false
                                return
                            }
                        }

                        pendingSpeechResult = null
                        viewModel.updateLiveTranscript("")
                        if (error == SpeechRecognizer.ERROR_CLIENT && !hasRetriedAfterClientError) {
                            hasRetriedAfterClientError = true
                            setupSpeechRecognizer()
                            viewModel.showError("Speech client error. Retrying recognizer...")
                            return
                        }
                        viewModel.showError("${errorMessageFor(error)} (code $error)")
                    }

                    override fun onResults(results: Bundle?) {
                        Log.d(logTag, "onResults")
                        val result =
                            results
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                .orEmpty()
                                .trim()
                        isSpeechSessionActive = false
                        hasRetriedAfterClientError = false
                        viewModel.updateMicLevel(0f)
                        viewModel.updateLiveTranscript(result)
                        if (result.isNotBlank()) {
                            pendingSpeechResult?.invoke(result)
                        } else {
                            viewModel.showError("Didn't catch that. Try again.")
                        }
                        pendingSpeechResult = null
                        viewModel.setListening(false)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial =
                            partialResults
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                .orEmpty()
                                .trim()
                        Log.d(logTag, "onPartialResults length=${partial.length}")
                        if (partial.isNotEmpty()) {
                            viewModel.updateLiveTranscript(partial)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                },
            )
        }
    }

    private fun startSpeechCapture() {
        if (speechRecognizer == null) {
            pendingSpeechResult = null
            viewModel.setListening(false)
            viewModel.showError("Speech recognizer is not ready.")
            return
        }

        if (isSpeechSessionActive) {
            viewModel.showError("Voice input is already running.")
            return
        }

        if (!hasAudioPermission()) {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        val recognizerIntent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2500L)
            }

        runCatching {
            viewModel.setListening(true)
            viewModel.updateLiveTranscript("")
            viewModel.updateMicLevel(0f)
            isSpeechSessionActive = true
            speechRecognizer?.startListening(recognizerIntent)
        }.onFailure {
            Log.e(logTag, "startListening failed", it)
            pendingSpeechResult = null
            isSpeechSessionActive = false
            viewModel.setListening(false)
            viewModel.updateLiveTranscript("")
            viewModel.updateMicLevel(0f)
            viewModel.showError("Unable to start voice input.")
        }
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    private fun handleAssistIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_ASSIST) {
            viewModel.closeSettings()
        }
    }

    private fun launchRemoteTextInput() {
        val remoteInput = RemoteInput.Builder(CHAT_TEXT_RESULT_KEY).setLabel("Type message").build()
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent().apply {
            RemoteInputIntentHelper.putRemoteInputsExtra(this, listOf(remoteInput))
            RemoteInputIntentHelper.putCancelLabelExtra(this, "Cancel")
            RemoteInputIntentHelper.putConfirmLabelExtra(this, "Done")
        }
        textInputLauncher.launch(intent)
    }

    private fun errorMessageFor(error: Int): String =
        when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio error while listening."
            SpeechRecognizer.ERROR_CLIENT -> "Speech client error. Try again."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
            SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network timeout."
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Try again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy."
            SpeechRecognizer.ERROR_SERVER -> "Speech service error."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out."
            else -> "Voice input failed."
        }

    private companion object {
        const val CHAT_TEXT_RESULT_KEY = "chat_text_input"
    }
}
