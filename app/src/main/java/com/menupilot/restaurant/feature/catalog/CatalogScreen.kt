package com.menupilot.restaurant.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.menupilot.domain.Eligibility
import com.menupilot.restaurant.app.CuratedDish
import com.menupilot.restaurant.app.MenuDishKey
import com.menupilot.restaurant.app.MenuPilotUiState
import com.menupilot.restaurant.assistant.formatPeso
import com.menupilot.restaurant.design.CallStaffAction
import com.menupilot.restaurant.design.PrimaryAction
import com.menupilot.restaurant.design.SafetyStatusBadge
import com.menupilot.restaurant.design.StatusTone
import com.menupilot.restaurant.feature.ConciergeDestination
import com.menupilot.restaurant.feature.ConciergeSideRail
import com.menupilot.restaurant.theme.MenuPilotRadii
import com.menupilot.restaurant.theme.MenuPilotSpacing

internal object CatalogTestTags {
    const val Root = "catalog-root"
    const val HeaderActions = "catalog-header-actions"
    const val SideRail = "catalog-side-rail"

    fun recommendationCard(dishId: String) = "recommendation-card-$dishId"

    fun viewDishAction(dishId: String) = "recommendation-card-$dishId-view-action"

    fun matchBadge(dishId: String) = "recommendation-card-$dishId-match-badge"

    fun popularityBadge(dishId: String) = "recommendation-card-$dishId-popularity-badge"
}

@Composable
fun CatalogScreen(
    state: MenuPilotUiState,
    salesWindowLabel: String,
    salesSourceLabel: String,
    salesUpdatedLabel: String,
    onBack: () -> Unit,
    onBrowseMenu: () -> Unit,
    onDishClick: (String, String) -> Unit,
    onReviewOrder: () -> Unit,
    onCallStaff: () -> Unit,
    modifier: Modifier = Modifier,
    locationLabel: String = "Table 12",
) {
    val bestMatches = state.curatedDishes.take(3)
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().testTag(CatalogTestTags.Root),
    ) {
        // A rail is useful on a table-mounted tablet, but not when a wide window is also short
        // (for example, split screen or a phone in landscape).
        val showRail = maxWidth >= 900.dp && maxHeight >= 600.dp
        if (showRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                ConciergeSideRail(
                    activeDestination = ConciergeDestination.HOME,
                    onHome = onBack,
                    onMyPicks = if (state.cartDishIds.isNotEmpty()) onReviewOrder else null,
                    onCallWaiter = onCallStaff,
                    bottomActionLabel = "Browse full menu",
                    onBottomAction = onBrowseMenu,
                    locationLabel = locationLabel,
                    modifier = Modifier.testTag(CatalogTestTags.SideRail),
                )
                CatalogCanvas(
                    state = state,
                    bestMatches = bestMatches,
                    salesWindowLabel = salesWindowLabel,
                    salesSourceLabel = salesSourceLabel,
                    salesUpdatedLabel = salesUpdatedLabel,
                    onBack = onBack,
                    onBrowseMenu = onBrowseMenu,
                    onDishClick = onDishClick,
                    onReviewOrder = onReviewOrder,
                    onCallStaff = onCallStaff,
                    showInlineActions = false,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            CatalogCanvas(
                state = state,
                bestMatches = bestMatches,
                salesWindowLabel = salesWindowLabel,
                salesSourceLabel = salesSourceLabel,
                salesUpdatedLabel = salesUpdatedLabel,
                onBack = onBack,
                onBrowseMenu = onBrowseMenu,
                onDishClick = onDishClick,
                onReviewOrder = onReviewOrder,
                onCallStaff = onCallStaff,
                showInlineActions = true,
            )
        }
    }
}

