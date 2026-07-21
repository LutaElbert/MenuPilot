package com.menupilot.assistant.contract

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class QwenDeviceEvalScenario(
    val id: String,
    val query: String,
    val expectedContract: String,
)

data class QwenDeviceEvalOutput(
    val scenarioId: String,
    val rawOutput: String,
)

/**
 * Versioned physical-device qualification contract shared by Android export and host evaluation.
 *
 * Updating the model, runtime, scenario text, or expected result requires a deliberate suite
 * version change. A partial scenario selection may be encoded for debugging, but the validator
 * will reject it as a qualification artifact.
 */
object QwenDeviceEvalContract {
    const val ARTIFACT_SCHEMA_VERSION = 1
    const val SUITE_ID = "menupilot-qwen-intent-device-v1"
    const val DATASET_NAME = "menupilot-qwen3-0.6b-device-runs-v1"
    const val DATASET_DESCRIPTION =
        "Pinned Android Qwen outputs evaluated on the host with the production intent.v1 parser."
    const val ARTIFACT_FILE_NAME = "qwen3-0.6b-intent-runs.json"
    const val MODEL_ID = "litert-community/Qwen3-0.6B"
    const val MODEL_REVISION = "dd97997951bb15a2a71f539ba17f604707c0b11a"
    const val RUNTIME_ID = "litertlm-android:0.14.0"
    const val MODEL_SHA256 =
        "b1baab462f6be49d70eada79d715c2c52cd9ece0cad00bddf6a2c097d23498e9"
    const val MODEL_EXACT_SIZE_BYTES = 497_664_000L
    const val SCENARIO_ID_FLAGSHIP = "flagship-allergy-diet-spice-budget"
    const val SCENARIO_ID_NEGATED_ALLERGY = "negated-allergy-with-spice"
    const val SCENARIO_ID_CEBUANO = "cebuano-shellfish-spice-speed"
    const val SCENARIO_ID_BESTSELLER = "weekly-bestseller"

    val scenarios = listOf(
        QwenDeviceEvalScenario(
            id = SCENARIO_ID_FLAGSHIP,
            query =
                "I'm allergic to peanuts, vegetarian, and want something spicy under ₱500.",
            expectedContract =
                "intent.v1|allergens=PEANUT:ALLERGY|diets=VEGETARIAN|" +
                    "preferences=SPICY|budgetMinor=50000|clarify=NO|unresolved=NONE",
        ),
        QwenDeviceEvalScenario(
            id = SCENARIO_ID_NEGATED_ALLERGY,
            query = "I'm not allergic to peanuts and I want something spicy.",
            expectedContract =
                "intent.v1|allergens=NONE|diets=NONE|preferences=SPICY|" +
                    "budgetMinor=NONE|clarify=NO|unresolved=NONE",
        ),
        QwenDeviceEvalScenario(
            id = SCENARIO_ID_CEBUANO,
            query = "Dili ko pwede og shellfish. Ganahan ko ug halang ug dali.",
            expectedContract =
                "intent.v1|allergens=SHELLFISH:UNSPECIFIED|diets=NONE|" +
                    "preferences=SPICY,QUICK|budgetMinor=NONE|clarify=NO|unresolved=NONE",
        ),
        QwenDeviceEvalScenario(
            id = SCENARIO_ID_BESTSELLER,
            query = "What is your bestseller this week?",
            expectedContract =
                "intent.v1|allergens=NONE|diets=NONE|preferences=POPULAR|" +
                    "budgetMinor=NONE|clarify=NO|unresolved=NONE",
        ),
    )

    val scenarioIds: List<String> = scenarios.map(QwenDeviceEvalScenario::id)
}

sealed interface QwenDeviceEvalArtifactValidation {
    data class Accepted(
        val scenarioIds: List<String>,
    ) : QwenDeviceEvalArtifactValidation

    data class Rejected(
        val reasons: List<String>,
    ) : QwenDeviceEvalArtifactValidation
}

object QwenDeviceEvalArtifactCodec {
    private const val MAX_ARTIFACT_CHARS = 128_000

