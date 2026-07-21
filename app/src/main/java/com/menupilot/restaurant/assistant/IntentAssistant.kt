package com.menupilot.restaurant.assistant

import com.menupilot.domain.AllergenId
import com.menupilot.domain.AvoidAllergen
import com.menupilot.domain.AvoidanceReason
import com.menupilot.domain.ConfirmedIntent
import com.menupilot.domain.ConstraintStrength
import com.menupilot.domain.DietId
import com.menupilot.domain.IntentAction
import com.menupilot.domain.IntentDraft
import com.menupilot.domain.PreferAttribute
import com.menupilot.domain.PriceLimit
import com.menupilot.domain.ProposedConstraint
import com.menupilot.domain.RequireDiet
import com.menupilot.assistant.contract.FunctionGemmaRoute
import com.menupilot.restaurant.app.BoundedConversationContext

enum class DiningBudgetScope {
    PER_DISH,
    WHOLE_ORDER,
}

data class DiningIntentSummary(
    val sourceQuery: String,
    val detectedLanguage: String,
    val allergens: List<String>,
    val avoidanceReasons: Map<String, AvoidanceReason> = emptyMap(),
    val diets: List<String>,
    val preferredAttributes: List<String>,
    val maximumPriceMinor: Long?,
    val budgetScope: DiningBudgetScope? =
        maximumPriceMinor?.let { DiningBudgetScope.PER_DISH },
    val intentDraft: IntentDraft,
) {
    init {
        require((maximumPriceMinor != null) == (budgetScope != null)) {
            "A dining budget amount and scope must be stored together."
        }
    }

    val requiresStaffVerification: Boolean
        get() = allergens.isNotEmpty()

    val chips: List<IntentChipModel>
        get() = buildList {
            allergens.forEach {
                val label = when (avoidanceReasons[it]) {
                    AvoidanceReason.ALLERGY -> "${it.displayName()} allergy"
                    AvoidanceReason.CELIAC -> "Avoid ${it.displayName()} • coeliac"
                    AvoidanceReason.INTOLERANCE -> "${it.displayName()} intolerance"
                    AvoidanceReason.RELIGIOUS -> "Avoid ${it.displayName()} • religious"
                    AvoidanceReason.ETHICAL -> "Avoid ${it.displayName()} • ethical"
                    AvoidanceReason.DISLIKE,
                    AvoidanceReason.UNSPECIFIED,
                    null,
                    -> "Avoid ${it.displayName()}"
                }
                add(IntentChipModel("allergen:$it", label))
            }
            diets.forEach {
                add(IntentChipModel("diet:$it", it.displayName()))
            }
            preferredAttributes.forEach {
                add(IntentChipModel("attribute:$it", "Prefers ${it.displayName()}"))
            }
            maximumPriceMinor?.let {
                add(IntentChipModel("budget", "Under ${formatPeso(it)}"))
            }
        }

    fun without(key: String): DiningIntentSummary = when {
        key.startsWith("allergen:") -> {
            val allergen = key.substringAfter(':')
            copy(
                allergens = allergens - allergen,
                avoidanceReasons = avoidanceReasons - allergen,
            )
        }
        key.startsWith("diet:") -> copy(diets = diets - key.substringAfter(':'))
        key.startsWith("attribute:") ->
            copy(preferredAttributes = preferredAttributes - key.substringAfter(':'))
        key == "budget" -> copy(maximumPriceMinor = null, budgetScope = null)
        else -> this
    }

    fun toConfirmedIntent(id: String): ConfirmedIntent = ConfirmedIntent(
        id = id,
        constraints = buildList {
            allergens.forEach {
                add(
                    AvoidAllergen(
                        allergenId = AllergenId(it),
                        reason = avoidanceReasons[it] ?: AvoidanceReason.UNSPECIFIED,
                    ),
                )
            }
            diets.forEach { add(RequireDiet(DietId(it))) }
            preferredAttributes.forEach { add(PreferAttribute(it, weight = 2)) }
            maximumPriceMinor?.let {
                add(
                    PriceLimit(
                        maximumPriceMinor = it,
                        strength = ConstraintStrength.HARD,
                    ),
                )
            }
        },
    )
}

data class IntentChipModel(
    val key: String,
    val label: String,
)

sealed interface AssistantInterpretation {
    val source: AssistantSource
    val fallbackReason: AssistantFallbackReason?

    data class Ready(
        val summary: DiningIntentSummary,
        val clarificationMessage: String,
        override val source: AssistantSource = AssistantSource.DETERMINISTIC_RULES,
        override val fallbackReason: AssistantFallbackReason? = null,
    ) : AssistantInterpretation

    data class NeedsClarification(
        val message: String,
        override val source: AssistantSource = AssistantSource.DETERMINISTIC_RULES,
        override val fallbackReason: AssistantFallbackReason? = null,
        val requirement: ClarificationRequirement = ClarificationRequirement.GENERAL_REQUEST,
    ) : AssistantInterpretation

