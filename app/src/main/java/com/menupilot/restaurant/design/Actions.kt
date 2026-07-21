package com.menupilot.restaurant.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.menupilot.restaurant.theme.MenuPilotDimensions
import com.menupilot.restaurant.theme.MenuPilotRadii
import com.menupilot.restaurant.theme.MenuPilotSpacing

@Composable
fun PrimaryAction(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  loading: Boolean = false,
  leadingContent: (@Composable RowScope.() -> Unit)? = null,
) {
  Button(
    onClick = onClick,
    modifier =
      modifier.defaultMinSize(
        minWidth = MenuPilotDimensions.minimumTouchTarget,
        minHeight = MenuPilotDimensions.actionHeight,
      ),
    enabled = enabled && !loading,
    shape = RoundedCornerShape(MenuPilotRadii.medium),
    contentPadding = PaddingValues(horizontal = MenuPilotSpacing.xl),
  ) {
    if (loading) {
      CircularProgressIndicator(
        modifier = Modifier.size(20.dp),
        color = MaterialTheme.colorScheme.onPrimary,
        strokeWidth = 2.dp,
      )
      Spacer(Modifier.size(MenuPilotSpacing.sm))
      Text(text = label, style = MaterialTheme.typography.labelLarge)
    } else {
      if (leadingContent != null) {
        leadingContent()
        Spacer(Modifier.size(MenuPilotSpacing.sm))
      }
      Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
  }
}

/**
 * A visually distinct escalation action for uncertain allergen, ingredient, or preparation data.
 */
@Composable
fun CallStaffAction(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  label: String = "Call staff",
  enabled: Boolean = true,
  leadingContent: (@Composable RowScope.() -> Unit)? = null,
) {
  OutlinedButton(
    onClick = onClick,
    modifier =
      modifier.defaultMinSize(
        minWidth = MenuPilotDimensions.minimumTouchTarget,
        minHeight = MenuPilotDimensions.actionHeight,
      ),
    enabled = enabled,
    shape = RoundedCornerShape(MenuPilotRadii.medium),
    colors =
      ButtonDefaults.outlinedButtonColors(
        contentColor = MaterialTheme.colorScheme.secondary,
      ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
    contentPadding = PaddingValues(horizontal = MenuPilotSpacing.xl),
  ) {
    Row {
      if (leadingContent != null) {
        leadingContent()
        Spacer(Modifier.size(MenuPilotSpacing.sm))
      }
      Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
  }
}
