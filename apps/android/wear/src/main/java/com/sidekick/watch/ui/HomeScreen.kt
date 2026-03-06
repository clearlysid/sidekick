package com.sidekick.watch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.sidekick.watch.viewmodel.ConversationSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    conversations: List<ConversationSummary>,
    onNewConversation: () -> Unit,
    onOpenConversation: (String) -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    AppScaffold {
        ScreenScaffold(scrollState = listState) { contentPadding ->
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
                            text = "Hello",
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                        contentAlignment = Alignment.Center,
                    ) {
                        FilledIconButton(
                            onClick = onNewConversation,
                            colors =
                                IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "New conversation",
                            )
                        }
                    }
                }

                conversations.take(5).forEach { conversation ->
                    item {
                        Card(
                            onClick = { onOpenConversation(conversation.id) },
                            modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                        ) {
                            Text(
                                text = conversation.initialPrompt?.take(42)?.ifBlank { "New conversation" }
                                    ?: "New conversation",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "Last updated ${formatLastUpdated(conversation.lastUpdatedEpochMs)}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatLastUpdated(epochMs: Long): String {
    val formatter = DateTimeFormatter.ofPattern("h:mm a")
    return formatter.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
}
