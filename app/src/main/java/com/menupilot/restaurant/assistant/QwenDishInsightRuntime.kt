package com.menupilot.restaurant.assistant

import android.os.Build
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.menupilot.assistant.contract.QwenDishInsightContract
import com.menupilot.assistant.contract.QwenDishInsightModelInput
import com.menupilot.assistant.contract.QwenDishInsightParseResult
import com.menupilot.assistant.contract.QwenDishInsightSelectionParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * One-shot LiteRT-LM adapter for Qwen's optional evidence selection.
 *
 * The engine is never retained between calls. The outer service parses the output again before
 * rendering, so early streaming acceptance here is only a latency optimization.
 */
@Singleton
class LiteRtLmQwenDishInsightSelectionGenerator @Inject constructor(
    private val modelLocator: OnDeviceModelLocator,
    private val inferenceCoordinator: OnDeviceInferenceCoordinator,
) : QwenDishInsightSelectionGenerator {
    private val parser = QwenDishInsightSelectionParser()

    override suspend fun generate(input: QwenDishInsightModelInput): String =
        inferenceCoordinator.withExclusiveEngine(MODEL_ID) {
            val verifiedModel = modelLocator.locateVerified()
            val engine = createEngine(verifiedModel)
            try {
                val conversation = createConversation(engine)
                generate(conversation, input)
            } finally {
                withContext(NonCancellable + Dispatchers.Default) {
                    closeEngine(engine)
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
            withTimeout(ENGINE_INITIALIZATION_TIMEOUT_MILLIS) {
                withContext(Dispatchers.Default) { candidate.initialize() }
            }
        } catch (error: TimeoutCancellationException) {
            withContext(NonCancellable + Dispatchers.Default) {
                closeEngine(candidate)
            }
            throw DishInsightModelTimeoutException(error)
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

    private suspend fun createConversation(engine: Engine): Conversation = try {
        withContext(Dispatchers.Default) {
            engine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(
                        QwenDishInsightContract.systemInstruction,
                    ),
                    samplerConfig = SamplerConfig(
                        topK = 1,
                        topP = 1.0,
                        temperature = 0.0,
                        seed = 0,
                    ),
                    automaticToolCalling = false,
                    channels = emptyList(),
                    extraContext = mapOf("enable_thinking" to false),
                ),
            )
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        throw ModelGenerationException.RuntimeFailed(error)
    } catch (error: LinkageError) {
        throw ModelGenerationException.RuntimeFailed(error)
    }

    private suspend fun generate(
        conversation: Conversation,
        input: QwenDishInsightModelInput,
    ): String = withContext(Dispatchers.Default) {
        val prompt = try {
            QwenDishInsightContract.guestPrompt(input)
        } catch (_: IllegalArgumentException) {
            throw ModelGenerationException.OutputRejected("invalid_insight_input")
        }
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
                    if (output.length > QwenDishInsightContract.MAX_OUTPUT_CHARS) {
                        cancelConversation(conversation)
                        throw ModelGenerationException.OutputRejected("output_too_large")
                    }
                    val candidate = output.toString()
                    if (
                        parser.parse(candidate, input) is
                        QwenDishInsightParseResult.Accepted
                    ) {
                        acceptedOutput = candidate
                        cancelConversation(conversation)
                    }
                }
            }
        } catch (error: TimeoutCancellationException) {
            cancelConversation(conversation)
            if (acceptedOutput == null) throw DishInsightModelTimeoutException(error)
        } catch (error: CancellationException) {
            if (acceptedOutput == null) {
                cancelConversation(conversation)
                throw error
            }
        } catch (error: ModelGenerationException) {
            throw error
        } catch (error: DishInsightModelTimeoutException) {
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

        val completed = acceptedOutput ?: output.toString()
        when (val parsed = parser.parse(completed, input)) {
            is QwenDishInsightParseResult.Accepted -> completed
            is QwenDishInsightParseResult.Rejected ->
                throw ModelGenerationException.OutputRejected(parsed.reason)
        }
    }

    private fun cancelConversation(conversation: Conversation) {
        try {
            if (conversation.isAlive) conversation.cancelProcess()
        } catch (_: Exception) {
            // The one-shot conversation is closed below.
        }
    }

    private fun closeConversation(conversation: Conversation) {
        try {
            if (conversation.isAlive) conversation.close()
        } catch (_: Exception) {
            // Native resources may already be tearing down.
        }
    }

    private fun closeEngine(engine: Engine) {
        try {
            if (engine.isInitialized()) engine.close()
        } catch (_: Exception) {
            // Closing is best-effort after completed, cancelled, or failed inference.
        } catch (_: LinkageError) {
            // Closing is best-effort after completed, cancelled, or failed inference.
        }
    }

    private fun isAndroidEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("/emu64") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu")

    private companion object {
        const val MODEL_ID = "qwen3_0_6b_dish_insight"
        const val MODEL_CONTEXT_TOKENS = 1_024
        const val ENGINE_INITIALIZATION_TIMEOUT_MILLIS = 1_800L
        const val INFERENCE_TIMEOUT_MILLIS = 900L
        val CPU_THREAD_COUNT = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
    }
}
