package com.sidekick.watch.presentation

import android.content.Intent
import android.os.Bundle
import android.app.RemoteInput
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.input.RemoteInputIntentHelper
import com.sidekick.watch.data.OpenAIRepository
import com.sidekick.watch.data.SettingsRepository
import com.sidekick.watch.data.SpacebotRepository
import com.sidekick.watch.presentation.theme.SidekickTheme
import com.sidekick.watch.ui.ChatScreen
import com.sidekick.watch.ui.HomeScreen
import com.sidekick.watch.ui.SettingsScreen
import com.sidekick.watch.viewmodel.ChatViewModel
import java.util.Locale
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }
    private val spacebotRepository by lazy { SpacebotRepository(okHttpClient) }
    private val openAIRepository by lazy { OpenAIRepository(okHttpClient) }

    private val viewModel: ChatViewModel by viewModels {
        ChatViewModel.Factory(
            settingsRepository = settingsRepository,
            spacebotRepository = spacebotRepository,
            openAIRepository = openAIRepository,
        )
    }

    private var requestedHomePage by mutableStateOf(false)
    private var requestedConversationPageId by mutableStateOf<String?>(null)
    private var requestedAssistantVoiceLaunch by mutableStateOf(false)
    private var shouldCreateConversationAfterComposer: Boolean = false

    private val textInputLauncher =
        registerForActivityResult(StartActivityForResult()) { result ->
            val data = result.data ?: return@registerForActivityResult
            val results = RemoteInput.getResultsFromIntent(data) ?: return@registerForActivityResult
            val enteredText = results.getCharSequence(CHAT_TEXT_RESULT_KEY)?.toString().orEmpty().trim()
            if (enteredText.isNotEmpty()) {
                if (shouldCreateConversationAfterComposer) {
                    startFreshConversationFromInput(enteredText)
                } else {
                    viewModel.sendMessage(enteredText)
                }
            }
            shouldCreateConversationAfterComposer = false
        }

    private val voiceInputLauncher =
        registerForActivityResult(StartActivityForResult()) { result ->
            val spokenText =
                result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    .orEmpty()
                    .trim()
            if (spokenText.isNotEmpty()) {
                startFreshConversationFromInput(spokenText)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleLaunchIntent(intent)

        setContent {
            val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
            val pagerState = rememberPagerState(initialPage = HOME_PAGE, pageCount = { PAGE_COUNT })
            val homeNavController = rememberNavController()

            LaunchedEffect(requestedHomePage) {
                if (requestedHomePage) {
                    pagerState.animateScrollToPage(HOME_PAGE)
                    homeNavController.navigate(HOME_LIST_ROUTE) {
                        popUpTo(HOME_LIST_ROUTE) { inclusive = false }
                        launchSingleTop = true
                    }
                    requestedHomePage = false
                }
            }

            LaunchedEffect(requestedAssistantVoiceLaunch) {
                if (requestedAssistantVoiceLaunch) {
                    pagerState.animateScrollToPage(HOME_PAGE)
                    homeNavController.navigate(HOME_LIST_ROUTE) {
                        popUpTo(HOME_LIST_ROUTE) { inclusive = false }
                        launchSingleTop = true
                    }
                    launchVoiceInput()
                    requestedAssistantVoiceLaunch = false
                }
            }

            LaunchedEffect(requestedConversationPageId) {
                val conversationId = requestedConversationPageId ?: return@LaunchedEffect
                pagerState.animateScrollToPage(HOME_PAGE)
                homeNavController.navigate("$HOME_CONVERSATION_ROUTE/$conversationId") {
                    launchSingleTop = true
                }
                requestedConversationPageId = null
            }

            SidekickTheme {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    if (page == HOME_PAGE) {
                        NavHost(
                            navController = homeNavController,
                            startDestination = HOME_LIST_ROUTE,
                        ) {
                            composable(HOME_LIST_ROUTE) {
                                HomeScreen(
                                    conversations = uiState.conversations,
                                    onNewConversationWithKeyboard = {
                                        shouldCreateConversationAfterComposer = true
                                        launchRemoteTextInput()
                                    },
                                    onNewConversationWithVoice = ::launchVoiceInput,
                                    onOpenConversation = { conversationId ->
                                        viewModel.openConversation(conversationId)
                                        homeNavController.navigate("$HOME_CONVERSATION_ROUTE/$conversationId")
                                    },
                                    loadMoreIncrement = HOME_CONVERSATIONS_PAGE_INCREMENT,
                                )
                            }
                            composable(
                                route = "$HOME_CONVERSATION_ROUTE/{conversationId}",
                                arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
                            ) { backStackEntry ->
                                val conversationId = backStackEntry.arguments?.getString("conversationId").orEmpty()
                                LaunchedEffect(conversationId) {
                                    if (conversationId.isNotBlank()) {
                                        viewModel.openConversation(conversationId)
                                    }
                                }
                                ChatScreen(
                                    uiState = uiState,
                                    conversationTitle = uiState.currentConversationTitle,
                                    onOpenTextInput = ::launchRemoteTextInput,
                                    onDismissError = viewModel::clearError,
                                )
                            }
                        }
                    } else {
                        SettingsScreen(
                            selectedAgentFlavorId = uiState.agentFlavorInput,
                            selectedAgentFlavorName = uiState.selectedAgentFlavorName,
                            baseUrl = uiState.baseUrlInput,
                            model = uiState.modelInput,
                            authToken = uiState.authTokenInput,
                            onSaveAgentFlavor = viewModel::saveAgentFlavor,
                            onSaveBaseUrl = viewModel::saveBaseUrl,
                            onSaveModel = viewModel::saveModel,
                            onSaveAuthToken = viewModel::saveAuthToken,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_ASSIST) {
            requestedHomePage = true
            requestedAssistantVoiceLaunch = true
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

    private fun launchVoiceInput() {
        val voiceIntent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak message")
            }
        if (voiceIntent.resolveActivity(packageManager) != null) {
            voiceInputLauncher.launch(voiceIntent)
        } else {
            shouldCreateConversationAfterComposer = true
            launchRemoteTextInput()
        }
    }

    private fun startFreshConversationFromInput(inputText: String) {
        val targetConversationId = viewModel.startNewConversation()
        viewModel.openConversation(targetConversationId)
        requestedConversationPageId = targetConversationId
        viewModel.sendMessage(inputText)
    }

    private companion object {
        const val CHAT_TEXT_RESULT_KEY = "chat_text_input"
        const val HOME_PAGE = 0
        const val PAGE_COUNT = 2
        const val HOME_LIST_ROUTE = "home/list"
        const val HOME_CONVERSATION_ROUTE = "home/conversation"
        const val HOME_CONVERSATIONS_PAGE_INCREMENT = 5
    }
}
