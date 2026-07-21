package com.menupilot.restaurant.design

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.menupilot.restaurant.theme.MenuPilotDimensions
import com.menupilot.restaurant.theme.MenuPilotRadii

/** An editable dietary, allergy, budget, or preference constraint. */
@Composable
fun IntentChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  leadingContent: (@Composable () -> Unit)? = null,
) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(text = label, style = MaterialTheme.typography.labelLarge) },
    modifier = modifier.heightIn(min = MenuPilotDimensions.minimumTouchTarget),
    enabled = enabled,
    shape = RoundedCornerShape(MenuPilotRadii.pill),
    leadingIcon = leadingContent,
    colors =
      FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surface,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
      ),
  )
}
