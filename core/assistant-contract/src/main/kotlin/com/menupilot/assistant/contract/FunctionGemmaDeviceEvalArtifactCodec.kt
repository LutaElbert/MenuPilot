package com.menupilot.assistant.contract

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.MessageDigest

enum class FunctionGemmaExecutionOutcome {
    MODEL_COMPLETED,
    TIMED_OUT,
    MODEL_OUTPUT_REJECTED,
    RUNTIME_ERROR,
}

enum class FunctionGemmaFallbackOutcome {
    NONE,
    APP_FAIL_CLOSED_EXPECTED_NOT_EXECUTED,
}

enum class FunctionGemmaEvalSource {
    SYNTHETIC_CONTRACT_TEST,
    PHYSICAL_ANDROID_DEVICE,
}

/**
 * Provenance for the binary and Android device that produced a routing artifact.
 *
 * [apkSha256] is the authoritative identity of the installed app binary. The optional host Git
 * fields identify the checkout that launched instrumentation, but are deliberately not described
 * as the source revision embedded in that APK.
 */
data class FunctionGemmaArtifactProvenance(
    val source: FunctionGemmaEvalSource,
    val manufacturer: String,
    val model: String,
    val device: String,
    val product: String,
    val buildFingerprint: String,
    val sdkInt: Int,
    val supportedAbis: List<String>,
    val isEmulator: Boolean,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val isDebuggable: Boolean,
    val apkSha256: String,
    val capturedAtEpochMillis: Long,
    val runId: String,
    val hostHarnessRevision: String?,
    val hostSourceTreeDirty: Boolean?,
) {
    companion object {
        fun syntheticContractTest() = FunctionGemmaArtifactProvenance(
            source = FunctionGemmaEvalSource.SYNTHETIC_CONTRACT_TEST,
            manufacturer = "NOT_APPLICABLE",
            model = "NOT_APPLICABLE",
            device = "NOT_APPLICABLE",
            product = "NOT_APPLICABLE",
            buildFingerprint = "NOT_APPLICABLE",
            sdkInt = 0,
            supportedAbis = emptyList(),
            isEmulator = false,
            packageName = "NOT_APPLICABLE",
            versionName = "NOT_APPLICABLE",
            versionCode = 0,
            isDebuggable = false,
            apkSha256 = "NOT_APPLICABLE",
            capturedAtEpochMillis = 0,
            runId = "synthetic-contract-test",
            hostHarnessRevision = null,
            hostSourceTreeDirty = null,
        )
    }
}

data class FunctionGemmaDeviceEvalOutput(
    val scenarioId: String,
    val calls: List<ModelFunctionCall>,
    val executionOutcome: FunctionGemmaExecutionOutcome,
    val fallbackOutcome: FunctionGemmaFallbackOutcome,
    val latencyMillis: Long? = null,
    val errorCode: String? = null,
)

object FunctionGemmaDeviceEvalContract {
    const val ARTIFACT_SCHEMA_VERSION = 2
    const val DATASET_NAME = "menupilot-functiongemma-route-device-runs-v2"
    const val DATASET_DESCRIPTION =
        "Pinned raw Android FunctionGemma route calls evaluated with the production strict parser."
    const val APP_PACKAGE_NAME = "com.menupilot.restaurant"
    const val EVALUATION_SCOPE = "RAW_MODEL_ROUTING_ONLY"
    const val DEVICE_EXECUTION = "LITERTLM_RAW_TOOL_CALL_CAPTURE"
    const val LATENCY_CLOCK = "ANDROID_ELAPSED_REALTIME_GENERATOR_CALL"
    const val LATENCY_PERCENTILE_METHOD = "NEAREST_RANK"
    const val P95_ROUTE_LATENCY_LIMIT_MILLIS = 5_000L
    const val MAX_ROUTE_LATENCY_LIMIT_MILLIS = 15_000L

    val systemInstructionSha256: String =
        sha256(FunctionGemmaRouteContract.systemInstruction)
    val toolDescriptionSha256: String =
        sha256(FunctionGemmaRouteContract.toolDescriptionJson)
    val promptContractSha256: String = sha256(
        buildString {
            append("schemaVersion=")
            append(FunctionGemmaRouteContract.SCHEMA_VERSION)
            append('\n')
            append(FunctionGemmaRouteContract.systemInstruction)
            append('\n')
            append(FunctionGemmaRouteContract.toolDescriptionJson)
        },
    )

    fun conversationText(request: FunctionGemmaConversationRequest): String =
        (request.history + FunctionGemmaTurn(
            role = FunctionGemmaTurnRole.USER,
            text = request.currentUserMessage,
        )).joinToString("\n") { turn -> "${turn.role.name}: ${turn.text}" }

    fun authoritativeContextText(
        context: FunctionGemmaAuthoritativeContext,
    ): String {
        require(
            (context.maximumPriceMinor != null) == (context.budgetScope != null),
        ) {
            "maximumPriceMinor and budgetScope must either both be set or both be absent."
        }
        return buildString {
            append("allergens=").append(context.allergens.orNone())
            append("|diets=").append(context.diets.orNone())
            append("|preferences=").append(context.preferredAttributes.orNone())
            append("|budgetMinor=").append(context.maximumPriceMinor ?: "NONE")
            append("|budgetScope=").append(context.budgetScope?.name ?: "NONE")
            append("|focusedDish=").append(context.focusedDishName ?: "NONE")
        }
    }

