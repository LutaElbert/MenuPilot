package com.menupilot.restaurant.feature.catalog

import androidx.activity.ComponentActivity
import androidx.lifecycle.SavedStateHandle
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.menupilot.domain.MenuPolicyEngine
import com.menupilot.domain.RecommendationEngine
import com.menupilot.restaurant.app.MenuPilotViewModel
import com.menupilot.restaurant.assistant.DeterministicIntentAssistant
import com.menupilot.restaurant.data.FixtureMenuRepository
import com.menupilot.restaurant.feedback.FeedbackCommentGenerator
import com.menupilot.restaurant.staff.DemoStaffAuthorizer
import com.menupilot.restaurant.theme.MenuPilotTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogAdaptiveLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun compactPortrait_keepsRecommendationBadgesSeparate() {
        setCatalogContent(size = DpSize(400.dp, 1000.dp))

        val card = placedBounds(CatalogTestTags.recommendationCard(FlagshipDishId))
        val matchBadge = placedBounds(CatalogTestTags.matchBadge(FlagshipDishId))
        val popularityBadge = placedBounds(CatalogTestTags.popularityBadge(FlagshipDishId))

        assertContained(card, matchBadge, "Match badge")
        assertContained(card, popularityBadge, "Popularity badge")
        assertFalse(
            "Recommendation match and popularity badges must not overlap.",
            matchBadge.overlaps(popularityBadge),
        )
    }

    @Test
    fun compactPortrait_largeFont_keepsActionsAndBadgesUsable() {
        setCatalogContent(
            size = DpSize(400.dp, 1000.dp),
            fontScale = 1.5f,
        )

        composeRule.onNodeWithTag(CatalogTestTags.HeaderActions).assertIsDisplayed()
        val card = placedBounds(CatalogTestTags.recommendationCard(FlagshipDishId))
        val matchBadge = placedBounds(CatalogTestTags.matchBadge(FlagshipDishId))
        val popularityBadge = placedBounds(CatalogTestTags.popularityBadge(FlagshipDishId))

        assertContained(card, matchBadge, "Large-text match badge")
        assertContained(card, popularityBadge, "Large-text popularity badge")
        assertFalse(
            "Large text must not cause recommendation badges to overlap.",
            matchBadge.overlaps(popularityBadge),
        )
    }

    @Test
    fun mediumTablet_keepsInlineActionsWithoutForcingDesktopRail() {
        setCatalogContent(size = DpSize(610.dp, 1000.dp))

        composeRule.onNodeWithTag(CatalogTestTags.SideRail).assertDoesNotExist()
        composeRule.onNodeWithTag(CatalogTestTags.HeaderActions).assertIsDisplayed()
        composeRule
            .onNodeWithTag(CatalogTestTags.recommendationCard(FlagshipDishId))
            .assertIsDisplayed()
    }

    @Test
    fun expandedTablet_usesRailWhenHeightSupportsIt() {
        setCatalogContent(size = DpSize(1000.dp, 1000.dp))

        val root = placedBounds(CatalogTestTags.Root)
        val rail = placedBounds(CatalogTestTags.SideRail)
        assertEquals("Rail must start at the catalog's leading edge.", root.left, rail.left, 1f)
        assertEquals("Rail must fill the catalog height.", root.height, rail.height, 1f)
        assertTrue("Rail must leave room for recommendation content.", rail.width < root.width)
        composeRule.onNodeWithTag(CatalogTestTags.HeaderActions).assertDoesNotExist()
    }

    @Test
    fun shortExpandedWindow_keepsInlineActionsInsteadOfClippingRail() {
        setCatalogContent(size = DpSize(1000.dp, 400.dp))

        composeRule.onNodeWithTag(CatalogTestTags.SideRail).assertDoesNotExist()
        composeRule.onNodeWithTag(CatalogTestTags.HeaderActions).assertIsDisplayed()
    }

    private fun setCatalogContent(
        size: DpSize,
        fontScale: Float = 1f,
    ) {
        val model = confirmedPreviewModel()
        composeRule.setContent {
            val state by model.uiState.collectAsState()
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(size) then
                    DeviceConfigurationOverride.FontScale(fontScale),
            ) {
                MenuPilotTheme {
                    CatalogScreen(
                        state = state,
                        salesWindowLabel = model.salesWindowLabel,
                        salesSourceLabel = model.salesSourceLabel,
                        salesUpdatedLabel = model.salesUpdatedLabel,
                        onBack = {},
                        onBrowseMenu = {},
                        onDishClick = { _, _ -> },
                        onReviewOrder = {},
                        onCallStaff = {},
                    )
                }
            }
        }
    }

    private fun confirmedPreviewModel(): MenuPilotViewModel {
        val clock = Clock.fixed(Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC)
        return MenuPilotViewModel(
            menuRepository = FixtureMenuRepository(clock),
            intentAssistant = DeterministicIntentAssistant(),
            menuPolicyEngine = MenuPolicyEngine(),
            recommendationEngine = RecommendationEngine(),
            clock = clock,
            staffAuthorizer = DemoStaffAuthorizer(),
            feedbackCommentGenerator = FeedbackCommentGenerator(),
            savedStateHandle = SavedStateHandle(),
        ).apply {
            beginSession()
            updateQuery(FlagshipPrompt)
            submitQuery()
            check(confirmIntent())
        }
    }

    private fun placedBounds(tag: String): Rect {
        val bounds = composeRule
            .onNodeWithTag(tag, useUnmergedTree = true)
            .assertExists()
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(
            "$tag must be composed and laid out with positive bounds.",
            bounds.width > 0f && bounds.height > 0f,
        )
        return bounds
    }

    private fun assertContained(
        parent: Rect,
        child: Rect,
        label: String,
    ) {
        assertTrue(
            "$label must remain inside its recommendation card.",
            child.left >= parent.left &&
                child.top >= parent.top &&
                child.right <= parent.right &&
                child.bottom <= parent.bottom,
        )
    }

    private companion object {
        const val FlagshipDishId = "chili_lime_tofu"
        const val FlagshipPrompt =
            "I'm allergic to peanuts, vegetarian, and want something spicy under ₱500."
    }
}
