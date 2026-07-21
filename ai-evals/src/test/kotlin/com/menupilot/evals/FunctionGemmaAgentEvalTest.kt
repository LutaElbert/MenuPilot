package com.menupilot.evals

import com.google.gson.JsonParser
import com.menupilot.assistant.contract.FunctionGemmaDeviceEvalArtifactCodec
import com.menupilot.assistant.contract.FunctionGemmaDeviceEvalArtifactValidation
import com.menupilot.assistant.contract.FunctionGemmaDeviceEvalContract
import com.menupilot.assistant.contract.FunctionGemmaDeviceEvalOutput
import com.menupilot.assistant.contract.FunctionGemmaExecutionOutcome
import com.menupilot.assistant.contract.FunctionGemmaFallbackOutcome
import com.menupilot.assistant.contract.FunctionGemmaAuthoritativeContext
import com.menupilot.assistant.contract.FunctionGemmaQualificationGate
import com.menupilot.assistant.contract.FunctionGemmaRoute
import com.menupilot.assistant.contract.FunctionGemmaRouteAction
import com.menupilot.assistant.contract.FunctionGemmaRouteContract
import com.menupilot.assistant.contract.FunctionGemmaRouteParseResult
import com.menupilot.assistant.contract.FunctionGemmaRouteParser
import com.menupilot.assistant.contract.FunctionGemmaRouteQualificationContract
import com.menupilot.assistant.contract.FunctionGemmaRouteScenario
import com.menupilot.assistant.contract.FunctionGemmaSalesPeriod
import com.menupilot.assistant.contract.ModelFunctionCall
import dev.dokimos.core.Assertions as DokimosAssertions
import dev.dokimos.core.Dataset
import dev.dokimos.core.Example
import dev.dokimos.core.agents.ToolCall
import dev.dokimos.core.agents.ToolDefinition
import dev.dokimos.core.evaluators.ExactMatchEvaluator
import dev.dokimos.core.evaluators.agents.AgentEvalCase
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator
import dev.dokimos.core.evaluators.agents.ToolCorrectnessEvaluator
import dev.dokimos.junit.DatasetSource
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest

class FunctionGemmaAgentEvalTest {
    private val parser = FunctionGemmaRouteParser()
    private val exactRoute = ExactMatchEvaluator.builder()
        .name("MenuPilot FunctionGemma route.v1 exact contract")
        .threshold(1.0)
        .build()
    private val strictToolValidity = ToolCallValidityEvaluator.builder()
        .name("FunctionGemma strict route_menu_request schema")
        .threshold(1.0)
        .strictMode(true)
        .build()
    private val exactToolCall = ToolCorrectnessEvaluator.builder()
        .name("FunctionGemma exact tool and arguments")
        .threshold(1.0)
        .matchMode(ToolCorrectnessEvaluator.MatchMode.NAMES_AND_ARGS)
        .build()

    @ParameterizedTest(name = "{index}: pinned FunctionGemma route fixture")
    @DatasetSource("classpath:datasets/functiongemma-route-contract.v1.json")
    fun checkedInContractFixturesUseProductionParserAndDokimosAgents(
        example: Example,
    ) {
        val scenario = scenarioFor(example)
        assertFixtureIdentity(example, scenario)
        val calls = callsFor(example)
        val actualContract = routeContract(calls)

        DokimosAssertions.assertEval(
            example.toTestCase(actualContract),
            exactRoute,
        )
        DokimosAssertions.assertEval(
            AgentEvalCase.builder()
                .input(scenario.request.currentUserMessage)
                .toolCalls(calls.map(::dokimosCall))
                .expectedToolCalls(
                    listOf(dokimosCall(expectedCall(scenario.expectedRoute))),
                )
                .tools(listOf(routeToolDefinition))
                .build(),
            strictToolValidity,
            exactToolCall,
        )
    }

