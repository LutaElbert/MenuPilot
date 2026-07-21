package com.menupilot.restaurant.assistant

import com.menupilot.assistant.contract.FunctionGemmaConversationRequest
import com.menupilot.assistant.contract.FunctionGemmaRoute
import com.menupilot.assistant.contract.FunctionGemmaRouteAction
import com.menupilot.assistant.contract.FunctionGemmaSalesPeriod
import com.menupilot.restaurant.app.BoundedConversationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Production Ask MenuPilot assistant.
 *
 * FunctionGemma proposes one read-only conversational route through ADK. Existing deterministic
 * rules and the optional Qwen interpreter continue to parse dining needs and remain the safety
 * floor. No route in this class directly mutates a shortlist, contacts staff, or creates an order.
 */
@Singleton
class AgenticIntentAssistant @Inject constructor(
    private val fallbackAssistant: HybridIntentAssistant,
    private val orchestrator: FunctionGemmaAgentOrchestrator,
) : IntentAssistant {

    override fun interpret(query: String): AssistantInterpretation =
        fallbackAssistant.interpret(query)

    override suspend fun interpretAsync(query: String): AssistantInterpretation =
        fallbackAssistant.interpretAsync(query)

    override suspend fun interpretAsync(
        query: String,
        context: BoundedConversationContext,
    ): AssistantInterpretation {
        // FunctionGemma is never allowed to route around deterministic health clarification or
        // any constraint the app can already recognize. This check runs before model input is
        // created, so even a malicious or badly misclassified route cannot weaken the safety floor.
        val safetyFloor = fallbackAssistant.interpret(query)
        if (safetyFloor.mustImmediatelyDominateAdvisoryRoute()) {
            return safetyFloor
        }
        val exactLocalCommand = query.exactAppOwnedCommand()

        val request = try {
            context.toFunctionGemmaRequest(query)
        } catch (_: FunctionGemmaInputRejectedException) {
            return fallbackWithAttribution(
                query,
                AssistantFallbackReason.MODEL_INPUT_REJECTED,
            )
        }

        return when (val routed = safelyRoute(context.sessionId, request)) {
            is FunctionGemmaRouteResult.Suggested -> {
                if (exactLocalCommand != null) {
                    if (routed.route.matches(exactLocalCommand.route)) {
                        applyAdvisoryRoute(query, routed.route)
                    } else {
                        exactLocalCommand.asFallback(
                            AssistantFallbackReason.MODEL_OUTPUT_REJECTED,
                        )
                    }
                } else {
                    val longTailAdmission =
                        if (routed.route.requiresLongTailSafetyAdmission()) {
                            fallbackAssistant.interpretForAgentRouteAdmission(query)
                        } else {
                            null
                        }
                    when {
                        longTailAdmission?.mustImmediatelyDominateAdvisoryRoute() == true ->
                            longTailAdmission
                        safetyFloor.mustDominateRoute(routed.route) ->
                            safetyFloor
                        else ->
                            applyAdvisoryRoute(
                                query = query,
                                route = routed.route,
                                longTailAdmission = longTailAdmission,
                            )
                    }
                }
            }
            is FunctionGemmaRouteResult.Fallback ->
                fallbackWithAttribution(query, routed.reason.toAssistantReason())
        }
    }

    override suspend fun forgetSession(sessionId: String) {
        orchestrator.forgetSession(sessionId)
    }

    private suspend fun safelyRoute(
        sessionId: String,
        request: FunctionGemmaConversationRequest,
    ): FunctionGemmaRouteResult = try {
        orchestrator.route(sessionId, request)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        FunctionGemmaRouteResult.Fallback(FunctionGemmaFallbackReason.MODEL_RUNTIME_FAILED)
    } catch (_: LinkageError) {
        FunctionGemmaRouteResult.Fallback(FunctionGemmaFallbackReason.MODEL_RUNTIME_FAILED)
    }

    private suspend fun applyAdvisoryRoute(
        query: String,
        route: FunctionGemmaRoute,
        longTailAdmission: AssistantInterpretation? = null,
    ): AssistantInterpretation = when (route.action) {
        FunctionGemmaRouteAction.SHAPE_MENU ->
            (
                longTailAdmission ?: fallbackAssistant.interpretForAgentRouteAdmission(query)
            ).withAttribution(
                AssistantSource.ON_DEVICE_FUNCTION_GEMMA,
            )
        FunctionGemmaRouteAction.BROWSE_MENU ->
            AssistantInterpretation.Routed(
                route = route,
                message =
                    "I can open a manual browse path without changing any needs you already " +
                        "confirmed.",
            )
        FunctionGemmaRouteAction.CLARIFY ->
            AssistantInterpretation.NeedsClarification(
                message =
                    "Tell me what you want to find—for example a preference, budget, ingredient " +
                        "to avoid, dish to explain, bestseller period, or waiter handoff.",
                source = AssistantSource.ON_DEVICE_FUNCTION_GEMMA,
                requirement = ClarificationRequirement.GENERAL_REQUEST,
            )
        FunctionGemmaRouteAction.EXPLAIN_DISH,
        FunctionGemmaRouteAction.COMPARE_DISHES,
        FunctionGemmaRouteAction.SHOW_BESTSELLERS,
        FunctionGemmaRouteAction.SUGGEST_PAIRING,
        FunctionGemmaRouteAction.REQUEST_WAITER,
        -> AssistantInterpretation.Routed(
            route = route,
            message = route.appOwnedAdvisoryMessage(),
        )
    }

    private suspend fun fallbackWithAttribution(
        query: String,
        reason: AssistantFallbackReason,
    ): AssistantInterpretation {
        query.exactAppOwnedCommand()?.let { command ->
            return command.asFallback(reason)
        }
        return fallbackAssistant.interpretForAgentRouteAdmission(query).withAttribution(
            source = AssistantSource.MODEL_FALLBACK,
            fallbackReason = reason,
        )
    }
}

