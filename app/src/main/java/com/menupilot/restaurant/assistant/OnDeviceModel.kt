package com.menupilot.restaurant.assistant

import android.content.Context
import android.os.Build
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.menupilot.assistant.contract.ModelIntentParseResult
import com.menupilot.assistant.contract.ModelIntentParser
import com.menupilot.assistant.contract.FunctionGemmaRouteContract
import com.menupilot.assistant.contract.QwenDeviceEvalContract
import com.menupilot.assistant.contract.QwenIntentContract
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class OnDeviceModelSpec(
    val fileName: String,
    val exactSizeBytes: Long,
    val sha256: String,
) {
    companion object {
        val QWEN3_0_6B_MIXED_INT4 = OnDeviceModelSpec(
            fileName = "qwen3_0_6b_mixed_int4.litertlm",
            exactSizeBytes = QwenDeviceEvalContract.MODEL_EXACT_SIZE_BYTES,
            sha256 = QwenDeviceEvalContract.MODEL_SHA256,
        )

        val FUNCTION_GEMMA_MOBILE_ACTIONS = OnDeviceModelSpec(
            fileName = FunctionGemmaRouteContract.MODEL_FILE_NAME,
            exactSizeBytes = FunctionGemmaRouteContract.MODEL_EXACT_SIZE_BYTES,
            sha256 = FunctionGemmaRouteContract.MODEL_SHA256,
        )
    }
}

sealed class ModelGenerationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class NotInstalled(val expectedPath: String) :
        ModelGenerationException("The on-device model is not installed.")

    class VerificationFailed(message: String) : ModelGenerationException(message)

    class RuntimeFailed(cause: Throwable) :
        ModelGenerationException("On-device inference failed.", cause)

    class OutputRejected(val rejectionReason: String) :
        ModelGenerationException("The model output did not pass the intent contract.")
}

data class VerifiedModelFile(
    val file: File,
    val sha256: String,
)

class ModelFileVerifier(
    private val spec: OnDeviceModelSpec = OnDeviceModelSpec.QWEN3_0_6B_MIXED_INT4,
) {
    fun verify(file: File): VerifiedModelFile {
        if (!file.isFile) throw ModelGenerationException.NotInstalled(file.absolutePath)
        if (file.length() != spec.exactSizeBytes) {
            throw ModelGenerationException.VerificationFailed(
                "The local model has an unexpected size.",
            )
        }
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actualHash = digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
        if (!actualHash.equals(spec.sha256, ignoreCase = true)) {
            throw ModelGenerationException.VerificationFailed(
                "The local model checksum does not match the pinned release.",
            )
        }
        return VerifiedModelFile(file = file, sha256 = actualHash)
    }
}

@Singleton
class OnDeviceModelLocator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val verifier = ModelFileVerifier()
    private val verificationMutex = Mutex()
    private var cached: CachedVerification? = null

    val expectedModelPath: File
        get() = File(File(context.filesDir, MODEL_DIRECTORY), MODEL_SPEC.fileName)

    suspend fun locateVerified(): VerifiedModelFile = verificationMutex.withLock {
        val file = expectedModelPath
        if (!file.isFile) throw ModelGenerationException.NotInstalled(file.absolutePath)
        cached
            ?.takeIf {
                it.path == file.absolutePath &&
                    it.size == file.length() &&
                    it.lastModified == file.lastModified()
            }
            ?.verified
            ?.let { return@withLock it }

        val verified = try {
            withContext(Dispatchers.IO) { verifier.verify(file) }
        } catch (error: ModelGenerationException) {
            throw error
        } catch (error: Exception) {
            throw ModelGenerationException.VerificationFailed(
                "The local model could not be verified.",
            )
        }
        cached = CachedVerification(
            path = file.absolutePath,
            size = file.length(),
            lastModified = file.lastModified(),
            verified = verified,
        )
        verified
    }

    private data class CachedVerification(
        val path: String,
        val size: Long,
        val lastModified: Long,
        val verified: VerifiedModelFile,
    )

    private companion object {
        const val MODEL_DIRECTORY = "models"
        val MODEL_SPEC = OnDeviceModelSpec.QWEN3_0_6B_MIXED_INT4
    }
}

interface OnDeviceTextGenerator {
    suspend fun generate(prompt: String): String
}

