package com.menupilot.restaurant.app

import androidx.lifecycle.SavedStateHandle
import com.menupilot.domain.AvoidanceReason
import com.menupilot.domain.MenuPolicyEngine
import com.menupilot.domain.RecommendationEngine
import com.menupilot.restaurant.assistant.DeterministicIntentAssistant
import com.menupilot.restaurant.assistant.DiningBudgetScope
import com.menupilot.restaurant.data.FixtureMenuRepository
import com.menupilot.restaurant.feedback.FeedbackCommentGenerator
import com.menupilot.restaurant.staff.DemoStaffAuthorizer
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSessionViewModelTest {

    @Test
    fun `follow-up turns amend preferences without losing earlier hard constraints`() {
        val viewModel = createViewModel()

        viewModel.updateQuery("I'm allergic to peanuts.")
        viewModel.submitQuery()
        viewModel.updateQuery("I also want something spicy under ₱500.")
        viewModel.submitQuery()

        val state = viewModel.uiState.value
        val intent = requireNotNull(state.intent)
        assertEquals(
            listOf(
                ConversationRole.ASSISTANT,
                ConversationRole.GUEST,
                ConversationRole.ASSISTANT,
                ConversationRole.GUEST,
                ConversationRole.ASSISTANT,
            ),
            state.conversationTurns.map(ConversationTurn::role),
        )
        assertTrue("peanut" in intent.allergens)
        assertEquals(AvoidanceReason.ALLERGY, intent.avoidanceReasons["peanut"])
        assertTrue("spicy" in intent.preferredAttributes)
        assertEquals(50_000L, intent.maximumPriceMinor)
        assertEquals(DiningBudgetScope.PER_DISH, intent.budgetScope)
        assertTrue(state.query.isBlank())
        assertTrue(state.canConfirmIntent)
    }

    @Test
    fun `ambiguous health follow-up retains prior needs and blocks catalog confirmation`() {
        val viewModel = createViewModel()
        viewModel.updateQuery("I'm allergic to peanuts.")
        viewModel.submitQuery()

        viewModel.updateQuery("I react to something in the sauce.")
        viewModel.submitQuery()

        val state = viewModel.uiState.value
        assertEquals(listOf("peanut"), requireNotNull(state.intent).allergens)
        assertTrue(state.conversationNeedsClarification)
        assertFalse(state.canConfirmIntent)
        assertFalse(viewModel.confirmIntent())
        assertTrue(state.curatedDishes.isEmpty())
        assertTrue(
            state.conversationTurns.last().message.contains(
                "earlier confirmed needs are still retained",
                ignoreCase = true,
            ),
        )
    }

    @Test
    fun `chip removal is explicit and a new session clears the shared tablet transcript`() {
        val viewModel = createViewModel()
        viewModel.updateQuery("I'm allergic to peanuts and I want something spicy.")
        viewModel.submitQuery()
        val previousSessionId = viewModel.uiState.value.sessionId

        viewModel.removeIntentChip("allergen:peanut")

        val amended = viewModel.uiState.value
        assertTrue(requireNotNull(amended.intent).allergens.isEmpty())
        assertTrue(
            amended.conversationTurns.last().message.contains(
                "Removed Peanut allergy",
                ignoreCase = true,
            ),
        )

        viewModel.beginSession()

        val reset = viewModel.uiState.value
        assertNotEquals(previousSessionId, reset.sessionId)
        assertEquals(
            listOf(
                ConversationTurn(
                    ConversationRole.ASSISTANT,
                    DEFAULT_CONVERSATION_GREETING,
                ),
            ),
            reset.conversationTurns,
        )
        assertTrue(reset.query.isBlank())
        assertEquals(null, reset.intent)
        assertFalse(reset.canConfirmIntent)
    }

    @Test
    fun `process recreation restores conversation and draft but not derived ordering state`() {
        val savedStateHandle = SavedStateHandle()
        val first = createViewModel(savedStateHandle)
        first.updateQuery(
            "I'm allergic to peanuts, want something spicy, and need the whole order under ₱500.",
        )
        first.submitQuery()
        assertTrue(first.confirmIntent())
        first.uiState.value.curatedDishes.first { it.canAddToOrder }.let { match ->
            first.addDishToShortlist(match.dish.id.value, match.dish.variantId.value)
        }
        first.updateQuery("Also keep it light")
        val expectedTurns = first.conversationTranscript()
        val expectedIntent = requireNotNull(first.uiState.value.intent)

        val restored = createViewModel(savedStateHandle).uiState.value

        assertEquals(expectedTurns, restored.conversationTurns)
        assertEquals("Also keep it light", restored.query)
        val restoredIntent = requireNotNull(restored.intent)
        assertEquals(expectedIntent.allergens, restoredIntent.allergens)
        assertEquals(
            expectedIntent.preferredAttributes,
            restoredIntent.preferredAttributes,
        )
        assertEquals(50_000L, restoredIntent.maximumPriceMinor)
        assertEquals(DiningBudgetScope.WHOLE_ORDER, restoredIntent.budgetScope)
        assertTrue(restored.curatedDishes.isEmpty())
        assertTrue(restored.cartEntries.isEmpty())
        assertEquals(null, restored.confirmedMenuRevision)
        assertEquals(null, restored.waiterHandoff)
    }

    @Test
    fun `bounded model context trims prose but carries every hard constraint separately`() {
        val viewModel = createViewModel()
        viewModel.updateQuery(
            "Keep my whole order under ₱500; I'm allergic to peanuts and vegetarian.",
        )
        viewModel.submitQuery()
        repeat(7) { index ->
            viewModel.updateQuery(
                if (index % 2 == 0) {
                    "I would also like something light."
                } else {
                    "What is popular this week?"
                },
            )
            viewModel.submitQuery()
        }

        val context = viewModel.boundedConversationContext()

        assertTrue(context.recentTurns.size <= 8)
        assertTrue(context.recentTurns.sumOf { it.message.length } <= 3_000)
        assertTrue(context.omittedTurnCount > 0)
        assertTrue("allergen:peanut:ALLERGY" in context.authoritativeHardConstraints)
        assertTrue("diet:vegetarian" in context.authoritativeHardConstraints)
        assertTrue("maximum_price_minor:50000" in context.authoritativeHardConstraints)
        assertTrue("budget_scope:whole_order" in context.authoritativeHardConstraints)
    }

    @Test
    fun `later soft preference wins while a higher budget cannot loosen the ceiling`() {
        val viewModel = createViewModel()
        viewModel.updateQuery("Keep my whole order under ₱500 and make it spicy.")
        viewModel.submitQuery()
        viewModel.updateQuery("Actually make it mild, with a budget of ₱700.")
        viewModel.submitQuery()

        val intent = requireNotNull(viewModel.uiState.value.intent)

        assertEquals(50_000L, intent.maximumPriceMinor)
        assertEquals(DiningBudgetScope.WHOLE_ORDER, intent.budgetScope)
        assertTrue("mild" in intent.preferredAttributes)
        assertFalse("spicy" in intent.preferredAttributes)
        assertTrue(
            intent.sourceQuery.contains("whole order", ignoreCase = true),
        )
        assertTrue(
            "budget_scope:whole_order" in
                viewModel.boundedConversationContext().authoritativeHardConstraints,
        )
    }

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): MenuPilotViewModel {
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
            savedStateHandle = savedStateHandle,
        )
    }
}
