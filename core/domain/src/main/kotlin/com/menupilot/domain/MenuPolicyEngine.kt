package com.menupilot.domain

import java.time.DateTimeException
import java.time.Instant

/**
 * Pure rules engine. Restaurant-maintained facts and a user-confirmed intent are its only inputs.
 */
class MenuPolicyEngine {

    fun evaluateMenu(
        snapshot: MenuSnapshot,
        intent: ConfirmedIntent,
        now: Instant,
        policy: SafetyPolicy = SafetyPolicy(),
    ): MenuResolution {
        validatePolicyAndSnapshot(snapshot, policy).takeIf { it.isNotEmpty() }?.let {
            return MenuResolution.Blocked(intent.id, it)
        }
        validateIntent(intent).takeIf { it.isNotEmpty() }?.let {
            return MenuResolution.ClarificationRequired(intent.id, it)
        }

        val evaluations = snapshot.items
            .flatMap { item -> item.variants.map { variant -> evaluateVariant(item, variant, intent, now, policy) } }
            .sortedWith(
                compareBy<ItemEvaluation> { it.eligibility.sortOrder }
                    .thenByDescending { it.softScore }
                    .thenBy { it.itemId.value }
                    .thenBy { it.variantId.value },
            )

        val hasCandidate = evaluations.any { it.eligibility.isCandidate }
        return if (hasCandidate) {
            MenuResolution.CatalogReady(intent.id, snapshot.revision, policy.version, evaluations)
        } else {
            MenuResolution.NoMatches(intent.id, snapshot.revision, policy.version, evaluations)
        }
    }

    private fun evaluateVariant(
        item: MenuItem,
        variant: MenuVariant,
        intent: ConfirmedIntent,
        now: Instant,
        policy: SafetyPolicy,
    ): ItemEvaluation {
        if (!item.available || !variant.available) {
            return ItemEvaluation(
                itemId = item.id,
                variantId = variant.id,
                eligibility = Eligibility.NOT_CANDIDATE_UNAVAILABLE,
                reasons = listOf(DecisionReason(DecisionCode.ITEM_UNAVAILABLE)),
                softScore = 0,
            )
        }

        val hardConstraints = intent.constraints.filter { it.strength == ConstraintStrength.HARD }
        val factFreshness = factFreshness(variant.factsVerifiedAt, now, policy)
        val reasons = buildList {
            hardConstraints.forEach { constraint ->
                addAll(evaluateHardConstraint(constraint, variant, factFreshness))
            }
        }.distinct()

        val hardEligibility = when {
            reasons.any { it.code.isKnownConflict } ->
                Eligibility.NOT_CANDIDATE_CONSTRAINT_CONFLICT
            reasons.any { it.code.isInsufficientData } ->
                Eligibility.NOT_CANDIDATE_INSUFFICIENT_DATA
            else -> null
        }

        val softEvaluation = evaluateSoftConstraints(intent.constraints, variant, factFreshness)
        if (hardEligibility != null) {
            return ItemEvaluation(
                itemId = item.id,
                variantId = variant.id,
                eligibility = hardEligibility,
                reasons = (reasons + softEvaluation.reasons).distinct(),
                softScore = softEvaluation.score,
            )
        }

        val needsStaffConfirmation =
            hardConstraints.any { it.requiresStaffConfirmation } || variant.requiresStaffApproval
        val confirmationReasons = buildList {
            if (hardConstraints.any { it.requiresStaffConfirmation }) {
                add(DecisionReason(DecisionCode.STAFF_CONFIRMATION_REQUIRED))
            }
            if (variant.requiresStaffApproval) {
                add(DecisionReason(DecisionCode.STAFF_APPROVAL_REQUIRED))
            }
        }

        return ItemEvaluation(
            itemId = item.id,
            variantId = variant.id,
            eligibility = if (needsStaffConfirmation) {
                Eligibility.CANDIDATE_REQUIRES_STAFF_CONFIRMATION
            } else {
                Eligibility.CANDIDATE
            },
            reasons = (confirmationReasons + softEvaluation.reasons).distinct(),
            softScore = softEvaluation.score,
        )
    }

