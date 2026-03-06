package com.sidekick.watch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

@Composable
fun SettingsScreen(
    agentFlavor: String,
    baseUrl: String,
    authToken: String,
    onAgentFlavorClick: () -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onAuthTokenChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    AppScaffold {
        val listState = rememberTransformingLazyColumnState()
        val transformationSpec = rememberTransformationSpec()

        ScreenScaffold(
            scrollState = listState,
            edgeButton = {
                EdgeButton(onClick = onSave) {
                    androidx.wear.compose.material3.Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Save",
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
                    Card(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    ) {
                        Text("Back", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                item {
                    Card(
                        onClick = onAgentFlavorClick,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    ) {
                        Text("Agent Flavour", style = MaterialTheme.typography.labelSmall)
                        Text(agentFlavor, style = MaterialTheme.typography.bodyMedium)
                        Text("Tap to switch", style = MaterialTheme.typography.labelSmall)
                    }
                }

                item {
                    Card(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    ) {
                        Text("Base URL", style = MaterialTheme.typography.labelSmall)
                        InputField(
                            value = baseUrl,
                            onValueChange = onBaseUrlChange,
                            keyboardType = KeyboardType.Uri,
                            placeholder = "https://...",
                        )
                    }
                }

                item {
                    Card(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    ) {
                        Text("Auth Token", style = MaterialTheme.typography.labelSmall)
                        InputField(
                            value = authToken,
                            onValueChange = onAuthTokenChange,
                            keyboardType = KeyboardType.Text,
                            placeholder = "token",
                        )
                    }
                }

                item {
                    Card(
                        onClick = onSave,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    ) {
                        Text("Save Changes", style = MaterialTheme.typography.bodyMedium)
                        Text("or press the edge button", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    placeholder: String,
) {
    val shape = RoundedCornerShape(14.dp)
    val colors = MaterialTheme.colorScheme

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceContainer, shape)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                innerTextField()
                if (value.isBlank()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        },
    )
}
