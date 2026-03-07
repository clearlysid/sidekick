package com.sidekick.watch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.EdgeButton
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
    onNewConversationWithKeyboard: () -> Unit,
    onNewConversationWithVoice: () -> Unit,
    onOpenConversation: (String) -> Unit,
    loadMoreIncrement: Int,
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val safeIncrement = loadMoreIncrement.coerceAtLeast(1)
    var visibleConversationCount by rememberSaveable { mutableIntStateOf(safeIncrement) }

    LaunchedEffect(conversations.size, safeIncrement) {
        visibleConversationCount =
            when {
                conversations.isEmpty() -> 0
                visibleConversationCount <= 0 -> minOf(safeIncrement, conversations.size)
                else -> visibleConversationCount.coerceAtMost(conversations.size)
            }
    }

    val canShowMore = visibleConversationCount < conversations.size

    AppScaffold {
        ScreenScaffold(
            scrollState = listState,
            edgeButton = {
                if (canShowMore) {
                    EdgeButton(
                        onClick = {
                            visibleConversationCount =
                                (visibleConversationCount + safeIncrement).coerceAtMost(conversations.size)
                        },
                    ) {
                        Text("Show more", style = MaterialTheme.typography.labelSmall)
                    }
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
                            text = "Hello!",
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                item {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .transformedHeight(this, transformationSpec),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledIconButton(
                            onClick = onNewConversationWithKeyboard,
                            colors =
                                IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Keyboard,
                                contentDescription = "New conversation (keyboard)",
                            )
                        }
                        FilledIconButton(
                            onClick = onNewConversationWithVoice,
                            colors =
                                IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = "New conversation (voice)",
                            )
                        }
                    }
                }

                conversations.take(visibleConversationCount).forEach { conversation ->
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
