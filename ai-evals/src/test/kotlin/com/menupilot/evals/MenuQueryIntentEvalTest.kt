package com.menupilot.evals

import com.menupilot.assistant.contract.ModelIntentParseResult
import com.menupilot.assistant.contract.ModelIntentParser
import com.menupilot.assistant.contract.ModelAvoidanceReason
import com.menupilot.assistant.contract.QwenDeviceEvalArtifactCodec
import com.menupilot.assistant.contract.QwenDeviceEvalArtifactValidation
import com.menupilot.assistant.contract.QwenDeviceEvalContract
import com.menupilot.assistant.contract.QwenDeviceEvalOutput
import com.menupilot.assistant.contract.QwenIntentContract
import dev.dokimos.core.Assertions as DokimosAssertions
import dev.dokimos.core.Dataset
import dev.dokimos.core.Example
import dev.dokimos.core.evaluators.ExactMatchEvaluator
import dev.dokimos.junit.DatasetSource
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions as JunitAssertions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest

class MenuQueryIntentEvalTest {
    private val parser = ModelIntentParser()
    private val exactIntentContract = ExactMatchEvaluator.builder()
        .name("MenuPilot production intent.v1 exact contract")
        .threshold(1.0)
        .build()

    @ParameterizedTest(name = "{index}: production parser emits the expected intent.v1 contract")
    @DatasetSource("classpath:datasets/menu-query-intents.v1.json")
    fun productionParserMatchesVersionedContract(example: Example) {
        evaluate(example)
    }

    /**
     * Device instrumentation exports this same Dokimos dataset shape with real Qwen raw outputs.
     * Pass it with:
     *
     * ./gradlew :ai-evals:test -PmenupilotModelRuns=/absolute/path/qwen-runs.json
     */
    @Test
    fun optionalOnDeviceQwenRunsMatchVersionedContract() {
        val configuredPath = System.getProperty("menupilot.modelRuns").orEmpty()
        Assumptions.assumeTrue(
            configuredPath.isNotBlank(),
            "No on-device model artifact was supplied.",
        )
        val path = Path.of(configuredPath).toAbsolutePath().normalize()
        JunitAssertions.assertTrue(
            Files.isRegularFile(path),
            "The configured model-run artifact does not exist: $path",
        )

        val rawArtifact = Files.readString(path)
        requireCompleteDeviceArtifact(rawArtifact)
        val examples = Dataset.fromJson(path).examples()
        JunitAssertions.assertAll(
            "Every on-device Qwen scenario must be reported",
            examples.mapIndexed { index, example ->
                Executable { evaluateWithContext(index, example) }
            },
        )
    }

    @Test
    fun completeDeviceEnvelopePassesManifestAndDokimosContract() {
        val artifact = QwenDeviceEvalArtifactCodec.encode(completeDeviceOutputs())

        requireCompleteDeviceArtifact(artifact)
        val examples = Dataset.fromJson(artifact).examples()
        JunitAssertions.assertEquals(
            QwenDeviceEvalContract.scenarioIds.size,
            examples.size,
        )
        JunitAssertions.assertAll(
            "A complete shared envelope must remain Dokimos-compatible",
            examples.mapIndexed { index, example ->
                Executable { evaluateWithContext(index, example) }
            },
        )
    }

    @Test
    fun hostRejectsTruncatedDeviceEnvelopeBeforeDokimosScoring() {
        val artifact = QwenDeviceEvalArtifactCodec.encode(
            completeDeviceOutputs().dropLast(1),
        )

        val failure = JunitAssertions.assertThrows(AssertionError::class.java) {
            requireCompleteDeviceArtifact(artifact)
        }
        JunitAssertions.assertTrue(
            failure.message.orEmpty().contains("missing_scenario_ids"),
        )
    }

    @Test
    fun regressionDatasetMeetsRestaurantPilotBreadthGate() {
        val dataset = loadRegressionDataset()
        val payloads = dataset.examples().map { example ->
            val rawOutput = requireNotNull(
                example.inputAs("rawOutput", String::class.java),
            )
            when (val parsed = parser.parse(rawOutput)) {
                is ModelIntentParseResult.Accepted -> parsed.payload
                is ModelIntentParseResult.Rejected ->
                    throw AssertionError("Regression fixture was rejected: ${parsed.reason}")
            }
        }
        val allergenIds = payloads.flatMap { payload ->
            payload.allergens.map { it.id }
        }.toSet()
        val reasons = payloads.flatMap { payload ->
            payload.allergens.map { it.reason }
        }.toSet()
        val languages = payloads.map { it.detectedLanguage }.toSet()
        val categories = dataset.examples().mapNotNull { example ->
            example.metadataAs("category", String::class.java)
        }.toSet()

        JunitAssertions.assertAll(
            "The versioned intent.v1 corpus must retain pilot breadth",
            Executable {
                JunitAssertions.assertTrue(
                    dataset.size() >= MINIMUM_PILOT_FIXTURES,
                    "Expected at least $MINIMUM_PILOT_FIXTURES fixtures, found ${dataset.size()}",
                )
            },
            Executable {
                JunitAssertions.assertTrue(
                    languages.containsAll(
                        setOf("English", "Filipino", "Taglish", "Cebuano"),
                    ),
                    "Missing required language coverage: $languages",
                )
            },
            Executable {
                JunitAssertions.assertEquals(
                    QwenIntentContract.SUPPORTED_ALLERGENS,
                    allergenIds,
                    "Every supported allergen must have an exact-contract fixture",
                )
            },
            Executable {
                JunitAssertions.assertTrue(
                    reasons.containsAll(
                        setOf(
                            ModelAvoidanceReason.ALLERGY,
                            ModelAvoidanceReason.CELIAC,
                            ModelAvoidanceReason.INTOLERANCE,
                            ModelAvoidanceReason.UNSPECIFIED,
                        ),
                    ),
                    "Missing avoidance-reason coverage: $reasons",
                )
            },
            Executable {
                JunitAssertions.assertTrue(
                    payloads.any { it.diets.isNotEmpty() } &&
                        payloads.any { it.preferences.isNotEmpty() } &&
                        payloads.any { it.maximumPriceMinor != null } &&
                        payloads.any { it.needsClarification },
                    "Diet, preference, budget, and clarification dimensions are all required.",
                )
            },
            Executable {
                JunitAssertions.assertTrue(
                    REQUIRED_RISK_CATEGORIES.all { required ->
                        categories.any { category -> required in category }
                    },
                    "Missing risk-category coverage: $categories",
                )
            },
        )
    }

