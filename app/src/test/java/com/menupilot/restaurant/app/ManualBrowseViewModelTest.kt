package com.menupilot.restaurant.app

import androidx.lifecycle.SavedStateHandle
import com.menupilot.domain.MenuPolicyEngine
import com.menupilot.domain.RecommendationEngine
import com.menupilot.restaurant.assistant.DeterministicIntentAssistant
import com.menupilot.restaurant.data.FixtureMenuRepository
import com.menupilot.restaurant.feedback.FeedbackCommentGenerator
import com.menupilot.restaurant.staff.DemoStaffAuthorizer
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualBrowseViewModelTest {

    @Test
    fun `neutral browse evaluates every fixture dish without artificial preferences`() {
        val viewModel = createViewModel()

        assertTrue(viewModel.beginBrowseSession())

        val state = viewModel.uiState.value
        val intent = requireNotNull(state.intent)
        assertTrue(intent.allergens.isEmpty())
        assertTrue(intent.avoidanceReasons.isEmpty())
        assertTrue(intent.diets.isEmpty())
        assertTrue(intent.preferredAttributes.isEmpty())
        assertNull(intent.maximumPriceMinor)
        assertEquals(viewModel.allDishes.size, state.curatedDishes.size)
        assertEquals(
            viewModel.allDishes.map { it.id.value }.toSet(),
            state.curatedDishes.map { it.dish.id.value }.toSet(),
        )
        assertEquals(0, state.excludedDishCount)
        assertNotNull(state.confirmedMenuRevision)
        assertNotNull(state.confirmedPolicyVersion)
        assertNotNull(state.confirmedIntentFingerprint)
        assertTrue(state.hasConfirmedCatalog)
        assertTrue(state.assistantMessage.contains("full menu", ignoreCase = true))
        assertTrue(
            state.curatedDishes.all {
                "Available on today’s menu" in it.matchReasons
            },
        )
    }

    @Test
    fun `neutral browse can shortlist a dish and create a guarded waiter handoff`() {
        val viewModel = createViewModel()
        assertTrue(viewModel.beginBrowseSession())
        val dish = viewModel.uiState.value.curatedDishes.first { it.canAddToOrder }

        viewModel.addDishToShortlist(dish.dish.id.value, dish.dish.variantId.value)

        val shortlisted = viewModel.uiState.value
        assertEquals(listOf(dish.dish.id.value), shortlisted.cartDishIds)
        assertTrue(shortlisted.canCreateWaiterHandoff)
        assertTrue(viewModel.createWaiterHandoff())
        assertNotNull(viewModel.uiState.value.handoffReference)
    }

    @Test
    fun `confirmed peanut allergy catalog remains filtered and staff guarded during exploration`() {
        val viewModel = createViewModel()
        viewModel.beginSession()
        viewModel.updateQuery("I'm allergic to peanuts.")
        viewModel.submitQuery()
        assertTrue(viewModel.confirmIntent())

        val catalog = viewModel.uiState.value.curatedDishes
        assertFalse(catalog.any { it.dish.id.value == "garden_kare_kare" })
        val insufficientData = catalog.single { it.dish.id.value == "miso_eggplant" }
        assertTrue(insufficientData.requiresStaffCheck)
        assertFalse(insufficientData.canAddToOrder)

        viewModel.addDishToShortlist(
            insufficientData.dish.id.value,
            insufficientData.dish.variantId.value,
        )
        assertTrue(viewModel.uiState.value.cartEntries.isEmpty())
        assertTrue(
            viewModel.uiState.value.staffRequestMessage.orEmpty()
                .contains("Staff must verify"),
        )

        viewModel.addDishToShortlist("chili_lime_tofu", "standard")
        assertTrue(viewModel.uiState.value.requiresStaffAcknowledgment)
    }

    private fun createViewModel(): MenuPilotViewModel {
        val clock = Clock.fixed(
            Instant.parse("2026-07-19T12:00:00Z"),
            ZoneOffset.UTC,
        )
        return MenuPilotViewModel(
            menuRepository = FixtureMenuRepository(clock),
            intentAssistant = DeterministicIntentAssistant(),
            menuPolicyEngine = MenuPolicyEngine(),
            recommendationEngine = RecommendationEngine(),
            clock = clock,
            staffAuthorizer = DemoStaffAuthorizer(),
            feedbackCommentGenerator = FeedbackCommentGenerator(),
            savedStateHandle = SavedStateHandle(),
        )
    }
}
