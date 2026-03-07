package com.sidekick.watch.presentation

import android.content.Intent
import android.os.Bundle
import android.app.RemoteInput
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
import com.sidekick.watch.data.SettingsRepository
import com.sidekick.watch.data.SpacebotRepository
import com.sidekick.watch.presentation.theme.SidekickTheme
import com.sidekick.watch.ui.ChatScreen
import com.sidekick.watch.ui.HomeScreen
import com.sidekick.watch.ui.SettingsScreen
import com.sidekick.watch.viewmodel.ChatViewModel
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {
    private val okHttpClient by lazy { OkHttpClient.Builder().build() }
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }
    private val spacebotRepository by lazy { SpacebotRepository(okHttpClient) }

    private val viewModel: ChatViewModel by viewModels {
        ChatViewModel.Factory(
            settingsRepository = settingsRepository,
            spacebotRepository = spacebotRepository,
        )
    }

    private var requestedHomePage by mutableStateOf(false)
    private var requestedConversationPageId by mutableStateOf<String?>(null)
    private var shouldCreateConversationAfterComposer: Boolean = false

    private val textInputLauncher =
        registerForActivityResult(StartActivityForResult()) { result ->
            val data = result.data ?: return@registerForActivityResult
            val results = RemoteInput.getResultsFromIntent(data) ?: return@registerForActivityResult
            val enteredText = results.getCharSequence(CHAT_TEXT_RESULT_KEY)?.toString().orEmpty().trim()
            if (enteredText.isNotEmpty()) {
                if (shouldCreateConversationAfterComposer) {
                    val targetConversationId = viewModel.startNewConversation()
                    viewModel.openConversation(targetConversationId)
                    requestedConversationPageId = targetConversationId
                    viewModel.sendMessage(enteredText)
                } else {
                    viewModel.sendMessage(enteredText)
                }
            }
            shouldCreateConversationAfterComposer = false
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAssistIntent(intent)

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
                                    onNewConversation = {
                                        shouldCreateConversationAfterComposer = true
                                        launchRemoteTextInput()
                                    },
                                    onOpenConversation = { conversationId ->
                                        viewModel.openConversation(conversationId)
                                        homeNavController.navigate("$HOME_CONVERSATION_ROUTE/$conversationId")
                                    },
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
                            authToken = uiState.authTokenInput,
                            onSaveAgentFlavor = viewModel::saveAgentFlavor,
                            onSaveBaseUrl = viewModel::saveBaseUrl,
                            onSaveAuthToken = viewModel::saveAuthToken,
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

    private fun handleAssistIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_ASSIST) {
            requestedHomePage = true
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

    private companion object {
        const val CHAT_TEXT_RESULT_KEY = "chat_text_input"
        const val HOME_PAGE = 0
        const val PAGE_COUNT = 2
        const val HOME_LIST_ROUTE = "home/list"
        const val HOME_CONVERSATION_ROUTE = "home/conversation"
    }
}
