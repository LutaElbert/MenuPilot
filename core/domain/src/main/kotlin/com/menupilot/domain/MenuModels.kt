package com.menupilot.domain

import java.time.Duration
import java.time.Instant

@JvmInline
value class MenuItemId(val value: String)

@JvmInline
value class VariantId(val value: String)

@JvmInline
value class AllergenId(val value: String)

@JvmInline
value class IngredientId(val value: String)

@JvmInline
value class DietId(val value: String)

data class MenuSnapshot(
    val revision: String,
    val generatedAt: Instant,
    val items: List<MenuItem>,
)

data class MenuItem(
    val id: MenuItemId,
    val name: String,
    val available: Boolean = true,
    val variants: List<MenuVariant>,
)

data class MenuVariant(
    val id: VariantId,
    val name: String = "",
    val recipeRevision: String,
    val available: Boolean = true,
    val factsVerifiedAt: Instant? = null,
    val allergenFacts: Map<AllergenId, AllergenFact> = emptyMap(),
    val ingredientFacts: Map<IngredientId, Presence> = emptyMap(),
    val dietFacts: Map<DietId, Conformance> = emptyMap(),
    val attributes: Set<String> = emptySet(),
    val priceMinor: Long = 0,
    val requiresStaffApproval: Boolean = false,
)

data class AllergenFact(
    val ingredientPresence: Presence = Presence.UNKNOWN,
    val crossContact: CrossContact = CrossContact.UNKNOWN,
)

enum class Presence {
    PRESENT,
    NOT_LISTED,
    UNKNOWN,
}

enum class CrossContact {
    KNOWN_RISK,
    POSSIBLE_RISK,
    NONE_REPORTED,
    UNKNOWN,
}

enum class Conformance {
    CONFORMS,
    DOES_NOT_CONFORM,
    UNKNOWN,
}

enum class ConstraintStrength {
    HARD,
    SOFT,
}

enum class AvoidanceReason(
    val requiresStaffConfirmation: Boolean,
    val mustBeHard: Boolean,
) {
    ALLERGY(requiresStaffConfirmation = true, mustBeHard = true),
    CELIAC(requiresStaffConfirmation = true, mustBeHard = true),
    INTOLERANCE(requiresStaffConfirmation = true, mustBeHard = true),
    RELIGIOUS(requiresStaffConfirmation = false, mustBeHard = false),
    ETHICAL(requiresStaffConfirmation = false, mustBeHard = false),
    DISLIKE(requiresStaffConfirmation = false, mustBeHard = false),
    UNSPECIFIED(requiresStaffConfirmation = false, mustBeHard = false),
}

sealed interface MenuConstraint {
    val strength: ConstraintStrength
}

data class AvoidAllergen(
    val allergenId: AllergenId,
    val reason: AvoidanceReason = AvoidanceReason.ALLERGY,
    override val strength: ConstraintStrength = ConstraintStrength.HARD,
) : MenuConstraint

data class AvoidIngredient(
    val ingredientId: IngredientId,
    val reason: AvoidanceReason = AvoidanceReason.DISLIKE,
    override val strength: ConstraintStrength = ConstraintStrength.SOFT,
) : MenuConstraint

data class RequireDiet(
    val dietId: DietId,
    val reason: AvoidanceReason = AvoidanceReason.ETHICAL,
    override val strength: ConstraintStrength = ConstraintStrength.HARD,
) : MenuConstraint

data class PreferAttribute(
    val attribute: String,
    val weight: Int = 1,
    override val strength: ConstraintStrength = ConstraintStrength.SOFT,
) : MenuConstraint

data class AvoidAttribute(
    val attribute: String,
    val weight: Int = 1,
    override val strength: ConstraintStrength = ConstraintStrength.SOFT,
) : MenuConstraint

data class PriceLimit(
    val maximumPriceMinor: Long,
    override val strength: ConstraintStrength = ConstraintStrength.SOFT,
) : MenuConstraint

data class ConfirmedIntent(
    val id: String,
    val constraints: List<MenuConstraint> = emptyList(),
)

