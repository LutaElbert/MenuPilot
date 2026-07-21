package com.menupilot.restaurant.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.menupilot.restaurant.app.CuratedDish
import com.menupilot.restaurant.assistant.formatPeso
import com.menupilot.restaurant.design.CallStaffAction
import com.menupilot.restaurant.design.PrimaryAction
import com.menupilot.restaurant.design.SafetyStatusBadge
import com.menupilot.restaurant.design.StatusTone
import com.menupilot.restaurant.theme.MenuPilotRadii
import com.menupilot.restaurant.theme.MenuPilotSpacing

internal object DishDetailTestTags {
    const val AddToPicks = "dish-detail-add-to-picks"
}

@Composable
fun DishDetailScreen(
    result: CuratedDish?,
    dishInsight: String? = null,
    isInCart: Boolean,
    salesWindowLabel: String,
    salesSourceLabel: String,
    salesUpdatedLabel: String,
    onBack: () -> Unit,
    onAddToCart: (String, String) -> Unit,
    onCallStaff: (String) -> Unit,
    modifier: Modifier = Modifier,
    hasConfirmedNeeds: Boolean = true,
) {
    if (result == null) {
        SelectDishPlaceholder(modifier)
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (!wide) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onBack) { Text("‹ Menu") }
                        Text(
                            text = "MenuPilot",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = formatPeso(result.dish.priceMinor),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier.fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (wide) {
                    Row(
                        modifier = Modifier.fillMaxSize()
                            .widthIn(max = 1200.dp)
                            .padding(horizontal = 34.dp, vertical = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(30.dp),
                    ) {
                        DishMediaColumn(
                            result = result,
                            isShortlisted = isInCart,
                            salesWindowLabel = salesWindowLabel,
                            onBack = onBack,
                            onAddToShortlist = onAddToCart,
                            onCallWaiter = onCallStaff,
                            showDesktopActions = true,
                            modifier = Modifier.weight(0.82f).fillMaxHeight(),
                        )
                        Column(
                            modifier = Modifier.weight(1.18f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            DishStory(
                                result = result,
                                dishInsight = dishInsight,
                                isShortlisted = isInCart,
                                salesWindowLabel = salesWindowLabel,
                                salesSourceLabel = salesSourceLabel,
                                salesUpdatedLabel = salesUpdatedLabel,
                                hasConfirmedNeeds = hasConfirmedNeeds,
                                onAddToShortlist = onAddToCart,
                                onCallWaiter = onCallStaff,
                                showActions = false,
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        DishMediaColumn(
                            result = result,
                            isShortlisted = isInCart,
                            salesWindowLabel = salesWindowLabel,
                            onBack = onBack,
                            onAddToShortlist = onAddToCart,
                            onCallWaiter = onCallStaff,
                            showDesktopActions = false,
                        )
                        Column(modifier = Modifier.padding(20.dp)) {
                            DishStory(
                                result = result,
                                dishInsight = dishInsight,
                                isShortlisted = isInCart,
                                salesWindowLabel = salesWindowLabel,
                                salesSourceLabel = salesSourceLabel,
                                salesUpdatedLabel = salesUpdatedLabel,
                                hasConfirmedNeeds = hasConfirmedNeeds,
                                onAddToShortlist = onAddToCart,
                                onCallWaiter = onCallStaff,
                                showActions = true,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DishMediaColumn(
    result: CuratedDish,
    isShortlisted: Boolean,
    salesWindowLabel: String,
    onBack: () -> Unit,
    onAddToShortlist: (String, String) -> Unit,
    onCallWaiter: (String) -> Unit,
    showDesktopActions: Boolean,
    modifier: Modifier = Modifier,
) {
    val dish = result.dish
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showDesktopActions) {
            TextButton(onClick = onBack) { Text("‹ Back to menu") }
        }
        Box(
            modifier = Modifier.fillMaxWidth()
                .then(
                    if (showDesktopActions) {
                        Modifier.weight(1f)
                    } else {
                        Modifier.height(230.dp)
                    },
                )
                .background(Color(dish.photoAccent), RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = dish.photoSymbol,
                style = MaterialTheme.typography.displayLarge,
            )
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                shape = RoundedCornerShape(MenuPilotRadii.pill),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            ) {
                Text(
                    text = dish.category.label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                shape = RoundedCornerShape(MenuPilotRadii.pill),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = 2.dp,
            ) {
                Text(
                    text = formatPeso(dish.priceMinor),
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            result.salesEvidence?.let { evidence ->
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                    shape = RoundedCornerShape(MenuPilotRadii.pill),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                ) {
                    Text(
                        text = buildString {
                            if (evidence.isBestseller) append("★ Bestseller • ")
                            append("${evidence.orderCount} ordered $salesWindowLabel")
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        if (showDesktopActions) {
            DishActions(
                result = result,
                isShortlisted = isShortlisted,
                onAddToShortlist = onAddToShortlist,
                onCallWaiter = onCallWaiter,
            )
        }
    }
}

@Composable
private fun DishStory(
    result: CuratedDish,
    dishInsight: String?,
    isShortlisted: Boolean,
    salesWindowLabel: String,
    salesSourceLabel: String,
    salesUpdatedLabel: String,
    hasConfirmedNeeds: Boolean,
    onAddToShortlist: (String, String) -> Unit,
    onCallWaiter: (String) -> Unit,
    showActions: Boolean,
) {
    val dish = result.dish
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = dish.name,
            style = MaterialTheme.typography.headlineLarge,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (listOf(dish.category.label) + dish.dietaryTags + dish.flavorTags)
                .distinct()
                .take(5)
                .forEach { tag ->
                    AssistChip(onClick = {}, label = { Text(tag) })
                }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (hasConfirmedNeeds) {
                        "✦  Why it may suit you"
                    } else {
                        "✦  Dish overview"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = if (hasConfirmedNeeds) {
                        result.matchReasons.joinToString(". ") + ". ${dish.description}"
                    } else {
                        dish.description
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        dishInsight?.takeIf(String::isNotBlank)?.let { insight ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(MenuPilotRadii.large),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Brief MenuPilot insight",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = insight,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        if (hasConfirmedNeeds) {
            EvaluationBadge(result)
        } else {
            SafetyStatusBadge(
                label = "Listed in the current menu record • waiter confirms availability",
                tone = StatusTone.Neutral,
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DetailFact("Flavor profile", dish.flavorTags.joinToString().ifBlank { "Ask waiter" })
            DetailFact("Course", dish.category.label)
            DetailFact("Prep time", "~${dish.prepMinutes} min")
            DetailFact(
                "Popularity",
                result.salesEvidence?.let {
                    "${it.orderCount} orders • $salesWindowLabel • ${it.sourceLabel}"
                } ?: "Current comparable sales evidence unavailable",
            )
        }

        HorizontalDivider()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Key ingredients", style = MaterialTheme.typography.titleLarge)
            Text(
                text = dish.ingredients.joinToString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Preparation notes", style = MaterialTheme.typography.titleLarge)
            Text(
                text = dish.preparationNote,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(MenuPilotRadii.large),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Questions to ask the waiter", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "•  Can the kitchen confirm today’s ingredients and cross-contact risks?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = if (hasConfirmedNeeds) {
                        "•  Can ${dish.name} be adjusted for my confirmed needs?"
                    } else {
                        "•  Is ${dish.name} available from the kitchen today?"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(MenuPilotRadii.large),
            color = MaterialTheme.colorScheme.errorContainer,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (dish.listedAllergens.isEmpty()) {
                        "Allergen notes: no allergens are listed in this recipe record."
                    } else {
                        "Allergen notes: ${dish.listedAllergens.joinToString()}."
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = "MenuPilot uses menu data only. For allergies, confirm today’s " +
                        "preparation and cross-contact conditions with the waiter or kitchen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontStyle = FontStyle.Italic,
                )
            }
        }

        Text(
            text = "$salesSourceLabel • $salesUpdatedLabel",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (showActions) {
            DishActions(
                result = result,
                isShortlisted = isShortlisted,
                onAddToShortlist = onAddToShortlist,
                onCallWaiter = onCallWaiter,
            )
        }
        Text(
            text = "My Picks is a conversation aid only. Your waiter confirms the dish with the kitchen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(MenuPilotSpacing.md))
    }
}

@Composable
private fun DetailFact(label: String, value: String) {
    Column(
        modifier = Modifier.widthIn(min = 125.dp, max = 180.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DishActions(
    result: CuratedDish,
    isShortlisted: Boolean,
    onAddToShortlist: (String, String) -> Unit,
    onCallWaiter: (String) -> Unit,
) {
    val dish = result.dish
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PrimaryAction(
            label = if (isShortlisted) "Added to My Picks" else "Add to My Picks",
            onClick = {
                onAddToShortlist(dish.id.value, dish.variantId.value)
            },
            modifier = Modifier.fillMaxWidth().testTag(DishDetailTestTags.AddToPicks),
            enabled = result.canAddToOrder && !isShortlisted,
        )
        CallStaffAction(
            label = "Call waiter about this",
            onClick = {
                onCallWaiter(
                    "I’m considering ${dish.name}. Please confirm today’s ingredients, " +
                        "availability, and preparation with the kitchen.",
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
