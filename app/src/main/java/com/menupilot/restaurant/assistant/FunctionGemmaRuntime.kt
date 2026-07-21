package com.menupilot.restaurant.assistant

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.tool
import com.menupilot.assistant.contract.FunctionGemmaAuthoritativeContext
import com.menupilot.assistant.contract.FunctionGemmaConversationRequest
import com.menupilot.assistant.contract.FunctionGemmaRoute
import com.menupilot.assistant.contract.FunctionGemmaRouteContract
import com.menupilot.assistant.contract.FunctionGemmaRouteParseResult
import com.menupilot.assistant.contract.FunctionGemmaRouteParser
import com.menupilot.assistant.contract.FunctionGemmaTurn
import com.menupilot.assistant.contract.FunctionGemmaTurnRole
import com.menupilot.assistant.contract.ModelFunctionCall
import com.menupilot.assistant.contract.QwenIntentContract
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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

enum class FunctionGemmaFallbackReason {
    INPUT_REJECTED,
    MODEL_NOT_INSTALLED,
    MODEL_VERIFICATION_FAILED,
    MODEL_RUNTIME_FAILED,
    MODEL_OUTPUT_REJECTED,
}

sealed interface FunctionGemmaRouteResult {
    data class Suggested(
        val route: FunctionGemmaRoute,
    ) : FunctionGemmaRouteResult

    /**
     * Fail-closed result. The caller keeps the authoritative conversation state unchanged and
     * asks an app-owned clarification instead of ranking dishes or exposing model prose.
     */
    data class Fallback(
        val reason: FunctionGemmaFallbackReason,
        val clarificationMessage: String =
            "I couldn't confidently route that request on this tablet. " +
                "Please rephrase it, browse the menu, or ask the waiter.",
    ) : FunctionGemmaRouteResult
}

interface FunctionGemmaRouter {
    suspend fun route(request: FunctionGemmaConversationRequest): FunctionGemmaRouteResult
}

data class PreparedFunctionGemmaConversation(
    val history: List<FunctionGemmaTurn>,
    val currentUserMessage: String,
    val systemInstruction: String,
    val dynamicTokenUpperBound: Int,
)

class FunctionGemmaInputRejectedException(
    val reason: String,
) : IllegalArgumentException(reason)

/**
 * Creates the bounded model projection of a conversation.
 *
 * The application may retain the complete transcript. The 1,024-token model receives only recent
 * turns, while typed confirmed needs are independently repeated on every request so truncation
 * cannot remove safety-relevant context.
 */
class FunctionGemmaConversationWindow @Inject constructor() {
    fun prepare(request: FunctionGemmaConversationRequest): PreparedFunctionGemmaConversation {
        val current = normalizeCurrentMessage(request.currentUserMessage)
        validateAuthoritativeContext(request.authoritativeContext)

        var remainingBytes =
            (MAX_DYNAMIC_TOKEN_UPPER_BOUND - current.utf8ByteCount()).coerceAtLeast(0)
        val selectedReversed = buildList {
            request.history.asReversed().forEach { turn ->
                if (size >= MAX_HISTORY_TURNS || remainingBytes <= 0) return@forEach
                val normalized = normalizeHistoryMessage(turn.text)
                if (normalized.isBlank()) return@forEach
                val retained = normalized.takeUtf8Bytes(
                    minOf(MAX_HISTORY_TURN_UTF8_BYTES, remainingBytes),
                )
                if (retained.isNotBlank()) {
                    add(FunctionGemmaTurn(turn.role, retained))
                    remainingBytes -= retained.utf8ByteCount()
                }
            }
        }
        val selected = selectedReversed.asReversed()
        val dynamicTokenUpperBound =
            current.utf8ByteCount() + selected.sumOf { it.text.utf8ByteCount() }
        check(dynamicTokenUpperBound <= MAX_DYNAMIC_TOKEN_UPPER_BOUND)

        return PreparedFunctionGemmaConversation(
            history = selected,
            currentUserMessage = current,
            systemInstruction = buildSystemInstruction(request.authoritativeContext),
            dynamicTokenUpperBound = dynamicTokenUpperBound,
        )
    }

