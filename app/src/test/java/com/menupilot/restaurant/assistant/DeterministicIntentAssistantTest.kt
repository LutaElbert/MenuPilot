package com.menupilot.restaurant.assistant

import com.menupilot.domain.AvoidAllergen
import com.menupilot.domain.AvoidanceReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicIntentAssistantTest {
    private val assistant = DeterministicIntentAssistant()

    @Test
    fun `flagship query extracts allergy diet preference and peso budget`() {
        val result = assistant.interpret(
            "I'm allergic to peanuts, vegetarian, and want something spicy under ₱500.",
        )

        val ready = result.requireReady()
        val summary = ready.summary
        assertEquals(listOf("peanut"), summary.allergens)
        assertEquals(listOf("vegetarian"), summary.diets)
        assertEquals(listOf("spicy"), summary.preferredAttributes)
        assertEquals(50_000L, summary.maximumPriceMinor)
        assertEquals(DiningBudgetScope.PER_DISH, summary.budgetScope)
        assertTrue(summary.requiresStaffVerification)
        assertTrue(ready.clarificationMessage.contains("kitchen must still confirm"))

        val constraints = summary.intentDraft.constraints
        assertTrue(
            constraints.any {
                it.type == "AVOID" &&
                    it.targetType == "ALLERGEN" &&
                    it.canonicalId == "peanut" &&
                    it.reasonHint == "ALLERGY" &&
                    it.modality == "MUST"
            },
        )
        assertTrue(
            constraints.any {
                it.type == "REQUIRE" &&
                    it.targetType == "DIET" &&
                    it.canonicalId == "vegetarian"
            },
        )
        assertTrue(
            constraints.any {
                it.type == "PREFER" &&
                    it.targetType == "ATTRIBUTE" &&
                    it.canonicalId == "spicy"
            },
        )
        assertTrue(
            constraints.any {
                it.type == "LIMIT" &&
                    it.targetType == "PRICE_MINOR" &&
                    it.canonicalId == "50000"
            },
        )
    }

    @Test
    fun `ordinary price limit is explicitly scoped per dish`() {
        val summary = assistant.interpret(
            "I want something spicy under ₱500.",
        ).requireReady().summary

        assertEquals(50_000L, summary.maximumPriceMinor)
        assertEquals(DiningBudgetScope.PER_DISH, summary.budgetScope)
    }

    @Test
    fun `allergy without a named allergen asks for clarification`() {
        val result = assistant.interpret("I have a food allergy. What can I order?")

        assertTrue(result is AssistantInterpretation.NeedsClarification)
        val clarification = result as AssistantInterpretation.NeedsClarification
        assertTrue(clarification.message.contains("Which ingredient or allergen"))
    }

    @Test
    fun `neutral ingredient question asks what the allergen mention means`() {
        val result = assistant.interpret("Does the salad contain peanuts?")

        assertTrue(result is AssistantInterpretation.NeedsClarification)
        val clarification = result as AssistantInterpretation.NeedsClarification
        assertTrue(clarification.message.contains("menu question"))
    }

    @Test
    fun `ingredient preference is not silently converted into an allergy`() {
        val summary = assistant.interpret(
            "I like peanuts and want something spicy.",
        ).requireReady().summary

        assertTrue(summary.allergens.isEmpty())
        assertEquals(listOf("spicy"), summary.preferredAttributes)
        assertFalse(summary.requiresStaffVerification)
    }

    @Test
    fun `generic ingredient avoidance stays hard without claiming an allergy`() {
        val summary = assistant.interpret("No peanuts, please.").requireReady().summary

        assertEquals(listOf("peanut"), summary.allergens)
        assertEquals(AvoidanceReason.UNSPECIFIED, summary.avoidanceReasons["peanut"])
        assertEquals("Avoid Peanut", summary.chips.single().label)
        val constraint = summary.toConfirmedIntent("session").constraints.single()
        assertEquals(AvoidanceReason.UNSPECIFIED, (constraint as AvoidAllergen).reason)
    }

    @Test
    fun `not spicy is interpreted as mild instead of spicy`() {
        val summary = assistant.interpret(
            "I want something not spicy.",
        ).requireReady().summary

        assertEquals(listOf("mild"), summary.preferredAttributes)
        assertFalse("spicy" in summary.preferredAttributes)
    }

    @Test
    fun `negated allergy statement does not create an allergen constraint`() {
        val summary = assistant.interpret(
            "I'm not allergic to peanuts and I want something spicy.",
        ).requireReady().summary

        assertTrue(summary.allergens.isEmpty())
        assertEquals(listOf("spicy"), summary.preferredAttributes)
        assertFalse(summary.requiresStaffVerification)
    }

    @Test
    fun `veggie does not substring match the egg allergen`() {
        val summary = assistant.interpret(
            "I want a veggie option.",
        ).requireReady().summary

        assertEquals(listOf("vegetarian"), summary.diets)
        assertTrue(summary.allergens.isEmpty())
    }

    @Test
    fun `bestseller query becomes a popular preference`() {
        val summary = assistant.interpret("Show me your bestseller.").requireReady().summary

        assertEquals(listOf("popular"), summary.preferredAttributes)
        assertTrue(summary.allergens.isEmpty())
        assertTrue(summary.diets.isEmpty())
        assertNull(summary.maximumPriceMinor)
        assertFalse(summary.requiresStaffVerification)
    }

    @Test
    fun `recognizes Filipino Taglish and Cebuano menu language`() {
        val filipino = assistant.interpret(
            "Gusto ako ng maanghang at walang karne.",
        ).requireReady().summary
        assertEquals("Filipino", filipino.detectedLanguage)
        assertEquals(listOf("vegetarian"), filipino.diets)
        assertEquals(listOf("spicy"), filipino.preferredAttributes)

        val taglish = assistant.interpret(
            "I want something maanghang at walang karne.",
        ).requireReady().summary
        assertEquals("Taglish", taglish.detectedLanguage)
        assertEquals(listOf("vegetarian"), taglish.diets)
        assertEquals(listOf("spicy"), taglish.preferredAttributes)

        val cebuano = assistant.interpret(
            "Ganahan ko ug halang, walay karne.",
        ).requireReady().summary
        assertEquals("Cebuano", cebuano.detectedLanguage)
        assertEquals(listOf("vegetarian"), cebuano.diets)
        assertEquals(listOf("spicy"), cebuano.preferredAttributes)
    }

    @Test
    fun `Cebuano cannot-have phrase preserves shellfish as a hard avoidance`() {
        val summary = assistant.interpret(
            "Dili ko pwede og shellfish. Ganahan ko ug halang ug dali.",
        ).requireReady().summary

        assertEquals("Cebuano", summary.detectedLanguage)
        assertEquals(listOf("shellfish"), summary.allergens)
        assertEquals(AvoidanceReason.UNSPECIFIED, summary.avoidanceReasons["shellfish"])
        assertEquals(listOf("spicy", "quick"), summary.preferredAttributes)
        assertTrue(summary.requiresStaffVerification)
    }

    @Test
    fun `unrelated natural language query falls back to popular dishes`() {
        val summary = assistant.interpret(
            "Can you tell me where I should sit while I wait?",
        ).requireReady().summary

        assertEquals(listOf("popular"), summary.preferredAttributes)
        assertTrue(summary.allergens.isEmpty())
        assertTrue(summary.diets.isEmpty())
        assertNull(summary.maximumPriceMinor)
        assertFalse(summary.requiresStaffVerification)
    }

    private fun AssistantInterpretation.requireReady(): AssistantInterpretation.Ready {
        assertTrue("Expected Ready but was $this", this is AssistantInterpretation.Ready)
        return this as AssistantInterpretation.Ready
    }
}
