package com.menupilot.restaurant.assistant

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.menupilot.assistant.contract.QwenDeviceEvalArtifactCodec
import com.menupilot.assistant.contract.QwenDeviceEvalArtifactValidation
import com.menupilot.assistant.contract.QwenDeviceEvalContract
import com.menupilot.assistant.contract.QwenDeviceEvalOutput
import com.menupilot.assistant.contract.QwenIntentContract
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit device/nightly stage. It does not download a model or run during local JVM tests.
 *
 * The exported file is a Dokimos dataset: guest request + raw Qwen output + the expected
 * production intent.v1 contract. The host-side :ai-evals test performs the actual gate.
 */
@RunWith(AndroidJUnit4::class)
class OnDeviceQwenArtifactTest {

    @Test
    fun exportPinnedQwenIntentRuns() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val locator = OnDeviceModelLocator(context)
        assumeTrue(
            "Provision ${locator.expectedModelPath} before running the on-device Qwen eval.",
            locator.expectedModelPath.isFile,
        )
        val generator = LiteRtLmTextGenerator(
            locator,
            OnDeviceInferenceCoordinator(),
        )
        val requestedScenarioIndex = InstrumentationRegistry.getArguments()
            .getString(SCENARIO_INDEX_ARGUMENT)
            ?.toIntOrNull()
        val selectedScenarios = requestedScenarioIndex
            ?.let { index ->
                listOf(
                    requireNotNull(QwenDeviceEvalContract.scenarios.getOrNull(index)) {
                        "Unknown scenario index $index."
                    },
                )
            }
            ?: QwenDeviceEvalContract.scenarios

        val outputs = selectedScenarios.map { scenario ->
            val rawOutput = generator.generate(
                QwenIntentContract.guestPrompt(scenario.query),
            )
            QwenDeviceEvalOutput(
                scenarioId = scenario.id,
                rawOutput = rawOutput,
            )
        }

        val artifact = QwenDeviceEvalArtifactCodec.encode(outputs)
        if (requestedScenarioIndex == null) {
            val validation = QwenDeviceEvalArtifactCodec.validate(artifact)
            assertTrue(
                "A full device export must satisfy its shared manifest: $validation",
                validation is QwenDeviceEvalArtifactValidation.Accepted,
            )
        }
        val outputDirectory = File(context.filesDir, "evals").apply { mkdirs() }
        val outputFile = File(
            outputDirectory,
            QwenDeviceEvalContract.ARTIFACT_FILE_NAME,
        )
        outputFile.writeText(artifact)

        assertTrue(outputFile.isFile)
        assertTrue(outputFile.length() > 0)
        println("MENUPILOT_MODEL_RUNS=${outputFile.absolutePath}")
    }

    private companion object {
        const val SCENARIO_INDEX_ARGUMENT = "scenarioIndex"
    }
}