    fun hardGateText(gates: Set<FunctionGemmaQualificationGate>): String =
        FunctionGemmaQualificationGate.entries
            .filter(gates::contains)
            .joinToString(",") { it.name }

    private fun List<String>.orNone(): String =
        if (isEmpty()) "NONE" else joinToString(",")

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        val digits = "0123456789abcdef"
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val unsigned = byte.toInt() and 0xff
                append(digits[unsigned ushr 4])
                append(digits[unsigned and 0x0f])
            }
        }
    }
}

sealed interface FunctionGemmaDeviceEvalArtifactValidation {
    data class Accepted(
        val scenarioIds: List<String>,
    ) : FunctionGemmaDeviceEvalArtifactValidation

    data class Rejected(
        val reasons: List<String>,
    ) : FunctionGemmaDeviceEvalArtifactValidation
}

/**
 * Shared raw-route envelope for Android export and host Dokimos qualification.
 *
 * [encode] permits partial output lists for device debugging. [validate] deliberately rejects
 * partial, duplicated, reordered, relabelled, or provenance-mismatched artifacts. Only
 * [validatePhysicalQualification] permits a physical-model qualification claim.
 */
object FunctionGemmaDeviceEvalArtifactCodec {
    private const val MAX_ARTIFACT_CHARS = 512_000
    private const val MAX_CALLS_JSON_CHARS = 16_384
    private const val MAX_ERROR_CODE_CHARS = 120
    private const val MAX_RECORDED_LATENCY_MILLIS = 300_000L
    private const val LATENCY_NOT_RECORDED = 0L
    private const val NO_ERROR_CODE = "NONE"
    private val gson = Gson()