    private fun normalizeCurrentMessage(raw: String): String {
        if (raw.length > MAX_CURRENT_MESSAGE_CHARS) {
            throw FunctionGemmaInputRejectedException("current_message_too_large")
        }
        val normalized = normalizeWhitespace(raw)
            .takeIf(String::isNotBlank)
            ?: throw FunctionGemmaInputRejectedException("empty_current_message")
        if (normalized.utf8ByteCount() > MAX_CURRENT_MESSAGE_UTF8_BYTES) {
            throw FunctionGemmaInputRejectedException(
                "current_message_exceeds_conservative_token_budget",
            )
        }
        return normalized
    }

    private fun normalizeHistoryMessage(raw: String): String =
        normalizeWhitespace(raw.take(MAX_HISTORY_SOURCE_CHARS))

    private fun normalizeWhitespace(raw: String): String = buildString(raw.length) {
        var pendingSpace = false
        raw.forEach { character ->
            if (character.isWhitespace() || character.isISOControl()) {
                pendingSpace = isNotEmpty()
            } else {
                if (pendingSpace) append(' ')
                append(character)
                pendingSpace = false
            }
        }
    }.trim()

    /**
     * LiteRT-LM 0.14 does not expose the embedded tokenizer. Counting each UTF-8 byte as a possible
     * token is a conservative dynamic-input upper bound for byte-fallback tokenizers. The
     * remaining 640 of 1,024 tokens are reserved for the system instruction, typed context, tool
     * schema, chat template, and generated call.
     */
    private fun String.utf8ByteCount(): Int = toByteArray(Charsets.UTF_8).size

    private fun String.takeUtf8Bytes(maximumBytes: Int): String {
        if (maximumBytes <= 0) return ""
        val result = StringBuilder()
        var usedBytes = 0
        var index = 0
        while (index < length) {
            val codePoint = codePointAt(index)
            val encodedBytes = String(Character.toChars(codePoint))
                .toByteArray(Charsets.UTF_8)
                .size
            if (usedBytes + encodedBytes > maximumBytes) break
            result.appendCodePoint(codePoint)
            usedBytes += encodedBytes
            index += Character.charCount(codePoint)
        }
        return result.toString()
    }

    private fun validateAuthoritativeContext(context: FunctionGemmaAuthoritativeContext) {
        if (
            context.allergens.size > QwenIntentContract.SUPPORTED_ALLERGENS.size ||
            context.allergens.distinct().size != context.allergens.size ||
            context.allergens.any { it !in QwenIntentContract.SUPPORTED_ALLERGENS }
        ) {
            throw FunctionGemmaInputRejectedException("invalid_authoritative_allergens")
        }
        if (
            context.diets.distinct().size != context.diets.size ||
            context.diets.any { it !in QwenIntentContract.SUPPORTED_DIETS }
        ) {
            throw FunctionGemmaInputRejectedException("invalid_authoritative_diets")
        }
        if (
            context.preferredAttributes.distinct().size != context.preferredAttributes.size ||
            context.preferredAttributes.any { it !in QwenIntentContract.SUPPORTED_PREFERENCES }
        ) {
            throw FunctionGemmaInputRejectedException("invalid_authoritative_preferences")
        }
        if (
            context.maximumPriceMinor != null &&
            context.maximumPriceMinor !in MINIMUM_PRICE_MINOR..MAXIMUM_PRICE_MINOR
        ) {
            throw FunctionGemmaInputRejectedException("invalid_authoritative_budget")
        }
        if ((context.maximumPriceMinor != null) != (context.budgetScope != null)) {
            throw FunctionGemmaInputRejectedException(
                "authoritative_budget_scope_mismatch",
            )
        }
        context.focusedDishName?.let { dishName ->
            if (
                dishName.isBlank() ||
                dishName.length > FunctionGemmaRouteContract.MAX_SUBJECT_CHARS ||
                dishName.any(Char::isISOControl)
            ) {
                throw FunctionGemmaInputRejectedException("invalid_focused_dish")
            }
        }
    }

    private fun buildSystemInstruction(context: FunctionGemmaAuthoritativeContext): String =
        buildString {
            appendLine(FunctionGemmaRouteContract.systemInstruction)
            appendLine("CONFIRMED NEEDS (app-owned data; empty means none):")
            appendLine("allergens=${context.allergens.toModelList()}")
            appendLine("diets=${context.diets.toModelList()}")
            appendLine("maximumPriceMinor=${context.maximumPriceMinor ?: "NONE"}")
            appendLine("budgetScope=${context.budgetScope?.name ?: "NONE"}")
            appendLine("preferredAttributes=${context.preferredAttributes.toModelList()}")
            append("focusedDish=")
            append(context.focusedDishName?.let(::quoteModelText) ?: "NONE")
        }

