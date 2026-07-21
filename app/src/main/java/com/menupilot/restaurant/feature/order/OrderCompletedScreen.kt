package com.menupilot.restaurant.feature.order

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.menupilot.restaurant.design.SafetyStatusBadge
import com.menupilot.restaurant.design.StatusTone
import com.menupilot.restaurant.design.PrimaryAction
import com.menupilot.restaurant.feature.handoff.HandoffPlacement
import com.menupilot.restaurant.feature.handoff.WaiterHandoffContext
import com.menupilot.restaurant.theme.MenuPilotRadii
import com.menupilot.restaurant.theme.MenuPilotSpacing

@Composable
fun WaiterHandoffScreen(
    handoffReference: String,
    onShareFeedback: () -> Unit,
    onSkipFeedback: () -> Unit,
    modifier: Modifier = Modifier,
    onShowPicks: () -> Unit = {},
    handoffContext: WaiterHandoffContext = WaiterHandoffContext(),
) {
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
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
        ) {
            val compactLayout = maxWidth < 600.dp
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
                    shape = RoundedCornerShape(MenuPilotRadii.extraLarge),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    tonalElevation = 4.dp,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(if (compactLayout) 24.dp else 36.dp),
                        verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        NotificationVisual()
                        Text(
                            text = waitingTitle(handoffContext),
                            style = MaterialTheme.typography.headlineLarge,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = waitingMessage(handoffContext),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        SafetyStatusBadge(
                            label = if (handoffContext.liveStaffChannelConnected) {
                                "Venue staff channel available"
                            } else {
                                "Show this screen to staff"
                            },
                            tone = StatusTone.Attention,
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(MenuPilotRadii.large),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    "HANDOFF REFERENCE",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                Text(
                                    handoffReference,
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    "${handoffContext.locationLabel} • ${handoffContext.sessionLabel}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(MenuPilotRadii.medium),
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    "Safety reminder",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Text(
                                    "Show My Picks and confirm every dietary need, current ingredient, " +
                                        "and preparation detail before ordering.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }

                        Text(
                            "This reference is not an order confirmation. No payment has been taken.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )

                        PrimaryAction(
                            label = "Show My Picks",
                            onClick = onShowPicks,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(
                            onClick = onShareFeedback,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Give feedback after the meal")
                        }
                        TextButton(
                            onClick = onSkipFeedback,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Finish without feedback")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationVisual() {
    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(120.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    CircleShape,
                ),
        )
        Box(
            modifier = Modifier.size(92.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    CircleShape,
                ),
        )
        Surface(
            modifier = Modifier.size(68.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            tonalElevation = 5.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "📋",
                    style = MaterialTheme.typography.headlineLarge,
                )
            }
        }
    }
}

private fun waitingTitle(context: WaiterHandoffContext): String =
    if (context.liveStaffChannelConnected) {
        when (context.placement) {
            HandoffPlacement.TABLE -> "Ready for staff handoff"
            HandoffPlacement.COUNTER -> "Ready for counter handoff"
        }
    } else {
        when (context.placement) {
            HandoffPlacement.TABLE -> "Ready to show My Picks"
            HandoffPlacement.COUNTER -> "Ready to show counter staff"
        }
    }

private fun waitingMessage(context: WaiterHandoffContext): String =
    if (context.liveStaffChannelConnected) {
        "The venue staff channel is available. Show ${context.locationLabel} and this " +
            "reference when you are ready; this prototype has not sent a notification."
    } else {
        "Show this reference and My Picks to a waiter at ${context.locationLabel}."
    }