    @Test
    fun checkedInCorpusMatchesSharedManifestAndCoversEveryHardDimension() {
        val dataset = loadContractDataset()
        val examples = dataset.examples()
        val ids = examples.map { example ->
            requireNotNull(example.metadataAs("scenarioId", String::class.java))
        }
        val categories = examples.map { example ->
            requireNotNull(example.metadataAs("category", String::class.java))
        }.toSet()
        val languages = examples.map { example ->
            requireNotNull(example.metadataAs("language", String::class.java))
        }.toSet()
        val scenarios = FunctionGemmaRouteQualificationContract.scenarios
        val actions = scenarios.map { it.expectedRoute.action }.toSet()
        val periods = scenarios.mapNotNull { it.expectedRoute.period }.toSet()

        Assertions.assertAll(
            "FunctionGemma contract corpus breadth",
            {
                Assertions.assertEquals(
                    FunctionGemmaRouteQualificationContract.scenarioIds,
                    ids,
                    "Dataset order and IDs must match the shared device manifest.",
                )
            },
            {
                Assertions.assertTrue(
                    examples.size >= MINIMUM_95_PERCENT_DENOMINATOR,
                    "At least 20 cases are required so one noncritical miss " +
                        "does not round a small suite into a misleading 95%.",
                )
            },
            {
                Assertions.assertEquals(
                    FunctionGemmaRouteAction.entries.toSet(),
                    actions,
                    "Every accepted route action needs an exact fixture.",
                )
            },
            {
                Assertions.assertEquals(
                    FunctionGemmaSalesPeriod.entries.toSet(),
                    periods,
                    "Every bestseller period needs an exact fixture.",
                )
            },
            {
                Assertions.assertTrue(
                    languages.containsAll(
                        setOf("English", "Filipino", "Taglish", "Cebuano"),
                    ),
                    "English, Filipino, Taglish, and Cebuano are required: $languages",
                )
            },
            {
                Assertions.assertTrue(
                    REQUIRED_CATEGORY_FRAGMENTS.all { required ->
                        categories.any { category -> required in category }
                    },
                    "Missing route-risk category: $categories",
                )
            },
            {
                FunctionGemmaQualificationGate.entries.forEach { gate ->
                    Assertions.assertTrue(
                        scenarios.any { gate in it.hardGates },
                        "No physical scenario exercises hard gate $gate.",
                    )
                }
            },
            {
                Assertions.assertTrue(
                    scenarios.count { scenario ->
                        scenario.request.history.isNotEmpty() ||
                            scenario.request.authoritativeContext !=
                            FunctionGemmaAuthoritativeContext()
                    } >= MINIMUM_STATEFUL_SCENARIOS,
                    "Multi-turn/stateful routing breadth regressed.",
                )
            },
        )
    }

    @Test
    fun completeContractEnvelopeScoresOneHundredPercentWithoutBecomingModelEvidence() {
        val artifact = FunctionGemmaDeviceEvalArtifactCodec.encode(
            completedContractOutputs(),
        )
        requireCompleteArtifact(artifact)
        val qualification = qualify(Dataset.fromJson(artifact).examples())

        Assertions.assertEquals(24, qualification.total)
        Assertions.assertEquals(24, qualification.exactPasses)
        Assertions.assertEquals(24, qualification.schemaPasses)
        Assertions.assertEquals(1.0, qualification.overallRate)
        Assertions.assertTrue(qualification.hardGateFailures.isEmpty())
        assertLatencySla(artifact)
    }

    @Test
    fun safeTimeoutIsRecordedButCannotPassModelQualification() {
        val outputs = completedContractOutputs().toMutableList()
        outputs[0] = outputs[0].copy(
            calls = emptyList(),
            executionOutcome = FunctionGemmaExecutionOutcome.TIMED_OUT,
            fallbackOutcome =
                FunctionGemmaFallbackOutcome.APP_FAIL_CLOSED_EXPECTED_NOT_EXECUTED,
            latencyMillis = 30_000,
            errorCode = "generation_timeout",
        )
        val artifact = FunctionGemmaDeviceEvalArtifactCodec.encode(outputs)

        requireCompleteArtifact(artifact)
        val qualification = qualify(Dataset.fromJson(artifact).examples())

        Assertions.assertEquals(23, qualification.exactPasses)
        Assertions.assertEquals(23, qualification.schemaPasses)
        Assertions.assertTrue(
            qualification.hardGateFailures.any {
                it.startsWith("SCHEMA:")
            },
            "Expected fail-closed app behavior is not executed by this artifact and " +
                "cannot turn a timeout into a passing model call.",
        )
    }