    /**
     * A read-only conversational route that is not an intent mutation.
     *
     * The ViewModel may show [message], but must not execute [route] as an order, waiter request,
     * review, payment, or safety decision. App-owned policy code handles any later user-confirmed
     * transition.
     */
    data class Routed(
        val route: FunctionGemmaRoute,
        val message: String,
        override val source: AssistantSource = AssistantSource.ON_DEVICE_FUNCTION_GEMMA,
        override val fallbackReason: AssistantFallbackReason? = null,
    ) : AssistantInterpretation
}

/**
 * The app-owned fact that must be supplied before a blocked conversational request can continue.
 *
 * Keeping this typed prevents an unrelated preference such as "make it spicy" from accidentally
 * clearing an unresolved allergy or budget question.
 */
enum class ClarificationRequirement {
    GENERAL_REQUEST,
    NAMED_HEALTH_CONSTRAINT,
    ALLERGEN_PURPOSE,
    SUPPORTED_RESTRICTION,
    BUDGET_AMOUNT,
    DISH_REFERENCE,
    CATALOG_STAFF_REVIEW,
}

enum class AssistantSource {
    ON_DEVICE_FUNCTION_GEMMA,
    ON_DEVICE_QWEN,
    DETERMINISTIC_RULES,
    MODEL_FALLBACK,
}

enum class AssistantFallbackReason {
    MODEL_INPUT_REJECTED,
    MODEL_NOT_INSTALLED,
    MODEL_VERIFICATION_FAILED,
    MODEL_RUNTIME_FAILED,
    MODEL_OUTPUT_REJECTED,
}

interface IntentAssistant {
    fun interpret(query: String): AssistantInterpretation

    /**
     * Production implementations may suspend while a local model initializes or generates.
     * Deterministic fakes and tests retain the synchronous boundary through the default method.
     */
    suspend fun interpretAsync(query: String): AssistantInterpretation = interpret(query)

    /**
     * Conversational overload used by Ask MenuPilot.
     *
     * Existing assistants remain source-compatible and fall back to the one-shot method. The
     * production agent uses the bounded recent transcript while authoritative needs remain a
     * separate typed input.
     */
    suspend fun interpretAsync(
        query: String,
        context: BoundedConversationContext,
    ): AssistantInterpretation = interpretAsync(query)

    /**
     * Releases orchestration history for a finished table session.
     *
     * One-shot assistants have nothing to release. Session-aware implementations override this
     * so a guest choosing "New session" also clears the matching in-memory agent ledger.
     */
    suspend fun forgetSession(sessionId: String) = Unit
}

internal fun AssistantInterpretation.withAttribution(
    source: AssistantSource,
    fallbackReason: AssistantFallbackReason? = null,
): AssistantInterpretation = when (this) {
    is AssistantInterpretation.NeedsClarification ->
        copy(source = source, fallbackReason = fallbackReason)
    is AssistantInterpretation.Ready ->
        copy(source = source, fallbackReason = fallbackReason)
    is AssistantInterpretation.Routed ->
        copy(source = source, fallbackReason = fallbackReason)
}

internal fun proposedConstraint(
    type: String,
    targetType: String,
    canonicalId: String,
    rawText: String,
    reasonHint: String? = null,
    modality: String? = null,
): ProposedConstraint = ProposedConstraint(
    type = type,
    targetType = targetType,
    canonicalId = canonicalId,
    rawText = rawText,
    reasonHint = reasonHint,
    modality = modality,
    confidence = 1.0,
)

internal fun draft(constraints: List<ProposedConstraint>) = IntentDraft(
    schemaVersion = 1,
    action = IntentAction.FILTER_MENU,
    constraints = constraints,
)

internal fun DiningIntentSummary.rebuiltDraft(): IntentDraft = draft(
    buildList {
        allergens.forEach { allergen ->
            add(
                proposedConstraint(
                    type = "AVOID",
                    targetType = "ALLERGEN",
                    canonicalId = allergen,
                    rawText = sourceQuery,
                    reasonHint = (
                        avoidanceReasons[allergen] ?: AvoidanceReason.UNSPECIFIED
                        ).name,
                    modality = "MUST",
                ),
            )
        }
        diets.forEach { diet ->
            add(
                proposedConstraint(
                    type = "REQUIRE",
                    targetType = "DIET",
                    canonicalId = diet,
                    rawText = sourceQuery,
                    modality = "MUST",
                ),
            )
        }
        preferredAttributes.forEach { attribute ->
            add(
                proposedConstraint(
                    type = "PREFER",
                    targetType = "ATTRIBUTE",
                    canonicalId = attribute,
                    rawText = sourceQuery,
                    modality = "SHOULD",
                ),
            )
        }
        maximumPriceMinor?.let { maximum ->
            add(
                proposedConstraint(
                    type = "LIMIT",
                    targetType = "PRICE_MINOR",
                    canonicalId = maximum.toString(),
                    rawText = sourceQuery,
                    modality = "MUST",
                ),
            )
        }
    },
)

fun String.displayName(): String =
    split('_').joinToString(" ") { part ->
        part.replaceFirstChar { character -> character.titlecase() }
    }

fun formatPeso(priceMinor: Long): String = "₱${priceMinor / 100}"