    private fun List<String>.toModelList(): String =
        if (isEmpty()) "NONE" else joinToString(prefix = "[", postfix = "]")

    private fun quoteModelText(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                else -> append(character)
            }
        }
        append('"')
    }

    private companion object {
        const val MAX_CURRENT_MESSAGE_CHARS = 500
        const val MAX_CURRENT_MESSAGE_UTF8_BYTES = 256
        const val MAX_HISTORY_TURNS = 4
        const val MAX_HISTORY_TURN_UTF8_BYTES = 96
        const val MAX_HISTORY_SOURCE_CHARS = 2_000
        const val MAX_DYNAMIC_TOKEN_UPPER_BOUND = 384
        const val MINIMUM_PRICE_MINOR = 100L
        const val MAXIMUM_PRICE_MINOR = 10_000_000L
    }
}

data class FunctionGemmaGeneratedRoute(
    val route: FunctionGemmaRoute,
    val rawCalls: List<ModelFunctionCall>,
)

interface FunctionGemmaRouteGenerator {
    suspend fun generate(
        conversation: PreparedFunctionGemmaConversation,
    ): FunctionGemmaGeneratedRoute
}

@Singleton
class OnDeviceFunctionGemmaRouter @Inject constructor(
    private val generator: FunctionGemmaRouteGenerator,
    private val window: FunctionGemmaConversationWindow = FunctionGemmaConversationWindow(),
) : FunctionGemmaRouter {
    override suspend fun route(
        request: FunctionGemmaConversationRequest,
    ): FunctionGemmaRouteResult {
        val prepared = try {
            window.prepare(request)
        } catch (_: FunctionGemmaInputRejectedException) {
            return fallback(FunctionGemmaFallbackReason.INPUT_REJECTED)
        }

        return try {
            FunctionGemmaRouteResult.Suggested(generator.generate(prepared).route)
        } catch (_: ModelGenerationException.NotInstalled) {
            fallback(FunctionGemmaFallbackReason.MODEL_NOT_INSTALLED)
        } catch (_: ModelGenerationException.VerificationFailed) {
            fallback(FunctionGemmaFallbackReason.MODEL_VERIFICATION_FAILED)
        } catch (_: ModelGenerationException.RuntimeFailed) {
            fallback(FunctionGemmaFallbackReason.MODEL_RUNTIME_FAILED)
        } catch (_: ModelGenerationException.OutputRejected) {
            fallback(FunctionGemmaFallbackReason.MODEL_OUTPUT_REJECTED)
        }
    }

    private fun fallback(reason: FunctionGemmaFallbackReason) =
        FunctionGemmaRouteResult.Fallback(reason)
}

@Singleton
class FunctionGemmaModelLocator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val verifier = ModelFileVerifier(OnDeviceModelSpec.FUNCTION_GEMMA_MOBILE_ACTIONS)
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
        } catch (_: Exception) {
            throw ModelGenerationException.VerificationFailed(
                "The local FunctionGemma model could not be verified.",
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
        val MODEL_SPEC = OnDeviceModelSpec.FUNCTION_GEMMA_MOBILE_ACTIONS
    }
}