    fun encode(
        outputs: List<FunctionGemmaDeviceEvalOutput>,
        provenance: FunctionGemmaArtifactProvenance =
            FunctionGemmaArtifactProvenance.syntheticContractTest(),
    ): String {
        val scenariosById =
            FunctionGemmaRouteQualificationContract.scenarios.associateBy { it.id }
        val examples = JsonArray()
        outputs.forEach { output ->
            val scenario = requireNotNull(scenariosById[output.scenarioId]) {
                "Unknown FunctionGemma device-eval scenario ID: ${output.scenarioId}"
            }
            examples.add(
                JsonObject().apply {
                    add(
                        "inputs",
                        JsonObject().apply {
                            addProperty(
                                "input",
                                scenario.request.currentUserMessage,
                            )
                            addProperty(
                                "conversation",
                                FunctionGemmaDeviceEvalContract.conversationText(
                                    scenario.request,
                                ),
                            )
                            addProperty(
                                "authoritativeContext",
                                FunctionGemmaDeviceEvalContract.authoritativeContextText(
                                    scenario.request.authoritativeContext,
                                ),
                            )
                            addProperty("callsJson", gson.toJson(output.calls))
                        },
                    )
                    add(
                        "expectedOutputs",
                        JsonObject().apply {
                            addProperty(
                                "output",
                                scenario.expectedRoute.toEvaluationContract(),
                            )
                        },
                    )
                    add(
                        "metadata",
                        JsonObject().apply {
                            addProperty("scenarioId", scenario.id)
                            addProperty(
                                "hardGates",
                                FunctionGemmaDeviceEvalContract.hardGateText(
                                    scenario.hardGates,
                                ),
                            )
                            addProperty("model", FunctionGemmaRouteContract.MODEL_ID)
                            addProperty(
                                "modelRevision",
                                FunctionGemmaRouteContract.MODEL_REVISION,
                            )
                            addProperty("runtime", FunctionGemmaRouteContract.RUNTIME_ID)
                            addProperty(
                                "promptContractSha256",
                                FunctionGemmaDeviceEvalContract.promptContractSha256,
                            )
                            addProperty(
                                "executionOutcome",
                                output.executionOutcome.name,
                            )
                            addProperty("fallbackOutcome", output.fallbackOutcome.name)
                            // Dokimos copies example metadata into immutable Java maps, which
                            // reject JSON null values. Explicit sentinels retain a fixed schema
                            // while preserving the distinction between an unmeasured latency and
                            // a failed run with a diagnostic.
                            addProperty(
                                "latencyMillis",
                                output.latencyMillis ?: LATENCY_NOT_RECORDED,
                            )
                            addProperty(
                                "errorCode",
                                output.errorCode ?: NO_ERROR_CODE,
                            )
                        },
                    )
                },
            )
        }

        return JsonObject().apply {
            addProperty(
                "artifactSchemaVersion",
                FunctionGemmaDeviceEvalContract.ARTIFACT_SCHEMA_VERSION,
            )
            addProperty(
                "suiteId",
                FunctionGemmaRouteQualificationContract.SUITE_ID,
            )
            add(
                "model",
                JsonObject().apply {
                    addProperty("id", FunctionGemmaRouteContract.MODEL_ID)
                    addProperty("revision", FunctionGemmaRouteContract.MODEL_REVISION)
                    addProperty("fileName", FunctionGemmaRouteContract.MODEL_FILE_NAME)
                    addProperty(
                        "manifestVersion",
                        FunctionGemmaRouteContract.MODEL_MANIFEST_VERSION,
                    )
                    addProperty(
                        "exactSizeBytes",
                        FunctionGemmaRouteContract.MODEL_EXACT_SIZE_BYTES,
                    )
                    addProperty("sha256", FunctionGemmaRouteContract.MODEL_SHA256)
                    addProperty("license", FunctionGemmaRouteContract.MODEL_LICENSE)
                    addProperty("runtime", FunctionGemmaRouteContract.RUNTIME_ID)
                },
            )
            add(
                "prompt",
                JsonObject().apply {
                    addProperty(
                        "schemaVersion",
                        FunctionGemmaRouteContract.SCHEMA_VERSION,
                    )
                    addProperty(
                        "systemInstructionSha256",
                        FunctionGemmaDeviceEvalContract.systemInstructionSha256,
                    )
                    addProperty(
                        "toolDescriptionSha256",
                        FunctionGemmaDeviceEvalContract.toolDescriptionSha256,
                    )
                    addProperty(
                        "contractSha256",
                        FunctionGemmaDeviceEvalContract.promptContractSha256,
                    )
                },
            )
            add(
                "manifest",
                JsonObject().apply {
                    addProperty(
                        "scenarioCount",
                        FunctionGemmaRouteQualificationContract.scenarioIds.size,
                    )
                    add(
                        "scenarioIds",
                        JsonArray().apply {
                            FunctionGemmaRouteQualificationContract.scenarioIds
                                .forEach(::add)
                        },
                    )
                },
            )
            add(
                "evaluationScope",
                JsonObject().apply {
                    addProperty("claim", FunctionGemmaDeviceEvalContract.EVALUATION_SCOPE)
                    addProperty(
                        "deviceExecution",
                        FunctionGemmaDeviceEvalContract.DEVICE_EXECUTION,
                    )
                    addProperty("integratedAppWorkflowExecuted", false)
                    addProperty("stateMutationObserved", false)
                    addProperty("catalogGroundingObserved", false)
                    addProperty("safetyPolicyObserved", false)
                },
            )
            add(
                "provenance",
                JsonObject().apply {
                    addProperty("source", provenance.source.name)
                    addProperty("manufacturer", provenance.manufacturer)
                    addProperty("model", provenance.model)
                    addProperty("device", provenance.device)
                    addProperty("product", provenance.product)
                    addProperty("buildFingerprint", provenance.buildFingerprint)
                    addProperty("sdkInt", provenance.sdkInt)
                    add(
                        "supportedAbis",
                        JsonArray().apply { provenance.supportedAbis.forEach(::add) },
                    )
                    addProperty("isEmulator", provenance.isEmulator)
                    addProperty("packageName", provenance.packageName)
                    addProperty("versionName", provenance.versionName)
                    addProperty("versionCode", provenance.versionCode)
                    addProperty("isDebuggable", provenance.isDebuggable)
                    addProperty("apkSha256", provenance.apkSha256)
                    addProperty(
                        "capturedAtEpochMillis",
                        provenance.capturedAtEpochMillis,
                    )
                    addProperty("runId", provenance.runId)
                    provenance.hostHarnessRevision?.let {
                        addProperty("hostHarnessRevision", it)
                    } ?: add("hostHarnessRevision", JsonNull.INSTANCE)
                    provenance.hostSourceTreeDirty?.let {
                        addProperty("hostSourceTreeDirty", it)
                    } ?: add("hostSourceTreeDirty", JsonNull.INSTANCE)
                },
            )
            add(
                "latencySla",
                JsonObject().apply {
                    addProperty("clock", FunctionGemmaDeviceEvalContract.LATENCY_CLOCK)
                    addProperty(
                        "percentileMethod",
                        FunctionGemmaDeviceEvalContract.LATENCY_PERCENTILE_METHOD,
                    )
                    addProperty(
                        "p95LimitMillis",
                        FunctionGemmaDeviceEvalContract.P95_ROUTE_LATENCY_LIMIT_MILLIS,
                    )
                    addProperty(
                        "maxLimitMillis",
                        FunctionGemmaDeviceEvalContract.MAX_ROUTE_LATENCY_LIMIT_MILLIS,
                    )
                },
            )
            addProperty("name", FunctionGemmaDeviceEvalContract.DATASET_NAME)
            addProperty(
                "description",
                FunctionGemmaDeviceEvalContract.DATASET_DESCRIPTION,
            )
            add("examples", examples)
        }.toString()
    }

    fun decodeCalls(rawCallsJson: String): List<ModelFunctionCall>? {
        if (
            rawCallsJson.isBlank() ||
            rawCallsJson.length > MAX_CALLS_JSON_CHARS
        ) {
            return null
        }
        val parsed = try {
            JsonParser.parseString(rawCallsJson)
        } catch (_: RuntimeException) {
            return null
        }
        if (!parsed.isJsonArray) return null
        return parsed.asJsonArray.map { element ->
            val call = element.objectValue() ?: return null
            if (call.keySet() != CALL_KEYS) return null
            val name = call.strictString("name") ?: return null
            val arguments = call.objectValue("arguments") ?: return null
            @Suppress("UNCHECKED_CAST")
            val argumentMap = try {
                gson.fromJson(arguments, Map::class.java) as? Map<String, Any?>
            } catch (_: RuntimeException) {
                null
            } ?: return null
            ModelFunctionCall(name = name, arguments = argumentMap)
        }
    }