@Composable
private fun CatalogCanvas(
    state: MenuPilotUiState,
    bestMatches: List<CuratedDish>,
    salesWindowLabel: String,
    salesSourceLabel: String,
    salesUpdatedLabel: String,
    onBack: () -> Unit,
    onBrowseMenu: () -> Unit,
    onDishClick: (String, String) -> Unit,
    onReviewOrder: () -> Unit,
    onCallStaff: () -> Unit,
    showInlineActions: Boolean,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CatalogHeader(
                state = state,
                salesWindowLabel = salesWindowLabel,
                salesSourceLabel = salesSourceLabel,
                salesUpdatedLabel = salesUpdatedLabel,
                onRefine = onBack,
                onBrowseMenu = onBrowseMenu,
                onMyPicks = onReviewOrder,
                onCallWaiter = onCallStaff,
                showInlineActions = showInlineActions,
            )
        },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val gridPadding = PaddingValues(
            start = innerPadding.calculateStartPadding(layoutDirection) + 18.dp,
            top = innerPadding.calculateTopPadding() + 12.dp,
            end = innerPadding.calculateEndPadding(layoutDirection) + 18.dp,
            bottom = innerPadding.calculateBottomPadding() + 18.dp,
        )
        if (bestMatches.isEmpty()) {
            EmptyCatalog(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                onBack = onBack,
                onCallStaff = onCallStaff,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(280.dp),
                modifier = Modifier.fillMaxSize().consumeWindowInsets(innerPadding),
                contentPadding = gridPadding,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(
                    items = bestMatches,
                    key = { "${it.dish.id.value}:${it.dish.variantId.value}" },
                ) { result ->
                    val rank = bestMatches.indexOf(result) + 1
                    DishCard(
                        result = result,
                        rank = rank,
                        total = bestMatches.size,
                        isShortlisted = MenuDishKey(
                            result.dish.id.value,
                            result.dish.variantId.value,
                        ) in state.cartVariantKeys,
                        salesWindowLabel = salesWindowLabel,
                        onCallWaiter = onCallStaff,
                        onClick = {
                            onDishClick(
                                result.dish.id.value,
                                result.dish.variantId.value,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogHeader(
    state: MenuPilotUiState,
    salesWindowLabel: String,
    salesSourceLabel: String,
    salesUpdatedLabel: String,
    onRefine: () -> Unit,
    onBrowseMenu: () -> Unit,
    onMyPicks: () -> Unit,
    onCallWaiter: () -> Unit,
    showInlineActions: Boolean,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val compactPane = maxWidth < 600.dp
        val largeText = LocalDensity.current.fontScale >= 1.3f
        val stackActions = largeText || maxWidth < 360.dp
        Column(
            modifier = Modifier.fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    horizontal = if (compactPane) 16.dp else 24.dp,
                    vertical = if (compactPane) 10.dp else 18.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(if (compactPane) 4.dp else 6.dp),
        ) {
            if (compactPane && largeText) {
                Column {
                    Text(
                        text = "Top matches",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    TextButton(
                        onClick = onRefine,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Refine")
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (compactPane) "Top matches" else "Top 3 Matches for You",
                        modifier = Modifier.weight(1f),
                        style = if (compactPane) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.headlineLarge
                        },
                    )
                    TextButton(onClick = onRefine) {
                        Text(if (compactPane) "Refine" else "Refine preferences")
                    }
                }
            }
            Text(
                text = state.intent?.chips?.takeIf { it.isNotEmpty() }?.joinToString(
                    prefix = if (compactPane) "For: " else "Curated for ",
                    separator = ", ",
                ) { it.label } ?: "Curated from what you told MenuPilot.",
                style = if (compactPane) {
                    MaterialTheme.typography.bodySmall
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (compactPane) {
                    "${state.cartDishIds.size} in My Picks  •  $salesWindowLabel popularity"
                } else {
                    "${state.cartDishIds.size} in My Picks  •  $salesWindowLabel popularity  •  " +
                        "$salesSourceLabel  •  $salesUpdatedLabel"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (showInlineActions) {
                if (compactPane && !stackActions) {
                    TextButton(
                        onClick = onBrowseMenu,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Browse full menu")
                    }
                }
                if (stackActions) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .padding(top = 4.dp)
                            .testTag(CatalogTestTags.HeaderActions),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            onClick = onBrowseMenu,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Browse full menu")
                        }
                        CallStaffAction(
                            label = "Waiter",
                            onClick = onCallWaiter,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PrimaryAction(
                            label = "My Picks (${state.cartDishIds.size})",
                            onClick = onMyPicks,
                            enabled = state.cartDishIds.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(top = 4.dp)
                            .testTag(CatalogTestTags.HeaderActions),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!compactPane) {
                            TextButton(onClick = onBrowseMenu) {
                                Text("Browse full menu")
                            }
                        }
                        CallStaffAction(
                            label = "Waiter",
                            onClick = onCallWaiter,
                            modifier = if (compactPane) Modifier.weight(1f) else Modifier,
                        )
                        if (!compactPane) {
                            Spacer(Modifier.weight(1f))
                        }
                        PrimaryAction(
                            label = "My Picks (${state.cartDishIds.size})",
                            onClick = onMyPicks,
                            enabled = state.cartDishIds.isNotEmpty(),
                            modifier = if (compactPane) Modifier.weight(1f) else Modifier,
                        )
                    }
                }
                Text(
                    text = "A waiter confirms what the kitchen receives.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DishCard(
    result: CuratedDish,
    rank: Int,
    total: Int,
    isShortlisted: Boolean,
    salesWindowLabel: String,
    onCallWaiter: () -> Unit,
    onClick: () -> Unit,
) {
    val dish = result.dish
    val largeText = LocalDensity.current.fontScale >= 1.3f
    Surface(
        modifier = Modifier.fillMaxWidth()
            .testTag(CatalogTestTags.recommendationCard(dish.id.value)),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 5.dp,
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .height(if (largeText) 230.dp else 190.dp)
                    .background(Color(dish.photoAccent))
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = dish.photoSymbol,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
                    style = MaterialTheme.typography.displayLarge,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth()
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        modifier = Modifier.testTag(CatalogTestTags.matchBadge(dish.id.value)),
                        shape = RoundedCornerShape(MenuPilotRadii.pill),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f),
                    ) {
                        Text(
                            text = if (rank == 1) {
                                "★  ${result.matchTier(rank)}"
                            } else {
                                "${result.matchTier(rank)}  •  $rank of $total"
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Surface(
                        modifier = Modifier.testTag(
                            CatalogTestTags.popularityBadge(dish.id.value),
                        ),
                        shape = RoundedCornerShape(MenuPilotRadii.pill),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    ) {
                        Text(
                            text = result.salesEvidence?.let { evidence ->
                                val prefix = if (evidence.isBestseller) "★ Bestseller • " else ""
                                "$prefix${evidence.orderCount} ordered $salesWindowLabel"
                            } ?: "Popularity unavailable",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (largeText) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(dish.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${dish.prepMinutes} min preparation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatPeso(dish.priceMinor),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(dish.name, style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "${dish.prepMinutes} min preparation",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = formatPeso(dish.priceMinor),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = dish.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (largeText) 5 else 3,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (dish.dietaryTags + dish.flavorTags).distinct().take(3).forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(MenuPilotRadii.pill),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = tag,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(MenuPilotRadii.medium),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Why it matches",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = result.matchReasons.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                EvaluationBadge(result)
                PrimaryAction(
                    label = if (isShortlisted) {
                        "In My Picks • View details"
                    } else if (result.canAddToOrder) {
                        "View & add to My Picks"
                    } else {
                        "View before asking waiter"
                    },
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth()
                        .testTag(CatalogTestTags.viewDishAction(dish.id.value)),
                )
                CallStaffAction(
                    label = "Ask waiter about this",
                    onClick = onCallWaiter,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun EvaluationBadge(result: CuratedDish) {
    when (result.evaluation.eligibility) {
        Eligibility.CANDIDATE_REQUIRES_STAFF_CONFIRMATION -> {
            SafetyStatusBadge(
                label = "Possible match • waiter and kitchen confirmation required",
                tone = StatusTone.Attention,
            )
        }
        Eligibility.CANDIDATE -> {
            SafetyStatusBadge(
                label = "Matches your confirmed needs",
                tone = StatusTone.Positive,
            )
        }
        Eligibility.NOT_CANDIDATE_INSUFFICIENT_DATA -> {
            SafetyStatusBadge(
                label = "Information unavailable • staff must check",
                tone = StatusTone.Attention,
            )
        }
        else -> {
            SafetyStatusBadge(
                label = "Does not match confirmed needs",
                tone = StatusTone.Critical,
            )
        }
    }
}

@Composable
private fun EmptyCatalog(
    onBack: () -> Unit,
    onCallStaff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No confident matches yet", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(MenuPilotSpacing.md))
        Text(
            text = "MenuPilot did not loosen your confirmed needs to fill the screen. Refine them or ask a waiter for a personal recommendation.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(MenuPilotSpacing.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryAction(label = "Refine request", onClick = onBack)
            CallStaffAction(label = "Ask waiter", onClick = onCallStaff)
        }
    }
}

@Composable
fun SelectDishPlaceholder(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Choose a best match", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Why it fits, ingredients, and restaurant verification details will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun CuratedDish.matchTier(rank: Int): String = when {
    requiresStaffCheck -> "Ask waiter to confirm"
    rank == 1 -> "Best match"
    else -> "Good alternative"
}
