package com.menupilot.assistant.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionGemmaRouteContractTest {
    private val parser = FunctionGemmaRouteParser()

    @Test
    fun `accepts a single strict shape-menu route`() {
        val result = parser.parse(call(action = "SHAPE_MENU"))

        val route = (result as FunctionGemmaRouteParseResult.Accepted).route
        assertEquals(FunctionGemmaRouteAction.SHAPE_MENU, route.action)
        assertEquals(
            "route.v1|action=SHAPE_MENU|subject=NONE|period=NONE",
            route.toEvaluationContract(),
        )
    }

    @Test
    fun `accepts grounded lookup and bestseller route shapes`() {
        val detail = parser.parse(
            call(action = "EXPLAIN_DISH", subject = "chicken inasal"),
        ) as FunctionGemmaRouteParseResult.Accepted
        assertEquals("chicken inasal", detail.route.subject)

        val bestseller = parser.parse(
            call(action = "SHOW_BESTSELLERS", period = "THIS_WEEK"),
        ) as FunctionGemmaRouteParseResult.Accepted
        assertEquals(FunctionGemmaSalesPeriod.THIS_WEEK, bestseller.route.period)
    }

    @Test
    fun `rejects zero duplicate and unknown tool calls`() {
        assertRejected(emptyList(), "expected_exactly_one_tool_call")
        assertRejected(
            call(action = "SHAPE_MENU") + call(action = "BROWSE_MENU"),
            "expected_exactly_one_tool_call",
        )
        assertRejected(
            listOf(ModelFunctionCall("place_order", mapOf("action" to "SHAPE_MENU"))),
            "unsupported_tool",
        )
    }

    @Test
    fun `rejects unknown fields types and enum values`() {
        assertRejected(
            listOf(
                ModelFunctionCall(
                    FunctionGemmaRouteContract.TOOL_NAME,
                    mapOf("action" to "SHAPE_MENU", "execute" to true),
                ),
            ),
            "unexpected_argument_fields",
        )
        assertRejected(
            listOf(
                ModelFunctionCall(
                    FunctionGemmaRouteContract.TOOL_NAME,
                    mapOf("action" to 1),
                ),
            ),
            "invalid_action",
        )
        assertRejected(call(action = "PLACE_ORDER"), "invalid_action")
    }

    @Test
    fun `enforces action-specific subject and period fields`() {
        assertRejected(call(action = "EXPLAIN_DISH"), "subject_action_mismatch")
        assertRejected(
            call(action = "SHAPE_MENU", subject = "anything"),
            "subject_action_mismatch",
        )
        assertRejected(
            call(action = "SHOW_BESTSELLERS"),
            "bestseller_period_required",
        )
        assertRejected(
            call(action = "BROWSE_MENU", period = "THIS_WEEK"),
            "period_action_mismatch",
        )
    }

    @Test
    fun `rejects unsafe unbounded subject values`() {
        assertRejected(
            call(action = "EXPLAIN_DISH", subject = "x".repeat(161)),
            "invalid_subject",
        )
        assertRejected(
            call(action = "EXPLAIN_DISH", subject = "dish\u0000name"),
            "invalid_subject",
        )
        assertRejected(
            call(action = "EXPLAIN_DISH", subject = " "),
            "invalid_subject",
        )
    }

    @Test
    fun `tool schema is read-only bounded and covers every enum`() {
        val schema = FunctionGemmaRouteContract.toolDescriptionJson

        assertTrue(schema.contains("\"additionalProperties\": false"))
        assertTrue(schema.contains("\"maxLength\": 160"))
        FunctionGemmaRouteAction.entries.forEach { action ->
            assertTrue(schema.contains("\"${action.name}\""))
        }
        FunctionGemmaSalesPeriod.entries.forEach { period ->
            assertTrue(schema.contains("\"${period.name}\""))
        }
        assertTrue(!schema.contains("place_order"))
        assertTrue(!schema.contains("submit_review"))
    }

    @Test
    fun `qualification manifest has unique broad scenario IDs`() {
        val contract = FunctionGemmaRouteQualificationContract
        val ids = contract.scenarioIds

        assertTrue(ids.size >= 20)
        assertEquals(ids.size, ids.distinct().size)
        assertTrue("prompt-injection-forget-safety" in ids)
        assertTrue("new-session-isolation" in ids)
        assertTrue("budget-non-loosening" in ids)
        assertEquals(ids, contract.scenarios.map(FunctionGemmaRouteScenario::id))
        assertTrue(
            contract.scenarios.all {
                FunctionGemmaQualificationGate.SCHEMA in it.hardGates
            },
        )
        assertTrue(
            contract.scenarios
                .filter { FunctionGemmaQualificationGate.SAFETY in it.hardGates }
                .isNotEmpty(),
        )
    }

    private fun call(
        action: String,
        subject: String? = null,
        period: String? = null,
    ): List<ModelFunctionCall> = listOf(
        ModelFunctionCall(
            name = FunctionGemmaRouteContract.TOOL_NAME,
            arguments = buildMap {
                put("action", action)
                subject?.let { put("subject", it) }
                period?.let { put("period", it) }
            },
        ),
    )

    private fun assertRejected(
        calls: List<ModelFunctionCall>,
        reason: String,
    ) {
        val result = parser.parse(calls)
        assertTrue(result is FunctionGemmaRouteParseResult.Rejected)
        assertEquals(reason, (result as FunctionGemmaRouteParseResult.Rejected).reason)
    }
}
