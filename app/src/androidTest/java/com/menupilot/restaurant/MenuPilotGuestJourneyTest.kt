package com.menupilot.restaurant

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.menupilot.restaurant.feature.catalog.CatalogTestTags
import com.menupilot.restaurant.feature.catalog.DishDetailTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Focused release-candidate journey for the restaurant-pilot contract.
 *
 * This deliberately follows the real Activity, Hilt graph, Navigation 3 back stack, deterministic
 * safety path, menu policy, optional-pairing boundary, and waiter-handoff revalidation. It stops at
 * feedback entry because feedback details have their own state and policy suites.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class MenuPilotGuestJourneyTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun allergyGuest_reachesHumanHandoff_withoutCreatingAnOrder() {
        composeRule
            .onNodeWithText("Ask MenuPilot")
            .assertIsDisplayed()
            .performClick()

        composeRule.onNode(hasSetTextAction()).performTextInput(FlagshipPrompt)
        composeRule.onNodeWithText("Send").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Confirm what I understood")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Peanut allergy", substring = true).assertExists()
        composeRule.onNodeWithText("Vegetarian", substring = true).assertExists()
        composeRule.onNodeWithText("Prefers Spicy", substring = true).assertExists()
        composeRule.onNodeWithText("Under ₱500", substring = true).assertExists()

        composeRule
            .onNodeWithText("Show my top 3 matches")
            .performScrollTo()
            .performClick()

        waitForTag(CatalogTestTags.viewDishAction(FlagshipDishId))
        composeRule
            .onNodeWithTag(CatalogTestTags.viewDishAction(FlagshipDishId))
            .assertExists()
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(DishDetailTestTags.AddToPicks)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Chili-Lime Tofu Bowl").assertExists()

        composeRule
            .onNodeWithTag(DishDetailTestTags.AddToPicks)
            .performScrollTo()
            .performClick()
        waitForText("Added to My Picks")
        clickFirstExistingText("‹ Menu", "‹ Back to menu")

        clickFirstExistingText("My Picks (1)", "My Picks")
        waitForText("Add to My Picks")
        composeRule
            .onAllNodesWithText("Add to My Picks")[0]
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Review My Picks").performScrollTo().performClick()
        waitForText("Calamansi Iced Tea")

        composeRule
            .onNodeWithText("Review with waiter")
            .performScrollTo()
            .performClick()
        waitForText("Ready to show My Picks")
        composeRule
            .onNodeWithText(
                "This reference is not an order confirmation. No payment has been taken.",
            )
            .assertExists()

        composeRule
            .onNodeWithText("Give feedback after the meal")
            .performScrollTo()
            .performClick()
        waitForText("How was your experience today?")
    }

    private fun clickFirstExistingText(vararg labels: String) {
        composeRule.waitUntil(timeoutMillis = ScreenTransitionTimeoutMillis) {
            labels.any { candidate ->
                composeRule.onAllNodesWithText(candidate).fetchSemanticsNodes().isNotEmpty()
            }
        }
        val label = labels.firstOrNull { candidate ->
            composeRule.onAllNodesWithText(candidate).fetchSemanticsNodes().isNotEmpty()
        } ?: error("None of the expected labels exist: ${labels.joinToString()}")
        composeRule.onNodeWithText(label).assertIsDisplayed().performClick()
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = ScreenTransitionTimeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = ScreenTransitionTimeoutMillis) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val FlagshipDishId = "chili_lime_tofu"
        const val FlagshipPrompt =
            "I'm allergic to peanuts, vegetarian, and want something spicy under ₱500."
        const val ScreenTransitionTimeoutMillis = 5_000L
    }
}
