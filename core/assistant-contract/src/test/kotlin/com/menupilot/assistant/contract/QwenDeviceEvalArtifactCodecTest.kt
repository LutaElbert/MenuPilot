package com.menupilot.assistant.contract

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QwenDeviceEvalArtifactCodecTest {

    @Test
    fun `complete pinned artifact is accepted`() {
        val validation = QwenDeviceEvalArtifactCodec.validate(completeArtifact())

        val accepted = validation as QwenDeviceEvalArtifactValidation.Accepted
        assertEquals(QwenDeviceEvalContract.scenarioIds, accepted.scenarioIds)
        assertEquals(
            QwenDeviceEvalContract.scenarioIds.size,
            QwenDeviceEvalContract.scenarioIds.toSet().size,
        )
    }

    @Test
    fun `missing scenario is rejected before output evaluation`() {
        val artifact = QwenDeviceEvalArtifactCodec.encode(completeOutputs().dropLast(1))

        val reasons = artifact.rejectionReasons()
        assertTrue("wrong_example_count" in reasons)
        assertTrue(
            reasons.any {
                it == "missing_scenario_ids:${QwenDeviceEvalContract.scenarioIds.last()}"
            },
        )
    }

    @Test
    fun `duplicate scenario is rejected before output evaluation`() {
        val outputs = completeOutputs()
        val artifact = QwenDeviceEvalArtifactCodec.encode(outputs + outputs.first())

        val reasons = artifact.rejectionReasons()
        assertTrue("wrong_example_count" in reasons)
        assertTrue(
            reasons.any {
                it == "duplicate_scenario_ids:${QwenDeviceEvalContract.scenarioIds.first()}"
            },
        )
    }

    @Test
    fun `extra scenario id is rejected before output evaluation`() {
        val artifact = mutate(completeArtifact()) { root ->
            root.getAsJsonArray("examples")
                .first()
                .asJsonObject
                .getAsJsonObject("metadata")
                .addProperty("scenarioId", "unexpected-scenario")
        }

        val reasons = artifact.rejectionReasons()
        assertTrue("unknown_scenario_id:unexpected-scenario" in reasons)
        assertTrue("extra_scenario_ids:unexpected-scenario" in reasons)
        assertTrue(
            reasons.any {
                it.startsWith(
                    "missing_scenario_ids:${QwenDeviceEvalContract.scenarioIds.first()}",
                )
            },
        )
    }

    @Test
    fun `wrong model revision and runtime provenance are rejected`() {
        val artifact = mutate(completeArtifact()) { root ->
            root.getAsJsonObject("model").apply {
                addProperty("id", "different-model")
                addProperty("revision", "unpinned-revision")
                addProperty("runtime", "litertlm-android:future")
                addProperty("sha256", "wrong-sha256")
                addProperty("exactSizeBytes", 1)
            }
            root.getAsJsonArray("examples")
                .first()
                .asJsonObject
                .getAsJsonObject("metadata")
                .apply {
                    addProperty("model", "different-model")
                    addProperty("modelRevision", "unpinned-revision")
                    addProperty("runtime", "litertlm-android:future")
                    addProperty("modelSha256", "wrong-sha256")
                }
        }

        val reasons = artifact.rejectionReasons()
        assertTrue("wrong_model_id" in reasons)
        assertTrue("wrong_model_revision" in reasons)
        assertTrue("wrong_runtime" in reasons)
        assertTrue("wrong_model_sha256" in reasons)
        assertTrue("wrong_model_size" in reasons)
        assertTrue(reasons.any { it.startsWith("metadata_model_mismatch:") })
        assertTrue(reasons.any { it.startsWith("metadata_revision_mismatch:") })
        assertTrue(reasons.any { it.startsWith("metadata_runtime_mismatch:") })
        assertTrue(reasons.any { it.startsWith("metadata_sha256_mismatch:") })
    }

    @Test
    fun `manifest count and ordered id set are pinned`() {
        val artifact = mutate(completeArtifact()) { root ->
            val manifest = root.getAsJsonObject("manifest")
            manifest.addProperty("scenarioCount", 99)
            manifest.getAsJsonArray("scenarioIds").remove(0)
        }

        val reasons = artifact.rejectionReasons()
        assertTrue("wrong_manifest_count" in reasons)
        assertTrue("wrong_manifest_scenario_ids" in reasons)
    }

    @Test
    fun `scenario query and expected contract cannot be relabelled`() {
        val artifact = mutate(completeArtifact()) { root ->
            val example = root.getAsJsonArray("examples").first().asJsonObject
            example.getAsJsonObject("inputs").addProperty("input", "different query")
            example.getAsJsonObject("expectedOutputs")
                .addProperty("output", "intent.v1|tampered")
        }

        val scenarioId = QwenDeviceEvalContract.scenarioIds.first()
        val reasons = artifact.rejectionReasons()
        assertTrue("query_mismatch:$scenarioId" in reasons)
        assertTrue("expected_contract_mismatch:$scenarioId" in reasons)
    }

    @Test
    fun `legacy unversioned artifact cannot be mistaken for qualification`() {
        val legacyArtifact =
            """
            {
              "name": "menupilot-qwen3-0.6b-device-runs",
              "description": "legacy",
              "examples": []
            }
            """.trimIndent()

        val reasons = legacyArtifact.rejectionReasons()
        assertTrue("invalid_artifact_schema" in reasons)
        assertTrue("wrong_suite_id" in reasons)
        assertTrue("invalid_model_manifest" in reasons)
        assertTrue("invalid_scenario_manifest" in reasons)
        assertTrue("wrong_example_count" in reasons)
    }

    private fun completeArtifact(): String =
        QwenDeviceEvalArtifactCodec.encode(completeOutputs())

    private fun completeOutputs(): List<QwenDeviceEvalOutput> =
        QwenDeviceEvalContract.scenarioIds.map { id ->
            QwenDeviceEvalOutput(scenarioId = id, rawOutput = "{}")
        }

    private fun mutate(
        artifact: String,
        block: (JsonObject) -> Unit,
    ): String {
        val root = JsonParser.parseString(artifact).asJsonObject
        block(root)
        return root.toString()
    }

    private fun String.rejectionReasons(): List<String> {
        val validation = QwenDeviceEvalArtifactCodec.validate(this)
        return (validation as QwenDeviceEvalArtifactValidation.Rejected).reasons
    }
}