/**
 * Transport-neutral representation of structured model output. It is deliberately not accepted
 * by [MenuPolicyEngine]; callers must validate, clarify, and convert it to [ConfirmedIntent].
 */
data class IntentDraft(
    val schemaVersion: Int = 1,
    val action: IntentAction = IntentAction.FILTER_MENU,
    val constraints: List<ProposedConstraint> = emptyList(),
    val unresolvedTerms: List<String> = emptyList(),
    val ambiguities: List<String> = emptyList(),
)

enum class IntentAction {
    FILTER_MENU,
    FIND_DISH,
    EDIT_ORDER,
}

data class ProposedConstraint(
    val type: String,
    val targetType: String,
    val canonicalId: String?,
    val rawText: String,
    val reasonHint: String?,
    val modality: String?,
    val confidence: Double,
    val sourceStart: Int? = null,
    val sourceEndExclusive: Int? = null,
)

data class SafetyPolicy(
    val version: String = "1",
    val maximumFactAge: Duration = Duration.ofDays(1),
    val maximumFutureSkew: Duration = Duration.ofMinutes(5),
)

enum class Eligibility {
    CANDIDATE,
    CANDIDATE_REQUIRES_STAFF_CONFIRMATION,
    NOT_CANDIDATE_CONSTRAINT_CONFLICT,
    NOT_CANDIDATE_INSUFFICIENT_DATA,
    NOT_CANDIDATE_UNAVAILABLE,
}

enum class DecisionCode {
    ITEM_UNAVAILABLE,
    ALLERGEN_PRESENT,
    KNOWN_CROSS_CONTACT_RISK,
    POSSIBLE_CROSS_CONTACT_RISK,
    ALLERGEN_DATA_UNKNOWN,
    INGREDIENT_PRESENT,
    INGREDIENT_DATA_UNKNOWN,
    DIET_REQUIREMENT_NOT_MET,
    DIET_DATA_UNKNOWN,
    REQUIRED_ATTRIBUTE_MISSING,
    AVOIDED_ATTRIBUTE_PRESENT,
    FACTS_MISSING,
    FACTS_STALE,
    FACTS_TIMESTAMP_INVALID,
    STAFF_CONFIRMATION_REQUIRED,
    STAFF_APPROVAL_REQUIRED,
    PRICE_LIMIT_EXCEEDED,
    SOFT_PREFERENCE_MISMATCH,
}

data class DecisionReason(
    val code: DecisionCode,
    val targetId: String? = null,
)

data class ItemEvaluation(
    val itemId: MenuItemId,
    val variantId: VariantId,
    val eligibility: Eligibility,
    val reasons: List<DecisionReason>,
    val softScore: Int,
)

enum class ClarificationCode {
    BLANK_INTENT_ID,
    BLANK_CONSTRAINT_TARGET,
    SAFETY_CONSTRAINT_MUST_BE_HARD,
    INVALID_PREFERENCE_WEIGHT,
    INVALID_PRICE_LIMIT,
}

data class ClarificationIssue(
    val code: ClarificationCode,
    val targetId: String? = null,
)

enum class EngineErrorCode {
    INVALID_POLICY,
    BLANK_MENU_REVISION,
    DUPLICATE_ITEM_ID,
    DUPLICATE_VARIANT_ID,
    BLANK_ITEM_ID,
    BLANK_VARIANT_ID,
    BLANK_RECIPE_REVISION,
}

data class EngineError(
    val code: EngineErrorCode,
    val targetId: String? = null,
)

sealed interface MenuResolution {
    val intentId: String

    data class ClarificationRequired(
        override val intentId: String,
        val issues: List<ClarificationIssue>,
    ) : MenuResolution

    data class CatalogReady(
        override val intentId: String,
        val menuRevision: String,
        val policyVersion: String,
        val evaluations: List<ItemEvaluation>,
    ) : MenuResolution

    data class NoMatches(
        override val intentId: String,
        val menuRevision: String,
        val policyVersion: String,
        val evaluations: List<ItemEvaluation>,
    ) : MenuResolution

    data class Blocked(
        override val intentId: String,
        val errors: List<EngineError>,
    ) : MenuResolution
}