    fun validate(rawArtifact: String): FunctionGemmaDeviceEvalArtifactValidation {
        if (rawArtifact.isBlank()) return rejected("empty_artifact")
        if (rawArtifact.length > MAX_ARTIFACT_CHARS) return rejected("artifact_too_large")
        val parsed = try {
            JsonParser.parseString(rawArtifact)
        } catch (_: RuntimeException) {
            return rejected("invalid_json")
        }
        if (!parsed.isJsonObject) return rejected("root_not_object")

        val root = parsed.asJsonObject
        val reasons = mutableListOf<String>()
        if (root.keySet() != ROOT_KEYS) reasons += "unexpected_root_fields"
        if (
            root.strictLong("artifactSchemaVersion") !=
            FunctionGemmaDeviceEvalContract.ARTIFACT_SCHEMA_VERSION.toLong()
        ) {
            reasons += "invalid_artifact_schema"
        }
        if (
            root.strictString("suiteId") !=
            FunctionGemmaRouteQualificationContract.SUITE_ID
        ) {
            reasons += "wrong_suite_id"
        }
        if (
            root.strictString("name") !=
            FunctionGemmaDeviceEvalContract.DATASET_NAME
        ) {
            reasons += "wrong_dataset_name"
        }
        if (
            root.strictString("description") !=
            FunctionGemmaDeviceEvalContract.DATASET_DESCRIPTION
        ) {
            reasons += "wrong_dataset_description"
        }
        validateModel(root.objectValue("model"), reasons)
        validatePrompt(root.objectValue("prompt"), reasons)
        validateManifest(root.objectValue("manifest"), reasons)
        validateEvaluationScope(root.objectValue("evaluationScope"), reasons)
        validateProvenance(root.objectValue("provenance"), reasons)
        validateLatencySla(root.objectValue("latencySla"), reasons)
        validateExamples(root.arrayValue("examples"), reasons)

        return if (reasons.isEmpty()) {
            FunctionGemmaDeviceEvalArtifactValidation.Accepted(
                scenarioIds = FunctionGemmaRouteQualificationContract.scenarioIds,
            )
        } else {
            FunctionGemmaDeviceEvalArtifactValidation.Rejected(reasons.distinct())
        }
    }

    /**
     * Stronger entry point for a score that will be described as physical-device evidence.
     *
     * Generic [validate] also accepts the explicit synthetic sentinel used to test the evaluator
     * itself. This method never does.
     */
    fun validatePhysicalQualification(
        rawArtifact: String,
    ): FunctionGemmaDeviceEvalArtifactValidation {
        val validated = validate(rawArtifact)
        if (validated is FunctionGemmaDeviceEvalArtifactValidation.Rejected) {
            return validated
        }
        val root = JsonParser.parseString(rawArtifact).asJsonObject
        val provenance = root.objectValue("provenance")
        val reasons = mutableListOf<String>()
        if (
            provenance?.strictString("source") !=
            FunctionGemmaEvalSource.PHYSICAL_ANDROID_DEVICE.name
        ) {
            reasons += "physical_qualification_requires_physical_source"
        }
        if (provenance?.strictBoolean("isEmulator") != false) {
            reasons += "physical_qualification_rejects_emulator"
        }
        return if (reasons.isEmpty()) validated else rejected(*reasons.toTypedArray())
    }

    private fun validateModel(
        model: JsonObject?,
        reasons: MutableList<String>,
    ) {
        if (model == null) {
            reasons += "invalid_model_manifest"
            return
        }
        if (model.keySet() != MODEL_KEYS) reasons += "unexpected_model_fields"
        if (model.strictString("id") != FunctionGemmaRouteContract.MODEL_ID) {
            reasons += "wrong_model_id"
        }
        if (
            model.strictString("revision") !=
            FunctionGemmaRouteContract.MODEL_REVISION
        ) {
            reasons += "wrong_model_revision"
        }
        if (
            model.strictString("fileName") !=
            FunctionGemmaRouteContract.MODEL_FILE_NAME
        ) {
            reasons += "wrong_model_file"
        }
        if (
            model.strictString("manifestVersion") !=
            FunctionGemmaRouteContract.MODEL_MANIFEST_VERSION
        ) {
            reasons += "wrong_model_manifest_version"
        }
        if (
            model.strictLong("exactSizeBytes") !=
            FunctionGemmaRouteContract.MODEL_EXACT_SIZE_BYTES
        ) {
            reasons += "wrong_model_size"
        }
        if (
            model.strictString("sha256") !=
            FunctionGemmaRouteContract.MODEL_SHA256
        ) {
            reasons += "wrong_model_sha256"
        }
        if (
            model.strictString("license") !=
            FunctionGemmaRouteContract.MODEL_LICENSE
        ) {
            reasons += "wrong_model_license"
        }
        if (
            model.strictString("runtime") !=
            FunctionGemmaRouteContract.RUNTIME_ID
        ) {
            reasons += "wrong_runtime"
        }
    }

