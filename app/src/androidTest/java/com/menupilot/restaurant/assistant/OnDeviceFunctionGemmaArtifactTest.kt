package com.menupilot.restaurant.assistant

import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.menupilot.assistant.contract.FunctionGemmaArtifactProvenance
import com.menupilot.assistant.contract.FunctionGemmaDeviceEvalArtifactCodec
import com.menupilot.assistant.contract.FunctionGemmaDeviceEvalArtifactValidation
import com.menupilot.assistant.contract.FunctionGemmaDeviceEvalOutput
import com.menupilot.assistant.contract.FunctionGemmaEvalSource
import com.menupilot.assistant.contract.FunctionGemmaExecutionOutcome
import com.menupilot.assistant.contract.FunctionGemmaFallbackOutcome
import com.menupilot.assistant.contract.FunctionGemmaRouteQualificationContract
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit physical-device stage. It never downloads a model and is not part of local JVM tests.
 *
 * The exporter records raw LiteRT-LM tool calls or a typed failure outcome. It does not execute
 * MenuPilot state transitions, catalog grounding, safety policy, upsells, or handoff. The host
 * Dokimos gate scores only raw routing with the exact production parser and pinned scenario
 * manifest; separate application tests own those integrated claims.
 */
@RunWith(AndroidJUnit4::class)
class OnDeviceFunctionGemmaArtifactTest {

    @Test
    fun exportPinnedFunctionGemmaRouteRuns() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        check(!isProbablyEmulator()) {
            "FunctionGemma physical qualification rejects emulator and simulator builds."
        }
        val provenance = physicalProvenance(context)
        val locator = FunctionGemmaModelLocator(context)
        assumeTrue(
            "Provision ${locator.expectedModelPath} before running the FunctionGemma eval.",
            locator.expectedModelPath.isFile,
        )
        val generator = LiteRtLmFunctionGemmaRouteGenerator(
            locator,
            OnDeviceInferenceCoordinator(),
        )
        val window = FunctionGemmaConversationWindow()
        val requestedScenarioIndex = InstrumentationRegistry.getArguments()
            .getString(SCENARIO_INDEX_ARGUMENT)
            ?.toIntOrNull()
        val selectedScenarios = requestedScenarioIndex
            ?.let { index ->
                listOf(
                    requireNotNull(
                        FunctionGemmaRouteQualificationContract.scenarios.getOrNull(index),
                    ) {
                        "Unknown FunctionGemma scenario index $index."
                    },
                )
            }
            ?: FunctionGemmaRouteQualificationContract.scenarios

        val outputs = selectedScenarios.map { scenario ->
            val startedAt = SystemClock.elapsedRealtime()
            try {
                val generated = generator.generate(window.prepare(scenario.request))
                FunctionGemmaDeviceEvalOutput(
                    scenarioId = scenario.id,
                    calls = generated.rawCalls,
                    executionOutcome = FunctionGemmaExecutionOutcome.MODEL_COMPLETED,
                    fallbackOutcome = FunctionGemmaFallbackOutcome.NONE,
                    latencyMillis = SystemClock.elapsedRealtime() - startedAt,
                )
            } catch (error: FunctionGemmaInputRejectedException) {
                failureOutput(
                    scenarioId = scenario.id,
                    executionOutcome =
                        FunctionGemmaExecutionOutcome.MODEL_OUTPUT_REJECTED,
                    errorCode = "input_rejected:${error.reason}",
                    startedAt = startedAt,
                )
            } catch (error: ModelGenerationException.OutputRejected) {
                failureOutput(
                    scenarioId = scenario.id,
                    executionOutcome =
                        FunctionGemmaExecutionOutcome.MODEL_OUTPUT_REJECTED,
                    errorCode = "output_rejected:${error.rejectionReason}",
                    startedAt = startedAt,
                )
            } catch (error: ModelGenerationException.RuntimeFailed) {
                val timedOut = error.cause is TimeoutCancellationException
                failureOutput(
                    scenarioId = scenario.id,
                    executionOutcome = if (timedOut) {
                        FunctionGemmaExecutionOutcome.TIMED_OUT
                    } else {
                        FunctionGemmaExecutionOutcome.RUNTIME_ERROR
                    },
                    errorCode = if (timedOut) "inference_timeout" else "runtime_failed",
                    startedAt = startedAt,
                )
            } catch (_: ModelGenerationException.NotInstalled) {
                failureOutput(
                    scenarioId = scenario.id,
                    executionOutcome = FunctionGemmaExecutionOutcome.RUNTIME_ERROR,
                    errorCode = "model_not_installed",
                    startedAt = startedAt,
                )
            } catch (_: ModelGenerationException.VerificationFailed) {
                failureOutput(
                    scenarioId = scenario.id,
                    executionOutcome = FunctionGemmaExecutionOutcome.RUNTIME_ERROR,
                    errorCode = "model_verification_failed",
                    startedAt = startedAt,
                )
            } catch (_: LinkageError) {
                failureOutput(
                    scenarioId = scenario.id,
                    executionOutcome = FunctionGemmaExecutionOutcome.RUNTIME_ERROR,
                    errorCode = "runtime_linkage_error",
                    startedAt = startedAt,
                )
            }
        }

