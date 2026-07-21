package com.menupilot.restaurant.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import com.menupilot.restaurant.theme.MenuPilotDimensions
import com.menupilot.restaurant.theme.MenuPilotRadii
import com.menupilot.restaurant.theme.MenuPilotSpacing
import com.menupilot.restaurant.theme.menuPilotSemanticColors

enum class StatusTone {
  Positive,
  Attention,
  Critical,
  Neutral,
}

/**
 * A non-interactive status marker. The label remains the primary signal; color is only supporting
 * information.
 */
@Composable
fun SafetyStatusBadge(
  label: String,
  tone: StatusTone,
  modifier: Modifier = Modifier,
) {
  val colors = badgeColors(tone)

  Surface(
    modifier = modifier.semantics(mergeDescendants = true) {},
    shape = RoundedCornerShape(MenuPilotRadii.pill),
    color = colors.container,
    contentColor = colors.content,
  ) {
    Row(
      modifier =
        Modifier.heightIn(min = MenuPilotDimensions.compactBadgeHeight)
          .padding(horizontal = MenuPilotSpacing.md, vertical = MenuPilotSpacing.xs),
      horizontalArrangement = Arrangement.spacedBy(MenuPilotSpacing.sm),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(MenuPilotDimensions.statusIndicatorSize)
            .background(colors.content, RoundedCornerShape(MenuPilotRadii.pill))
      )
      Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
  }
}

private data class BadgeColors(val container: Color, val content: Color)

@Composable
private fun badgeColors(tone: StatusTone): BadgeColors {
  val semantic = MaterialTheme.menuPilotSemanticColors
  return when (tone) {
    StatusTone.Positive ->
      BadgeColors(
        container = semantic.successContainer,
        content = semantic.onSuccessContainer,
      )
    StatusTone.Attention ->
      BadgeColors(
        container = semantic.attentionContainer,
        content = semantic.onAttentionContainer,
      )
    StatusTone.Critical ->
      BadgeColors(
        container = semantic.criticalContainer,
        content = semantic.onCriticalContainer,
      )
    StatusTone.Neutral ->
      BadgeColors(
        container = MaterialTheme.colorScheme.surfaceVariant,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
      )
  }
}