    private fun evaluate(example: Example) {
        val rawOutput = requireNotNull(
            example.inputAs("rawOutput", String::class.java),
        ) {
            "Every scenario must include the raw model output under inputs.rawOutput."
        }
        val actualOutput = when (val parsed = parser.parse(rawOutput)) {
            is ModelIntentParseResult.Accepted -> parsed.payload.toEvaluationContract()
            is ModelIntentParseResult.Rejected -> "REJECTED:${parsed.reason}"
        }

        DokimosAssertions.assertEval(
            example.toTestCase(actualOutput),
            exactIntentContract,
        )
    }

    private fun evaluateWithContext(index: Int, example: Example) {
        try {
            evaluate(example)
        } catch (failure: AssertionError) {
            val input = example.inputAs("input", String::class.java)
            val category =
                example.metadataAs("scenarioId", String::class.java)
                    ?: example.metadataAs("category", String::class.java)
                    ?: "unlabelled"
            throw AssertionError(
                "Scenario ${index + 1} [$category] failed for input: $input\n${failure.message}",
                failure,
            )
        }
    }

    private fun requireCompleteDeviceArtifact(rawArtifact: String) {
        when (val validation = QwenDeviceEvalArtifactCodec.validate(rawArtifact)) {
            is QwenDeviceEvalArtifactValidation.Accepted -> Unit
            is QwenDeviceEvalArtifactValidation.Rejected ->
                throw AssertionError(
                    "Device artifact failed completeness/provenance validation:\n" +
                        validation.reasons.joinToString("\n"),
                )
        }
    }

    private fun completeDeviceOutputs(): List<QwenDeviceEvalOutput> =
        QwenDeviceEvalContract.scenarios.map { scenario ->
            QwenDeviceEvalOutput(
                scenarioId = scenario.id,
                rawOutput = knownGoodRawOutput(scenario.id),
            )
        }

    private fun knownGoodRawOutput(scenarioId: String): String = when (scenarioId) {
        QwenDeviceEvalContract.SCENARIO_ID_FLAGSHIP ->
            """
            {
              "schemaVersion": 1,
              "action": "FILTER_MENU",
              "detectedLanguage": "English",
              "allergens": [{"id": "peanut", "reason": "ALLERGY"}],
              "diets": ["vegetarian"],
              "preferences": ["spicy"],
              "maximumPriceMinor": 50000,
              "needsClarification": false,
              "clarificationMessage": null,
              "unresolvedTerms": []
            }
            """.trimIndent()
        QwenDeviceEvalContract.SCENARIO_ID_NEGATED_ALLERGY ->
            """
            {
              "schemaVersion": 1,
              "action": "FILTER_MENU",
              "detectedLanguage": "English",
              "allergens": [],
              "diets": [],
              "preferences": ["spicy"],
              "maximumPriceMinor": null,
              "needsClarification": false,
              "clarificationMessage": null,
              "unresolvedTerms": []
            }
            """.trimIndent()
        QwenDeviceEvalContract.SCENARIO_ID_CEBUANO ->
            """
            {
              "schemaVersion": 1,
              "action": "FILTER_MENU",
              "detectedLanguage": "Cebuano",
              "allergens": [{"id": "shellfish", "reason": "UNSPECIFIED"}],
              "diets": [],
              "preferences": ["spicy", "quick"],
              "maximumPriceMinor": null,
              "needsClarification": false,
              "clarificationMessage": null,
              "unresolvedTerms": []
            }
            """.trimIndent()
        QwenDeviceEvalContract.SCENARIO_ID_BESTSELLER ->
            """
            {
              "schemaVersion": 1,
              "action": "FILTER_MENU",
              "detectedLanguage": "English",
              "allergens": [],
              "diets": [],
              "preferences": ["popular"],
              "maximumPriceMinor": null,
              "needsClarification": false,
              "clarificationMessage": null,
              "unresolvedTerms": []
            }
            """.trimIndent()
        else -> error("No known-good output for device scenario $scenarioId")
    }

    private fun loadRegressionDataset(): Dataset {
        val resource = requireNotNull(
            javaClass.getResource("/datasets/menu-query-intents.v1.json"),
        )
        return Dataset.fromJson(Path.of(resource.toURI()))
    }

    private companion object {
        const val MINIMUM_PILOT_FIXTURES = 32
        val REQUIRED_RISK_CATEGORIES = setOf(
            "allergy",
            "diet",
            "budget",
            "negation",
            "clarification",
            "prompt-injection",
        )
    }
}