    private fun validatePrompt(
        prompt: JsonObject?,
        reasons: MutableList<String>,
    ) {
        if (prompt == null) {
            reasons += "invalid_prompt_manifest"
            return
        }
        if (prompt.keySet() != PROMPT_KEYS) reasons += "unexpected_prompt_fields"
        if (
            prompt.strictLong("schemaVersion") !=
            FunctionGemmaRouteContract.SCHEMA_VERSION.toLong()
        ) {
            reasons += "wrong_prompt_schema"
        }
        if (
            prompt.strictString("systemInstructionSha256") !=
            FunctionGemmaDeviceEvalContract.systemInstructionSha256
        ) {
            reasons += "wrong_system_instruction_sha256"
        }
        if (
            prompt.strictString("toolDescriptionSha256") !=
            FunctionGemmaDeviceEvalContract.toolDescriptionSha256
        ) {
            reasons += "wrong_tool_description_sha256"
        }
        if (
            prompt.strictString("contractSha256") !=
            FunctionGemmaDeviceEvalContract.promptContractSha256
        ) {
            reasons += "wrong_prompt_contract_sha256"
        }
    }

    private fun validateManifest(
        manifest: JsonObject?,
        reasons: MutableList<String>,
    ) {
        if (manifest == null) {
            reasons += "invalid_scenario_manifest"
            return
        }
        if (manifest.keySet() != MANIFEST_KEYS) reasons += "unexpected_manifest_fields"
        if (
            manifest.strictLong("scenarioCount") !=
            FunctionGemmaRouteQualificationContract.scenarioIds.size.toLong()
        ) {
            reasons += "wrong_manifest_count"
        }
        val ids = manifest.stringList("scenarioIds")
        if (ids == null) {
            reasons += "invalid_manifest_scenario_ids"
        } else {
            if (ids.distinct().size != ids.size) {
                reasons += "duplicate_manifest_scenario_ids"
            }
            if (ids != FunctionGemmaRouteQualificationContract.scenarioIds) {
                reasons += "wrong_manifest_scenario_ids"
            }
        }
    }

    private fun validateEvaluationScope(
        scope: JsonObject?,
        reasons: MutableList<String>,
    ) {
        if (scope == null) {
            reasons += "invalid_evaluation_scope"
            return
        }
        if (scope.keySet() != EVALUATION_SCOPE_KEYS) {
            reasons += "unexpected_evaluation_scope_fields"
        }
        if (scope.strictString("claim") != FunctionGemmaDeviceEvalContract.EVALUATION_SCOPE) {
            reasons += "wrong_evaluation_scope"
        }
        if (
            scope.strictString("deviceExecution") !=
            FunctionGemmaDeviceEvalContract.DEVICE_EXECUTION
        ) {
            reasons += "wrong_device_execution_scope"
        }
        INTEGRATION_OBSERVATION_FIELDS.forEach { field ->
            if (scope.strictBoolean(field) != false) {
                reasons += "unsupported_integration_observation:$field"
            }
        }
    }

    private fun validateProvenance(
        provenance: JsonObject?,
        reasons: MutableList<String>,
    ) {
        if (provenance == null) {
            reasons += "invalid_provenance"
            return
        }
        if (provenance.keySet() != PROVENANCE_KEYS) {
            reasons += "unexpected_provenance_fields"
        }
        val source = provenance.strictString("source")
            ?.let { runCatching { FunctionGemmaEvalSource.valueOf(it) }.getOrNull() }
        if (source == null) {
            reasons += "invalid_provenance_source"
            return
        }
        val isEmulator = provenance.strictBoolean("isEmulator")
        val isDebuggable = provenance.strictBoolean("isDebuggable")
        val revisionElement = provenance.get("hostHarnessRevision")
        val revision = provenance.nullableStrictString("hostHarnessRevision")
        val dirtyElement = provenance.get("hostSourceTreeDirty")
        val dirty = provenance.nullableStrictBoolean("hostSourceTreeDirty")
        if (
            revisionElement == null ||
            (
                revisionElement !is JsonNull &&
                    (revision == null || !GIT_SHA_PATTERN.matches(revision))
                )
        ) {
            reasons += "invalid_host_harness_revision"
        }
        if (
            dirtyElement == null ||
            (dirtyElement !is JsonNull && dirty == null)
        ) {
            reasons += "invalid_host_source_tree_dirty"
        }
        if ((revision == null) != (dirty == null)) {
            reasons += "incomplete_host_harness_provenance"
        }

        when (source) {
            FunctionGemmaEvalSource.SYNTHETIC_CONTRACT_TEST -> {
                val expected = FunctionGemmaArtifactProvenance.syntheticContractTest()
                if (
                    provenance.strictString("manufacturer") != expected.manufacturer ||
                    provenance.strictString("model") != expected.model ||
                    provenance.strictString("device") != expected.device ||
                    provenance.strictString("product") != expected.product ||
                    provenance.strictString("buildFingerprint") !=
                        expected.buildFingerprint ||
                    provenance.strictLong("sdkInt") != expected.sdkInt.toLong() ||
                    provenance.stringList("supportedAbis") != expected.supportedAbis ||
                    isEmulator != expected.isEmulator ||
                    provenance.strictString("packageName") != expected.packageName ||
                    provenance.strictString("versionName") != expected.versionName ||
                    provenance.strictLong("versionCode") != expected.versionCode ||
                    isDebuggable != expected.isDebuggable ||
                    provenance.strictString("apkSha256") != expected.apkSha256 ||
                    provenance.strictLong("capturedAtEpochMillis") !=
                        expected.capturedAtEpochMillis ||
                    provenance.strictString("runId") != expected.runId ||
                    revision != null ||
                    dirty != null
                ) {
                    reasons += "invalid_synthetic_provenance"
                }
            }
            FunctionGemmaEvalSource.PHYSICAL_ANDROID_DEVICE -> {
                PHYSICAL_TEXT_FIELDS.forEach { field ->
                    val value = provenance.strictString(field)
                    if (
                        value == null ||
                        value.isBlank() ||
                        value.length > MAX_PROVENANCE_TEXT_CHARS ||
                        value.any(Char::isISOControl)
                    ) {
                        reasons += "invalid_physical_provenance:$field"
                    }
                }
                val sdkInt = provenance.strictLong("sdkInt")
                if (sdkInt == null || sdkInt !in MIN_ANDROID_SDK..MAX_ANDROID_SDK) {
                    reasons += "invalid_physical_provenance:sdkInt"
                }
                val abis = provenance.stringList("supportedAbis")
                if (
                    abis == null ||
                    abis.isEmpty() ||
                    abis.size > MAX_SUPPORTED_ABIS ||
                    abis.distinct().size != abis.size ||
                    abis.any {
                        it.isBlank() ||
                            it.length > MAX_ABI_CHARS ||
                            it.any(Char::isISOControl)
                    }
                ) {
                    reasons += "invalid_physical_provenance:supportedAbis"
                }
                if (isEmulator != false) {
                    reasons += "physical_device_is_emulator"
                }
                if (
                    provenance.strictString("packageName") !=
                    FunctionGemmaDeviceEvalContract.APP_PACKAGE_NAME
                ) {
                    reasons += "wrong_app_package"
                }
                if (isDebuggable == null) {
                    reasons += "invalid_physical_provenance:isDebuggable"
                }
                val apkSha256 = provenance.strictString("apkSha256")
                if (apkSha256 == null || !SHA256_PATTERN.matches(apkSha256)) {
                    reasons += "invalid_apk_sha256"
                }
                val capturedAt = provenance.strictLong("capturedAtEpochMillis")
                if (capturedAt == null || capturedAt <= 0) {
                    reasons += "invalid_capture_time"
                }
                val runId = provenance.strictString("runId")
                if (
                    runId == null ||
                    runId.isBlank() ||
                    runId.length > MAX_RUN_ID_CHARS ||
                    runId.any(Char::isISOControl)
                ) {
                    reasons += "invalid_run_id"
                }
                val versionCode = provenance.strictLong("versionCode")
                if (versionCode == null || versionCode <= 0) {
                    reasons += "invalid_app_version_code"
                }
            }
        }
    }