@Singleton
class LiteRtLmTextGenerator @Inject constructor(
    private val modelLocator: OnDeviceModelLocator,
    private val inferenceCoordinator: OnDeviceInferenceCoordinator,
) : OnDeviceTextGenerator {
    private val inferenceMutex = Mutex()
    private val outputParser = ModelIntentParser()

    override suspend fun generate(prompt: String): String = inferenceMutex.withLock {
        inferenceCoordinator.withExclusiveEngine(MODEL_ID) {
            val verifiedModel = modelLocator.locateVerified()
            val engine = createEngine(verifiedModel)
            try {
                val conversation = withContext(Dispatchers.Default) {
                    engine.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(QwenIntentContract.systemInstruction),
                            samplerConfig = SamplerConfig(
                                topK = 20,
                                topP = 0.8,
                                temperature = 0.7,
                                seed = 0,
                            ),
                            automaticToolCalling = false,
                            channels = emptyList(),
                            extraContext = mapOf("enable_thinking" to false),
                        ),
                    )
                }
                generate(conversation, prompt)
            } finally {
                withContext(NonCancellable + Dispatchers.Default) {
                    closeEngine(engine)
                }
            }
        }
    }

    private suspend fun createEngine(verifiedModel: VerifiedModelFile): Engine {
        if (isAndroidEmulator()) {
            throw ModelGenerationException.RuntimeFailed(
                IllegalStateException(
                    "The pinned Qwen CPU model requires representative physical hardware.",
                ),
            )
        }
        val candidate = try {
            Engine(
                EngineConfig(
                    modelPath = verifiedModel.file.absolutePath,
                    backend = Backend.CPU(threadCount = CPU_THREAD_COUNT),
                    maxNumTokens = MODEL_CONTEXT_TOKENS,
                    cacheDir = verifiedModel.file.parentFile?.absolutePath,
                ),
            )
        } catch (error: LinkageError) {
            throw ModelGenerationException.RuntimeFailed(error)
        }
        try {
            withContext(Dispatchers.Default) {
                candidate.initialize()
            }
        } catch (error: CancellationException) {
            withContext(NonCancellable + Dispatchers.Default) {
                closeEngine(candidate)
            }
            throw error
        } catch (error: Exception) {
            withContext(NonCancellable + Dispatchers.Default) {
                closeEngine(candidate)
            }
            throw ModelGenerationException.RuntimeFailed(error)
        } catch (error: LinkageError) {
            withContext(NonCancellable + Dispatchers.Default) {
                closeEngine(candidate)
            }
            throw ModelGenerationException.RuntimeFailed(error)
        }
        return candidate
    }

    private suspend fun generate(
        conversation: Conversation,
        prompt: String,
    ): String = withContext(Dispatchers.Default) {
        val output = StringBuilder()
        var acceptedOutput: String? = null
        try {
            withTimeout(INFERENCE_TIMEOUT_MILLIS) {
                conversation.sendMessageAsync(
                    prompt,
                    extraContext = mapOf("enable_thinking" to false),
                ).collect { chunk ->
                    if (acceptedOutput != null) return@collect
                    output.append(chunk.toString())
                    if (output.length > MAX_OUTPUT_CHARS) {
                        cancelConversation(conversation)
                        throw ModelGenerationException.OutputRejected("output_too_large")
                    }
                    val candidate = output.toString()
                    if (outputParser.parse(candidate) is ModelIntentParseResult.Accepted) {
                        acceptedOutput = candidate
                        cancelConversation(conversation)
                    }
                }
            }
        } catch (error: TimeoutCancellationException) {
            cancelConversation(conversation)
            if (acceptedOutput == null) {
                throw ModelGenerationException.RuntimeFailed(error)
            }
        } catch (error: CancellationException) {
            if (acceptedOutput == null) {
                cancelConversation(conversation)
                throw error
            }
        } catch (error: ModelGenerationException) {
            throw error
        } catch (error: Exception) {
            if (acceptedOutput == null) {
                cancelConversation(conversation)
                throw ModelGenerationException.RuntimeFailed(error)
            }
        } finally {
            withContext(NonCancellable) {
                closeConversation(conversation)
            }
        }
        acceptedOutput ?: output.toString()
    }

    private fun cancelConversation(conversation: Conversation) {
        try {
            if (conversation.isAlive) conversation.cancelProcess()
        } catch (_: Exception) {
            // The new conversation is closed below; there is no state to reuse.
        }
    }

    private fun closeConversation(conversation: Conversation) {
        try {
            if (conversation.isAlive) conversation.close()
        } catch (_: Exception) {
            // Native resources are already being torn down.
        }
    }

    private fun closeEngine(engine: Engine) {
        try {
            if (engine.isInitialized()) engine.close()
        } catch (_: Exception) {
            // Closing is best-effort after a completed, cancelled, or failed one-shot request.
        }
    }

    private fun isAndroidEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("/emu64") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu")

    private companion object {
        const val MODEL_ID = "qwen3_0_6b_intent"
        const val MODEL_CONTEXT_TOKENS = 2_048
        const val MAX_OUTPUT_CHARS = 8_192
        const val INFERENCE_TIMEOUT_MILLIS = 60_000L
        val CPU_THREAD_COUNT = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
    }
}
