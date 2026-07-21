package com.menupilot.restaurant.assistant

import com.menupilot.assistant.contract.FunctionGemmaAuthoritativeContext
import com.menupilot.assistant.contract.FunctionGemmaBudgetScope
import com.menupilot.assistant.contract.FunctionGemmaConversationRequest
import com.menupilot.assistant.contract.FunctionGemmaTurn
import com.menupilot.assistant.contract.FunctionGemmaTurnRole
import com.menupilot.assistant.contract.QwenIntentContract
import com.menupilot.restaurant.app.BoundedConversationContext
import com.menupilot.restaurant.app.ConversationRole

/**
 * Converts the ViewModel's transport-neutral handoff into the stable FunctionGemma contract.
 *
 * The conversion is strict because silently dropping a malformed app-owned hard constraint would
 * make a bounded model projection look less restrictive than the actual dining session.
 */
fun BoundedConversationContext.toFunctionGemmaRequest(
    currentUserMessage: String,
    focusedDishName: String? = null,
): FunctionGemmaConversationRequest {
    val parsed = parseAuthoritativeHardConstraints(authoritativeHardConstraints)
    val withoutDuplicateCurrent = recentTurns.let { turns ->
        val last = turns.lastOrNull()
        if (
            last?.role == ConversationRole.GUEST &&
            last.message.normalizedForComparison() ==
            currentUserMessage.normalizedForComparison()
        ) {
            turns.dropLast(1)
        } else {
            turns
        }
    }
    return FunctionGemmaConversationRequest(
        history = withoutDuplicateCurrent.map { turn ->
            FunctionGemmaTurn(
                role = when (turn.role) {
                    ConversationRole.GUEST -> FunctionGemmaTurnRole.USER
                    ConversationRole.ASSISTANT -> FunctionGemmaTurnRole.ASSISTANT
                },
                text = turn.message,
            )
        },
        currentUserMessage = currentUserMessage,
        authoritativeContext = FunctionGemmaAuthoritativeContext(
            allergens = parsed.allergens,
            diets = parsed.diets,
            maximumPriceMinor = parsed.maximumPriceMinor,
            budgetScope = parsed.budgetScope,
            preferredAttributes = confirmedPreferences,
            focusedDishName = focusedDishName ?: this.focusedDishName,
        ),
    )
}

private data class ParsedHardConstraints(
    val allergens: List<String>,
    val diets: List<String>,
    val maximumPriceMinor: Long?,
    val budgetScope: FunctionGemmaBudgetScope?,
)

private fun parseAuthoritativeHardConstraints(
    encodedConstraints: List<String>,
): ParsedHardConstraints {
    val allergens = mutableListOf<String>()
    val diets = mutableListOf<String>()
    var maximumPriceMinor: Long? = null
    var budgetScope: FunctionGemmaBudgetScope? = null

    encodedConstraints.forEach { encoded ->
        when {
            encoded.startsWith("allergen:") -> {
                val parts = encoded.split(':')
                val id = parts.getOrNull(1)
                    ?.takeIf { it in QwenIntentContract.SUPPORTED_ALLERGENS }
                    ?: throw FunctionGemmaInputRejectedException(
                        "invalid_bounded_allergen",
                    )
                val reason = parts.getOrNull(2)
                    ?.takeIf { it in AVOIDANCE_REASON_NAMES }
                    ?: throw FunctionGemmaInputRejectedException(
                        "invalid_bounded_allergen_reason",
                    )
                if (parts.size != 3 || reason.isBlank() || id in allergens) {
                    throw FunctionGemmaInputRejectedException(
                        "invalid_bounded_allergen",
                    )
                }
                allergens += id
            }
            encoded.startsWith("diet:") -> {
                val id = encoded.substringAfter(':')
                    .takeIf { it in QwenIntentContract.SUPPORTED_DIETS }
                    ?: throw FunctionGemmaInputRejectedException("invalid_bounded_diet")
                if (id in diets) {
                    throw FunctionGemmaInputRejectedException("duplicate_bounded_diet")
                }
                diets += id
            }
            encoded.startsWith("maximum_price_minor:") -> {
                if (maximumPriceMinor != null) {
                    throw FunctionGemmaInputRejectedException(
                        "duplicate_bounded_budget",
                    )
                }
                maximumPriceMinor = encoded.substringAfter(':')
                    .toLongOrNull()
                    ?.takeIf { it > 0L }
                    ?: throw FunctionGemmaInputRejectedException(
                        "invalid_bounded_budget",
                    )
            }
            encoded.startsWith("budget_scope:") -> {
                val scope = when (encoded.substringAfter(':')) {
                    "per_dish" -> FunctionGemmaBudgetScope.PER_DISH
                    "whole_order" -> FunctionGemmaBudgetScope.WHOLE_ORDER
                    else -> null
                }
                if (budgetScope != null || scope == null) {
                    throw FunctionGemmaInputRejectedException(
                        "invalid_bounded_budget_scope",
                    )
                }
                budgetScope = scope
            }
            else -> throw FunctionGemmaInputRejectedException(
                "unknown_bounded_constraint",
            )
        }
    }
    if ((budgetScope != null) != (maximumPriceMinor != null)) {
        throw FunctionGemmaInputRejectedException("bounded_budget_scope_mismatch")
    }
    return ParsedHardConstraints(
        allergens = allergens,
        diets = diets,
        maximumPriceMinor = maximumPriceMinor,
        budgetScope = budgetScope,
    )
}

private fun String.normalizedForComparison(): String =
    trim().replace(Regex("""\s+"""), " ")

private val AVOIDANCE_REASON_NAMES = setOf(
    "ALLERGY",
    "CELIAC",
    "INTOLERANCE",
    "RELIGIOUS",
    "ETHICAL",
    "DISLIKE",
    "UNSPECIFIED",
)
