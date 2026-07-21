package com.menupilot.restaurant.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.menupilot.restaurant.app.CuratedDish
import com.menupilot.restaurant.app.MenuDishKey
import com.menupilot.restaurant.app.MenuPilotUiState
import com.menupilot.restaurant.assistant.IntentChipModel
import com.menupilot.restaurant.assistant.formatPeso
import com.menupilot.restaurant.data.DishCategory
import com.menupilot.restaurant.design.CallStaffAction
import com.menupilot.restaurant.design.IntentChip
import com.menupilot.restaurant.design.PrimaryAction
import com.menupilot.restaurant.design.SafetyStatusBadge
import com.menupilot.restaurant.design.StatusTone
import com.menupilot.restaurant.feature.ConciergeDestination
import com.menupilot.restaurant.feature.ConciergeSideRail
import com.menupilot.restaurant.theme.MenuPilotDimensions
import com.menupilot.restaurant.theme.MenuPilotRadii
import com.menupilot.restaurant.theme.MenuPilotSpacing

/**
 * Manual, safety-aware menu browsing for guests who are not ready to ask for recommendations.
 *
 * The supplied [dishes] are the source of truth for this screen. Search and category filtering are
 * local display operations only; neither changes the guest's confirmed needs nor places a request.
 */
@Composable
fun BrowseMenuScreen(
    state: MenuPilotUiState,
    dishes: List<CuratedDish>,
    salesWindowLabel: String,
    salesSourceLabel: String,
    salesUpdatedLabel: String,
    onBack: () -> Unit,
    onAskAssistant: () -> Unit,
    onDishClick: (String, String) -> Unit,
    onReviewPicks: () -> Unit,
    onCallStaff: () -> Unit,
    modifier: Modifier = Modifier,
    locationLabel: String = "Table 12",
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val showRail = maxWidth >= 1120.dp

        if (showRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                ConciergeSideRail(
                    activeDestination = ConciergeDestination.HOME,
                    onHome = onBack,
                    onMyPicks = if (state.cartEntries.isNotEmpty()) onReviewPicks else null,
                    onCallWaiter = onCallStaff,
                    bottomActionLabel = "Ask MenuPilot",
                    onBottomAction = onAskAssistant,
                    locationLabel = locationLabel,
                )
                BrowseMenuCanvas(
                    state = state,
                    dishes = dishes,
                    salesWindowLabel = salesWindowLabel,
                    salesSourceLabel = salesSourceLabel,
                    salesUpdatedLabel = salesUpdatedLabel,
                    onBack = onBack,
                    onAskAssistant = onAskAssistant,
                    onDishClick = onDishClick,
                    onReviewPicks = onReviewPicks,
                    onCallStaff = onCallStaff,
                    showBackAction = false,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            BrowseMenuCanvas(
                state = state,
                dishes = dishes,
                salesWindowLabel = salesWindowLabel,
                salesSourceLabel = salesSourceLabel,
                salesUpdatedLabel = salesUpdatedLabel,
                onBack = onBack,
                onAskAssistant = onAskAssistant,
                onDishClick = onDishClick,
                onReviewPicks = onReviewPicks,
                onCallStaff = onCallStaff,
                showBackAction = true,
            )
        }
    }
}

