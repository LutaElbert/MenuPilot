package com.menupilot.restaurant.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColorScheme =
  lightColorScheme(
    primary = ConciergePrimary,
    onPrimary = ConciergeOnPrimary,
    primaryContainer = ConciergePrimaryContainer,
    onPrimaryContainer = ConciergeOnPrimaryContainer,
    secondary = ConciergeSecondary,
    onSecondary = ConciergeOnSecondary,
    secondaryContainer = ConciergeSecondaryContainer,
    onSecondaryContainer = ConciergeOnSecondaryContainer,
    tertiary = ConciergeTertiary,
    onTertiary = ConciergeOnTertiary,
    tertiaryContainer = ConciergeTertiaryContainer,
    onTertiaryContainer = ConciergeOnTertiaryContainer,
    background = ConciergeBackground,
    onBackground = ConciergeOnBackground,
    surface = ConciergeSurface,
    onSurface = ConciergeOnSurface,
    surfaceVariant = ConciergeSurfaceHighest,
    onSurfaceVariant = ConciergeOnSurfaceVariant,
    surfaceContainerLowest = ConciergeSurfaceLowest,
    surfaceContainerLow = ConciergeSurfaceLow,
    surfaceContainer = ConciergeSurfaceContainer,
    surfaceContainerHigh = ConciergeSurfaceHigh,
    surfaceContainerHighest = ConciergeSurfaceHighest,
    surfaceDim = ConciergeSurfaceDim,
    surfaceBright = ConciergeSurface,
    surfaceTint = ConciergePrimary,
    outline = ConciergeOutline,
    outlineVariant = ConciergeOutlineVariant,
    error = ConciergeError,
    onError = ConciergeOnError,
    errorContainer = ConciergeErrorContainer,
    onErrorContainer = ConciergeOnErrorContainer,
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = ConciergeDarkPrimary,
    onPrimary = ConciergeDarkOnPrimary,
    primaryContainer = ConciergeDarkPrimaryContainer,
    onPrimaryContainer = ConciergeDarkOnPrimaryContainer,
    secondary = ConciergeDarkSecondary,
    onSecondary = ConciergeDarkOnSecondary,
    secondaryContainer = ConciergeDarkSecondaryContainer,
    onSecondaryContainer = ConciergeDarkOnSecondaryContainer,
    tertiary = ConciergeDarkTertiary,
    onTertiary = ConciergeDarkOnTertiary,
    tertiaryContainer = ConciergeDarkTertiaryContainer,
    onTertiaryContainer = ConciergeDarkOnTertiaryContainer,
    background = ConciergeDarkBackground,
    onBackground = ConciergeDarkOnBackground,
    surface = ConciergeDarkSurface,
    onSurface = ConciergeDarkOnBackground,
    surfaceVariant = ConciergeDarkSurfaceVariant,
    onSurfaceVariant = ConciergeDarkOnSurfaceVariant,
    outline = ConciergeDarkOutline,
    outlineVariant = ConciergeDarkOutlineVariant,
    error = ConciergeDarkError,
    onError = ConciergeDarkOnError,
    errorContainer = ConciergeDarkErrorContainer,
    onErrorContainer = ConciergeDarkOnErrorContainer,
  )

val MenuPilotShapes =
  Shapes(
    small = RoundedCornerShape(MenuPilotRadii.small),
    medium = RoundedCornerShape(MenuPilotRadii.medium),
    large = RoundedCornerShape(MenuPilotRadii.large),
    extraLarge = RoundedCornerShape(MenuPilotRadii.extraLarge),
  )

@Immutable
data class MenuPilotSemanticColors(
  val successContainer: Color,
  val onSuccessContainer: Color,
  val attentionContainer: Color,
  val onAttentionContainer: Color,
  val criticalContainer: Color,
  val onCriticalContainer: Color,
)

private val LightSemanticColors =
  MenuPilotSemanticColors(
    successContainer = SuccessLight,
    onSuccessContainer = OnSuccessLight,
    attentionContainer = AttentionLight,
    onAttentionContainer = OnAttentionLight,
    criticalContainer = CriticalLight,
    onCriticalContainer = OnCriticalLight,
  )

private val DarkSemanticColors =
  MenuPilotSemanticColors(
    successContainer = SuccessDark,
    onSuccessContainer = OnSuccessDark,
    attentionContainer = AttentionDark,
    onAttentionContainer = OnAttentionDark,
    criticalContainer = CriticalDark,
    onCriticalContainer = OnCriticalDark,
  )

private val LocalMenuPilotSemanticColors = staticCompositionLocalOf { LightSemanticColors }

val MaterialTheme.menuPilotSemanticColors: MenuPilotSemanticColors
  @Composable
  @ReadOnlyComposable
  get() = LocalMenuPilotSemanticColors.current

/**
 * MenuPilot's brand theme intentionally opts out of dynamic color so safety and restaurant status
 * colors remain consistent across shared tablets.
 */
@Composable
fun MenuPilotTheme(
  darkTheme: Boolean = false,
  content: @Composable () -> Unit,
) {
  val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

  CompositionLocalProvider(LocalMenuPilotSemanticColors provides semanticColors) {
    MaterialTheme(
      colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
      typography = MenuPilotTypography,
      shapes = MenuPilotShapes,
      content = content,
    )
  }
}