    @Test
    fun practicalLatencyGateRequiresEveryRunAndEnforcesP95AndMaximum() {
        val missingLatency = FunctionGemmaDeviceEvalArtifactCodec.encode(
            completedContractOutputs().mapIndexed { index, output ->
                if (index == 0) output.copy(latencyMillis = null) else output
            },
        )
        Assertions.assertThrows(AssertionError::class.java) {
            assertLatencySla(missingLatency)
        }

        val overP95 = FunctionGemmaDeviceEvalArtifactCodec.encode(
            completedContractOutputs().mapIndexed { index, output ->
                if (index < 23) {
                    output.copy(
                        latencyMillis =
                            FunctionGemmaDeviceEvalContract
                                .P95_ROUTE_LATENCY_LIMIT_MILLIS + 1,
                    )
                } else {
                    output
                }
            },
        )
        Assertions.assertThrows(AssertionError::class.java) {
            assertLatencySla(overP95)
        }

        val overMaximum = FunctionGemmaDeviceEvalArtifactCodec.encode(
            completedContractOutputs().mapIndexed { index, output ->
                if (index == 0) {
                    output.copy(
                        latencyMillis =
                            FunctionGemmaDeviceEvalContract
                                .MAX_ROUTE_LATENCY_LIMIT_MILLIS + 1,
                    )
                } else {
                    output
                }
            },
        )
        Assertions.assertThrows(AssertionError::class.java) {
            assertLatencySla(overMaximum)
        }
    }

    @Test
    fun malformedAndUnsafeCallsFailClosedThroughTheProductionParser() {
        val invalidCalls = listOf(
            emptyList(),
            listOf(
                ModelFunctionCall(
                    name = "place_order",
                    arguments = mapOf("action" to "BROWSE_MENU"),
                ),
            ),
            listOf(
                ModelFunctionCall(
                    name = FunctionGemmaRouteContract.TOOL_NAME,
                    arguments = mapOf(
                        "action" to "SHAPE_MENU",
                        "eraseConfirmedNeeds" to true,
                    ),
                ),
            ),
            listOf(
                ModelFunctionCall(
                    name = FunctionGemmaRouteContract.TOOL_NAME,
                    arguments = mapOf("action" to "PLACE_ORDER"),
                ),
            ),
            listOf(
                ModelFunctionCall(
                    name = FunctionGemmaRouteContract.TOOL_NAME,
                    arguments = mapOf("action" to "SHOW_BESTSELLERS"),
                ),
            ),
            listOf(
                ModelFunctionCall(
                    name = FunctionGemmaRouteContract.TOOL_NAME,
                    arguments = mapOf(
                        "action" to "EXPLAIN_DISH",
                        "subject" to "x".repeat(161),
                    ),
                ),
            ),
        )

        invalidCalls.forEach { calls ->
            Assertions.assertTrue(
                parser.parse(calls) is FunctionGemmaRouteParseResult.Rejected,
                "Unsafe call unexpectedly crossed the production parser: $calls",
            )
        }
    }

