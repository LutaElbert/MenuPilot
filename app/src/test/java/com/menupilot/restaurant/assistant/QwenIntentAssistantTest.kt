package com.menupilot.restaurant.assistant

import com.menupilot.domain.AvoidanceReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QwenIntentAssistantTest {

    @Test
    fun `valid model JSON becomes a guest-reviewable intent`() = kotlinx.coroutines.test.runTest {
        val interpreter = QwenIntentInterpreter(FakeGenerator(validModelOutput()))

        val result = interpreter.interpret(
            "May allergy ako sa mani. Gusto ko ng maanghang under ₱500.",
        ) as AssistantInterpretation.Ready

        assertEquals(AssistantSource.ON_DEVICE_QWEN, result.source)
        assertEquals("Taglish", result.summary.detectedLanguage)
        assertEquals(listOf("peanut"), result.summary.allergens)
        assertEquals(AvoidanceReason.ALLERGY, result.summary.avoidanceReasons["peanut"])
        assertEquals(listOf("spicy"), result.summary.preferredAttributes)
        assertEquals(50_000L, result.summary.maximumPriceMinor)
    }

    @Test
    fun `recognized allergy intent uses the instant deterministic path`() =
        kotlinx.coroutines.test.runTest {
            var generated = false
            val qwen = QwenIntentInterpreter(
                object : OnDeviceTextGenerator {
                    override suspend fun generate(prompt: String): String {
                        generated = true
                        return validModelOutput()
                    }
                },
            )
            val hybrid = HybridIntentAssistant(DeterministicIntentAssistant(), qwen)

            val result = hybrid.interpretAsync(
                "I'm allergic to peanuts and want something spicy under ₱500.",
            ) as AssistantInterpretation.Ready

            assertEquals(AssistantSource.DETERMINISTIC_RULES, result.source)
            assertEquals(listOf("peanut"), result.summary.allergens)
            assertEquals(AvoidanceReason.ALLERGY, result.summary.avoidanceReasons["peanut"])
            assertTrue(!generated)
        }

    @Test
    fun `recognized diet and budget use the instant deterministic path`() =
        kotlinx.coroutines.test.runTest {
            var generated = false
            val qwen = QwenIntentInterpreter(
                object : OnDeviceTextGenerator {
                    override suspend fun generate(prompt: String): String {
                        generated = true
                        return validModelOutput()
                    }
                },
            )
            val hybrid = HybridIntentAssistant(DeterministicIntentAssistant(), qwen)

            val result = hybrid.interpretAsync(
                "I'm vegetarian and want something spicy under ₱500.",
            ) as AssistantInterpretation.Ready

            assertEquals(listOf("vegetarian"), result.summary.diets)
            assertEquals(50_000L, result.summary.maximumPriceMinor)
            assertTrue(!generated)
        }

    @Test
    fun `missing model falls back to deterministic interpretation with attribution`() =
        kotlinx.coroutines.test.runTest {
            val qwen = QwenIntentInterpreter(
                object : OnDeviceTextGenerator {
                    override suspend fun generate(prompt: String): String {
                        throw ModelGenerationException.NotInstalled("/models/qwen.litertlm")
                    }
                },
            )
            val hybrid = HybridIntentAssistant(DeterministicIntentAssistant(), qwen)

            val result = hybrid.interpretAsync("Find me something savory")

            assertTrue(result is AssistantInterpretation.NeedsClarification)
            assertEquals(AssistantSource.MODEL_FALLBACK, result.source)
            assertEquals(
                AssistantFallbackReason.MODEL_NOT_INSTALLED,
                result.fallbackReason,
            )
        }

    @Test
    fun `contract rejection falls back instead of exposing model text`() =
        kotlinx.coroutines.test.runTest {
            val hybrid = hybridWith("""{"dish":"This is guaranteed safe"}""")

            val result = hybrid.interpretAsync("Find me something savory")

            assertTrue(result is AssistantInterpretation.NeedsClarification)
            assertEquals(AssistantSource.MODEL_FALLBACK, result.source)
            assertEquals(
                AssistantFallbackReason.MODEL_OUTPUT_REJECTED,
                result.fallbackReason,
            )
        }

    @Test
    fun `unknown-language allergy fails closed when the model is unavailable`() =
        kotlinx.coroutines.test.runTest {
            val qwen = QwenIntentInterpreter(
                object : OnDeviceTextGenerator {
                    override suspend fun generate(prompt: String): String {
                        throw ModelGenerationException.NotInstalled("/models/qwen.litertlm")
                    }
                },
            )
            val hybrid = HybridIntentAssistant(DeterministicIntentAssistant(), qwen)

            val result = hybrid.interpretAsync("Soy alérgico a los cacahuetes")

            assertTrue(result is AssistantInterpretation.NeedsClarification)
            assertEquals(AssistantSource.MODEL_FALLBACK, result.source)
            assertEquals(
                AssistantFallbackReason.MODEL_NOT_INSTALLED,
                result.fallbackReason,
            )
        }

    @Test
    fun `schema-valid empty model intent cannot become an implicit popular menu`() =
        kotlinx.coroutines.test.runTest {
            val emptyOutput = validModelOutput()
                .replace(
                    """[{"id":"peanut","reason":"ALLERGY"}]""",
                    "[]",
                )
                .replace("[\"spicy\"]", "[]")
                .replace("\"maximumPriceMinor\":50000", "\"maximumPriceMinor\":null")
            val hybrid = hybridWith(emptyOutput)

            val result = hybrid.interpretAsync("Soy alérgico a los cacahuetes")

            assertTrue(result is AssistantInterpretation.NeedsClarification)
            assertEquals(
                AssistantFallbackReason.MODEL_OUTPUT_REJECTED,
                result.fallbackReason,
            )
        }

    @Test
    fun `deterministic safety clarification bypasses model generation`() =
        kotlinx.coroutines.test.runTest {
            var generated = false
            val qwen = QwenIntentInterpreter(
                object : OnDeviceTextGenerator {
                    override suspend fun generate(prompt: String): String {
                        generated = true
                        return validModelOutput()
                    }
                },
            )
            val hybrid = HybridIntentAssistant(DeterministicIntentAssistant(), qwen)

            val result = hybrid.interpretAsync("Does the salad contain peanuts?")

            assertTrue(result is AssistantInterpretation.NeedsClarification)
            assertTrue(!generated)
        }

    private fun hybridWith(output: String): HybridIntentAssistant =
        HybridIntentAssistant(
            deterministicAssistant = DeterministicIntentAssistant(),
            qwenInterpreter = QwenIntentInterpreter(FakeGenerator(output)),
        )

    private class FakeGenerator(private val output: String) : OnDeviceTextGenerator {
        override suspend fun generate(prompt: String): String = output
    }

    private fun validModelOutput(): String =
        """
        {
          "schemaVersion":1,
          "action":"FILTER_MENU",
          "detectedLanguage":"Taglish",
          "allergens":[{"id":"peanut","reason":"ALLERGY"}],
          "diets":[],
          "preferences":["spicy"],
          "maximumPriceMinor":50000,
          "needsClarification":false,
          "clarificationMessage":null,
          "unresolvedTerms":[]
        }
        """.trimIndent()
}
