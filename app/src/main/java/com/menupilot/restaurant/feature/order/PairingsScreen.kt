package com.menupilot.restaurant.feature.order

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.menupilot.domain.Eligibility
import com.menupilot.restaurant.app.MenuPilotUiState
import com.menupilot.restaurant.app.OrderSuggestion
import com.menupilot.restaurant.assistant.formatPeso
import com.menupilot.restaurant.design.PrimaryAction
import com.menupilot.restaurant.design.SafetyStatusBadge
import com.menupilot.restaurant.design.StatusTone
import com.menupilot.restaurant.feature.handoff.WaiterHandoffContext
import com.menupilot.restaurant.theme.MenuPilotRadii

/**
 * A deliberately optional pairing step between recommendations and My Picks.
 *
 * At most two suggestions are visible, nothing is preselected, and either footer action continues
 * to the guest-controlled waiter handoff flow without submitting an order.
 */
@Composable
fun PairingsScreen(
    state: MenuPilotUiState,
    onAddSuggestion: (String, String) -> Unit,
    onDismissSuggestion: (String, String) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    handoffContext: WaiterHandoffContext = WaiterHandoffContext(),
) {
    val pairings = state.orderSuggestions.take(2)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "MenuPilot",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    handoffContext.locationLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            val useTwoColumns = maxWidth >= 760.dp
            Column(
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "Pairs well with your picks",
                        style = MaterialTheme.typography.displaySmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Optional additions chosen to complement the dishes already in My Picks.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Maximum 2 • nothing preselected • full menu prices shown",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }

                state.suggestionNotice?.let { notice ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
                        shape = RoundedCornerShape(MenuPilotRadii.medium),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            notice,
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }

                if (pairings.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
                        shape = RoundedCornerShape(MenuPilotRadii.extraLarge),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("✓", style = MaterialTheme.typography.displaySmall)
                            Text(
                                "My Picks are ready as they are",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                "No optional pairing is being suggested for this shortlist.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else if (useTwoColumns) {
                    Row(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        pairings.forEach { pairing ->
                            PairingCard(
                                suggestion = pairing,
                                onAdd = {
                                    onAddSuggestion(
                                        pairing.variant.itemId.value,
                                        pairing.variant.variantId.value,
                                    )
                                },
                                onDismiss = {
                                    onDismissSuggestion(
                                        pairing.variant.itemId.value,
                                        pairing.variant.variantId.value,
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        pairings.forEach { pairing ->
                            PairingCard(
                                suggestion = pairing,
                                onAdd = {
                                    onAddSuggestion(
                                        pairing.variant.itemId.value,
                                        pairing.variant.variantId.value,
                                    )
                                },
                                onDismiss = {
                                    onDismissSuggestion(
                                        pairing.variant.itemId.value,
                                        pairing.variant.variantId.value,
                                    )
                                },
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp))

                if (useTwoColumns) {
                    Row(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onSkip,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("No thanks, continue")
                        }
                        PrimaryAction(
                            label = "Review My Picks",
                            onClick = onContinue,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PrimaryAction(
                            label = "Review My Picks",
                            onClick = onContinue,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            onClick = onSkip,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text("No thanks, continue")
                        }
                    }
                }

                Text(
                    "Pairings are suggestions only. MenuPilot never adds one automatically and " +
                        "does not place an order or take payment.",
                    modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PairingCard(
    suggestion: OrderSuggestion,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canAdd = suggestion.eligibility == Eligibility.CANDIDATE ||
        suggestion.eligibility == Eligibility.CANDIDATE_REQUIRES_STAFF_CONFIRMATION

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MenuPilotRadii.extraLarge),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 3.dp,
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .height(132.dp)
                    .background(Color(suggestion.dish.photoAccent).copy(alpha = 0.18f)),
            ) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
                    shape = RoundedCornerShape(MenuPilotRadii.pill),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                ) {
                    Text(
                        "✦  Suggested pairing",
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    suggestion.dish.photoSymbol,
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.displayLarge,
                )
            }

            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        suggestion.dish.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        formatPeso(suggestion.dish.priceMinor),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    "${suggestion.dish.prepMinutes} min • ${suggestion.dish.category.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    suggestion.dish.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(MenuPilotRadii.medium),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Why it pairs well", style = MaterialTheme.typography.labelLarge)
                        Text(suggestion.reason, style = MaterialTheme.typography.bodyMedium)
                        suggestion.evidence?.let { evidence ->
                            Text(
                                "Restaurant signal: $evidence",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                PairingEligibilityBadge(suggestion.eligibility)
                OutlinedButton(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    enabled = canAdd,
                ) {
                    Text("Add to My Picks")
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Not for me")
                }
            }
        }
    }
}

@Composable
private fun PairingEligibilityBadge(eligibility: Eligibility) {
    val (label, tone) = when (eligibility) {
        Eligibility.CANDIDATE ->
            "Matches confirmed needs" to StatusTone.Positive
        Eligibility.CANDIDATE_REQUIRES_STAFF_CONFIRMATION ->
            "Waiter confirmation required" to StatusTone.Attention
        Eligibility.NOT_CANDIDATE_INSUFFICIENT_DATA ->
            "Information incomplete • waiter must check" to StatusTone.Attention
        Eligibility.NOT_CANDIDATE_CONSTRAINT_CONFLICT ->
            "Conflicts with confirmed needs" to StatusTone.Critical
        Eligibility.NOT_CANDIDATE_UNAVAILABLE ->
            "Currently unavailable" to StatusTone.Neutral
    }
    SafetyStatusBadge(label = label, tone = tone)
}