private fun AssistantInterpretation.mustImmediatelyDominateAdvisoryRoute(): Boolean = when (this) {
    is AssistantInterpretation.NeedsClarification -> true
    is AssistantInterpretation.Ready ->
        summary.allergens.isNotEmpty() ||
            summary.diets.isNotEmpty() ||
            summary.maximumPriceMinor != null
    is AssistantInterpretation.Routed -> true
}

private fun AssistantInterpretation.mustDominateRoute(
    route: FunctionGemmaRoute,
): Boolean = when (this) {
    is AssistantInterpretation.NeedsClarification -> true
    is AssistantInterpretation.Routed -> true
    is AssistantInterpretation.Ready -> {
        val hasRecognizedSoftPreference = summary.intentDraft.constraints.isNotEmpty()
        hasRecognizedSoftPreference &&
            route.action != FunctionGemmaRouteAction.SHAPE_MENU &&
            route.action != FunctionGemmaRouteAction.SHOW_BESTSELLERS
    }
}

private fun FunctionGemmaRoute.requiresLongTailSafetyAdmission(): Boolean =
    action != FunctionGemmaRouteAction.CLARIFY

private data class ExactAppOwnedCommand(
    val route: FunctionGemmaRoute,
) {
    fun asFallback(reason: AssistantFallbackReason): AssistantInterpretation.Routed =
        AssistantInterpretation.Routed(
            route = route,
            message = route.appOwnedAdvisoryMessage(),
            source = AssistantSource.MODEL_FALLBACK,
            fallbackReason = reason,
        )
}

/**
 * A deliberately narrow offline command vocabulary.
 *
 * Matching is anchored to the whole normalized utterance. Extra allergy language, conjunctions,
 * or prompt-injection text therefore cannot take this no-Qwen path.
 */