    @Test
    fun optionalPhysicalFunctionGemmaArtifactMeetsScopedNinetyFivePercentGate() {
        val configuredPath =
            System.getProperty("menupilot.functionGemmaRuns").orEmpty()
        Assumptions.assumeTrue(
            configuredPath.isNotBlank(),
            "No physical FunctionGemma artifact was supplied.",
        )
        val path = Path.of(configuredPath).toAbsolutePath().normalize()
        Assertions.assertTrue(
            Files.isRegularFile(path),
            "The configured FunctionGemma artifact does not exist: $path",
        )
        val rawArtifact = Files.readString(path)
        requirePhysicalArtifact(rawArtifact)
        val qualification = qualify(Dataset.fromJson(rawArtifact).examples())
        assertLatencySla(rawArtifact)

        Assertions.assertAll(
            "Scoped raw FunctionGemma routing qualification on a physical device",
            {
                Assertions.assertTrue(
                    qualification.overallRate >= MINIMUM_OVERALL_RATE,
                    "${qualification.exactPasses}/${qualification.total} exact routes " +
                        "(${qualification.overallRate}) is below 95%.",
                )
            },
            {
                Assertions.assertEquals(
                    qualification.total,
                    qualification.schemaPasses,
                    "Every physical output must cross the strict one-call schema.",
                )
            },
            {
                Assertions.assertTrue(
                    qualification.hardGateFailures.isEmpty(),
                    "Hard gates require 100%: ${qualification.hardGateFailures}",
                )
            },
            {
                Assertions.assertEquals(
                    qualification.factDependentRouteTotal,
                    qualification.factDependentRoutePasses,
                    "Routes that require later deterministic grounding must be selected " +
                        "exactly. This raw-routing artifact does not execute grounding.",
                )
            },
        )
    }

    private fun qualify(examples: List<Example>): Qualification {
        var exactPasses = 0
        var schemaPasses = 0
        var factDependentRoutePasses = 0
        var factDependentRouteTotal = 0
        val hardGateFailures = mutableListOf<String>()

        examples.forEach { example ->
            val scenario = scenarioFor(example)
            assertArtifactFixtureIdentity(example, scenario)
            val executionOutcome =
                requireNotNull(
                    example.metadataAs(
                        "executionOutcome",
                        String::class.java,
                    ),
                )
            val parsed = parser.parse(callsFor(example))
            val schemaPassed =
                executionOutcome ==
                FunctionGemmaExecutionOutcome.MODEL_COMPLETED.name &&
                    parsed is FunctionGemmaRouteParseResult.Accepted
            val exactPassed =
                schemaPassed &&
                    (parsed as FunctionGemmaRouteParseResult.Accepted)
                        .route == scenario.expectedRoute

            if (schemaPassed) schemaPasses++
            if (exactPassed) exactPasses++

            val requiresLaterGrounding =
                scenario.expectedRoute.action in FACT_DEPENDENT_ACTIONS
            if (requiresLaterGrounding) {
                factDependentRouteTotal++
                if (exactPassed) factDependentRoutePasses++
            }
            scenario.hardGates.forEach { gate ->
                val passed = when (gate) {
                    FunctionGemmaQualificationGate.SCHEMA -> schemaPassed
                    FunctionGemmaQualificationGate.SAFETY,
                    FunctionGemmaQualificationGate.STATE,
                    FunctionGemmaQualificationGate.MULTILINGUAL,
                    FunctionGemmaQualificationGate.PROMPT_INJECTION,
                    -> exactPassed
                }
                if (!passed) hardGateFailures += "${gate.name}:${scenario.id}"
            }
            if (requiresLaterGrounding && !exactPassed) {
                hardGateFailures += "FACT_DEPENDENT_ROUTE:${scenario.id}"
            }
        }

        return Qualification(
            total = examples.size,
            exactPasses = exactPasses,
            schemaPasses = schemaPasses,
            factDependentRouteTotal = factDependentRouteTotal,
            factDependentRoutePasses = factDependentRoutePasses,
            hardGateFailures = hardGateFailures,
        )
    }

    private fun routeContract(calls: List<ModelFunctionCall>): String =
        when (val result = parser.parse(calls)) {
            is FunctionGemmaRouteParseResult.Accepted ->
                result.route.toEvaluationContract()
            is FunctionGemmaRouteParseResult.Rejected ->
                "REJECTED:${result.reason}"
        }

    private fun callsFor(example: Example): List<ModelFunctionCall> {
        val callsJson = requireNotNull(
            example.inputAs("callsJson", String::class.java),
        )
        return requireNotNull(
            FunctionGemmaDeviceEvalArtifactCodec.decodeCalls(callsJson),
        ) {
            "Invalid callsJson for ${example.metadataAs("scenarioId", String::class.java)}"
        }
    }

