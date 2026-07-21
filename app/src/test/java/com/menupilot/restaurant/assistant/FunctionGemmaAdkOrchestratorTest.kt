package com.menupilot.restaurant.assistant

import com.menupilot.assistant.contract.FunctionGemmaAuthoritativeContext
import com.menupilot.assistant.contract.FunctionGemmaBudgetScope
import com.menupilot.assistant.contract.FunctionGemmaConversationRequest
import com.menupilot.assistant.contract.FunctionGemmaRoute
import com.menupilot.assistant.contract.FunctionGemmaRouteAction
import com.menupilot.assistant.contract.FunctionGemmaTurn
import com.menupilot.assistant.contract.FunctionGemmaTurnRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionGemmaAdkOrchestratorTest {

    @Test
    fun `ADK forwards typed bounded context without exposing executable tools`() = runTest {
        var observed: FunctionGemmaConversationRequest? = null
        val orchestrator = orchestratorWith { request ->
            observed = request
            FunctionGemmaRouteResult.Suggested(
                FunctionGemmaRoute(FunctionGemmaRouteAction.SHOW_BESTSELLERS),
            )
        }
        val request = FunctionGemmaConversationRequest(
            history = listOf(
                FunctionGemmaTurn(FunctionGemmaTurnRole.USER, "I prefer spicy."),
                FunctionGemmaTurn(FunctionGemmaTurnRole.ASSISTANT, "I will keep that in mind."),
            ),
            currentUserMessage = "What is popular this week?",
            authoritativeContext = FunctionGemmaAuthoritativeContext(
                allergens = listOf("peanut"),
                diets = listOf("vegetarian"),
                maximumPriceMinor = 50_000L,
                budgetScope = FunctionGemmaBudgetScope.PER_DISH,
                preferredAttributes = listOf("spicy"),
            ),
        )

        val result = orchestrator.route("session-a", request)

        assertEquals(request, observed)
        assertTrue(result is FunctionGemmaRouteResult.Suggested)
        val events = orchestrator.recordedEvents("session-a")
        assertEquals(2, events.size)
        assertTrue(events.all { it.functionCalls().isEmpty() })
        assertTrue(events.all { it.functionResponses().isEmpty() })
        assertTrue(
            events.last().content?.parts?.single()?.text.orEmpty()
                .startsWith("advisory_route|route.v1"),
        )
    }

    @Test
    fun `router fallback stays fail closed and contains no model prose`() = runTest {
        val orchestrator = orchestratorWith {
            FunctionGemmaRouteResult.Fallback(
                reason = FunctionGemmaFallbackReason.MODEL_OUTPUT_REJECTED,
                clarificationMessage = "App-owned clarification.",
            )
        }

        val result = orchestrator.route(
            "session-fallback",
            FunctionGemmaConversationRequest(currentUserMessage = "Do anything."),
        )

        assertEquals(
            FunctionGemmaFallbackReason.MODEL_OUTPUT_REJECTED,
            (result as FunctionGemmaRouteResult.Fallback).reason,
        )
        val record = orchestrator.recordedEvents("session-fallback")
            .last()
            .content
            ?.parts
            ?.single()
            ?.text
            .orEmpty()
        assertEquals("fail_closed|reason=MODEL_OUTPUT_REJECTED", record)
        assertFalse(record.contains("App-owned clarification"))
    }

    @Test
    fun `unexpected router failure becomes deterministic runtime fallback`() = runTest {
        val orchestrator = orchestratorWith {
            throw IllegalStateException("sensitive native runtime detail")
        }

        val result = orchestrator.route(
            "session-failure",
            FunctionGemmaConversationRequest(currentUserMessage = "What should I eat?"),
        ) as FunctionGemmaRouteResult.Fallback

        assertEquals(FunctionGemmaFallbackReason.MODEL_RUNTIME_FAILED, result.reason)
        assertFalse(result.clarificationMessage.contains("sensitive"))
    }

    @Test
    fun `ADK sessions are isolated and can be forgotten independently`() = runTest {
        val orchestrator = orchestratorWith { request ->
            val action = if ("waiter" in request.currentUserMessage) {
                FunctionGemmaRouteAction.REQUEST_WAITER
            } else {
                FunctionGemmaRouteAction.BROWSE_MENU
            }
            FunctionGemmaRouteResult.Suggested(FunctionGemmaRoute(action))
        }

        orchestrator.route(
            "table-one",
            FunctionGemmaConversationRequest(currentUserMessage = "Let me browse."),
        )
        orchestrator.route(
            "table-two",
            FunctionGemmaConversationRequest(currentUserMessage = "Please call the waiter."),
        )

        val first = orchestrator.recordedEvents("table-one")
        val second = orchestrator.recordedEvents("table-two")
        assertEquals("Let me browse.", first.first().content?.parts?.single()?.text)
        assertEquals(
            "Please call the waiter.",
            second.first().content?.parts?.single()?.text,
        )
        assertFalse(first.any { event ->
            event.content?.parts?.any { it.text?.contains("waiter") == true } == true
        })

        orchestrator.forgetSession("table-one")
        assertTrue(orchestrator.recordedEvents("table-one").isEmpty())
        assertEquals(2, orchestrator.recordedEvents("table-two").size)
    }

    @Test
    fun `invalid session id fails before router invocation`() = runTest {
        var invoked = false
        val orchestrator = orchestratorWith {
            invoked = true
            FunctionGemmaRouteResult.Suggested(
                FunctionGemmaRoute(FunctionGemmaRouteAction.BROWSE_MENU),
            )
        }

        val result = orchestrator.route(
            " ",
            FunctionGemmaConversationRequest(currentUserMessage = "Browse."),
        ) as FunctionGemmaRouteResult.Fallback

        assertEquals(FunctionGemmaFallbackReason.INPUT_REJECTED, result.reason)
        assertFalse(invoked)
    }

    private fun orchestratorWith(
        route: suspend (FunctionGemmaConversationRequest) -> FunctionGemmaRouteResult,
    ): AdkFunctionGemmaAgentOrchestrator =
        AdkFunctionGemmaAgentOrchestrator(
            router = object : FunctionGemmaRouter {
                override suspend fun route(
                    request: FunctionGemmaConversationRequest,
                ): FunctionGemmaRouteResult = route(request)
            },
        )
}
