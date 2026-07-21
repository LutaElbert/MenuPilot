package com.menupilot.restaurant.feature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.menupilot.restaurant.design.PrimaryAction
import com.menupilot.restaurant.theme.MenuPilotRadii
import com.menupilot.restaurant.theme.MenuPilotSpacing

internal enum class ConciergeDestination {
    HOME,
    PICKS,
}

/**
 * Tablet-only hospitality rail translated from the Stitch concierge screens.
 *
 * Navigation remains owned by the calling screen; this component only presents the available
 * actions and never places an order.
 */
@Composable
internal fun ConciergeSideRail(
    activeDestination: ConciergeDestination,
    onHome: () -> Unit,
    onMyPicks: (() -> Unit)?,
    onCallWaiter: () -> Unit,
    bottomActionLabel: String,
    onBottomAction: () -> Unit,
    modifier: Modifier = Modifier,
    locationLabel: String = "Table 12",
) {
    Surface(
        modifier = modifier.width(196.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(MenuPilotRadii.pill),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = "🍴",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            Text(
                text = "MenuPilot",
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = locationLabel,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
                verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.sm),
            ) {
                RailDestination(
                    label = "Home",
                    selected = activeDestination == ConciergeDestination.HOME,
                    onClick = onHome,
                )
                RailDestination(
                    label = "My Picks",
                    selected = activeDestination == ConciergeDestination.PICKS,
                    onClick = onMyPicks,
                )
                RailDestination(
                    label = "Call Waiter",
                    selected = false,
                    onClick = onCallWaiter,
                )
            }

            Spacer(Modifier.weight(1f))
            PrimaryAction(
                label = bottomActionLabel,
                onClick = onBottomAction,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "A waiter confirms every pick.",
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RailDestination(
    label: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(
            enabled = onClick != null,
            onClick = { onClick?.invoke() },
        ),
        shape = RoundedCornerShape(MenuPilotRadii.medium),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            style = MaterialTheme.typography.labelLarge,
            color = when {
                selected -> MaterialTheme.colorScheme.onPrimary
                onClick == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