    fun encode(outputs: List<QwenDeviceEvalOutput>): String {
        val scenariosById = QwenDeviceEvalContract.scenarios.associateBy { it.id }
        val examples = JsonArray()
        outputs.forEach { output ->
            val scenario = requireNotNull(scenariosById[output.scenarioId]) {
                "Unknown device-eval scenario ID: ${output.scenarioId}"
            }
            examples.add(
                JsonObject().apply {
                    add(
                        "inputs",
                        JsonObject().apply {
                            addProperty("input", scenario.query)
                            addProperty("rawOutput", output.rawOutput)
                        },
                    )
                    add(
                        "expectedOutputs",
                        JsonObject().apply {
                            addProperty("output", scenario.expectedContract)
                        },
                    )
                    add(
                        "metadata",
                        JsonObject().apply {
                            addProperty("scenarioId", scenario.id)
                            addProperty("model", QwenDeviceEvalContract.MODEL_ID)
                            addProperty(
                                "modelRevision",
                                QwenDeviceEvalContract.MODEL_REVISION,
                            )
                            addProperty("runtime", QwenDeviceEvalContract.RUNTIME_ID)
                            addProperty("modelSha256", QwenDeviceEvalContract.MODEL_SHA256)
                        },
                    )
                },
            )
        }

        return JsonObject().apply {
            addProperty(
                "artifactSchemaVersion",
                QwenDeviceEvalContract.ARTIFACT_SCHEMA_VERSION,
            )
            addProperty("suiteId", QwenDeviceEvalContract.SUITE_ID)
            add(
                "model",
                JsonObject().apply {
                    addProperty("id", QwenDeviceEvalContract.MODEL_ID)
                    addProperty("revision", QwenDeviceEvalContract.MODEL_REVISION)
                    addProperty("runtime", QwenDeviceEvalContract.RUNTIME_ID)
                    addProperty("sha256", QwenDeviceEvalContract.MODEL_SHA256)
                    addProperty(
                        "exactSizeBytes",
                        QwenDeviceEvalContract.MODEL_EXACT_SIZE_BYTES,
                    )
                },
            )
            add(
                "manifest",
                JsonObject().apply {
                    addProperty(
                        "scenarioCount",
                        QwenDeviceEvalContract.scenarioIds.size,
                    )
                    add(
                        "scenarioIds",
                        JsonArray().apply {
                            QwenDeviceEvalContract.scenarioIds.forEach(::add)
                        },
                    )
                },
            )
            addProperty("name", QwenDeviceEvalContract.DATASET_NAME)
            addProperty("description", QwenDeviceEvalContract.DATASET_DESCRIPTION)
            add("examples", examples)
        }.toString()
    }

    fun validate(rawArtifact: String): QwenDeviceEvalArtifactValidation {
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
            QwenDeviceEvalContract.ARTIFACT_SCHEMA_VERSION.toLong()
        ) {
            reasons += "invalid_artifact_schema"
        }
        if (root.strictString("suiteId") != QwenDeviceEvalContract.SUITE_ID) {
            reasons += "wrong_suite_id"
        }
        if (root.strictString("name") != QwenDeviceEvalContract.DATASET_NAME) {
            reasons += "wrong_dataset_name"
        }
        if (
            root.strictString("description") !=
            QwenDeviceEvalContract.DATASET_DESCRIPTION
        ) {
            reasons += "wrong_dataset_description"
        }

        validateModelManifest(root.objectValue("model"), reasons)
        validateScenarioManifest(root.objectValue("manifest"), reasons)
        validateExamples(root.arrayValue("examples"), reasons)