    private fun evaluateHardConstraint(
        constraint: MenuConstraint,
        variant: MenuVariant,
        freshness: FactFreshness,
    ): List<DecisionReason> {
        if (
            constraint !is PreferAttribute &&
            constraint !is AvoidAttribute &&
            constraint !is PriceLimit &&
            freshness != FactFreshness.CURRENT
        ) {
            return listOf(DecisionReason(freshness.reasonCode, constraint.targetId))
        }

        return when (constraint) {
            is AvoidAllergen -> evaluateAllergen(constraint, variant)
            is AvoidIngredient -> when (variant.ingredientFacts[constraint.ingredientId]) {
                Presence.PRESENT ->
                    listOf(DecisionReason(DecisionCode.INGREDIENT_PRESENT, constraint.ingredientId.value))
                Presence.NOT_LISTED -> emptyList()
                Presence.UNKNOWN, null ->
                    listOf(DecisionReason(DecisionCode.INGREDIENT_DATA_UNKNOWN, constraint.ingredientId.value))
            }
            is RequireDiet -> when (variant.dietFacts[constraint.dietId]) {
                Conformance.CONFORMS -> emptyList()
                Conformance.DOES_NOT_CONFORM ->
                    listOf(DecisionReason(DecisionCode.DIET_REQUIREMENT_NOT_MET, constraint.dietId.value))
                Conformance.UNKNOWN, null ->
                    listOf(DecisionReason(DecisionCode.DIET_DATA_UNKNOWN, constraint.dietId.value))
            }
            is PreferAttribute -> {
                if (constraint.attribute in variant.attributes) emptyList()
                else listOf(DecisionReason(DecisionCode.REQUIRED_ATTRIBUTE_MISSING, constraint.attribute))
            }
            is AvoidAttribute -> {
                if (constraint.attribute !in variant.attributes) emptyList()
                else listOf(DecisionReason(DecisionCode.AVOIDED_ATTRIBUTE_PRESENT, constraint.attribute))
            }
            is PriceLimit -> {
                if (variant.priceMinor <= constraint.maximumPriceMinor) {
                    emptyList()
                } else {
                    listOf(
                        DecisionReason(
                            DecisionCode.PRICE_LIMIT_EXCEEDED,
                            constraint.maximumPriceMinor.toString(),
                        ),
                    )
                }
            }
        }
    }

    private fun evaluateAllergen(
        constraint: AvoidAllergen,
        variant: MenuVariant,
    ): List<DecisionReason> {
        val fact = variant.allergenFacts[constraint.allergenId]
            ?: return listOf(
                DecisionReason(DecisionCode.ALLERGEN_DATA_UNKNOWN, constraint.allergenId.value),
            )

        return buildList {
            when (fact.ingredientPresence) {
                Presence.PRESENT ->
                    add(DecisionReason(DecisionCode.ALLERGEN_PRESENT, constraint.allergenId.value))
                Presence.UNKNOWN ->
                    add(DecisionReason(DecisionCode.ALLERGEN_DATA_UNKNOWN, constraint.allergenId.value))
                Presence.NOT_LISTED -> Unit
            }
            when (fact.crossContact) {
                CrossContact.KNOWN_RISK ->
                    add(DecisionReason(DecisionCode.KNOWN_CROSS_CONTACT_RISK, constraint.allergenId.value))
                CrossContact.POSSIBLE_RISK ->
                    add(DecisionReason(DecisionCode.POSSIBLE_CROSS_CONTACT_RISK, constraint.allergenId.value))
                CrossContact.UNKNOWN ->
                    add(DecisionReason(DecisionCode.ALLERGEN_DATA_UNKNOWN, constraint.allergenId.value))
                CrossContact.NONE_REPORTED -> Unit
            }
        }
    }

