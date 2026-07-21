package com.menupilot.restaurant.assistant

import com.menupilot.assistant.contract.ModelAvoidanceReason
import com.menupilot.assistant.contract.ModelIntentParseResult
import com.menupilot.assistant.contract.ModelIntentParser
import com.menupilot.assistant.contract.ModelIntentPayload
import com.menupilot.assistant.contract.QwenIntentContract
import com.menupilot.domain.AvoidanceReason
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QwenIntentInterpreter @Inject constructor(
    private val generator: OnDeviceTextGenerator,
) {
    private val parser = ModelIntentParser()

    suspend fun interpret(query: String): AssistantInterpretation {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return AssistantInterpretation.NeedsClarification(
                message = "Tell me what you feel like eating or what you need to avoid.",
                source = AssistantSource.ON_DEVICE_QWEN,
            )
        }
        if (normalizedQuery.length > QwenIntentContract.MAX_QUERY_CHARS) {
            throw ModelGenerationException.OutputRejected("query_too_large")
        }

        val rawOutput = generator.generate(QwenIntentContract.guestPrompt(normalizedQuery))
        val payload = when (val parsed = parser.parse(rawOutput)) {
            is ModelIntentParseResult.Accepted -> parsed.payload
            is ModelIntentParseResult.Rejected ->
                throw ModelGenerationException.OutputRejected(parsed.reason)
        }
        if (
            !payload.needsClarification &&
            payload.allergens.isEmpty() &&
            payload.diets.isEmpty() &&
            payload.preferences.isEmpty() &&
            payload.maximumPriceMinor == null
        ) {
            throw ModelGenerationException.OutputRejected("empty_intent")
        }
        if (payload.needsClarification) {
            return AssistantInterpretation.NeedsClarification(
                message =
                    "I couldn’t map every part of that request to this restaurant’s verified " +
                        "menu facts. Please rephrase it or ask the waiter to check.",
                source = AssistantSource.ON_DEVICE_QWEN,
            )
        }
        return AssistantInterpretation.Ready(
            summary = payload.toDiningIntentSummary(normalizedQuery),
            clarificationMessage = if (payload.allergens.isNotEmpty()) {
                "I’ll avoid the listed ingredients and reported cross-contact risks. " +
                    "The kitchen must still confirm before you choose."
            } else {
                "Here’s what I understood. You can edit it before I shape the menu."
            },
            source = AssistantSource.ON_DEVICE_QWEN,
        )
    }

    private fun ModelIntentPayload.toDiningIntentSummary(query: String): DiningIntentSummary {
        val reasons = allergens.associate { allergen ->
            allergen.id to allergen.reason.toDomainReason()
        }
        val summary = DiningIntentSummary(
            sourceQuery = query,
            detectedLanguage = detectedLanguage,
            allergens = allergens.map { it.id },
            avoidanceReasons = reasons,
            diets = diets,
            preferredAttributes = preferences,
            maximumPriceMinor = maximumPriceMinor,
            budgetScope = maximumPriceMinor?.let {
                query.currentTurnBudgetScope()
            },
            intentDraft = draft(emptyList()),
        )
        return summary.copy(intentDraft = summary.rebuiltDraft())
    }

    private fun ModelAvoidanceReason.toDomainReason(): AvoidanceReason = when (this) {
        ModelAvoidanceReason.ALLERGY -> AvoidanceReason.ALLERGY
        ModelAvoidanceReason.CELIAC -> AvoidanceReason.CELIAC
        ModelAvoidanceReason.INTOLERANCE -> AvoidanceReason.INTOLERANCE
        ModelAvoidanceReason.RELIGIOUS -> AvoidanceReason.RELIGIOUS
        ModelAvoidanceReason.ETHICAL -> AvoidanceReason.ETHICAL
        ModelAvoidanceReason.DISLIKE -> AvoidanceReason.DISLIKE
        ModelAvoidanceReason.UNSPECIFIED -> AvoidanceReason.UNSPECIFIED
    }
}