        val artifact = FunctionGemmaDeviceEvalArtifactCodec.encode(
            outputs = outputs,
            provenance = provenance,
        )
        if (requestedScenarioIndex == null) {
            val validation =
                FunctionGemmaDeviceEvalArtifactCodec
                    .validatePhysicalQualification(artifact)
            assertTrue(
                "A full FunctionGemma export must satisfy its shared manifest: $validation",
                validation is FunctionGemmaDeviceEvalArtifactValidation.Accepted,
            )
        }
        val outputDirectory = File(context.filesDir, "evals").apply { mkdirs() }
        val outputFile = File(
            outputDirectory,
            FunctionGemmaRouteQualificationContract.ARTIFACT_FILE_NAME,
        )
        outputFile.writeText(artifact)

        assertTrue(outputFile.isFile)
        assertTrue(outputFile.length() > 0)
        println("MENUPILOT_FUNCTIONGEMMA_RUNS=${outputFile.absolutePath}")
    }

    @Suppress("DEPRECATION")
    private fun physicalProvenance(
        context: android.content.Context,
    ): FunctionGemmaArtifactProvenance {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }
        val applicationInfo = context.applicationInfo
        val arguments = InstrumentationRegistry.getArguments()
        val hostRevision = arguments.getString(HOST_HARNESS_REVISION_ARGUMENT)
            ?.lowercase()
            ?.takeIf { GIT_SHA_PATTERN.matches(it) }
        val hostDirty = arguments.getString(HOST_SOURCE_TREE_DIRTY_ARGUMENT)
            ?.toBooleanStrictOrNull()
            ?.takeIf { hostRevision != null }

        return FunctionGemmaArtifactProvenance(
            source = FunctionGemmaEvalSource.PHYSICAL_ANDROID_DEVICE,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            device = Build.DEVICE,
            product = Build.PRODUCT,
            buildFingerprint = Build.FINGERPRINT,
            sdkInt = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            isEmulator = false,
            packageName = context.packageName,
            versionName = packageInfo.versionName.orEmpty(),
            versionCode = versionCode,
            isDebuggable =
                applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            apkSha256 = sha256(File(applicationInfo.sourceDir)),
            capturedAtEpochMillis = System.currentTimeMillis(),
            runId = UUID.randomUUID().toString(),
            hostHarnessRevision = hostRevision,
            hostSourceTreeDirty = hostDirty,
        )
    }

    private fun isProbablyEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()
        val product = Build.PRODUCT.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        return fingerprint.startsWith("generic") ||
            "emulator" in fingerprint ||
            "unknown" == fingerprint ||
            "google_sdk" in model ||
            "emulator" in model ||
            "android sdk built for" in model ||
            "sdk_gphone" in model ||
            "genymotion" in manufacturer ||
            (brand.startsWith("generic") && device.startsWith("generic")) ||
            "sdk_gphone" in product ||
            "simulator" in product ||
            "vbox" in product ||
            "goldfish" in hardware ||
            "ranchu" in hardware ||
            "vbox86" in hardware
    }

    private fun sha256(file: File): String {
        check(file.isFile) { "Installed base APK is missing: ${file.absolutePath}" }
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val hash = digest.digest()
        val digits = "0123456789abcdef"
        return buildString(hash.size * 2) {
            hash.forEach { byte ->
                val unsigned = byte.toInt() and 0xff
                append(digits[unsigned ushr 4])
                append(digits[unsigned and 0x0f])
            }
        }
    }

    private fun failureOutput(
        scenarioId: String,
        executionOutcome: FunctionGemmaExecutionOutcome,
        errorCode: String,
        startedAt: Long,
    ) = FunctionGemmaDeviceEvalOutput(
        scenarioId = scenarioId,
        calls = emptyList(),
        executionOutcome = executionOutcome,
        fallbackOutcome =
            FunctionGemmaFallbackOutcome.APP_FAIL_CLOSED_EXPECTED_NOT_EXECUTED,
        latencyMillis = SystemClock.elapsedRealtime() - startedAt,
        errorCode = errorCode.take(MAX_ERROR_CODE_CHARS),
    )

    private companion object {
        const val SCENARIO_INDEX_ARGUMENT = "scenarioIndex"
        const val HOST_HARNESS_REVISION_ARGUMENT = "hostHarnessRevision"
        const val HOST_SOURCE_TREE_DIRTY_ARGUMENT = "hostSourceTreeDirty"
        const val MAX_ERROR_CODE_CHARS = 120
        val GIT_SHA_PATTERN = Regex("""[0-9a-f]{40}""")
    }
}
