package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.AssistantLanguage
import com.example.core.JarvisSystemPrompt
import com.example.core.JarvisToolSpec
import com.example.core.PromptSection
import kotlinx.coroutines.launch

private enum class PromptViewMode { SECTIONS, FULL_PROMPT, TOOL_SCHEMAS }

@Composable
fun PromptBlueprintScreen(
    activeLanguage: AssistantLanguage,
    customAddendum: String,
    snackbarHostState: SnackbarHostState,
    onSaveCustomAddendum: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var viewMode by rememberSaveable { mutableStateOf(PromptViewMode.SECTIONS) }
    var editableAddendum by rememberSaveable(customAddendum) { mutableStateOf(customAddendum) }

    val compiledPrompt = remember(activeLanguage, customAddendum) {
        JarvisSystemPrompt.buildCompiledPrompt(activeLanguage, customAddendum)
    }
    val toolsJsonPretty = remember {
        JarvisSystemPrompt.buildGeminiToolsJson().toString(2)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "blueprint_header") {
            Card(
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "JARVIS System Prompt Blueprint",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Text(
                        text = "Active instruction contract enforced on every Gemini tool-calling turn. Personal Gemini API keys are stored exclusively in the Settings vault and never injected into this prompt.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                copyToClipboard(context, compiledPrompt)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Full JARVIS system prompt copied")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("copy_prompt_button")
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Prompt")
                        }
                        OutlinedButton(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "JARVIS Android System Prompt")
                                    putExtra(Intent.EXTRA_TEXT, compiledPrompt)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Export System Prompt"))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("share_prompt_button")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export")
                        }
                    }
                }
            }
        }

        item(key = "view_mode_tabs") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = viewMode == PromptViewMode.SECTIONS,
                    onClick = { viewMode = PromptViewMode.SECTIONS },
                    label = { Text("Directives") },
                    leadingIcon = { Icon(Icons.Filled.Description, null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("prompt_mode_sections")
                )
                FilterChip(
                    selected = viewMode == PromptViewMode.FULL_PROMPT,
                    onClick = { viewMode = PromptViewMode.FULL_PROMPT },
                    label = { Text("Full Text") },
                    modifier = Modifier.testTag("prompt_mode_full")
                )
                FilterChip(
                    selected = viewMode == PromptViewMode.TOOL_SCHEMAS,
                    onClick = { viewMode = PromptViewMode.TOOL_SCHEMAS },
                    label = { Text("12 Tools JSON") },
                    leadingIcon = { Icon(Icons.Filled.Code, null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("prompt_mode_tools")
                )
            }
        }

        when (viewMode) {
            PromptViewMode.SECTIONS -> {
                items(JarvisSystemPrompt.sections, key = { it.id }) { section ->
                    PromptSectionCard(section)
                }

                item(key = "custom_addendum_card") {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Custom Persona Addendum (Optional)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Appended after the core security, permissions, and privacy directives.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = editableAddendum,
                                onValueChange = { editableAddendum = it },
                                placeholder = { Text("e.g., Address me as Sir and keep responses under 2 sentences.") },
                                minLines = 3,
                                maxLines = 5,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("custom_addendum_input")
                            )
                            Button(
                                onClick = {
                                    onSaveCustomAddendum(editableAddendum)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Custom addendum saved")
                                    }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("save_addendum_button")
                            ) {
                                Icon(Icons.Filled.Save, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Addendum")
                            }
                        }
                    }
                }
            }

            PromptViewMode.FULL_PROMPT -> {
                item(key = "raw_prompt_card") {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = compiledPrompt,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            PromptViewMode.TOOL_SCHEMAS -> {
                items(JarvisSystemPrompt.toolSpecs, key = { it.name }) { spec ->
                    ToolSpecCard(spec)
                }
                item(key = "raw_tools_json") {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Gemini FunctionDeclarations JSON",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                OutlinedButton(
                                    onClick = {
                                        copyToClipboard(context, toolsJsonPretty)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Tool schema JSON copied")
                                        }
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text("Copy JSON")
                                }
                            }
                            Text(
                                text = toolsJsonPretty,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun PromptSectionCard(section: PromptSection) {
    val badgeColor = when (section.badge) {
        "API KEY", "PRIVACY" -> MaterialTheme.colorScheme.secondary
        "PERMISSIONS" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Card(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = section.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = section.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ToolSpecCard(spec: JarvisToolSpec) {
    Card(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = spec.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = spec.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Text(
                text = spec.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (spec.parameters.isNotEmpty()) {
                spec.parameters.forEach { p ->
                    Text(
                        text = "• ${p.name} (${p.type}${if (p.required) ", required" else ""}): ${p.description}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("JARVIS Prompt", text))
}
