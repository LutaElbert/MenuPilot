package com.menupilot.restaurant.feature.intake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.menupilot.restaurant.app.ConversationRole
import com.menupilot.restaurant.app.MenuPilotUiState
import com.menupilot.restaurant.assistant.AssistantFallbackReason
import com.menupilot.restaurant.assistant.AssistantSource
import com.menupilot.restaurant.design.IntentChip
import com.menupilot.restaurant.design.PrimaryAction
import com.menupilot.restaurant.feature.ConciergeDestination
import com.menupilot.restaurant.feature.ConciergeSideRail
import com.menupilot.restaurant.theme.MenuPilotRadii
import com.menupilot.restaurant.voice.VoiceInputFailure
import com.menupilot.restaurant.voice.VoiceInputState
import com.menupilot.restaurant.voice.VoiceUnavailableReason

@Composable
fun IntakeScreen(
    state: MenuPilotUiState,
    voiceState: VoiceInputState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmitQuery: () -> Unit,
    onRemoveChip: (String) -> Unit,
    onConfirmIntent: () -> Unit,
    onNewSession: () -> Unit,
    onCallStaff: () -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    modifier: Modifier = Modifier,
    locationLabel: String = "Table 12",
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val showRail = maxWidth >= 900.dp
        if (showRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                ConciergeSideRail(
                    activeDestination = ConciergeDestination.HOME,
                    onHome = onBack,
                    onMyPicks = null,
                    onCallWaiter = onCallStaff,
                    bottomActionLabel = when {
                        state.isAssistantThinking -> "Understanding…"
                        state.query.isNotBlank() -> "Ask MenuPilot"
                        state.conversationNeedsClarification -> "Answer the question"
                        state.canConfirmIntent -> "Find matches"
                        else -> "Ask MenuPilot"
                    },
                    onBottomAction = {
                        if (state.query.isNotBlank()) {
                            onSubmitQuery()
                        } else if (state.canConfirmIntent) {
                            onConfirmIntent()
                        }
                    },
                    locationLabel = locationLabel,
                )
                IntakeConversationScaffold(
                    state = state,
                    voiceState = voiceState,
                    showCompactHeader = false,
                    locationLabel = locationLabel,
                    onBack = onBack,
                    onQueryChange = onQueryChange,
                    onSubmitQuery = onSubmitQuery,
                    onRemoveChip = onRemoveChip,
                    onConfirmIntent = onConfirmIntent,
                    onNewSession = onNewSession,
                    onCallStaff = onCallStaff,
                    onStartVoice = onStartVoice,
                    onStopVoice = onStopVoice,
                    onCancelVoice = onCancelVoice,
                    onOpenVoiceSettings = onOpenVoiceSettings,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            IntakeConversationScaffold(
                state = state,
                voiceState = voiceState,
                showCompactHeader = true,
                locationLabel = locationLabel,
                onBack = onBack,
                onQueryChange = onQueryChange,
                onSubmitQuery = onSubmitQuery,
                onRemoveChip = onRemoveChip,
                onConfirmIntent = onConfirmIntent,
                onNewSession = onNewSession,
                onCallStaff = onCallStaff,
                onStartVoice = onStartVoice,
                onStopVoice = onStopVoice,
                onCancelVoice = onCancelVoice,
                onOpenVoiceSettings = onOpenVoiceSettings,
            )
        }
    }
}

