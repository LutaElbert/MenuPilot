package com.menupilot.restaurant.assistant

import com.menupilot.assistant.contract.FunctionGemmaTurnRole
import com.menupilot.assistant.contract.FunctionGemmaBudgetScope
import com.menupilot.restaurant.app.BoundedConversationContext
import com.menupilot.restaurant.app.ConversationRole
import com.menupilot.restaurant.app.ConversationTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FunctionGemmaConversationAdapterTest {

    @Test
    fun `maps bounded ViewModel handoff without duplicating current guest turn`() {
        val request = BoundedConversationContext(
            sessionId = "session-7",
            recentTurns = listOf(
                ConversationTurn(ConversationRole.GUEST, "I am vegetarian."),
                ConversationTurn(ConversationRole.ASSISTANT, "I kept that preference."),
                ConversationTurn(ConversationRole.GUEST, "What is popular this week?"),
            ),
            authoritativeHardConstraints = listOf(
                "allergen:peanut:ALLERGY",
                "diet:vegetarian",
                "maximum_price_minor:50000",
                "budget_scope:per_dish",
            ),
            confirmedPreferences = listOf("spicy"),
            omittedTurnCount = 4,
            focusedDishName = "Chicken Inasal Bowl",
        ).toFunctionGemmaRequest(
            currentUserMessage = "What is popular this week?",
        )

        assertEquals(2, request.history.size)
        assertEquals(FunctionGemmaTurnRole.USER, request.history.first().role)
        assertEquals(listOf("peanut"), request.authoritativeContext.allergens)
        assertEquals(listOf("vegetarian"), request.authoritativeContext.diets)
        assertEquals(50_000L, request.authoritativeContext.maximumPriceMinor)
        assertEquals(
            FunctionGemmaBudgetScope.PER_DISH,
            request.authoritativeContext.budgetScope,
        )
        assertEquals(listOf("spicy"), request.authoritativeContext.preferredAttributes)
        assertEquals("Chicken Inasal Bowl", request.authoritativeContext.focusedDishName)
    }

    @Test
    fun `malformed app-owned constraint is never silently omitted`() {
        val context = BoundedConversationContext(
            sessionId = "session-8",
            recentTurns = emptyList(),
            authoritativeHardConstraints = listOf("allergen:unknown:ALLERGY"),
            confirmedPreferences = emptyList(),
            omittedTurnCount = 0,
        )

        assertThrows(FunctionGemmaInputRejectedException::class.java) {
            context.toFunctionGemmaRequest("Show my matches.")
        }
    }
}