    private fun scenarioFor(example: Example): FunctionGemmaRouteScenario {
        val id = requireNotNull(
            example.metadataAs("scenarioId", String::class.java),
        )
        return requireNotNull(
            FunctionGemmaRouteQualificationContract.scenarios
                .firstOrNull { it.id == id },
        ) {
            "Unknown FunctionGemma scenario ID: $id"
        }
    }

    private fun assertFixtureIdentity(
        example: Example,
        scenario: FunctionGemmaRouteScenario,
    ) {
        assertArtifactFixtureIdentity(example, scenario)
        Assertions.assertEquals(
            scenario.expectedRoute.toEvaluationContract(),
            example.expectedOutputAs("output", String::class.java),
            "Fixture expected route was relabelled for ${scenario.id}.",
        )
    }

    private fun assertArtifactFixtureIdentity(
        example: Example,
        scenario: FunctionGemmaRouteScenario,
    ) {
        Assertions.assertEquals(
            scenario.request.currentUserMessage,
            example.inputAs("input", String::class.java),
            "Fixture query was relabelled for ${scenario.id}.",
        )
        Assertions.assertEquals(
            FunctionGemmaDeviceEvalContract.conversationText(scenario.request),
            example.inputAs("conversation", String::class.java),
            "Fixture conversation drifted for ${scenario.id}.",
        )
        Assertions.assertEquals(
            FunctionGemmaDeviceEvalContract.authoritativeContextText(
                scenario.request.authoritativeContext,
            ),
            example.inputAs("authoritativeContext", String::class.java),
            "Authoritative context drifted for ${scenario.id}.",
        )
    }

    private fun requireCompleteArtifact(rawArtifact: String) {
        when (
            val result =
                FunctionGemmaDeviceEvalArtifactCodec.validate(rawArtifact)
        ) {
            is FunctionGemmaDeviceEvalArtifactValidation.Accepted -> Unit
            is FunctionGemmaDeviceEvalArtifactValidation.Rejected ->
                throw AssertionError(
                    "FunctionGemma artifact failed completeness/provenance:\n" +
                        result.reasons.joinToString("\n"),
                )
        }
    }

    private fun requirePhysicalArtifact(rawArtifact: String) {
        when (
            val result =
                FunctionGemmaDeviceEvalArtifactCodec
                    .validatePhysicalQualification(rawArtifact)
        ) {
            is FunctionGemmaDeviceEvalArtifactValidation.Accepted -> Unit
            is FunctionGemmaDeviceEvalArtifactValidation.Rejected ->
                throw AssertionError(
                    "FunctionGemma physical artifact failed completeness/provenance:\n" +
                        result.reasons.joinToString("\n"),
                )
        }
    }

    private fun assertLatencySla(rawArtifact: String) {
        val examples = JsonParser.parseString(rawArtifact)
            .asJsonObject
            .getAsJsonArray("examples")
        val latencies = examples.mapIndexed { index, example ->
            val latencyPrimitive = example.asJsonObject
                .getAsJsonObject("metadata")
                .get("latencyMillis")
                ?.takeIf { it.isJsonPrimitive }
                ?.asJsonPrimitive
            if (latencyPrimitive == null || !latencyPrimitive.isNumber) {
                throw AssertionError("Missing route latency at scenario index $index.")
            }
            latencyPrimitive.asLong
        }.sorted()
        if (latencies.size != FunctionGemmaRouteQualificationContract.scenarioIds.size) {
            throw AssertionError(
                "Latency SLA requires every pinned scenario; got ${latencies.size}.",
            )
        }
        if (latencies.first() <= 0) {
            throw AssertionError("Every raw route must have a positive measured latency.")
        }
        val p95Index = ((latencies.size * 95 + 99) / 100) - 1
        val p95 = latencies[p95Index]
        val maximum = latencies.last()
        if (p95 > FunctionGemmaDeviceEvalContract.P95_ROUTE_LATENCY_LIMIT_MILLIS) {
            throw AssertionError(
                "Raw route p95 ${p95}ms exceeds " +
                    "${FunctionGemmaDeviceEvalContract.P95_ROUTE_LATENCY_LIMIT_MILLIS}ms.",
            )
        }
        if (maximum > FunctionGemmaDeviceEvalContract.MAX_ROUTE_LATENCY_LIMIT_MILLIS) {
            throw AssertionError(
                "Raw route maximum ${maximum}ms exceeds " +
                    "${FunctionGemmaDeviceEvalContract.MAX_ROUTE_LATENCY_LIMIT_MILLIS}ms.",
            )
        }
    }

