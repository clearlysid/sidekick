package com.sidekick.watch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.sidekick.watch.viewmodel.ChatUiState
import com.sidekick.watch.viewmodel.MessageRole

@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onOpenTextInput: () -> Unit,
    onSendText: () -> Unit,
    onMicClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDismissError: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    AppScaffold {
        ScreenScaffold(
            scrollState = listState,
            edgeButton = {
                EdgeButton(onClick = onOpenTextInput) {
                    Icon(
                        imageVector = Icons.Filled.Create,
                        contentDescription = "Compose",
                    )
                }
            },
        ) { contentPadding ->
            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Card(
                        onClick = onSettingsClick,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    ) {
                        Text("Settings", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (uiState.messages.isEmpty()) {
                    item {
                        Card(
                            onClick = onMicClick,
                            modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                        ) {
                            Text("No messages yet", style = MaterialTheme.typography.bodySmall)
                            Text("Tap mic or compose", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (uiState.draftMessage.isNotBlank()) {
                    item {
                        Card(
                            onClick = onSendText,
                            modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                        ) {
                            Text("Draft", style = MaterialTheme.typography.labelSmall)
                            Text(uiState.draftMessage, style = MaterialTheme.typography.bodySmall)
                            Text("Tap to send", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                uiState.messages.forEach { message ->
                    item {
                        val isUser = message.role == MessageRole.USER
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                        ) {
                            Text(
                                text = if (isUser) "You" else "Spacebot",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            )
                            Text(message.text, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (uiState.isSending || uiState.isPolling) {
                    item {
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                        ) {
                            Text(
                                text = if (uiState.isSending) "Sending..." else "Waiting for reply...",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                if (uiState.isListening) {
                    item {
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                        ) {
                            Text("Listening...", style = MaterialTheme.typography.bodySmall)
                            if (uiState.liveTranscript.isNotBlank()) {
                                Text(uiState.liveTranscript, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                uiState.errorMessage?.let { error ->
                    item {
                        Card(
                            onClick = onDismissError,
                            modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                        ) {
                            Text("Error", style = MaterialTheme.typography.labelSmall)
                            Text(error, style = MaterialTheme.typography.bodySmall)
                            Text("Tap to dismiss", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
