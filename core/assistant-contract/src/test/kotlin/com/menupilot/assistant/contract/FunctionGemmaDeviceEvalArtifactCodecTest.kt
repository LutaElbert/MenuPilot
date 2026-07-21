package com.menupilot.assistant.contract

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionGemmaDeviceEvalArtifactCodecTest {

    @Test
    fun `accepts a complete pinned synthetic envelope only as contract evidence`() {
        val artifact = FunctionGemmaDeviceEvalArtifactCodec.encode(completeOutputs())

        val accepted = FunctionGemmaDeviceEvalArtifactCodec.validate(artifact)
            as FunctionGemmaDeviceEvalArtifactValidation.Accepted

        assertEquals(
            FunctionGemmaRouteQualificationContract.scenarioIds,
            accepted.scenarioIds,
        )
        assertRejectedPhysical(
            artifact,
            "physical_qualification_requires_physical_source",
        )
    }

    @Test
    fun `physical qualification requires real build provenance and rejects emulator`() {
        val artifact = FunctionGemmaDeviceEvalArtifactCodec.encode(
            completeOutputs(),
            physicalProvenance(),
        )
        assertTrue(
            FunctionGemmaDeviceEvalArtifactCodec.validatePhysicalQualification(
                artifact,
            ) is FunctionGemmaDeviceEvalArtifactValidation.Accepted,
        )
        val revisionlessArtifact = FunctionGemmaDeviceEvalArtifactCodec.encode(
            completeOutputs(),
            physicalProvenance().copy(
                hostHarnessRevision = null,
                hostSourceTreeDirty = null,
            ),
        )
        assertTrue(
            FunctionGemmaDeviceEvalArtifactCodec.validatePhysicalQualification(
                revisionlessArtifact,
            ) is FunctionGemmaDeviceEvalArtifactValidation.Accepted,
        )

        val emulator = JsonParser.parseString(artifact).asJsonObject
        emulator.getAsJsonObject("provenance")
            .addProperty("isEmulator", true)
        assertRejected(
            emulator.toString(),
            "physical_device_is_emulator",
        )
        assertRejectedPhysical(
            emulator.toString(),
            "physical_device_is_emulator",
        )
    }

    @Test
    fun `rejects app device scope and latency provenance drift`() {
        val root = JsonParser.parseString(
            FunctionGemmaDeviceEvalArtifactCodec.encode(
                completeOutputs(),
                physicalProvenance(),
            ),
        ).asJsonObject
        root.getAsJsonObject("provenance").apply {
            addProperty("packageName", "other.app")
            addProperty("apkSha256", "not-a-sha")
            addProperty("hostHarnessRevision", "floating-main")
        }
        root.getAsJsonObject("evaluationScope").apply {
            addProperty("claim", "INTEGRATED_AGENT")
            addProperty("catalogGroundingObserved", true)
        }
        root.getAsJsonObject("latencySla").apply {
            addProperty("clock", "wall-clock")
            addProperty("p95LimitMillis", 99_999)
        }

        assertRejected(
            root.toString(),
            "wrong_app_package",
            "invalid_apk_sha256",
            "invalid_host_harness_revision",
            "wrong_evaluation_scope",
            "unsupported_integration_observation:catalogGroundingObserved",
            "wrong_latency_clock",
            "wrong_p95_latency_limit",
        )
    }

    @Test
    fun `rejects missing duplicate unknown and reordered scenarios`() {
        assertRejected(
            FunctionGemmaDeviceEvalArtifactCodec.encode(completeOutputs().dropLast(1)),
            "wrong_example_count",
            "missing_scenario_id:",
            "wrong_example_order",
        )
        assertRejected(
            FunctionGemmaDeviceEvalArtifactCodec.encode(
                completeOutputs().dropLast(1) + completeOutputs().first(),
            ),
            "duplicate_scenario_id:",
            "missing_scenario_id:",
            "wrong_example_order",
        )
        assertRejected(
            FunctionGemmaDeviceEvalArtifactCodec.encode(
                completeOutputs().reversed(),
            ),
            "wrong_example_order",
        )

        val unknown = JsonParser.parseString(
            FunctionGemmaDeviceEvalArtifactCodec.encode(completeOutputs()),
        ).asJsonObject
        unknown.getAsJsonArray("examples")
            .first()
            .asJsonObject
            .getAsJsonObject("metadata")
            .addProperty("scenarioId", "unknown-scenario")
        assertRejected(
            unknown.toString(),
            "unknown_scenario_id:unknown-scenario",
            "missing_scenario_id:",
            "extra_scenario_id:unknown-scenario",
        )
    }

    @Test
    fun `rejects model runtime and prompt provenance drift`() {
        val root = completeArtifactRoot()
        root.getAsJsonObject("model").apply {
            addProperty("revision", "floating-main")
            addProperty("fileName", "other.litertlm")
            addProperty("manifestVersion", "latest")
            addProperty("exactSizeBytes", 1)
            addProperty("sha256", "0".repeat(64))
            addProperty("license", "unknown")
            addProperty("runtime", "other-runtime")
        }
        root.getAsJsonObject("prompt").apply {
            addProperty("schemaVersion", 2)
            addProperty("systemInstructionSha256", "wrong")
            addProperty("toolDescriptionSha256", "wrong")
            addProperty("contractSha256", "wrong")
        }

        assertRejected(
            root.toString(),
            "wrong_model_revision",
            "wrong_model_file",
            "wrong_model_manifest_version",
            "wrong_model_size",
            "wrong_model_sha256",
            "wrong_model_license",
            "wrong_runtime",
            "wrong_prompt_schema",
            "wrong_system_instruction_sha256",
            "wrong_tool_description_sha256",
            "wrong_prompt_contract_sha256",
        )
    }

    @Test
    fun `rejects relabelled request context route gates and per-run provenance`() {
        val root = completeArtifactRoot()
        val first = root.getAsJsonArray("examples").first().asJsonObject
        first.getAsJsonObject("inputs").apply {
            addProperty("input", "relabeled")
            addProperty("conversation", "USER: relabeled")
            addProperty("authoritativeContext", "allergens=NONE")
        }
        first.getAsJsonObject("expectedOutputs")
            .addProperty(
                "output",
                "route.v1|action=BROWSE_MENU|subject=NONE|period=NONE",
            )
        first.getAsJsonObject("metadata").apply {
            addProperty("hardGates", "SCHEMA")
            addProperty("model", "other")
            addProperty("modelRevision", "other")
            addProperty("runtime", "other")
            addProperty("promptContractSha256", "other")
        }

        assertRejected(
            root.toString(),
            "query_mismatch:",
            "conversation_mismatch:",
            "authoritative_context_mismatch:",
            "expected_route_mismatch:",
            "hard_gates_mismatch:",
            "metadata_model_mismatch:",
            "metadata_revision_mismatch:",
            "metadata_runtime_mismatch:",
            "metadata_prompt_mismatch:",
        )
    }

    @Test
    fun `records expected unexecuted app fallback without treating failures as completed`() {
        val timedOut = completeOutputs().toMutableList()
        timedOut[0] = timedOut[0].copy(
            calls = emptyList(),
            executionOutcome = FunctionGemmaExecutionOutcome.TIMED_OUT,
            fallbackOutcome =
                FunctionGemmaFallbackOutcome.APP_FAIL_CLOSED_EXPECTED_NOT_EXECUTED,
            latencyMillis = 30_000,
            errorCode = "generation_timeout",
        )

        assertTrue(
            FunctionGemmaDeviceEvalArtifactCodec.validate(
                FunctionGemmaDeviceEvalArtifactCodec.encode(timedOut),
            ) is FunctionGemmaDeviceEvalArtifactValidation.Accepted,
        )

        val rejectedOutput = timedOut.toMutableList()
        rejectedOutput[0] = rejectedOutput[0].copy(
            executionOutcome = FunctionGemmaExecutionOutcome.MODEL_OUTPUT_REJECTED,
            errorCode = "expected_exactly_one_tool_call",
        )
        assertTrue(
            FunctionGemmaDeviceEvalArtifactCodec.validate(
                FunctionGemmaDeviceEvalArtifactCodec.encode(rejectedOutput),
            ) is FunctionGemmaDeviceEvalArtifactValidation.Accepted,
        )
    }

    @Test
    fun `rejects unsafe or contradictory failure metadata`() {
        val root = completeArtifactRoot()
        root.getAsJsonArray("examples")
            .first()
            .asJsonObject
            .getAsJsonObject("metadata")
            .apply {
                addProperty("executionOutcome", "TIMED_OUT")
                addProperty("fallbackOutcome", "NONE")
            }
        assertRejected(
            root.toString(),
            "missing_expected_app_fallback:",
            "failed_run_missing_error:",
        )

        val completedWithFallback = completeArtifactRoot()
        completedWithFallback.getAsJsonArray("examples")
            .first()
            .asJsonObject
            .getAsJsonObject("metadata")
            .apply {
                addProperty(
                    "fallbackOutcome",
                    "APP_FAIL_CLOSED_EXPECTED_NOT_EXECUTED",
                )
                addProperty("errorCode", "should-not-exist")
            }
        assertRejected(
            completedWithFallback.toString(),
            "completed_run_must_not_fallback:",
            "completed_run_has_error:",
        )
    }

    @Test
    fun `rejects malformed calls and invalid bounded diagnostics`() {
        val root = completeArtifactRoot()
        val first = root.getAsJsonArray("examples").first().asJsonObject
        first.getAsJsonObject("inputs").addProperty("callsJson", "not-json")
        first.getAsJsonObject("metadata").apply {
            addProperty("latencyMillis", -1)
            addProperty("errorCode", "x".repeat(121))
        }

        assertRejected(
            root.toString(),
            "invalid_calls_json:",
            "invalid_latency:",
            "invalid_error_code:",
        )
    }

    @Test
    fun `prompt hashes and scenario serialization are stable and nonempty`() {
        assertEquals(
            64,
            FunctionGemmaDeviceEvalContract.systemInstructionSha256.length,
        )
        assertEquals(
            64,
            FunctionGemmaDeviceEvalContract.toolDescriptionSha256.length,
        )
        assertEquals(
            64,
            FunctionGemmaDeviceEvalContract.promptContractSha256.length,
        )
        assertTrue(
            FunctionGemmaRouteQualificationContract.scenarios.all { scenario ->
                FunctionGemmaDeviceEvalContract.conversationText(scenario.request)
                    .endsWith("USER: ${scenario.request.currentUserMessage}") &&
                    FunctionGemmaDeviceEvalContract.authoritativeContextText(
                        scenario.request.authoritativeContext,
                    ).isNotBlank() &&
                    FunctionGemmaDeviceEvalContract.hardGateText(
                        scenario.hardGates,
                    ).contains("SCHEMA")
            },
        )
    }

    @Test
    fun `authoritative budget amount and scope are always paired`() {
        val budgetScenario = FunctionGemmaRouteQualificationContract.scenarios
            .single { it.id == "budget-non-loosening" }
        assertTrue(
            FunctionGemmaDeviceEvalContract.authoritativeContextText(
                budgetScenario.request.authoritativeContext,
            ).contains("|budgetMinor=50000|budgetScope=PER_DISH|"),
        )

        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            FunctionGemmaDeviceEvalContract.authoritativeContextText(
                FunctionGemmaAuthoritativeContext(maximumPriceMinor = 50_000),
            )
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            FunctionGemmaDeviceEvalContract.authoritativeContextText(
                FunctionGemmaAuthoritativeContext(
                    budgetScope = FunctionGemmaBudgetScope.WHOLE_ORDER,
                ),
            )
        }
    }

    private fun completeOutputs(): List<FunctionGemmaDeviceEvalOutput> =
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

    private fun physicalProvenance() = FunctionGemmaArtifactProvenance(
        source = FunctionGemmaEvalSource.PHYSICAL_ANDROID_DEVICE,
        manufacturer = "Xiaomi",
        model = "Tablet",
        device = "tablet",
        product = "tablet_global",
        buildFingerprint = "xiaomi/tablet/tablet:15/build/release-keys",
        sdkInt = 35,
        supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
        isEmulator = false,
        packageName = FunctionGemmaDeviceEvalContract.APP_PACKAGE_NAME,
        versionName = "1.0",
        versionCode = 1,
        isDebuggable = true,
        apkSha256 = "a".repeat(64),
        capturedAtEpochMillis = 1_753_000_000_000,
        runId = "physical-test-run",
        hostHarnessRevision = "b".repeat(40),
        hostSourceTreeDirty = false,
    )

    private fun completeArtifactRoot() = JsonParser.parseString(
        FunctionGemmaDeviceEvalArtifactCodec.encode(completeOutputs()),
    ).asJsonObject

    private fun assertRejected(
        artifact: String,
        vararg expectedReasonFragments: String,
    ) {
        val result = FunctionGemmaDeviceEvalArtifactCodec.validate(artifact)
        assertTrue(result is FunctionGemmaDeviceEvalArtifactValidation.Rejected)
        val reasons =
            (result as FunctionGemmaDeviceEvalArtifactValidation.Rejected).reasons
        expectedReasonFragments.forEach { fragment ->
            assertTrue(
                "Expected rejection containing '$fragment', got $reasons",
                reasons.any { it.contains(fragment) },
            )
        }
    }

    private fun assertRejectedPhysical(
        artifact: String,
        vararg expectedReasonFragments: String,
    ) {
        val result =
            FunctionGemmaDeviceEvalArtifactCodec
                .validatePhysicalQualification(artifact)
        assertTrue(result is FunctionGemmaDeviceEvalArtifactValidation.Rejected)
        val reasons =
            (result as FunctionGemmaDeviceEvalArtifactValidation.Rejected).reasons
        expectedReasonFragments.forEach { fragment ->
            assertTrue(
                "Expected physical rejection containing '$fragment', got $reasons",
                reasons.any { it.contains(fragment) },
            )
        }
    }
}