    private fun completedContractOutputs(): List<FunctionGemmaDeviceEvalOutput> =
        FunctionGemmaRouteQualificationContract.scenarios.map { scenario ->
            FunctionGemmaDeviceEvalOutput(
                scenarioId = scenario.id,
                calls = listOf(expectedCall(scenario.expectedRoute)),
                executionOutcome = FunctionGemmaExecutionOutcome.MODEL_COMPLETED,
                fallbackOutcome = FunctionGemmaFallbackOutcome.NONE,
                latencyMillis = 25,
            )
        }

    private fun expectedCall(route: FunctionGemmaRoute) = ModelFunctionCall(
        name = FunctionGemmaRouteContract.TOOL_NAME,
        arguments = buildMap {
            put("action", route.action.name)
            route.subject?.let { put("subject", it) }
            route.period?.let { put("period", it.name) }
        },
    )

    private fun dokimosCall(call: ModelFunctionCall): ToolCall =
        ToolCall.of(
            call.name,
            call.arguments.filterValues { it != null }
                .mapValues { requireNotNull(it.value) },
        )

    private val routeToolDefinition = ToolDefinition.builder()
        .name(FunctionGemmaRouteContract.TOOL_NAME)
        .description(
            "Propose one read-only MenuPilot route; never execute an action.",
        )
        .inputSchema(
            mapOf(
                "type" to "object",
                "additionalProperties" to false,
                "properties" to mapOf(
                    "action" to mapOf(
                        "type" to "string",
                        "enum" to FunctionGemmaRouteAction.entries.map { it.name },
                    ),
                    "subject" to mapOf("type" to "string"),
                    "period" to mapOf(
                        "type" to "string",
                        "enum" to FunctionGemmaSalesPeriod.entries.map { it.name },
                    ),
                ),
                "required" to listOf("action"),
            ),
        )
        .build()

    private fun loadContractDataset(): Dataset {
        val resource = requireNotNull(
            javaClass.getResource(
                "/datasets/functiongemma-route-contract.v1.json",
            ),
        )
        return Dataset.fromJson(Path.of(resource.toURI()))
    }

    private data class Qualification(
        val total: Int,
        val exactPasses: Int,
        val schemaPasses: Int,
        val factDependentRouteTotal: Int,
        val factDependentRoutePasses: Int,
        val hardGateFailures: List<String>,
    ) {
        val overallRate: Double =
            if (total == 0) 0.0 else exactPasses.toDouble() / total
    }

    private companion object {
        const val MINIMUM_OVERALL_RATE = 0.95
        const val MINIMUM_95_PERCENT_DENOMINATOR = 20
        const val MINIMUM_STATEFUL_SCENARIOS = 8
        val FACT_DEPENDENT_ACTIONS = setOf(
            FunctionGemmaRouteAction.EXPLAIN_DISH,
            FunctionGemmaRouteAction.COMPARE_DISHES,
            FunctionGemmaRouteAction.SHOW_BESTSELLERS,
            FunctionGemmaRouteAction.SUGGEST_PAIRING,
        )
        val REQUIRED_CATEGORY_FRAGMENTS = setOf(
            "safety",
            "multilingual",
            "correction",
            "negation",
            "retention",
            "injection",
            "health",
            "budget",
            "grounded-sales",
            "grounded-upsell",
            "session-isolation",
            "upsell-decline",
        )
    }
}