        return if (reasons.isEmpty()) {
            QwenDeviceEvalArtifactValidation.Accepted(
                scenarioIds = QwenDeviceEvalContract.scenarioIds,
            )
        } else {
            QwenDeviceEvalArtifactValidation.Rejected(reasons.distinct())
        }
    }

    private fun validateModelManifest(
        model: JsonObject?,
        reasons: MutableList<String>,
    ) {
        if (model == null) {
            reasons += "invalid_model_manifest"
            return
        }
        if (model.keySet() != MODEL_KEYS) reasons += "unexpected_model_fields"
        if (model.strictString("id") != QwenDeviceEvalContract.MODEL_ID) {
            reasons += "wrong_model_id"
        }
        if (model.strictString("revision") != QwenDeviceEvalContract.MODEL_REVISION) {
            reasons += "wrong_model_revision"
        }
        if (model.strictString("runtime") != QwenDeviceEvalContract.RUNTIME_ID) {
            reasons += "wrong_runtime"
        }
        if (model.strictString("sha256") != QwenDeviceEvalContract.MODEL_SHA256) {
            reasons += "wrong_model_sha256"
        }
        if (
            model.strictLong("exactSizeBytes") !=
            QwenDeviceEvalContract.MODEL_EXACT_SIZE_BYTES
        ) {
            reasons += "wrong_model_size"
        }
    }

    private fun validateScenarioManifest(
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
            QwenDeviceEvalContract.scenarioIds.size.toLong()
        ) {
            reasons += "wrong_manifest_count"
        }
        val ids = manifest.stringList("scenarioIds")
        if (ids == null) {
            reasons += "invalid_manifest_scenario_ids"
            return
        }
        if (ids.distinct().size != ids.size) reasons += "duplicate_manifest_scenario_ids"
        if (ids != QwenDeviceEvalContract.scenarioIds) {
            reasons += "wrong_manifest_scenario_ids"
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
        val expectedIds = QwenDeviceEvalContract.scenarioIds.toSet()
        val scenariosById = QwenDeviceEvalContract.scenarios.associateBy { it.id }
        val actualIds = mutableListOf<String>()
        if (examples.size() != expectedIds.size) reasons += "wrong_example_count"

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
            if (
                inputs == null ||
                expectedOutputs == null ||
                metadata == null
            ) {
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
            val scenario = scenariosById[scenarioId]
            if (scenario == null) {
                reasons += "unknown_scenario_id:$scenarioId"
            } else {
                if (inputs.strictString("input") != scenario.query) {
                    reasons += "query_mismatch:$scenarioId"
                }
                if (inputs.strictString("rawOutput") == null) {
                    reasons += "invalid_raw_output:$scenarioId"
                }
                if (
                    expectedOutputs.strictString("output") !=
                    scenario.expectedContract
                ) {
                    reasons += "expected_contract_mismatch:$scenarioId"
                }
            }
            if (metadata.strictString("model") != QwenDeviceEvalContract.MODEL_ID) {
                reasons += "metadata_model_mismatch:$scenarioId"
            }
            if (
                metadata.strictString("modelRevision") !=
                QwenDeviceEvalContract.MODEL_REVISION
            ) {
                reasons += "metadata_revision_mismatch:$scenarioId"
            }
            if (
                metadata.strictString("runtime") !=
                QwenDeviceEvalContract.RUNTIME_ID
            ) {
                reasons += "metadata_runtime_mismatch:$scenarioId"
            }
            if (
                metadata.strictString("modelSha256") !=
                QwenDeviceEvalContract.MODEL_SHA256
            ) {
                reasons += "metadata_sha256_mismatch:$scenarioId"
            }
        }

        val duplicateIds = actualIds.groupingBy { it }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
            .sorted()
        val missingIds = (expectedIds - actualIds.toSet()).sorted()
        val extraIds = (actualIds.toSet() - expectedIds).sorted()
        if (duplicateIds.isNotEmpty()) {
            reasons += "duplicate_scenario_ids:${duplicateIds.joinToString(",")}"
        }
        if (missingIds.isNotEmpty()) {
            reasons += "missing_scenario_ids:${missingIds.joinToString(",")}"
        }
        if (extraIds.isNotEmpty()) {
            reasons += "extra_scenario_ids:${extraIds.joinToString(",")}"
        }
    }

    private fun rejected(reason: String) =
        QwenDeviceEvalArtifactValidation.Rejected(listOf(reason))

    private fun JsonObject.strictString(name: String): String? =
        get(name)
            ?.takeIf(JsonElement::isJsonPrimitive)
            ?.asJsonPrimitive
            ?.takeIf { it.isString }
            ?.asString

    private fun JsonObject.strictLong(name: String): Long? {
        val primitive = get(name)
            ?.takeIf(JsonElement::isJsonPrimitive)
            ?.asJsonPrimitive
            ?: return null
        if (!primitive.isNumber) return null
        val raw = primitive.asString
        if (!INTEGER_PATTERN.matches(raw)) return null
        return raw.toLongOrNull()
    }

    private fun JsonObject.objectValue(name: String): JsonObject? =
        get(name)?.objectValue()

    private fun JsonElement.objectValue(): JsonObject? =
        takeIf(JsonElement::isJsonObject)?.asJsonObject

    private fun JsonObject.arrayValue(name: String): JsonArray? =
        get(name)?.takeIf(JsonElement::isJsonArray)?.asJsonArray

    private fun JsonObject.stringList(name: String): List<String>? {
        val values = arrayValue(name) ?: return null
        return values.map { item ->
            item
                .takeIf(JsonElement::isJsonPrimitive)
                ?.asJsonPrimitive
                ?.takeIf { it.isString }
                ?.asString
                ?: return null
        }
    }

    private val INTEGER_PATTERN = Regex("""(?:0|[1-9]\d*)""")
    private val ROOT_KEYS = setOf(
        "artifactSchemaVersion",
        "suiteId",
        "model",
        "manifest",
        "name",
        "description",
        "examples",
    )
    private val MODEL_KEYS = setOf("id", "revision", "runtime", "sha256", "exactSizeBytes")
    private val MANIFEST_KEYS = setOf("scenarioCount", "scenarioIds")
    private val EXAMPLE_KEYS = setOf("inputs", "expectedOutputs", "metadata")
    private val INPUT_KEYS = setOf("input", "rawOutput")
    private val OUTPUT_KEYS = setOf("output")
    private val METADATA_KEYS = setOf(
        "scenarioId",
        "model",
        "modelRevision",
        "runtime",
        "modelSha256",
    )
}
