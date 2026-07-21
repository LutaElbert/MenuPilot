package com.menupilot.restaurant.assistant

import com.menupilot.assistant.contract.QwenDishInsightModelInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QwenDishInsightServiceTest {
    @Test
    fun `immediate insight renders grounded copy without invoking Qwen`() {
        var generated = false
        val service = GroundedQwenDishInsightService(
            generator = QwenDishInsightSelectionGenerator {
                generated = true
                error("must not run")
            },
        )

        val result = service.immediateInsight(standardFacts())

        assertFalse(generated)
        assertEquals(DishInsightSource.DETERMINISTIC_GROUNDED, result.source)
        assertEquals(null, result.fallbackReason)
        assertEquals(
            "The restaurant describes Fire-Roasted Miso Eggplant as spicy and smoky.",
            result.text,
        )
    }

    @Test
    fun `Qwen selects evidence but never authors the visible sentence`() = runTest {
        val service = GroundedQwenDishInsightService(
            generator = QwenDishInsightSelectionGenerator {
                """
                {
                  "schemaVersion":1,
                  "action":"select_grounded_dish_insight",
                  "dishToken":"miso_eggplant:standard",
                  "angle":"PROFILE",
                  "evidenceIds":["profile.1"]
                }
                """.trimIndent()
            },
        )

        val result = service.insight(standardFacts())

        assertEquals(DishInsightSource.QWEN_GROUNDED_SELECTION, result.source)
        assertEquals(listOf("profile.1"), result.evidenceIds)
        assertEquals(
            "The restaurant describes Fire-Roasted Miso Eggplant as smoky.",
            result.text,
        )
        assertEquals(null, result.fallbackReason)
    }

    @Test
    fun `missing model immediately returns deterministic grounded copy`() = runTest {
        val service = GroundedQwenDishInsightService(
            generator = QwenDishInsightSelectionGenerator {
                throw ModelGenerationException.NotInstalled("/models/qwen.litertlm")
            },
        )

        val result = service.insight(standardFacts())

        assertEquals(DishInsightSource.DETERMINISTIC_GROUNDED, result.source)
        assertEquals(DishInsightFallbackReason.MODEL_NOT_INSTALLED, result.fallbackReason)
        assertEquals(listOf("profile.0", "profile.1"), result.evidenceIds)
        assertEquals(
            "The restaurant describes Fire-Roasted Miso Eggplant as spicy and smoky.",
            result.text,
        )
    }

    @Test
    fun `model timeout has one bounded attempt and deterministic fallback`() = runTest {
        var attempts = 0
        val service = GroundedQwenDishInsightService(
            generator = QwenDishInsightSelectionGenerator {
                attempts += 1
                delay(Long.MAX_VALUE)
                error("unreachable")
            },
            modelBudgetMillis = 100L,
        )

        val result = service.insight(standardFacts())

        assertEquals(1, attempts)
        assertEquals(DishInsightFallbackReason.MODEL_TIMEOUT, result.fallbackReason)
        assertEquals(100L, testScheduler.currentTime)
    }

    @Test
    fun `model prose safety claims and malformed output are never displayed`() = runTest {
        val unsafeRawOutput = "This dish is guaranteed allergy-safe and costs only ₱420."
        val service = GroundedQwenDishInsightService(
            generator = QwenDishInsightSelectionGenerator { unsafeRawOutput },
        )

        val result = service.insight(standardFacts())

        assertEquals(DishInsightFallbackReason.MODEL_OUTPUT_REJECTED, result.fallbackReason)
        assertFalse(result.text.contains("safe", ignoreCase = true))
        assertFalse(result.text.contains("₱"))
        assertFalse(result.text.contains("420"))
        assertFalse(result.text.contains(unsafeRawOutput))
    }

    @Test
    fun `prohibited restaurant fields never enter the model allowlist`() = runTest {
        var captured: QwenDishInsightModelInput? = null
        val service = GroundedQwenDishInsightService(
            generator = QwenDishInsightSelectionGenerator { input ->
                captured = input
                """
                {
                  "schemaVersion":1,
                  "action":"select_grounded_dish_insight",
                  "dishToken":"garden_kare_kare:standard",
                  "angle":"CATEGORY",
                  "evidenceIds":["category.0"]
                }
                """.trimIndent()
            },
        )
        val facts = GroundedDishInsightFacts(
            dishToken = "garden_kare_kare:standard",
            dishName = "Garden Kare-Kare",
            categoryLabel = "Mains",
            profileTags = listOf(
                "Contains peanuts",
                "Bestseller this week",
                "Only ₱450",
                "Available now",
                "Rich",
            ),
        )

        val result = service.insight(facts)

        assertEquals(listOf("Rich", "Mains"), captured?.evidence?.map { it.value })
        assertEquals("Garden Kare-Kare appears in the menu's mains category.", result.text)
        assertFalse(captured.toString().contains("peanut", ignoreCase = true))
        assertFalse(captured.toString().contains("bestseller", ignoreCase = true))
        assertFalse(captured.toString().contains("₱"))
        assertFalse(captured.toString().contains("available", ignoreCase = true))
    }

    @Test
    fun `invalid identity and empty evidence use neutral app owned fallback without model`() =
        runTest {
            var generated = false
            val service = GroundedQwenDishInsightService(
                generator = QwenDishInsightSelectionGenerator {
                    generated = true
                    error("must not run")
                },
            )

            val result = service.insight(
                GroundedDishInsightFacts(
                    dishToken = "invalid token with spaces",
                    dishName = "\u0000",
                    categoryLabel = "",
                    profileTags = emptyList(),
                ),
            )

            assertFalse(generated)
            assertEquals(DishInsightFallbackReason.NO_USABLE_EVIDENCE, result.fallbackReason)
            assertEquals(
                "Ask your waiter for the restaurant's description of this dish.",
                result.text,
            )
        }

    @Test
    fun `unknown evidence and stale dish output cannot cross contaminate dishes`() = runTest {
        val service = GroundedQwenDishInsightService(
            generator = QwenDishInsightSelectionGenerator {
                """
                {
                  "schemaVersion":1,
                  "action":"select_grounded_dish_insight",
                  "dishToken":"another_dish:standard",
                  "angle":"PROFILE",
                  "evidenceIds":["invented.0"]
                }
                """.trimIndent()
            },
        )

        val result = service.insight(standardFacts())

        assertEquals(DishInsightFallbackReason.MODEL_OUTPUT_REJECTED, result.fallbackReason)
        assertTrue(result.text.contains("spicy and smoky"))
        assertFalse(result.text.contains("invented"))
    }

    private fun standardFacts() = GroundedDishInsightFacts(
        dishToken = "miso_eggplant:standard",
        dishName = "Fire-Roasted Miso Eggplant",
        categoryLabel = "Mains",
        profileTags = listOf("Spicy", "Smoky"),
    )
}