    private fun evaluateSoftConstraints(
        constraints: List<MenuConstraint>,
        variant: MenuVariant,
        freshness: FactFreshness,
    ): SoftEvaluation {
        var score = 0
        val reasons = mutableListOf<DecisionReason>()

        constraints.filter { it.strength == ConstraintStrength.SOFT }.forEach { constraint ->
            val matches = when (constraint) {
                is AvoidAllergen ->
                    freshness == FactFreshness.CURRENT &&
                        variant.allergenFacts[constraint.allergenId]?.ingredientPresence != Presence.PRESENT
                is AvoidIngredient ->
                    freshness == FactFreshness.CURRENT &&
                        variant.ingredientFacts[constraint.ingredientId] != Presence.PRESENT
                is RequireDiet ->
                    freshness == FactFreshness.CURRENT &&
                        variant.dietFacts[constraint.dietId] == Conformance.CONFORMS
                is PreferAttribute -> constraint.attribute in variant.attributes
                is AvoidAttribute -> constraint.attribute !in variant.attributes
                is PriceLimit -> variant.priceMinor <= constraint.maximumPriceMinor
            }
            if (!matches) {
                score -= constraint.weight
                reasons += DecisionReason(DecisionCode.SOFT_PREFERENCE_MISMATCH, constraint.targetId)
            }
        }
        return SoftEvaluation(score, reasons.distinct())
    }

    private fun validateIntent(intent: ConfirmedIntent): List<ClarificationIssue> = buildList {
        if (intent.id.isBlank()) add(ClarificationIssue(ClarificationCode.BLANK_INTENT_ID))
        intent.constraints.forEach { constraint ->
            if (constraint.targetId.isBlank()) {
                add(ClarificationIssue(ClarificationCode.BLANK_CONSTRAINT_TARGET))
            }
            if (constraint.reason?.mustBeHard == true && constraint.strength != ConstraintStrength.HARD) {
                add(
                    ClarificationIssue(
                        ClarificationCode.SAFETY_CONSTRAINT_MUST_BE_HARD,
                        constraint.targetId,
                    ),
                )
            }
            if (constraint.weight <= 0) {
                add(
                    ClarificationIssue(
                        ClarificationCode.INVALID_PREFERENCE_WEIGHT,
                        constraint.targetId,
                    ),
                )
            }
            if (constraint is PriceLimit && constraint.maximumPriceMinor <= 0) {
                add(
                    ClarificationIssue(
                        ClarificationCode.INVALID_PRICE_LIMIT,
                        constraint.maximumPriceMinor.toString(),
                    ),
                )
            }
        }
    }.distinct()

    private fun validatePolicyAndSnapshot(
        snapshot: MenuSnapshot,
        policy: SafetyPolicy,
    ): List<EngineError> = buildList {
        if (policy.version.isBlank() || policy.maximumFactAge.isNegative || policy.maximumFutureSkew.isNegative) {
            add(EngineError(EngineErrorCode.INVALID_POLICY))
        }
        if (snapshot.revision.isBlank()) add(EngineError(EngineErrorCode.BLANK_MENU_REVISION))

        val itemIds = mutableSetOf<String>()
        snapshot.items.forEach { item ->
            if (item.id.value.isBlank()) add(EngineError(EngineErrorCode.BLANK_ITEM_ID))
            if (!itemIds.add(item.id.value)) {
                add(EngineError(EngineErrorCode.DUPLICATE_ITEM_ID, item.id.value))
            }
            val variantIds = mutableSetOf<String>()
            item.variants.forEach { variant ->
                if (variant.id.value.isBlank()) add(EngineError(EngineErrorCode.BLANK_VARIANT_ID))
                if (!variantIds.add(variant.id.value)) {
                    add(EngineError(EngineErrorCode.DUPLICATE_VARIANT_ID, variant.id.value))
                }
                if (variant.recipeRevision.isBlank()) {
                    add(EngineError(EngineErrorCode.BLANK_RECIPE_REVISION, variant.id.value))
                }
            }
        }
    }.distinct()