@Singleton
class HybridIntentAssistant @Inject constructor(
    private val deterministicAssistant: DeterministicIntentAssistant,
    private val qwenInterpreter: QwenIntentInterpreter,
) : IntentAssistant {

    override fun interpret(query: String): AssistantInterpretation =
        deterministicAssistant.interpret(query)

    override suspend fun interpretAsync(query: String): AssistantInterpretation =
        interpretWithModel(
            query = query,
            requireLongTailSafetyCheck = false,
        )

    /**
     * Admission check for a FunctionGemma advisory route.
     *
     * A deterministic soft preference must not suppress the long-tail interpreter here. For
     * example, "spicy" can be recognized locally while an allergy in another language is only
     * recognized by Qwen. The caller uses only the typed interpretation; Qwen never supplies the
     * restaurant-facing prose for the eventual advisory answer.
     */
    internal suspend fun interpretForAgentRouteAdmission(
        query: String,
    ): AssistantInterpretation =
        interpretWithModel(
            query = query,
            requireLongTailSafetyCheck = true,
        )

    private suspend fun interpretWithModel(
        query: String,
        requireLongTailSafetyCheck: Boolean,
    ): AssistantInterpretation {
        val safetyFloor = deterministicAssistant.interpret(query)
        if (
            query.isBlank() ||
            safetyFloor is AssistantInterpretation.NeedsClarification
        ) {
            return safetyFloor
        }
        if (safetyFloor is AssistantInterpretation.Ready) {
            val hasRecognizedConstraint =
                safetyFloor.summary.intentDraft.constraints.isNotEmpty()
            val canUseSafetyFloorWithoutModel =
                safetyFloor.summary.hasHardConstraints() ||
                    (!requireLongTailSafetyCheck && hasRecognizedConstraint)
            if (canUseSafetyFloorWithoutModel) return safetyFloor
        }

        return try {
            when (val modelResult = qwenInterpreter.interpret(query)) {
                is AssistantInterpretation.NeedsClarification -> modelResult
                is AssistantInterpretation.Ready -> {
                    val deterministicReady = safetyFloor as AssistantInterpretation.Ready
                    modelResult.copy(
                        summary = modelResult.summary.withSafetyFloor(
                            deterministicReady.summary,
                        ),
                    )
                }
                is AssistantInterpretation.Routed ->
                    throw ModelGenerationException.OutputRejected(
                        "unexpected_qwen_route",
                    )
            }
        } catch (error: ModelGenerationException.NotInstalled) {
            fallbackOrClarify(
                safetyFloor,
                AssistantFallbackReason.MODEL_NOT_INSTALLED,
                requireLongTailSafetyCheck,
            )
        } catch (error: ModelGenerationException.VerificationFailed) {
            fallbackOrClarify(
                safetyFloor,
                AssistantFallbackReason.MODEL_VERIFICATION_FAILED,
                requireLongTailSafetyCheck,
            )
        } catch (error: ModelGenerationException.OutputRejected) {
            fallbackOrClarify(
                safetyFloor,
                AssistantFallbackReason.MODEL_OUTPUT_REJECTED,
                requireLongTailSafetyCheck,
            )
        } catch (error: ModelGenerationException.RuntimeFailed) {
            fallbackOrClarify(
                safetyFloor,
                AssistantFallbackReason.MODEL_RUNTIME_FAILED,
                requireLongTailSafetyCheck,
            )
        }
    }

    private fun DiningIntentSummary.hasHardConstraints(): Boolean =
        allergens.isNotEmpty() ||
            diets.isNotEmpty() ||
            maximumPriceMinor != null

    private fun fallbackOrClarify(
        safetyFloor: AssistantInterpretation,
        reason: AssistantFallbackReason,
        requiredLongTailSafetyCheck: Boolean,
    ): AssistantInterpretation {
        if (requiredLongTailSafetyCheck) {
            return AssistantInterpretation.NeedsClarification(
                message =
                    "I couldn’t complete the on-device safety interpretation for that request. " +
                        "Please rephrase it or ask the waiter before choosing.",
                source = AssistantSource.MODEL_FALLBACK,
                fallbackReason = reason,
                requirement = ClarificationRequirement.NAMED_HEALTH_CONSTRAINT,
            )
        }
        if (
            safetyFloor is AssistantInterpretation.Ready &&
            safetyFloor.summary.intentDraft.constraints.isEmpty()
        ) {
            return AssistantInterpretation.NeedsClarification(
                message =
                    "I couldn’t confidently interpret that request with the local fallback. " +
                        "Please rephrase it or ask the waiter before choosing.",
                source = AssistantSource.MODEL_FALLBACK,
                fallbackReason = reason,
            )
        }
        return safetyFloor.withAttribution(
            source = AssistantSource.MODEL_FALLBACK,
            fallbackReason = reason,
        )
    }

    private fun DiningIntentSummary.withSafetyFloor(
        floor: DiningIntentSummary,
    ): DiningIntentSummary {
        val mergedAllergens = (allergens + floor.allergens).distinct()
        val mergedReasons = mergedAllergens.associateWith { allergen ->
            strongerReason(
                avoidanceReasons[allergen],
                floor.avoidanceReasons[allergen],
            )
        }
        val mergedPrice = stricterPriceLimit(
            maximumPriceMinor,
            floor.maximumPriceMinor,
        )
        val merged = copy(
            allergens = mergedAllergens,
            avoidanceReasons = mergedReasons,
            diets = (diets + floor.diets).distinct(),
            maximumPriceMinor = mergedPrice,
            budgetScope = if (mergedPrice == null) {
                null
            } else if (
                budgetScope == DiningBudgetScope.WHOLE_ORDER ||
                floor.budgetScope == DiningBudgetScope.WHOLE_ORDER
            ) {
                DiningBudgetScope.WHOLE_ORDER
            } else {
                budgetScope ?: floor.budgetScope ?: DiningBudgetScope.PER_DISH
            },
        )
        return merged.copy(intentDraft = merged.rebuiltDraft())
    }

    private fun stricterPriceLimit(
        model: Long?,
        floor: Long?,
    ): Long? = listOfNotNull(model, floor).minOrNull()

    private fun strongerReason(
        model: AvoidanceReason?,
        floor: AvoidanceReason?,
    ): AvoidanceReason {
        val first = model ?: AvoidanceReason.UNSPECIFIED
        val second = floor ?: AvoidanceReason.UNSPECIFIED
        return if (first.safetyPriority >= second.safetyPriority) first else second
    }

    private val AvoidanceReason.safetyPriority: Int
        get() = when (this) {
            AvoidanceReason.ALLERGY -> 7
            AvoidanceReason.CELIAC -> 6
            AvoidanceReason.INTOLERANCE -> 5
            AvoidanceReason.RELIGIOUS -> 4
            AvoidanceReason.ETHICAL -> 3
            AvoidanceReason.DISLIKE -> 2
            AvoidanceReason.UNSPECIFIED -> 1
        }
}

private fun String.currentTurnBudgetScope(): DiningBudgetScope {
    val normalized = lowercase()
    val explicitlyWhole = listOf(
        "whole order",
        "entire order",
        "order total",
        "total order",
        "whole meal",
        "buong order",
        "lahat ng order",
        "tibuok order",
    ).any(normalized::contains)
    return if (explicitlyWhole) {
        DiningBudgetScope.WHOLE_ORDER
    } else {
        DiningBudgetScope.PER_DISH
    }
}
