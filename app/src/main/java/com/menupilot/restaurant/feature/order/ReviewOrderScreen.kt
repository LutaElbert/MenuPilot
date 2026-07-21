package com.menupilot.restaurant.feature.order

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.menupilot.restaurant.app.MenuPilotUiState
import com.menupilot.restaurant.assistant.formatPeso
import com.menupilot.restaurant.data.MenuDish
import com.menupilot.restaurant.design.CallStaffAction
import com.menupilot.restaurant.design.PrimaryAction
import com.menupilot.restaurant.design.SafetyStatusBadge
import com.menupilot.restaurant.design.StatusTone
import com.menupilot.restaurant.feature.handoff.HandoffPlacement
import com.menupilot.restaurant.feature.handoff.WaiterHandoffContext
import com.menupilot.restaurant.feature.handoff.WaiterHandoffStatus
import com.menupilot.restaurant.theme.MenuPilotRadii

private val ExpandedOrderBreakpoint = 900.dp
private val SuggestionPaneMinWidth = 380.dp
private val SuggestionPaneMaxWidth = 460.dp

@Composable
@Suppress("UNUSED_PARAMETER")
fun ShortlistScreen(
    state: MenuPilotUiState,
    dishes: List<MenuDish>,
    onBack: () -> Unit,
    onRemoveDish: (String, String) -> Unit,
    onAddSuggestion: (String, String) -> Unit,
    onDismissSuggestion: (String, String) -> Unit,
    onRequestStaff: () -> Unit,
    onStaffAcknowledge: (String) -> Boolean,
    onCreateHandoff: () -> Unit,
    modifier: Modifier = Modifier,
    handoffContext: WaiterHandoffContext = WaiterHandoffContext(),
) {
    var showStaffVerification by rememberSaveable { mutableStateOf(false) }
    var staffPin by rememberSaveable { mutableStateOf("") }
    var staffPinError by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                val compactHeader = maxWidth < 600.dp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) {
                        Text(if (compactHeader) "‹ Back" else "‹ Change something")
                    }
                    Text(
                        text = "My picks",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            handoffContext.locationLabel,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        if (!compactHeader) {
                            Text(
                                handoffContext.sessionLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            if (maxWidth >= ExpandedOrderBreakpoint) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(22.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    OrderMainPane(
                        state = state,
                        dishes = dishes,
                        onRemoveDish = onRemoveDish,
                        onRequestStaff = onRequestStaff,
                        onShowStaffVerification = { showStaffVerification = true },
                        onChangePicks = onBack,
                        handoffContext = handoffContext,
                        modifier = Modifier.weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                    )
                    Surface(
                        modifier = Modifier
                            .widthIn(
                                min = SuggestionPaneMinWidth,
                                max = SuggestionPaneMaxWidth,
                            )
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(MenuPilotRadii.extraLarge),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        tonalElevation = 2.dp,
                    ) {
                        HandoffSupportingPane(
                            state = state,
                            dishes = dishes,
                            onCreateHandoff = onCreateHandoff,
                            onChangePicks = onBack,
                            handoffContext = handoffContext,
                            modifier = Modifier.fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp),
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    OrderMainPane(
                        state = state,
                        dishes = dishes,
                        onRemoveDish = onRemoveDish,
                        onRequestStaff = onRequestStaff,
                        onShowStaffVerification = { showStaffVerification = true },
                        onChangePicks = onBack,
                        handoffContext = handoffContext,
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(MenuPilotRadii.extraLarge),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        tonalElevation = 2.dp,
                    ) {
                        HandoffSupportingPane(
                            state = state,
                            dishes = dishes,
                            onCreateHandoff = onCreateHandoff,
                            onChangePicks = onBack,
                            handoffContext = handoffContext,
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                }
            }
        }
    }

    if (showStaffVerification) {
        AlertDialog(
            onDismissRequest = {
                showStaffVerification = false
                staffPin = ""
                staffPinError = false
            },
            title = { Text("Waiter confirmation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "A waiter must review the guest's exact needs, current menu revision, " +
                            "and shortlisted dishes before entering their PIN.",
                    )
                    OutlinedTextField(
                        value = staffPin,
                        onValueChange = {
                            staffPin = it.filter(Char::isDigit).take(8)
                            staffPinError = false
                        },
                        label = { Text("Staff PIN") },
                        singleLine = true,
                        isError = staffPinError,
                        supportingText = if (staffPinError) {
                            { Text("PIN not recognized.") }
                        } else {
                            {
                                Text(
                                    "Local prototype credential; replace with restaurant staff " +
                                        "authentication.",
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (onStaffAcknowledge(staffPin)) {
                            showStaffVerification = false
                            staffPin = ""
                            staffPinError = false
                        } else {
                            staffPinError = true
                        }
                    },
                    enabled = staffPin.isNotBlank(),
                ) {
                    Text("Verify")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showStaffVerification = false
                        staffPin = ""
                        staffPinError = false
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun OrderMainPane(
    state: MenuPilotUiState,
    dishes: List<MenuDish>,
    onRemoveDish: (String, String) -> Unit,
    onRequestStaff: () -> Unit,
    onShowStaffVerification: () -> Unit,
    onChangePicks: () -> Unit,
    handoffContext: WaiterHandoffContext,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Ready to ask the waiter?",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Review My Picks for ${handoffContext.locationLabel} before asking a waiter " +
                    "to join you.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        WaiterSummaryCard(
            state = state,
            dishes = dishes,
            status = reviewHandoffStatus(state, dishes),
            context = handoffContext,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Review My Picks", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onChangePicks) { Text("Change something") }
        }

        if (dishes.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(MenuPilotRadii.large),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("🍽️", style = MaterialTheme.typography.displaySmall)
                    Text("No picks yet", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Choose a recommendation first, then come back to review it with a waiter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onChangePicks) { Text("Find a dish") }
                }
            }
        }
        dishes.forEach { dish ->
            PickCard(
                dish = dish,
                needsWaiterCheck = state.cartEntries.firstOrNull {
                    it.itemId == dish.id.value
                }?.requiresStaffConfirmation == true,
                onRemove = {
                    onRemoveDish(dish.id.value, dish.variantId.value)
                },
            )
        }

        if (state.requiresStaffAcknowledgment) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(MenuPilotRadii.large),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SafetyStatusBadge(
                        label = if (state.staffAcknowledged) {
                            "Confirmed with ${state.staffAcknowledgment?.staffDisplayName ?: "waiter"}"
                        } else {
                            "Waiter confirmation needed"
                        },
                        tone = if (state.staffAcknowledged) {
                            StatusTone.Positive
                        } else {
                            StatusTone.Attention
                        },
                    )
                    Text(
                        text = "Exact request: ${intentAllergySummary(state)}. " +
                            "The waiter and kitchen must confirm current ingredients and preparation " +
                            "conditions. MenuPilot has not approved or placed an order.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!state.staffAcknowledged) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CallStaffAction(
                                onClick = onRequestStaff,
                                modifier = Modifier.weight(1f),
                                label = "Call waiter",
                            )
                            PrimaryAction(
                                label = "Waiter confirms",
                                onClick = onShowStaffVerification,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HandoffSupportingPane(
    state: MenuPilotUiState,
    dishes: List<MenuDish>,
    onCreateHandoff: () -> Unit,
    onChangePicks: () -> Unit,
    handoffContext: WaiterHandoffContext,
    modifier: Modifier = Modifier,
) {
    val total = dishes.sumOf(MenuDish::priceMinor)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                handoffContext.locationLabel,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Waiter handoff",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "Your picks and confirmed dining notes are ready to review with a person.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HandoffActionCard(
            context = handoffContext,
            pickCount = dishes.size,
            dietaryNoteCount = state.intent?.let { it.allergens.size + it.diets.size } ?: 0,
            estimateMinor = total,
            canContinue = state.canCreateWaiterHandoff,
            onReviewWithWaiter = onCreateHandoff,
            onChangePicks = onChangePicks,
        )
        if (dishes.isEmpty()) {
            Text(
                text = "Add at least one dish before reviewing My Picks with a waiter.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (state.requiresStaffAcknowledgment && !state.staffAcknowledged) {
            Text(
                text = "The unresolved ingredient or preparation questions will be included in " +
                    "the handoff. Calling the waiter does not approve them or place an order.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.errorMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun intentAllergySummary(state: MenuPilotUiState): String {
    val allergens = state.intent?.allergens.orEmpty()
    return if (allergens.isEmpty()) {
        "confirmed dining request"
    } else {
        "${allergens.joinToString()} allergy"
    }
}

@Composable
private fun WaiterSummaryCard(
    state: MenuPilotUiState,
    dishes: List<MenuDish>,
    status: WaiterHandoffStatus,
    context: WaiterHandoffContext,
) {
    val (badgeLabel, badgeTone) = when (status) {
        WaiterHandoffStatus.BUILDING_SHORTLIST ->
            "Building My Picks" to StatusTone.Neutral
        WaiterHandoffStatus.READY_TO_CALL ->
            "Ready for waiter review" to StatusTone.Positive
        WaiterHandoffStatus.WAITER_CONFIRMATION_NEEDED ->
            "Questions for the waiter" to StatusTone.Attention
        WaiterHandoffStatus.READY_TO_SHOW ->
            "Dining notes reviewed" to StatusTone.Positive
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MenuPilotRadii.large),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier.fillMaxHeight()
                    .width(6.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Column(
                modifier = Modifier.padding(20.dp).weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp)
                                .background(
                                    MaterialTheme.colorScheme.errorContainer,
                                    RoundedCornerShape(MenuPilotRadii.pill),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("📋", style = MaterialTheme.typography.titleLarge)
                        }
                        Column {
                            Text(
                                "Waiter handoff summary",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                context.locationLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    SafetyStatusBadge(label = badgeLabel, tone = badgeTone)
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(MenuPilotRadii.medium),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        buildWaiterSummary(state, dishes),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    "My Picks is a conversation aid—not an order, reservation, or payment.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun PickCard(
    dish: MenuDish,
    needsWaiterCheck: Boolean,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MenuPilotRadii.large),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(88.dp)
                    .background(
                        Color(dish.photoAccent).copy(alpha = 0.18f),
                        RoundedCornerShape(MenuPilotRadii.medium),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(dish.photoSymbol, style = MaterialTheme.typography.displaySmall)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        dish.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        formatPeso(dish.priceMinor),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    "${dish.category.label} • ${dish.prepMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val tags = (dish.dietaryTags + dish.flavorTags).distinct().take(3)
                if (tags.isNotEmpty()) {
                    Text(
                        tags.joinToString("  •  "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (needsWaiterCheck) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(MenuPilotRadii.small),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            "Question for waiter: confirm ingredients and preparation with the kitchen.",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                TextButton(onClick = onRemove) { Text("Remove from My Picks") }
            }
        }
    }
}

@Composable
private fun HandoffActionCard(
    context: WaiterHandoffContext,
    pickCount: Int,
    dietaryNoteCount: Int,
    estimateMinor: Long,
    canContinue: Boolean,
    onReviewWithWaiter: () -> Unit,
    onChangePicks: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MenuPilotRadii.large),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SummaryMetric("My Picks", pickCount.toString())
            SummaryMetric("Dietary notes", dietaryNoteCount.toString())
            SummaryMetric("Menu estimate", formatPeso(estimateMinor))
            HorizontalDivider()
            PrimaryAction(
                label = when (context.placement) {
                    HandoffPlacement.TABLE -> "Review with waiter"
                    HandoffPlacement.COUNTER -> "Review with counter staff"
                },
                onClick = onReviewWithWaiter,
                modifier = Modifier.fillMaxWidth(),
                enabled = canContinue,
            )
            TextButton(
                onClick = onChangePicks,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("Change something")
            }
            Text(
                if (context.liveStaffChannelConnected) {
                    "A staff channel is configured for this tablet, but this prototype prepares " +
                        "a screen-only handoff. It has not notified staff or placed an order."
                } else {
                    "This prepares a screen to show the waiter. It does not contact the kitchen, " +
                        "place an order, or take payment."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

private fun buildWaiterSummary(
    state: MenuPilotUiState,
    dishes: List<MenuDish>,
): String {
    if (dishes.isEmpty()) return "No dishes have been added to My Picks yet."

    val names = dishes.joinToString(limit = 3, truncated = "…") { it.name }
    val notes = state.intent?.chips.orEmpty().joinToString { it.label }
    return buildString {
        append("Guest is considering ")
        append(names)
        append(".")
        if (notes.isNotBlank()) {
            append(" Notes: ")
            append(notes)
            append(".")
        }
        append(" Please confirm current ingredients, preparation, and final choices together.")
    }
}

private fun reviewHandoffStatus(
    state: MenuPilotUiState,
    dishes: List<MenuDish>,
): WaiterHandoffStatus = when {
    dishes.isEmpty() -> WaiterHandoffStatus.BUILDING_SHORTLIST
    state.requiresStaffAcknowledgment && !state.staffAcknowledged ->
        WaiterHandoffStatus.WAITER_CONFIRMATION_NEEDED
    state.staffAcknowledged -> WaiterHandoffStatus.READY_TO_SHOW
    else -> WaiterHandoffStatus.READY_TO_CALL
}