@Composable
private fun BrowseMenuCanvas(
    state: MenuPilotUiState,
    dishes: List<CuratedDish>,
    salesWindowLabel: String,
    salesSourceLabel: String,
    salesUpdatedLabel: String,
    onBack: () -> Unit,
    onAskAssistant: () -> Unit,
    onDishClick: (String, String) -> Unit,
    onReviewPicks: () -> Unit,
    onCallStaff: () -> Unit,
    showBackAction: Boolean,
    modifier: Modifier = Modifier,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val categories = remember(dishes) {
        dishes.map { it.dish.category }.distinct().sortedBy(DishCategory::catalogOrder)
    }
    val visibleDishes = remember(dishes, searchQuery, selectedCategory) {
        val normalizedQuery = searchQuery.trim().lowercase()
        dishes.filter { curatedDish ->
            val dish = curatedDish.dish
            val inSelectedCategory =
                selectedCategory == null || dish.category.name == selectedCategory
            val matchesQuery = normalizedQuery.isEmpty() ||
                buildList {
                    add(dish.name)
                    add(dish.description)
                    add(dish.category.label)
                    add(dish.preparationNote)
                    addAll(dish.ingredients)
                    addAll(dish.listedAllergens)
                    addAll(dish.dietaryTags)
                    addAll(dish.flavorTags)
                }.joinToString(separator = " ").lowercase().contains(normalizedQuery)
            inSelectedCategory && matchesQuery
        }
    }
    val confirmedIntentChips = if (state.confirmedIntentFingerprint != null) {
        state.intent?.chips.orEmpty()
    } else {
        emptyList()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            BrowseMenuHeader(
                picksCount = state.cartEntries.size,
                showBackAction = showBackAction,
                onBack = onBack,
                onAskAssistant = onAskAssistant,
                onReviewPicks = onReviewPicks,
            )
        },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        LazyVerticalGrid(
            columns = GridCells.Adaptive(286.dp),
            modifier = Modifier.fillMaxSize().consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                start = innerPadding.calculateLeftPadding(layoutDirection) + MenuPilotSpacing.lg,
                top = innerPadding.calculateTopPadding() + MenuPilotSpacing.lg,
                end = innerPadding.calculateRightPadding(layoutDirection) + MenuPilotSpacing.lg,
                bottom = innerPadding.calculateBottomPadding() + MenuPilotSpacing.xl,
            ),
            horizontalArrangement = Arrangement.spacedBy(MenuPilotSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.lg),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseIntro(
                    hasConfirmedIntent = confirmedIntentChips.isNotEmpty(),
                    onAskAssistant = onAskAssistant,
                )
            }

            if (confirmedIntentChips.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ConfirmedNeedsPanel(chips = confirmedIntentChips)
                }
            }

            if (state.excludedDishCount > 0) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HiddenForNeedsNotice(hiddenCount = state.excludedDishCount)
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().heightIn(
                        min = MenuPilotDimensions.actionHeight,
                    ),
                    label = { Text("Search the menu") },
                    placeholder = { Text("Dish, ingredient, or tag") },
                    leadingIcon = { Text("⌕", style = MaterialTheme.typography.titleLarge) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            TextButton(onClick = { searchQuery = "" }) {
                                Text("Clear")
                            }
                        }
                    } else {
                        null
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(MenuPilotRadii.large),
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                CategoryFilters(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onSelected = { selectedCategory = it },
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                ResultsSummary(
                    visibleCount = visibleDishes.size,
                    totalCount = dishes.size,
                    searchQuery = searchQuery,
                    salesWindowLabel = salesWindowLabel,
                    salesSourceLabel = salesSourceLabel,
                    salesUpdatedLabel = salesUpdatedLabel,
                )
            }

            if (visibleDishes.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyBrowseResults(
                        hasFilters = searchQuery.isNotBlank() || selectedCategory != null,
                        onClear = {
                            searchQuery = ""
                            selectedCategory = null
                        },
                        onAskAssistant = onAskAssistant,
                    )
                }
            } else {
                items(
                    items = visibleDishes,
                    key = { "${it.dish.id.value}:${it.dish.variantId.value}" },
                ) { curatedDish ->
                    BrowseDishCard(
                        result = curatedDish,
                        salesWindowLabel = salesWindowLabel,
                        isInPicks = MenuDishKey(
                            curatedDish.dish.id.value,
                            curatedDish.dish.variantId.value,
                        ) in state.cartVariantKeys,
                        hasConfirmedNeeds = confirmedIntentChips.isNotEmpty(),
                        onClick = {
                            onDishClick(
                                curatedDish.dish.id.value,
                                curatedDish.dish.variantId.value,
                            )
                        },
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                WaiterConfirmationPanel(onCallStaff = onCallStaff)
            }
        }
    }
}

