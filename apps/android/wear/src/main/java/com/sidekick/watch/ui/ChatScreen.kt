package com.sidekick.watch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CircularProgressIndicator
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
    conversationTitle: String,
    onOpenTextInput: () -> Unit,
    onDismissError: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val compactMessagePadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)

    AppScaffold {
        ScreenScaffold(
            scrollState = listState,
            edgeButton = {
                EdgeButton(onClick = onOpenTextInput) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Compose",
                    )
                }
            },
        ) { contentPadding ->
            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = conversationTitle,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                }

                uiState.messages.forEach { message ->
                    item {
                        if (message.role == MessageRole.USER) {
                            Card(
                                onClick = {},
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = compactMessagePadding,
                                transformation = SurfaceTransformation(transformationSpec),
                            ) {
                                Text(
                                    text = message.text,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        } else {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodySmall,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                if (uiState.isSending || uiState.isPolling) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                            )
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