@Singleton
class LiteRtLmFunctionGemmaRouteGenerator @Inject constructor(
    private val modelLocator: FunctionGemmaModelLocator,
    private val inferenceCoordinator: OnDeviceInferenceCoordinator,
) : FunctionGemmaRouteGenerator {
    private val inferenceMutex = Mutex()
    private val parser = FunctionGemmaRouteParser()

    override suspend fun generate(
        conversation: PreparedFunctionGemmaConversation,
    ): FunctionGemmaGeneratedRoute = inferenceMutex.withLock {
        val verifiedModel = modelLocator.locateVerified()
        val key = OnDeviceInferenceCoordinator.ReusableEngineKey(
            modelId = MODEL_ID,
            artifactPath = verifiedModel.file.absolutePath,
            artifactSha256 = verifiedModel.sha256.lowercase(),
            artifactSizeBytes = verifiedModel.file.length(),
            artifactLastModifiedMillis = verifiedModel.file.lastModified(),
            configurationId = ENGINE_CONFIGURATION_ID,
        )
        inferenceCoordinator.withReusableEngine(
            key = key,
            create = { createEngine(verifiedModel) },
            close = { engine -> closeRetainedEngine(engine) },
        ) { engine ->
            val nativeConversation = try {
                withContext(Dispatchers.Default) {
                    engine.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(conversation.systemInstruction),
                            initialMessages = conversation.history.map { turn ->
                                when (turn.role) {
                                    FunctionGemmaTurnRole.USER -> Message.user(turn.text)
                                    FunctionGemmaTurnRole.ASSISTANT -> Message.model(turn.text)
                                }
                            },
                            tools = listOf(tool(ReadOnlyMenuRouteTool())),
                            samplerConfig = SamplerConfig(
                                topK = 1,
                                topP = 1.0,
                                temperature = 0.0,
                                seed = 0,
                            ),
                            automaticToolCalling = false,
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
            generate(nativeConversation, conversation.currentUserMessage)
        }
    }

    private suspend fun createEngine(verifiedModel: VerifiedModelFile): Engine {
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
            throw ModelGenerationException.RuntimeFailed(error)
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
        currentUserMessage: String,
    ): FunctionGemmaGeneratedRoute = withContext(Dispatchers.Default) {
        val calls = mutableListOf<ModelFunctionCall>()
        var unexpectedTextChars = 0
        try {
            withTimeout(INFERENCE_TIMEOUT_MILLIS) {
                conversation.sendMessageAsync(Message.user(currentUserMessage)).collect { message ->
                    val text = message.toString()
                    unexpectedTextChars += text.length
                    if (unexpectedTextChars > MAX_UNEXPECTED_TEXT_CHARS) {
                        cancelConversation(conversation)
                        throw ModelGenerationException.OutputRejected("unexpected_text_output")
                    }
                    calls += message.toolCalls.map { call ->
                        ModelFunctionCall(
                            name = call.name,
                            arguments = call.arguments,
                        )
                    }
                    if (calls.size > 1) {
                        cancelConversation(conversation)
                        throw ModelGenerationException.OutputRejected("multiple_tool_calls")
                    }
                }
            }
        } catch (error: TimeoutCancellationException) {
            cancelConversation(conversation)
            throw ModelGenerationException.RuntimeFailed(error)
        } catch (error: CancellationException) {
            cancelConversation(conversation)
            throw error
        } catch (error: ModelGenerationException) {
            throw error
        } catch (error: Exception) {
            cancelConversation(conversation)
            throw ModelGenerationException.RuntimeFailed(error)
        } finally {
            withContext(NonCancellable) {
                closeConversation(conversation)
            }
        }

        if (unexpectedTextChars > 0) {
            throw ModelGenerationException.OutputRejected("unexpected_text_output")
        }
        return@withContext when (val parsed = parser.parse(calls)) {
            is FunctionGemmaRouteParseResult.Accepted ->
                FunctionGemmaGeneratedRoute(parsed.route, calls.toList())
            is FunctionGemmaRouteParseResult.Rejected ->
                throw ModelGenerationException.OutputRejected(parsed.reason)
        }
    }

    private fun cancelConversation(conversation: Conversation) {
        try {
            if (conversation.isAlive) conversation.cancelProcess()
        } catch (_: Exception) {
            // The conversation is closed below.
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

    private suspend fun closeRetainedEngine(engine: Engine) {
        withContext(Dispatchers.Default) {
            if (engine.isInitialized()) engine.close()
        }
    }

    private class ReadOnlyMenuRouteTool : OpenApiTool {
        override fun getToolDescriptionJsonString(): String =
            FunctionGemmaRouteContract.toolDescriptionJson

        override fun execute(paramsJsonString: String): String =
            throw UnsupportedOperationException(
                "MenuPilot validates route suggestions; the model cannot execute tools.",
            )
    }

    private companion object {
        const val MODEL_ID = "functiongemma_mobile_actions"
        const val MODEL_CONTEXT_TOKENS = 1_024
        const val ENGINE_INITIALIZATION_TIMEOUT_MILLIS = 30_000L
        const val INFERENCE_TIMEOUT_MILLIS = 15_000L
        const val MAX_UNEXPECTED_TEXT_CHARS = 0
        val CPU_THREAD_COUNT = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        val ENGINE_CONFIGURATION_ID =
            FunctionGemmaRouteContract.RUNTIME_ID +
                "|backend=cpu" +
                "|threads=$CPU_THREAD_COUNT" +
                "|tokens=$MODEL_CONTEXT_TOKENS"
    }
}