@Composable
private fun BrowseMenuHeader(
    picksCount: Int,
    showBackAction: Boolean,
    onBack: () -> Unit,
    onAskAssistant: () -> Unit,
    onReviewPicks: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        ) {
            val compact = maxWidth < 680.dp
            if (compact) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = MenuPilotSpacing.lg,
                        vertical = MenuPilotSpacing.sm,
                    ),
                    verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.sm),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showBackAction) {
                            TextButton(
                                onClick = onBack,
                                modifier = Modifier.heightIn(
                                    min = MenuPilotDimensions.minimumTouchTarget,
                                ),
                            ) {
                                Text("Back")
                            }
                            Spacer(Modifier.width(MenuPilotSpacing.xs))
                        }
                        Text(
                            text = "Browse the menu",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        TextButton(
                            onClick = onReviewPicks,
                            enabled = picksCount > 0,
                            modifier = Modifier.heightIn(
                                min = MenuPilotDimensions.minimumTouchTarget,
                            ),
                        ) {
                            Text("My Picks ($picksCount)")
                        }
                    }
                    PrimaryAction(
                        label = "Ask MenuPilot",
                        onClick = onAskAssistant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = MenuPilotSpacing.xl,
                        vertical = MenuPilotSpacing.md,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(MenuPilotSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showBackAction) {
                        TextButton(
                            onClick = onBack,
                            modifier = Modifier.heightIn(
                                min = MenuPilotDimensions.minimumTouchTarget,
                            ),
                        ) {
                            Text("Back")
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Browse the menu",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            text = "Explore everything, then ask a waiter when you are ready.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = onReviewPicks,
                        enabled = picksCount > 0,
                        modifier = Modifier.heightIn(
                            min = MenuPilotDimensions.minimumTouchTarget,
                        ),
                    ) {
                        Text("My Picks ($picksCount)")
                    }
                    PrimaryAction(
                        label = "Ask MenuPilot",
                        onClick = onAskAssistant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseIntro(
    hasConfirmedIntent: Boolean,
    onAskAssistant: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(MenuPilotRadii.extraLarge),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().padding(MenuPilotSpacing.xl),
        ) {
            val compact = maxWidth < 620.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.md)) {
                    BrowseIntroCopy(hasConfirmedIntent = hasConfirmedIntent)
                    PrimaryAction(
                        label = "Help me find my best dish",
                        onClick = onAskAssistant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MenuPilotSpacing.xl),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        BrowseIntroCopy(hasConfirmedIntent = hasConfirmedIntent)
                    }
                    PrimaryAction(
                        label = "Help me find my best dish",
                        onClick = onAskAssistant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseIntroCopy(hasConfirmedIntent: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.xs)) {
        Text(
            text = "Not sure what fits?",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = if (hasConfirmedIntent) {
                "Your confirmed needs stay active. MenuPilot can turn them into a short, personal set of suggestions."
            } else {
                "Tell MenuPilot about allergies, dietary needs, mood, budget, or flavors and get a personal shortlist."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun ConfirmedNeedsPanel(chips: List<IntentChipModel>) {
    Surface(
        shape = RoundedCornerShape(MenuPilotRadii.large),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MenuPilotSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.sm),
        ) {
            Text(
                text = "Confirmed needs are active",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Safety and dietary checks remain visible while you browse.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MenuPilotSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.sm),
            ) {
                chips.forEach { chip ->
                    Surface(
                        shape = RoundedCornerShape(MenuPilotRadii.pill),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(
                            text = "✓  ${chip.label}",
                            modifier = Modifier.padding(
                                horizontal = MenuPilotSpacing.md,
                                vertical = MenuPilotSpacing.sm,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HiddenForNeedsNotice(hiddenCount: Int) {
    Surface(
        shape = RoundedCornerShape(MenuPilotRadii.large),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(MenuPilotSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(MenuPilotSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("◈", style = MaterialTheme.typography.titleLarge)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$hiddenCount ${if (hiddenCount == 1) "item is" else "items are"} hidden for your confirmed needs",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "MenuPilot does not loosen confirmed constraints just to show more choices.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun CategoryFilters(
    categories: List<DishCategory>,
    selectedCategory: String?,
    onSelected: (String?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MenuPilotSpacing.sm),
    ) {
        IntentChip(
            label = "All",
            selected = selectedCategory == null,
            onClick = { onSelected(null) },
        )
        categories.forEach { category ->
            IntentChip(
                label = category.label,
                selected = selectedCategory == category.name,
                onClick = { onSelected(category.name) },
            )
        }
    }
}

@Composable
private fun ResultsSummary(
    visibleCount: Int,
    totalCount: Int,
    searchQuery: String,
    salesWindowLabel: String,
    salesSourceLabel: String,
    salesUpdatedLabel: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MenuPilotSpacing.lg),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when {
                    searchQuery.isNotBlank() -> "$visibleCount ${if (visibleCount == 1) "result" else "results"}"
                    visibleCount == totalCount -> "$totalCount menu ${if (totalCount == 1) "item" else "items"}"
                    else -> "$visibleCount of $totalCount menu items"
                },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Popularity: $salesWindowLabel • $salesSourceLabel • $salesUpdatedLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "Tap a dish for details",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun BrowseDishCard(
    result: CuratedDish,
    salesWindowLabel: String,
    isInPicks: Boolean,
    hasConfirmedNeeds: Boolean,
    onClick: () -> Unit,
) {
    val dish = result.dish
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(MenuPilotRadii.extraLarge),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 4.dp,
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().height(164.dp)
                    .background(Color(dish.photoAccent)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = dish.photoSymbol,
                    style = MaterialTheme.typography.displayLarge,
                )
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(MenuPilotSpacing.md),
                    shape = RoundedCornerShape(MenuPilotRadii.pill),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Text(
                        text = result.salesEvidence?.let { evidence ->
                            val prefix = if (evidence.isBestseller) "★ Bestseller • " else ""
                            "$prefix${evidence.orderCount} ordered " +
                                salesWindowLabel.lowercase()
                        } ?: "Popularity unavailable",
                        modifier = Modifier.padding(
                            horizontal = MenuPilotSpacing.md,
                            vertical = MenuPilotSpacing.sm,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(MenuPilotSpacing.md),
                    shape = RoundedCornerShape(MenuPilotRadii.pill),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                ) {
                    Text(
                        text = dish.category.label,
                        modifier = Modifier.padding(
                            horizontal = MenuPilotSpacing.md,
                            vertical = MenuPilotSpacing.sm,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(MenuPilotSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.md),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MenuPilotSpacing.md),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = dish.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${dish.prepMinutes} min • ${dish.preparationNote}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = formatPeso(dish.priceMinor),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text = dish.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                if (dish.ingredients.isNotEmpty()) {
                    Text(
                        text = "Key ingredients: ${dish.ingredients.take(4).joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MenuPilotSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.sm),
                ) {
                    (dish.dietaryTags + dish.flavorTags).distinct().take(4).forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(MenuPilotRadii.pill),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = tag,
                                modifier = Modifier.padding(
                                    horizontal = MenuPilotSpacing.md,
                                    vertical = MenuPilotSpacing.xs,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.xs)) {
                    Text(
                        text = if (hasConfirmedNeeds) {
                            "MenuPilot evaluation"
                        } else {
                            "Menu information"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (hasConfirmedNeeds) {
                        EvaluationBadge(result = result)
                    } else {
                        SafetyStatusBadge(
                            label = "Listed in the current menu record • ask waiter about allergies",
                            tone = StatusTone.Neutral,
                        )
                    }
                }

                if (isInPicks) {
                    Text(
                        text = "✓ Saved in My Picks",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                PrimaryAction(
                    label = "View dish details",
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun EmptyBrowseResults(
    hasFilters: Boolean,
    onClear: () -> Unit,
    onAskAssistant: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(MenuPilotRadii.extraLarge),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MenuPilotSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.md),
        ) {
            Text(
                text = "No dishes found",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = if (hasFilters) {
                    "Try another search or category. Your confirmed needs will stay active."
                } else {
                    "Ask MenuPilot or speak with a waiter for help."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MenuPilotSpacing.md)) {
                if (hasFilters) {
                    TextButton(
                        onClick = onClear,
                        modifier = Modifier.heightIn(
                            min = MenuPilotDimensions.minimumTouchTarget,
                        ),
                    ) {
                        Text("Clear filters")
                    }
                }
                PrimaryAction(
                    label = "Ask MenuPilot",
                    onClick = onAskAssistant,
                )
            }
        }
    }
}

@Composable
private fun WaiterConfirmationPanel(onCallStaff: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(MenuPilotRadii.extraLarge),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().padding(MenuPilotSpacing.xl),
        ) {
            val compact = maxWidth < 620.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.md)) {
                    WaiterConfirmationCopy()
                    CallStaffAction(
                        label = "Call waiter",
                        onClick = onCallStaff,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MenuPilotSpacing.xl),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        WaiterConfirmationCopy()
                    }
                    CallStaffAction(
                        label = "Call waiter",
                        onClick = onCallStaff,
                    )
                }
            }
        }
    }
}

@Composable
private fun WaiterConfirmationCopy() {
    Column(verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.xs)) {
        Text(
            text = "Your waiter confirms the final choice",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "MenuPilot helps you explore. A waiter and the kitchen confirm ingredients, availability, and what is prepared for you.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