    private fun validateLatencySla(
        sla: JsonObject?,
        reasons: MutableList<String>,
    ) {
        if (sla == null) {
            reasons += "invalid_latency_sla"
            return
        }
        if (sla.keySet() != LATENCY_SLA_KEYS) reasons += "unexpected_latency_sla_fields"
        if (sla.strictString("clock") != FunctionGemmaDeviceEvalContract.LATENCY_CLOCK) {
            reasons += "wrong_latency_clock"
        }
        if (
            sla.strictString("percentileMethod") !=
            FunctionGemmaDeviceEvalContract.LATENCY_PERCENTILE_METHOD
        ) {
            reasons += "wrong_latency_percentile_method"
        }
        if (
            sla.strictLong("p95LimitMillis") !=
            FunctionGemmaDeviceEvalContract.P95_ROUTE_LATENCY_LIMIT_MILLIS
        ) {
            reasons += "wrong_p95_latency_limit"
        }
        if (
            sla.strictLong("maxLimitMillis") !=
            FunctionGemmaDeviceEvalContract.MAX_ROUTE_LATENCY_LIMIT_MILLIS
        ) {
            reasons += "wrong_max_latency_limit"
        }
    }

    private fun validateExamples(
        examples: JsonArray?,
        reasons: MutableList<String>,
    ) {
        if (examples == null) {
            reasons += "invalid_examples"
            return
        }
        val scenarios =
            FunctionGemmaRouteQualificationContract.scenarios.associateBy { it.id }
        val actualIds = mutableListOf<String>()
        if (examples.size() != scenarios.size) reasons += "wrong_example_count"

        examples.forEachIndexed { index, element ->
            val example = element.objectValue()
            if (example == null) {
                reasons += "invalid_example:$index"
                return@forEachIndexed
            }
            if (example.keySet() != EXAMPLE_KEYS) {
                reasons += "unexpected_example_fields:$index"
            }
            val inputs = example.objectValue("inputs")
            val expectedOutputs = example.objectValue("expectedOutputs")
            val metadata = example.objectValue("metadata")
            if (inputs == null || expectedOutputs == null || metadata == null) {
                reasons += "invalid_example_structure:$index"
                return@forEachIndexed
            }
            if (inputs.keySet() != INPUT_KEYS) reasons += "unexpected_input_fields:$index"
            if (expectedOutputs.keySet() != OUTPUT_KEYS) {
                reasons += "unexpected_output_fields:$index"
            }
            if (metadata.keySet() != METADATA_KEYS) {
                reasons += "unexpected_metadata_fields:$index"
            }

            val scenarioId = metadata.strictString("scenarioId")
            if (scenarioId == null) {
                reasons += "missing_scenario_id:$index"
                return@forEachIndexed
            }
            actualIds += scenarioId
            val scenario = scenarios[scenarioId]
            if (scenario == null) {
                reasons += "unknown_scenario_id:$scenarioId"
                return@forEachIndexed
            }
            if (inputs.strictString("input") != scenario.request.currentUserMessage) {
                reasons += "query_mismatch:$scenarioId"
            }
            if (
                inputs.strictString("conversation") !=
                FunctionGemmaDeviceEvalContract.conversationText(scenario.request)
            ) {
                reasons += "conversation_mismatch:$scenarioId"
            }
            if (
                inputs.strictString("authoritativeContext") !=
                FunctionGemmaDeviceEvalContract.authoritativeContextText(
                    scenario.request.authoritativeContext,
                )
            ) {
                reasons += "authoritative_context_mismatch:$scenarioId"
            }
            val callsJson = inputs.strictString("callsJson")
            if (callsJson == null || decodeCalls(callsJson) == null) {
                reasons += "invalid_calls_json:$scenarioId"
            }
            if (
                expectedOutputs.strictString("output") !=
                scenario.expectedRoute.toEvaluationContract()
            ) {
                reasons += "expected_route_mismatch:$scenarioId"
            }
            if (
                metadata.strictString("hardGates") !=
                FunctionGemmaDeviceEvalContract.hardGateText(scenario.hardGates)
            ) {
                reasons += "hard_gates_mismatch:$scenarioId"
            }
            validatePerExampleProvenance(metadata, scenarioId, reasons)
            validateOutcome(metadata, scenarioId, reasons)
        }

        val duplicateIds = actualIds.groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        duplicateIds.forEach { reasons += "duplicate_scenario_id:$it" }
        val expectedIds = FunctionGemmaRouteQualificationContract.scenarioIds
        (expectedIds.toSet() - actualIds.toSet())
            .forEach { reasons += "missing_scenario_id:$it" }
        (actualIds.toSet() - expectedIds.toSet())
            .forEach { reasons += "extra_scenario_id:$it" }
        if (actualIds != expectedIds) reasons += "wrong_example_order"
    }

