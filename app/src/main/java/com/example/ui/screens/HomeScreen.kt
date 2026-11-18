package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tools.PermissionStatusSnapshot
import com.example.ui.AssistantRunState
import com.example.ui.OrbPhase
import com.example.ui.RecentInteraction
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OrbCyan
import com.example.ui.theme.OrbPink
import com.example.ui.theme.OrbPurple
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HomeScreen(
    orbPhase: OrbPhase,
    runState: AssistantRunState,
    hasApiKey: Boolean,
    wakeWordEnabled: Boolean,
    deviceStatus: PermissionStatusSnapshot,
    customShortcutLabel: String,
    customShortcutCommand: String,
    recentInteractions: List<RecentInteraction>,
    onToggleWakeWord: (Boolean) -> Unit,
    onMicClick: () -> Unit,
    onSendCommand: (String) -> Unit,
    onSetVolume: (Int) -> Unit,
    onToggleFlashlight: (Boolean) -> Unit,
    onSaveCustomShortcut: (String, String) -> Unit,
    onSeeAllActivity: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showKeyboardInput by rememberSaveable { mutableStateOf(false) }
    var typedCommand by rememberSaveable { mutableStateOf("") }
    var showVolumeDialog by rememberSaveable { mutableStateOf(false) }
    var showShortcutDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .imePadding()
    ) {
        // 1. Minimalist Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "JARVIS",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp
                ),
                color = TextPrimaryDark
            )

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = ObsidianSurface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (wakeWordEnabled) ArcCyan.copy(alpha = 0.5f) else ObsidianOutline
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { onToggleWakeWord(!wakeWordEnabled) }
                    .testTag("wake_word_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (wakeWordEnabled) ArcCyan else TextSecondaryDark)
                    )
                    Text(
                        text = if (wakeWordEnabled) "Wake word on" else "Wake word off",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (wakeWordEnabled) ArcCyan else TextSecondaryDark
                    )
                }
            }
        }

        // Scrollable Body: Orb + Status + Quick Actions + Recent
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "voice_orb_centerpiece") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    JarvisVoiceOrb(
                        phase = orbPhase,
                        modifier = Modifier
                            .size(182.dp)
                            .clickable(onClick = onMicClick)
                            .testTag("jarvis_orb")
                    )

                    val statusTitle = when (orbPhase) {
                        OrbPhase.STANDBY -> "Standby"
                        OrbPhase.LISTENING -> "Listening..."
                        OrbPhase.THINKING -> "Thinking..."
                        OrbPhase.SPEAKING -> "Speaking..."
                    }

                    val latestReply = recentInteractions.firstOrNull()?.response
                    val statusSubtitle = when (orbPhase) {
                        OrbPhase.STANDBY -> when {
                            !latestReply.isNullOrBlank() -> latestReply
                            wakeWordEnabled -> "Say a command or tap the mic."
                            else -> "Tap the mic to talk."
                        }
                        OrbPhase.LISTENING -> "Speak naturally in English, Hindi, or Hinglish."
                        OrbPhase.THINKING -> (runState as? AssistantRunState.Running)?.activeToolLabel
                            ?: "Processing command..."
                        OrbPhase.SPEAKING -> latestReply ?: "Speaking response..."
                    }

                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = TextPrimaryDark,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("orb_status_text")
                    )

                    Text(
                        text = statusSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondaryDark,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )

                    if (!hasApiKey) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable(onClick = onNavigateToSettings)
                                .testTag("missing_api_key_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Add personal Gemini API key in Settings",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }

            // Quick Action Cards Row (4 rounded dark cards #151B24)
            item(key = "quick_actions_row") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionCard(
                        icon = Icons.Filled.OpenInNew,
                        label = "Open",
                        active = false,
                        testTag = "quick_action_open",
                        onClick = { onSendCommand("Read what is on my screen right now") },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCard(
                        icon = Icons.Filled.VolumeUp,
                        label = "Volume",
                        active = false,
                        testTag = "quick_action_volume",
                        onClick = { showVolumeDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCard(
                        icon = if (deviceStatus.flashlightEnabled) {
                            Icons.Filled.FlashlightOn
                        } else {
                            Icons.Filled.FlashlightOff
                        },
                        label = if (deviceStatus.flashlightEnabled) "Torch off" else "Torch on",
                        active = deviceStatus.flashlightEnabled,
                        testTag = "quick_action_torch",
                        onClick = { onToggleFlashlight(!deviceStatus.flashlightEnabled) },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCard(
                        icon = Icons.Filled.Bolt,
                        label = customShortcutLabel,
                        active = false,
                        testTag = "quick_action_custom",
                        onClick = { onSendCommand(customShortcutCommand) },
                        onLongClick = { showShortcutDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Recent Section
            item(key = "recent_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT",
                        style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = TextSecondaryDark
                    )
                    Text(
                        text = "See all",
                        style = MaterialTheme.typography.labelLarge,
                        color = ArcCyan,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onSeeAllActivity)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("recent_see_all")
                    )
                }
            }

            if (recentInteractions.isEmpty()) {
                item(key = "recent_empty") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ObsidianSurface,
                        border = BorderStroke(1.dp, ObsidianOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Nothing yet. What you ask shows up here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondaryDark,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 20.dp)
                        )
                    }
                }
            } else {
                items(recentInteractions.take(2), key = { it.id }) { item ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ObsidianSurface,
                        border = BorderStroke(1.dp, ObsidianOutline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onSeeAllActivity)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.query,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = TextPrimaryDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (item.toolCount > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${item.toolCount} tool",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ArcCyan
                                    )
                                }
                            }
                            Text(
                                text = item.response,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondaryDark,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Optional expandable keyboard input bar
        AnimatedVisibility(visible = showKeyboardInput) {
            Surface(
                color = ObsidianSurface,
                border = BorderStroke(1.dp, ObsidianOutline),
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = typedCommand,
                        onValueChange = { typedCommand = it },
                        placeholder = {
                            Text(
                                "Type command (English, Hindi, Hinglish)...",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (typedCommand.isNotBlank()) {
                                    val cmd = typedCommand
                                    typedCommand = ""
                                    showKeyboardInput = false
                                    onSendCommand(cmd)
                                }
                            }
                        ),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ArcCyan,
                            unfocusedBorderColor = ObsidianOutline
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("command_input")
                    )

                    IconButton(
                        onClick = {
                            if (typedCommand.isNotBlank()) {
                                val cmd = typedCommand
                                typedCommand = ""
                                showKeyboardInput = false
                                onSendCommand(cmd)
                            }
                        },
                        enabled = typedCommand.isNotBlank() && runState is AssistantRunState.Idle,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(ArcCyan)
                            .testTag("send_command_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send command",
                            tint = ObsidianBg
                        )
                    }
                }
            }
        }

        // Bottom Centered Circular Cyan Mic Button + Keyboard Toggle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            // Centered Circular Cyan Mic Button
            Surface(
                shape = CircleShape,
                color = ArcCyan,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMicClick)
                    .testTag("voice_input_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (orbPhase == OrbPhase.SPEAKING) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (orbPhase == OrbPhase.SPEAKING) "Stop speaking" else "Speak to JARVIS",
                        tint = ObsidianBg,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            // Nearby Keyboard Toggle Button on the right
            Surface(
                shape = CircleShape,
                color = ObsidianSurface,
                border = BorderStroke(1.dp, ObsidianOutline),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 36.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { showKeyboardInput = !showKeyboardInput }
                    .testTag("keyboard_toggle_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (showKeyboardInput) Icons.Filled.Close else Icons.Filled.Keyboard,
                        contentDescription = if (showKeyboardInput) "Close keyboard" else "Type a command",
                        tint = if (showKeyboardInput) ArcCyan else TextSecondaryDark,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }

    if (showVolumeDialog) {
        var sliderPercent by remember(deviceStatus.mediaVolumePercent) {
            mutableFloatStateOf(deviceStatus.mediaVolumePercent.toFloat())
        }
        AlertDialog(
            onDismissRequest = { showVolumeDialog = false },
            containerColor = ObsidianSurface,
            title = { Text("Media Volume (${sliderPercent.toInt()}%)", color = TextPrimaryDark) },
            text = {
                Slider(
                    value = sliderPercent,
                    onValueChange = { sliderPercent = it },
                    onValueChangeFinished = { onSetVolume(sliderPercent.toInt()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = ArcCyan,
                        activeTrackColor = ArcCyan
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSetVolume(sliderPercent.toInt())
                        showVolumeDialog = false
                    }
                ) {
                    Text("Done", color = ArcCyan)
                }
            }
        )
    }

    if (showShortcutDialog) {
        var labelInput by rememberSaveable(customShortcutLabel) { mutableStateOf(customShortcutLabel) }
        var commandInput by rememberSaveable(customShortcutCommand) { mutableStateOf(customShortcutCommand) }
        AlertDialog(
            onDismissRequest = { showShortcutDialog = false },
            containerColor = ObsidianSurface,
            title = { Text("Customize Quick Action", color = TextPrimaryDark) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it },
                        label = { Text("Button label (max 14 chars)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = commandInput,
                        onValueChange = { commandInput = it },
                        label = { Text("Command to run") },
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveCustomShortcut(labelInput, commandInput)
                        showShortcutDialog = false
                    }
                ) {
                    Text("Save", color = ArcCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { showShortcutDialog = false }) {
                    Text("Cancel", color = TextSecondaryDark)
                }
            }
        )
    }
}

@Composable
private fun JarvisVoiceOrb(
    phase: OrbPhase,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbAnim")
    val pulseSpeed = when (phase) {
        OrbPhase.STANDBY -> 2800
        OrbPhase.LISTENING -> 950
        OrbPhase.THINKING -> 1100
        OrbPhase.SPEAKING -> 750
    }
    val breath by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = if (phase == OrbPhase.STANDBY) 1.02f else 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseSpeed, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbBreath"
    )
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (phase == OrbPhase.THINKING) 2400 else 7500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbOrbit"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = (size.minDimension / 2f) * 0.72f * breath

        // Soft outer ambient halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    OrbPurple.copy(alpha = if (phase == OrbPhase.STANDBY) 0.30f else 0.45f),
                    OrbCyan.copy(alpha = 0.16f),
                    Color.Transparent
                ),
                center = center,
                radius = baseRadius * 1.48f
            ),
            radius = baseRadius * 1.48f,
            center = center
        )

        // Subtle dynamic ring when listening or thinking
        if (phase != OrbPhase.STANDBY) {
            drawCircle(
                color = if (phase == OrbPhase.LISTENING) OrbCyan.copy(alpha = 0.42f)
                else OrbPink.copy(alpha = 0.36f),
                radius = baseRadius * 1.16f,
                center = center,
                style = Stroke(width = 2.5.dp.toPx())
            )
        }

        val rad = Math.toRadians(orbitAngle.toDouble())
        val highlightOffset = Offset(
            x = center.x - (baseRadius * 0.28f * cos(rad)).toFloat(),
            y = center.y - (baseRadius * 0.28f * sin(rad)).toFloat()
        )

        // Main 3D-style multi-tone gradient sphere (purple, pink, cyan)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    OrbCyan.copy(alpha = 0.95f),
                    OrbPurple.copy(alpha = 0.92f),
                    OrbPink.copy(alpha = 0.85f),
                    Color(0xFF1F1638)
                ),
                center = highlightOffset,
                radius = baseRadius * 1.25f
            ),
            radius = baseRadius,
            center = center
        )

        // Inner specular gloss highlight for 3D depth
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.38f),
                    Color.Transparent
                ),
                center = Offset(center.x - baseRadius * 0.34f, center.y - baseRadius * 0.36f),
                radius = baseRadius * 0.65f
            ),
            radius = baseRadius,
            center = center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickActionCard(
    icon: ImageVector,
    label: String,
    active: Boolean,
    testTag: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (active) ArcCyan.copy(alpha = 0.16f) else ObsidianSurface,
        border = BorderStroke(
            width = 1.dp,
            color = if (active) ArcCyan.copy(alpha = 0.55f) else ObsidianOutline
        ),
        modifier = modifier
            .height(78.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) ArcCyan else TextPrimaryDark,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = if (active) ArcCyan else TextSecondaryDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