    private fun factFreshness(
        verifiedAt: Instant?,
        now: Instant,
        policy: SafetyPolicy,
    ): FactFreshness {
        if (verifiedAt == null) return FactFreshness.MISSING
        return try {
            when {
                verifiedAt.isAfter(now.plus(policy.maximumFutureSkew)) -> FactFreshness.INVALID_TIMESTAMP
                verifiedAt.isBefore(now.minus(policy.maximumFactAge)) -> FactFreshness.STALE
                else -> FactFreshness.CURRENT
            }
        } catch (_: DateTimeException) {
            FactFreshness.INVALID_TIMESTAMP
        } catch (_: ArithmeticException) {
            FactFreshness.INVALID_TIMESTAMP
        }
    }

    private data class SoftEvaluation(
        val score: Int,
        val reasons: List<DecisionReason>,
    )

    private enum class FactFreshness(val reasonCode: DecisionCode) {
        CURRENT(DecisionCode.FACTS_MISSING),
        MISSING(DecisionCode.FACTS_MISSING),
        STALE(DecisionCode.FACTS_STALE),
        INVALID_TIMESTAMP(DecisionCode.FACTS_TIMESTAMP_INVALID),
    }
}

private val Eligibility.isCandidate: Boolean
    get() = this == Eligibility.CANDIDATE ||
        this == Eligibility.CANDIDATE_REQUIRES_STAFF_CONFIRMATION

private val Eligibility.sortOrder: Int
    get() = when (this) {
        Eligibility.CANDIDATE_REQUIRES_STAFF_CONFIRMATION -> 0
        Eligibility.CANDIDATE -> 1
        Eligibility.NOT_CANDIDATE_INSUFFICIENT_DATA -> 2
        Eligibility.NOT_CANDIDATE_CONSTRAINT_CONFLICT -> 3
        Eligibility.NOT_CANDIDATE_UNAVAILABLE -> 4
    }

private val DecisionCode.isKnownConflict: Boolean
    get() = this == DecisionCode.ALLERGEN_PRESENT ||
        this == DecisionCode.KNOWN_CROSS_CONTACT_RISK ||
        this == DecisionCode.POSSIBLE_CROSS_CONTACT_RISK ||
        this == DecisionCode.INGREDIENT_PRESENT ||
        this == DecisionCode.DIET_REQUIREMENT_NOT_MET ||
        this == DecisionCode.REQUIRED_ATTRIBUTE_MISSING ||
        this == DecisionCode.AVOIDED_ATTRIBUTE_PRESENT ||
        this == DecisionCode.PRICE_LIMIT_EXCEEDED

private val DecisionCode.isInsufficientData: Boolean
    get() = this == DecisionCode.ALLERGEN_DATA_UNKNOWN ||
        this == DecisionCode.INGREDIENT_DATA_UNKNOWN ||
        this == DecisionCode.DIET_DATA_UNKNOWN ||
        this == DecisionCode.FACTS_MISSING ||
        this == DecisionCode.FACTS_STALE ||
        this == DecisionCode.FACTS_TIMESTAMP_INVALID

private val MenuConstraint.reason: AvoidanceReason?
    get() = when (this) {
        is AvoidAllergen -> reason
        is AvoidIngredient -> reason
        is RequireDiet -> reason
        is PreferAttribute, is AvoidAttribute, is PriceLimit -> null
    }

private val MenuConstraint.requiresStaffConfirmation: Boolean
    get() = reason?.requiresStaffConfirmation == true

private val MenuConstraint.targetId: String
    get() = when (this) {
        is AvoidAllergen -> allergenId.value
        is AvoidIngredient -> ingredientId.value
        is RequireDiet -> dietId.value
        is PreferAttribute -> attribute
        is AvoidAttribute -> attribute
        is PriceLimit -> maximumPriceMinor.toString()
    }

private val MenuConstraint.weight: Int
    get() = when (this) {
        is PreferAttribute -> weight
        is AvoidAttribute -> weight
        is PriceLimit -> 1
        else -> 1
    }