    private fun validatePerExampleProvenance(
        metadata: JsonObject,
        scenarioId: String,
        reasons: MutableList<String>,
    ) {
        if (metadata.strictString("model") != FunctionGemmaRouteContract.MODEL_ID) {
            reasons += "metadata_model_mismatch:$scenarioId"
        }
        if (
            metadata.strictString("modelRevision") !=
            FunctionGemmaRouteContract.MODEL_REVISION
        ) {
            reasons += "metadata_revision_mismatch:$scenarioId"
        }
        if (
            metadata.strictString("runtime") !=
            FunctionGemmaRouteContract.RUNTIME_ID
        ) {
            reasons += "metadata_runtime_mismatch:$scenarioId"
        }
        if (
            metadata.strictString("promptContractSha256") !=
            FunctionGemmaDeviceEvalContract.promptContractSha256
        ) {
            reasons += "metadata_prompt_mismatch:$scenarioId"
        }
    }

    private fun validateOutcome(
        metadata: JsonObject,
        scenarioId: String,
        reasons: MutableList<String>,
    ) {
        val execution = metadata.strictString("executionOutcome")
            ?.let { value ->
                runCatching { FunctionGemmaExecutionOutcome.valueOf(value) }.getOrNull()
            }
        val fallback = metadata.strictString("fallbackOutcome")
            ?.let { value ->
                runCatching { FunctionGemmaFallbackOutcome.valueOf(value) }.getOrNull()
            }
        if (execution == null) reasons += "invalid_execution_outcome:$scenarioId"
        if (fallback == null) reasons += "invalid_fallback_outcome:$scenarioId"

        val latency = metadata.strictLong("latencyMillis")
        if (
            latency == null ||
            latency !in LATENCY_NOT_RECORDED..MAX_RECORDED_LATENCY_MILLIS
        ) {
            reasons += "invalid_latency:$scenarioId"
        }
        val rawErrorCode = metadata.strictString("errorCode")
        val errorCode = rawErrorCode?.takeUnless { it == NO_ERROR_CODE }
        if (
            rawErrorCode == null ||
            (
                rawErrorCode != NO_ERROR_CODE &&
                    (
                        rawErrorCode.isBlank() ||
                            rawErrorCode.length > MAX_ERROR_CODE_CHARS ||
                            rawErrorCode.any(Char::isISOControl)
                        )
                )
        ) {
            reasons += "invalid_error_code:$scenarioId"
        }
        when (execution) {
            FunctionGemmaExecutionOutcome.MODEL_COMPLETED -> {
                if (fallback != FunctionGemmaFallbackOutcome.NONE) {
                    reasons += "completed_run_must_not_fallback:$scenarioId"
                }
                if (errorCode != null) reasons += "completed_run_has_error:$scenarioId"
            }
            FunctionGemmaExecutionOutcome.TIMED_OUT,
            FunctionGemmaExecutionOutcome.MODEL_OUTPUT_REJECTED,
            FunctionGemmaExecutionOutcome.RUNTIME_ERROR,
            -> {
                if (
                    fallback !=
                    FunctionGemmaFallbackOutcome.APP_FAIL_CLOSED_EXPECTED_NOT_EXECUTED
                ) {
                    reasons += "missing_expected_app_fallback:$scenarioId"
                }
                if (errorCode == null) reasons += "failed_run_missing_error:$scenarioId"
            }
            null -> Unit
        }
    }

    private fun rejected(
        vararg reasons: String,
    ) = FunctionGemmaDeviceEvalArtifactValidation.Rejected(reasons.toList())

    private fun JsonObject.strictString(name: String): String? =
        get(name).strictString()