@Composable
private fun IntakeConversationScaffold(
    state: MenuPilotUiState,
    voiceState: VoiceInputState,
    showCompactHeader: Boolean,
    locationLabel: String,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmitQuery: () -> Unit,
    onRemoveChip: (String) -> Unit,
    onConfirmIntent: () -> Unit,
    onNewSession: () -> Unit,
    onCallStaff: () -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val conversationScrollState = rememberScrollState()
    LaunchedEffect(state.conversationTurns.size, state.isAssistantThinking) {
        conversationScrollState.animateScrollTo(conversationScrollState.maxValue)
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (showCompactHeader) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) { Text("‹ Back") }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MenuPilot",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = locationLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onNewSession) { Text("New") }
                    TextButton(onClick = onCallStaff) { Text("Waiter") }
                }
            }
        },
        bottomBar = {
            IntakeComposer(
                query = state.query,
                hasIntent = state.intent != null,
                isAssistantThinking = state.isAssistantThinking,
                voiceState = voiceState,
                onQueryChange = onQueryChange,
                onSubmitQuery = onSubmitQuery,
                onStartVoice = onStartVoice,
                onStopVoice = onStopVoice,
                onCancelVoice = onCancelVoice,
                onOpenVoiceSettings = onOpenVoiceSettings,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 780.dp)
                    .fillMaxSize()
                    .verticalScroll(conversationScrollState)
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(MenuPilotRadii.pill),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = "On-device menu guide  •  " +
                                "${state.intent?.detectedLanguage ?: "Multilingual"}  •  " +
                                state.assistantRuntimeLabel,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    if (!showCompactHeader) {
                        TextButton(onClick = onNewSession) { Text("New session") }
                    }
                }

                state.conversationTurns.forEach { turn ->
                    when (turn.role) {
                        ConversationRole.ASSISTANT -> AssistantTurn(message = turn.message)
                        ConversationRole.GUEST -> GuestTurn(message = turn.message)
                    }
                }
                if (state.isAssistantThinking) {
                    AssistantTurn(
                        message =
                            "I’m interpreting that locally. The restaurant’s verified menu rules " +
                                "will still decide which dishes can appear.",
                    )
                }
                if (state.conversationTurns.none { it.role == ConversationRole.GUEST }) {
                    QuickConversationChips(
                        labels = listOf("Best seller", "Something light", "Something spicy"),
                        onSelect = { onQueryChange(it.toConversationQuery()) },
                    )
                } else if (state.conversationNeedsClarification) {
                    QuickConversationChips(
                        labels = listOf("Peanut allergy", "Vegetarian", "Gluten-free"),
                        onSelect = { label ->
                            onQueryChange(
                                state.query.appendPreference(label.toConversationQuery()),
                            )
                        },
                    )
                }

                state.intent?.let {
                    ConversationIntentReview(
                        state = state,
                        onRemoveChip = onRemoveChip,
                        onConfirmIntent = onConfirmIntent,
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(MenuPilotRadii.large),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = "Nothing is ordered here. Your waiter confirms availability, " +
                            "today’s ingredients, preparation, and cross-contact risks " +
                            "with the kitchen.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantTurn(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            shape = RoundedCornerShape(MenuPilotRadii.pill),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = "MP",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(
                topStart = 6.dp,
                topEnd = 22.dp,
                bottomEnd = 22.dp,
                bottomStart = 22.dp,
            ),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 3.dp,
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(18.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun GuestTurn(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.82f),
            shape = RoundedCornerShape(
                topStart = 22.dp,
                topEnd = 6.dp,
                bottomEnd = 22.dp,
                bottomStart = 22.dp,
            ),
            color = MaterialTheme.colorScheme.primary,
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(18.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun QuickConversationChips(
    labels: List<String>,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.padding(start = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEach { label ->
            AssistChip(
                onClick = { onSelect(label) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun ConversationIntentReview(
    state: MenuPilotUiState,
    onRemoveChip: (String) -> Unit,
    onConfirmIntent: () -> Unit,
) {
    val intent = state.intent ?: return
    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = 48.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Confirm what I understood",
                style = MaterialTheme.typography.headlineSmall,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                intent.chips.forEach { chip ->
                    IntentChip(
                        label = "×  ${chip.label}",
                        selected = true,
                        onClick = { onRemoveChip(chip.key) },
                    )
                }
            }
            if (intent.requiresStaffVerification) {
                Surface(
                    shape = RoundedCornerShape(MenuPilotRadii.medium),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = "Allergies remain hard constraints, but menu data cannot confirm " +
                            "today’s kitchen conditions. Please ask the waiter.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            PrimaryAction(
                label = if (state.conversationNeedsClarification) {
                    "Answer the question above"
                } else {
                    "Show my top 3 matches"
                },
                onClick = onConfirmIntent,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canConfirmIntent,
            )
        }
    }
}

@Composable
private fun IntakeComposer(
    query: String,
    hasIntent: Boolean,
    isAssistantThinking: Boolean,
    voiceState: VoiceInputState,
    onQueryChange: (String) -> Unit,
    onSubmitQuery: () -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 780.dp),
                shape = RoundedCornerShape(MenuPilotRadii.pill),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                shadowElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = when (voiceState) {
                            VoiceInputState.Idle -> onStartVoice
                            is VoiceInputState.Listening -> onStopVoice
                            is VoiceInputState.PermissionDenied ->
                                if (voiceState.canRequestAgain) onStartVoice else onOpenVoiceSettings
                            is VoiceInputState.Failed -> onStartVoice
                            else -> ({})
                        },
                        enabled = voiceState !is VoiceInputState.Unavailable &&
                            voiceState != VoiceInputState.Processing,
                    ) {
                        Text(voiceState.composerActionLabel)
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type your preferences here…") },
                        minLines = 1,
                        maxLines = 3,
                        shape = RoundedCornerShape(MenuPilotRadii.pill),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                    )
                    TextButton(
                        onClick = onSubmitQuery,
                        enabled = query.isNotBlank() &&
                            !voiceState.isCapturingVoice &&
                            !isAssistantThinking,
                    ) {
                        Text(
                            when {
                                isAssistantThinking -> "Working…"
                                hasIntent -> "Update"
                                else -> "Send"
                            },
                        )
                    }
                }
            }

            if (voiceState != VoiceInputState.Idle) {
                Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${voiceState.voiceTitle}: ${voiceState.voiceSupportingText}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (voiceState is VoiceInputState.Listening) {
                        TextButton(onClick = onCancelVoice) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

private val VoiceInputState.composerActionLabel: String
    get() = when (this) {
        VoiceInputState.Idle -> "Speak"
        is VoiceInputState.Listening -> "Use"
        VoiceInputState.Processing -> "…"
        is VoiceInputState.PermissionDenied -> if (canRequestAgain) "Allow" else "Settings"
        is VoiceInputState.Unavailable -> "Type"
        is VoiceInputState.Failed -> "Retry"
    }

private fun String.toConversationQuery(): String = when (this) {
    "Best seller" -> "What is your best seller this week?"
    "Something light" -> "I want something light but filling"
    "Something spicy" -> "I want something spicy"
    "Peanut allergy" -> "I’m allergic to peanuts"
    "Vegetarian" -> "I’m vegetarian"
    "Gluten-free" -> "I need gluten-free food"
    else -> this
}

private fun String.appendPreference(preference: String): String =
    if (isBlank()) preference else "$this. $preference"

private val MenuPilotUiState.assistantRuntimeLabel: String
    get() = when {
        isAssistantThinking -> "Private • working locally"
        assistantSource == AssistantSource.ON_DEVICE_FUNCTION_GEMMA ->
            "Private • FunctionGemma + local rules"
        assistantSource == AssistantSource.ON_DEVICE_QWEN ->
            "Private • Qwen3 on this tablet"
        assistantSource == AssistantSource.MODEL_FALLBACK -> when (assistantFallbackReason) {
            AssistantFallbackReason.MODEL_INPUT_REJECTED ->
                "Private • local rules (context rejected)"
            AssistantFallbackReason.MODEL_NOT_INSTALLED ->
                "Private • local rules (model not installed)"
            AssistantFallbackReason.MODEL_VERIFICATION_FAILED ->
                "Private • local rules (model check failed)"
            AssistantFallbackReason.MODEL_RUNTIME_FAILED ->
                "Private • local rules (model unavailable)"
            AssistantFallbackReason.MODEL_OUTPUT_REJECTED ->
                "Private • local rules (response rejected)"
            null -> "Private • local rules"
        }
        assistantSource == AssistantSource.DETERMINISTIC_RULES ->
            "Private • local safety rules"
        else -> "Private session"
    }

private val VoiceInputState.isCapturingVoice: Boolean
    get() = this is VoiceInputState.Listening || this is VoiceInputState.Processing

private val VoiceInputState.voiceTitle: String
    get() = when (this) {
        VoiceInputState.Idle -> "Speak your request"
        is VoiceInputState.Listening -> "Listening…"
        VoiceInputState.Processing -> "Preparing your transcript…"
        is VoiceInputState.PermissionDenied -> "Microphone permission is off"
        is VoiceInputState.Unavailable -> "Voice input isn’t available"
        is VoiceInputState.Failed -> when (failure) {
            VoiceInputFailure.NO_SPEECH -> "I didn’t catch that"
            VoiceInputFailure.AUDIO -> "The microphone couldn’t start"
            VoiceInputFailure.BUSY -> "Voice input is busy"
            VoiceInputFailure.LANGUAGE -> "This voice language isn’t available"
            VoiceInputFailure.PERMISSION -> "Microphone permission is off"
            VoiceInputFailure.SERVICE -> "The voice service is unavailable"
            VoiceInputFailure.CLIENT -> "Voice input stopped"
        }
    }

private val VoiceInputState.voiceSupportingText: String
    get() = when (this) {
        VoiceInputState.Idle ->
            "Tap Speak and talk naturally. Recognition runs on this tablet."
        is VoiceInputState.Listening ->
            partialTranscript.ifBlank { "Say what you need, including any allergies." }
        VoiceInputState.Processing ->
            "You can edit the text before MenuPilot understands it."
        is VoiceInputState.PermissionDenied -> if (canRequestAgain) {
            "Allow access only when you want to speak, or keep typing."
        } else {
            "Enable microphone access in Android Settings, or keep typing."
        }
        is VoiceInputState.Unavailable -> when (reason) {
            VoiceUnavailableReason.REQUIRES_ANDROID_12 ->
                "On-device speech requires Android 12 or newer. Keyboard entry still works."
            VoiceUnavailableReason.ON_DEVICE_RECOGNIZER_MISSING ->
                "No on-device speech model is installed. Keyboard entry still works."
        }
        is VoiceInputState.Failed -> when (failure) {
            VoiceInputFailure.NO_SPEECH ->
                "Tap Try again, speak closer to the tablet, or type your request."
            VoiceInputFailure.AUDIO ->
                "Another app may be using the microphone. Keyboard entry still works."
            VoiceInputFailure.BUSY ->
                "Wait a moment, then try again or use the keyboard."
            VoiceInputFailure.LANGUAGE ->
                "Use an installed tablet language or type your request."
            VoiceInputFailure.PERMISSION ->
                "Allow microphone access when prompted, or keep typing."
            VoiceInputFailure.SERVICE,
            VoiceInputFailure.CLIENT,
            -> "Try once more, or continue with the keyboard."
        }
    }