private fun String.exactAppOwnedCommand(): ExactAppOwnedCommand? =
    when (normalizedStandaloneCommand()) {
        in CurrentWindowBestsellerCommands ->
            ExactAppOwnedCommand(
                FunctionGemmaRoute(
                    action = FunctionGemmaRouteAction.SHOW_BESTSELLERS,
                    period = FunctionGemmaSalesPeriod.THIS_WEEK,
                ),
            )
        in WaiterCommands ->
            ExactAppOwnedCommand(
                FunctionGemmaRoute(FunctionGemmaRouteAction.REQUEST_WAITER),
            )
        else -> null
    }

private fun String.normalizedStandaloneCommand(): String =
    trim()
        .lowercase()
        .replace(Regex("""[?!.]+$"""), "")
        .replace(Regex("""\s+"""), " ")

private fun FunctionGemmaRoute.matches(expected: FunctionGemmaRoute): Boolean =
    action == expected.action &&
        (
            action != FunctionGemmaRouteAction.SHOW_BESTSELLERS ||
                period == expected.period
            )

private val CurrentWindowBestsellerCommands = setOf(
    "what is your best seller this week",
    "what is your bestseller this week",
    "what's your best seller this week",
    "what's your bestseller this week",
    "whats your best seller this week",
    "whats your bestseller this week",
    "what are your best sellers this week",
    "what are your bestsellers this week",
    "show me your best seller this week",
    "show me your bestseller this week",
    "show me your best sellers this week",
    "show me your bestsellers this week",
    "please show me your best seller this week",
    "please show me your bestseller this week",
    "please show me your best sellers this week",
    "please show me your bestsellers this week",
)

private val WaiterCommands = setOf(
    "call waiter",
    "call the waiter",
    "please call waiter",
    "please call the waiter",
    "can you call the waiter",
    "could you call the waiter",
    "get the waiter",
    "please get the waiter",
    "i need a waiter",
    "pwede tawagin ang waiter",
)

private fun FunctionGemmaFallbackReason.toAssistantReason(): AssistantFallbackReason = when (this) {
    FunctionGemmaFallbackReason.INPUT_REJECTED ->
        AssistantFallbackReason.MODEL_INPUT_REJECTED
    FunctionGemmaFallbackReason.MODEL_NOT_INSTALLED ->
        AssistantFallbackReason.MODEL_NOT_INSTALLED
    FunctionGemmaFallbackReason.MODEL_VERIFICATION_FAILED ->
        AssistantFallbackReason.MODEL_VERIFICATION_FAILED
    FunctionGemmaFallbackReason.MODEL_RUNTIME_FAILED ->
        AssistantFallbackReason.MODEL_RUNTIME_FAILED
    FunctionGemmaFallbackReason.MODEL_OUTPUT_REJECTED ->
        AssistantFallbackReason.MODEL_OUTPUT_REJECTED
}

private fun FunctionGemmaRoute.appOwnedAdvisoryMessage(): String = when (action) {
    FunctionGemmaRouteAction.EXPLAIN_DISH ->
        "I can explain ${subject.orEmpty()} using this restaurant’s verified menu facts. " +
            "Open its dish card for details; allergy safety still needs kitchen confirmation."
    FunctionGemmaRouteAction.COMPARE_DISHES ->
        "I understood that you want to compare ${subject.orEmpty()}. Choose the dish cards you " +
            "want to inspect; I won’t treat either one as safe without verified menu facts."
    FunctionGemmaRouteAction.SHOW_BESTSELLERS ->
        "I understood that you want bestsellers for ${period?.name?.lowercase()?.replace('_', ' ')}. " +
            "Only verified sales records for that exact period may be shown."
    FunctionGemmaRouteAction.SUGGEST_PAIRING ->
        "I understood that you want a pairing for ${subject.orEmpty()}. Pairings are only shown " +
            "after the main dish and your confirmed filters pass local policy checks."
    FunctionGemmaRouteAction.REQUEST_WAITER ->
        "I can prepare a waiter handoff after you confirm your shortlist. Nothing has been sent."
    else -> "I understood that request, but no restaurant action has been taken."
}