    private fun JsonObject.strictLong(name: String): Long? =
        get(name).strictLong()

    private fun JsonObject.nullableStrictString(name: String): String? {
        val value = get(name) ?: return null
        return if (value is JsonNull) null else value.strictString()
    }

    private fun JsonObject.nullableStrictLong(name: String): Long? {
        val value = get(name) ?: return null
        return if (value is JsonNull) null else value.strictLong()
    }

    private fun JsonObject.strictBoolean(name: String): Boolean? =
        get(name).strictBoolean()

    private fun JsonObject.nullableStrictBoolean(name: String): Boolean? {
        val value = get(name) ?: return null
        return if (value is JsonNull) null else value.strictBoolean()
    }

    private fun JsonObject.objectValue(name: String): JsonObject? =
        get(name).objectValue()

    private fun JsonObject.arrayValue(name: String): JsonArray? =
        get(name)?.takeIf(JsonElement::isJsonArray)?.asJsonArray

    private fun JsonObject.stringList(name: String): List<String>? {
        val array = arrayValue(name) ?: return null
        return array.map { it.strictString() ?: return null }
    }

    private fun JsonElement?.objectValue(): JsonObject? =
        this?.takeIf(JsonElement::isJsonObject)?.asJsonObject

    private fun JsonElement?.strictString(): String? {
        val primitive = this
            ?.takeIf(JsonElement::isJsonPrimitive)
            ?.asJsonPrimitive
            ?: return null
        return primitive.takeIf { it.isString }?.asString
    }

    private fun JsonElement?.strictLong(): Long? {
        val primitive = this
            ?.takeIf(JsonElement::isJsonPrimitive)
            ?.asJsonPrimitive
            ?: return null
        if (!primitive.isNumber) return null
        val raw = primitive.asString
        if (!INTEGER_PATTERN.matches(raw)) return null
        return raw.toLongOrNull()
    }

    private fun JsonElement?.strictBoolean(): Boolean? {
        val primitive = this
            ?.takeIf(JsonElement::isJsonPrimitive)
            ?.asJsonPrimitive
            ?: return null
        return primitive.takeIf { it.isBoolean }?.asBoolean
    }

    private val ROOT_KEYS = setOf(
        "artifactSchemaVersion",
        "suiteId",
        "model",
        "prompt",
        "manifest",
        "evaluationScope",
        "provenance",
        "latencySla",
        "name",
        "description",
        "examples",
    )
    private val MODEL_KEYS = setOf(
        "id",
        "revision",
        "fileName",
        "manifestVersion",
        "exactSizeBytes",
        "sha256",
        "license",
        "runtime",
    )
    private val PROMPT_KEYS = setOf(
        "schemaVersion",
        "systemInstructionSha256",
        "toolDescriptionSha256",
        "contractSha256",
    )
    private val MANIFEST_KEYS = setOf("scenarioCount", "scenarioIds")
    private val EVALUATION_SCOPE_KEYS = setOf(
        "claim",
        "deviceExecution",
        "integratedAppWorkflowExecuted",
        "stateMutationObserved",
        "catalogGroundingObserved",
        "safetyPolicyObserved",
    )
    private val INTEGRATION_OBSERVATION_FIELDS = setOf(
        "integratedAppWorkflowExecuted",
        "stateMutationObserved",
        "catalogGroundingObserved",
        "safetyPolicyObserved",
    )
    private val PROVENANCE_KEYS = setOf(
        "source",
        "manufacturer",
        "model",
        "device",
        "product",
        "buildFingerprint",
        "sdkInt",
        "supportedAbis",
        "isEmulator",
        "packageName",
        "versionName",
        "versionCode",
        "isDebuggable",
        "apkSha256",
        "capturedAtEpochMillis",
        "runId",
        "hostHarnessRevision",
        "hostSourceTreeDirty",
    )
    private val LATENCY_SLA_KEYS = setOf(
        "clock",
        "percentileMethod",
        "p95LimitMillis",
        "maxLimitMillis",
    )
    private val EXAMPLE_KEYS = setOf("inputs", "expectedOutputs", "metadata")
    private val INPUT_KEYS = setOf(
        "input",
        "conversation",
        "authoritativeContext",
        "callsJson",
    )
    private val OUTPUT_KEYS = setOf("output")
    private val METADATA_KEYS = setOf(
        "scenarioId",
        "hardGates",
        "model",
        "modelRevision",
        "runtime",
        "promptContractSha256",
        "executionOutcome",
        "fallbackOutcome",
        "latencyMillis",
        "errorCode",
    )
    private val CALL_KEYS = setOf("name", "arguments")
    private val INTEGER_PATTERN = Regex("""(?:0|[1-9]\d*)""")
    private val SHA256_PATTERN = Regex("""[0-9a-f]{64}""")
    private val GIT_SHA_PATTERN = Regex("""[0-9a-f]{40}""")
    private val PHYSICAL_TEXT_FIELDS = setOf(
        "manufacturer",
        "model",
        "device",
        "product",
        "buildFingerprint",
        "versionName",
    )
    private const val MIN_ANDROID_SDK = 26L
    private const val MAX_ANDROID_SDK = 100L
    private const val MAX_PROVENANCE_TEXT_CHARS = 160
    private const val MAX_SUPPORTED_ABIS = 8
    private const val MAX_ABI_CHARS = 64
    private const val MAX_RUN_ID_CHARS = 120
}
