package com.menupilot.restaurant.feature.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.menupilot.restaurant.R
import com.menupilot.restaurant.design.PrimaryAction
import com.menupilot.restaurant.feature.ConciergeDestination
import com.menupilot.restaurant.feature.ConciergeSideRail
import com.menupilot.restaurant.theme.MenuPilotRadii
import com.menupilot.restaurant.theme.MenuPilotSpacing

private data class WelcomeQuickPrompt(
    val symbol: String,
    val title: String,
    val supportingText: String,
    val query: String?,
)

private val welcomeQuickPrompts = listOf(
    WelcomeQuickPrompt(
        symbol = "★",
        title = "Best sellers this week",
        supportingText = "See what guests enjoyed most this week, with the sales window named.",
        query = "What is your best seller this week?",
    ),
    WelcomeQuickPrompt(
        symbol = "♡",
        title = "I have dietary needs",
        supportingText = "Tell us allergies and preferences before MenuPilot suggests anything.",
        query = null,
    ),
)

@Composable
fun WelcomeScreen(
    onStart: (String?) -> Unit,
    onBrowseMenu: () -> Unit,
    onCallStaff: () -> Unit,
    modifier: Modifier = Modifier,
    locationLabel: String = "Table 12",
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val showRail = maxWidth >= 900.dp
        val compactHeight = maxHeight < 600.dp
        if (showRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                ConciergeSideRail(
                    activeDestination = ConciergeDestination.HOME,
                    onHome = {},
                    onMyPicks = null,
                    onCallWaiter = onCallStaff,
                    bottomActionLabel = "View menu",
                    onBottomAction = onBrowseMenu,
                    locationLabel = locationLabel,
                )
                WelcomeCanvas(
                    onStart = onStart,
                    onBrowseMenu = onBrowseMenu,
                    showCompactHeader = false,
                    compactHeight = compactHeight,
                    locationLabel = locationLabel,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            WelcomeCanvas(
                onStart = onStart,
                onBrowseMenu = onBrowseMenu,
                onCallStaff = onCallStaff,
                showCompactHeader = true,
                compactHeight = compactHeight,
                locationLabel = locationLabel,
            )
        }
    }
}

@Composable
private fun WelcomeCanvas(
    onStart: (String?) -> Unit,
    onBrowseMenu: () -> Unit,
    showCompactHeader: Boolean,
    compactHeight: Boolean,
    locationLabel: String,
    modifier: Modifier = Modifier,
    onCallStaff: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        if (showCompactHeader) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                TextButton(onClick = onCallStaff) { Text("Call waiter") }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {}
            Image(
                painter = painterResource(R.drawable.concierge_welcome_restaurant),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.28f,
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.58f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.78f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                        ),
                    ),
                ),
            )
            Column(
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = 24.dp,
                        vertical = if (compactHeight) 20.dp else 36.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "How would you like to explore?",
                    modifier = Modifier.widthIn(max = 760.dp),
                    style = if (compactHeight) {
                        MaterialTheme.typography.headlineLarge
                    } else {
                        MaterialTheme.typography.displayMedium
                    },
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Browse every dish yourself, or ask MenuPilot to help find what fits you.",
                    modifier = Modifier.widthIn(max = 700.dp).padding(top = 10.dp),
                    style = if (compactHeight) {
                        MaterialTheme.typography.bodyMedium
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(if (compactHeight) 18.dp else 36.dp))
                WelcomeChoiceGrid(
                    onBrowseMenu = onBrowseMenu,
                    onStart = onStart,
                    compactHeight = compactHeight,
                    modifier = Modifier.widthIn(max = 880.dp),
                )

                Spacer(Modifier.height(if (compactHeight) 22.dp else 30.dp))
                Text(
                    text = "Or start with a quick question",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                WelcomeQuickPromptGrid(
                    prompts = welcomeQuickPrompts,
                    onStart = onStart,
                    modifier = Modifier.widthIn(max = 760.dp),
                )

                Spacer(Modifier.height(28.dp))
                Text(
                    text = "MenuPilot helps you decide; it never orders for you. " +
                        "Your waiter and kitchen confirm today’s ingredients, preparation, " +
                        "and cross-contact risks.",
                    modifier = Modifier.widthIn(max = 760.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (showCompactHeader) {
                    Spacer(Modifier.height(MenuPilotSpacing.lg))
                    PrimaryAction(
                        label = "Browse the menu",
                        onClick = onBrowseMenu,
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeChoiceGrid(
    onBrowseMenu: () -> Unit,
    onStart: (String?) -> Unit,
    compactHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val twoColumns = maxWidth >= 620.dp && LocalDensity.current.fontScale < 1.3f
        if (twoColumns) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                WelcomeChoiceCard(
                    symbol = "☰",
                    title = "Browse the menu",
                    supportingText = "Explore categories, dishes, prices, ingredients, and details " +
                        "at your own pace.",
                    compactSupportingText = "Categories, dishes, prices, and ingredients.",
                    actionLabel = "Browse dishes",
                    onClick = onBrowseMenu,
                    compact = compactHeight,
                    modifier = Modifier.weight(1f),
                )
                WelcomeChoiceCard(
                    symbol = "✦",
                    title = "Ask MenuPilot",
                    supportingText = "Describe your cravings, allergies, dietary needs, budget, " +
                        "spice level, or occasion.",
                    compactSupportingText = "Cravings, allergies, dietary needs, and budget.",
                    actionLabel = "Help me choose",
                    onClick = { onStart(null) },
                    compact = compactHeight,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                WelcomeChoiceCard(
                    symbol = "☰",
                    title = "Browse the menu",
                    supportingText = "Explore categories, dishes, prices, ingredients, and details " +
                        "at your own pace.",
                    compactSupportingText = "Categories, dishes, prices, and ingredients.",
                    actionLabel = "Browse dishes",
                    onClick = onBrowseMenu,
                    compact = compactHeight,
                )
                WelcomeChoiceCard(
                    symbol = "✦",
                    title = "Ask MenuPilot",
                    supportingText = "Describe your cravings, allergies, dietary needs, budget, " +
                        "spice level, or occasion.",
                    compactSupportingText = "Cravings, allergies, dietary needs, and budget.",
                    actionLabel = "Help me choose",
                    onClick = { onStart(null) },
                    compact = compactHeight,
                )
            }
        }
    }
}

@Composable
private fun WelcomeChoiceCard(
    symbol: String,
    title: String,
    supportingText: String,
    compactSupportingText: String,
    actionLabel: String,
    onClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
    ) {
        if (compact) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WelcomeChoiceSymbol(symbol)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = compactSupportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "$actionLabel  →",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WelcomeChoiceSymbol(symbol)
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$actionLabel  →",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun WelcomeChoiceSymbol(symbol: String) {
    Surface(
        shape = RoundedCornerShape(MenuPilotRadii.medium),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = symbol,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun WelcomeQuickPromptGrid(
    prompts: List<WelcomeQuickPrompt>,
    onStart: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val twoColumns = maxWidth >= 560.dp && LocalDensity.current.fontScale < 1.3f
        if (twoColumns) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                prompts.forEach { prompt ->
                    WelcomeQuickPromptCard(
                        prompt = prompt,
                        onClick = { onStart(prompt.query) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                prompts.forEach { prompt ->
                    WelcomeQuickPromptCard(
                        prompt = prompt,
                        onClick = { onStart(prompt.query) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeQuickPromptCard(
    prompt: WelcomeQuickPrompt,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MenuPilotRadii.large),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(MenuPilotRadii.pill),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = prompt.symbol,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = prompt.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = prompt.supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
