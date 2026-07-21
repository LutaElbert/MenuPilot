package com.menupilot.restaurant.assistant

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.InvocationContext
import com.google.adk.kt.events.Event
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.sessions.InMemorySessionService
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.menupilot.assistant.contract.FunctionGemmaConversationRequest
import com.menupilot.assistant.contract.FunctionGemmaRouteContract
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

/**
 * Session-aware, on-device orchestration boundary for MenuPilot's read-only FunctionGemma router.
 *
 * ADK owns invocation and session lifecycle only. The custom agent has no ADK tools, never executes
 * a model function call, and forwards the complete typed request to [FunctionGemmaRouter]. The
 * router remains responsible for bounding model-visible history and repeating authoritative needs.
 */
interface FunctionGemmaAgentOrchestrator {
    suspend fun route(
        sessionId: String,
        request: FunctionGemmaConversationRequest,
    ): FunctionGemmaRouteResult

    suspend fun forgetSession(sessionId: String)
}

@Singleton
class AdkFunctionGemmaAgentOrchestrator private constructor(
    router: FunctionGemmaRouter,
    private val sessionService: InMemorySessionService,
) : FunctionGemmaAgentOrchestrator {
    @Inject
    constructor(
        router: FunctionGemmaRouter,
    ) : this(
        router = router,
        sessionService = InMemorySessionService(),
    )

    private val exchange = FunctionGemmaInvocationExchange()
    private val agent = FunctionGemmaRoutingAgent(router, exchange)
    private val runner = InMemoryRunner(
        agent = agent,
        appName = APP_NAME,
        sessionService = sessionService,
        artifactService = null,
        memoryService = null,
    )

    override suspend fun route(
        sessionId: String,
        request: FunctionGemmaConversationRequest,
    ): FunctionGemmaRouteResult {
        if (!sessionId.isValidSessionId()) {
            return failClosed(FunctionGemmaFallbackReason.INPUT_REJECTED)
        }

        val invocationId = "menupilot-" + UUID.randomUUID()
        val completion = exchange.register(invocationId, request)
        return try {
            runner.runAsync(
                userId = LOCAL_GUEST_USER_ID,
                sessionId = sessionId,
                invocationId = invocationId,
                newMessage = Content(
                    role = Role.USER,
                    parts = listOf(Part(text = request.currentUserMessage)),
                ),
            ).collect()

            if (completion.isCompleted) {
                completion.await()
            } else {
                failClosed(FunctionGemmaFallbackReason.MODEL_RUNTIME_FAILED)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failClosed(FunctionGemmaFallbackReason.MODEL_RUNTIME_FAILED)
        } catch (_: LinkageError) {
            failClosed(FunctionGemmaFallbackReason.MODEL_RUNTIME_FAILED)
        } finally {
            exchange.discard(invocationId)
        }
    }

    override suspend fun forgetSession(sessionId: String) {
        if (sessionId.isValidSessionId()) {
            sessionService.deleteSession(
                SessionKey(APP_NAME, LOCAL_GUEST_USER_ID, sessionId),
            )
        }
    }

    internal suspend fun recordedEvents(sessionId: String): List<Event> =
        if (sessionId.isValidSessionId()) {
            sessionService.listEvents(
                SessionKey(APP_NAME, LOCAL_GUEST_USER_ID, sessionId),
            ).events
        } else {
            emptyList()
        }

    private fun String.isValidSessionId(): Boolean =
        isNotBlank() &&
            length <= MAX_SESSION_ID_CHARS &&
            none(Char::isISOControl)

    private companion object {
        const val APP_NAME = "MenuPilotOnDeviceConcierge"
        const val LOCAL_GUEST_USER_ID = "table-guest"
        const val MAX_SESSION_ID_CHARS = 160
    }
}

/**
 * A custom ADK agent with no tool registry. It records a sanitized decision event while returning
 * the typed result through an invocation-scoped exchange, so no model output is reparsed from ADK
 * text and no ADK event can trigger an app action.
 */
private class FunctionGemmaRoutingAgent(
    private val router: FunctionGemmaRouter,
    private val exchange: FunctionGemmaInvocationExchange,
) : BaseAgent(
    name = "menupilot_functiongemma_router",
    description = "Routes a guest menu request without executing restaurant actions.",
    subAgents = emptyList(),
) {
    override fun runAsyncImpl(context: InvocationContext): Flow<Event> = flow {
        val pending = exchange.take(context.invocationId)
        val result = if (pending == null) {
            failClosed(FunctionGemmaFallbackReason.MODEL_RUNTIME_FAILED)
        } else {
            try {
                router.route(pending.request)
            } catch (error: CancellationException) {
                pending.completion.cancel(error)
                throw error
            } catch (_: Exception) {
                failClosed(FunctionGemmaFallbackReason.MODEL_RUNTIME_FAILED)
            } catch (_: LinkageError) {
                failClosed(FunctionGemmaFallbackReason.MODEL_RUNTIME_FAILED)
            }
        }

        pending?.completion?.complete(result)
        emit(
            Event(
                invocationId = context.invocationId,
                author = name,
                branch = context.branch,
                content = Content(
                    role = Role.MODEL,
                    parts = listOf(Part(text = result.toSafeDecisionRecord())),
                ),
                turnComplete = true,
                modelVersion = result.modelVersionRecord(),
            ),
        )
    }
}

private data class PendingFunctionGemmaInvocation(
    val request: FunctionGemmaConversationRequest,
    val completion: CompletableDeferred<FunctionGemmaRouteResult>,
)

private class FunctionGemmaInvocationExchange {
    private val pending = ConcurrentHashMap<String, PendingFunctionGemmaInvocation>()

    fun register(
        invocationId: String,
        request: FunctionGemmaConversationRequest,
    ): CompletableDeferred<FunctionGemmaRouteResult> {
        val completion = CompletableDeferred<FunctionGemmaRouteResult>()
        check(
            pending.putIfAbsent(
                invocationId,
                PendingFunctionGemmaInvocation(request, completion),
            ) == null,
        ) {
            "Duplicate FunctionGemma invocation id"
        }
        return completion
    }

    fun take(invocationId: String): PendingFunctionGemmaInvocation? =
        pending.remove(invocationId)

    fun discard(invocationId: String) {
        pending.remove(invocationId)?.completion?.cancel()
    }
}

private fun FunctionGemmaRouteResult.toSafeDecisionRecord(): String = when (this) {
    is FunctionGemmaRouteResult.Suggested ->
        "advisory_route|" + route.toEvaluationContract()
    is FunctionGemmaRouteResult.Fallback ->
        "fail_closed|reason=${reason.name}"
}

private fun FunctionGemmaRouteResult.modelVersionRecord(): String? = when (this) {
    is FunctionGemmaRouteResult.Suggested ->
        FunctionGemmaRouteContract.MODEL_ID + "@" + FunctionGemmaRouteContract.MODEL_REVISION
    is FunctionGemmaRouteResult.Fallback -> null
}

private fun failClosed(
    reason: FunctionGemmaFallbackReason,
): FunctionGemmaRouteResult.Fallback = FunctionGemmaRouteResult.Fallback(reason)
