package com.menupilot.domain

import java.time.Duration
import java.time.Instant

/**
 * An exact menu variant identity. Recipe revision is part of the identity so a recommendation
 * cannot silently survive a recipe change.
 */
data class MenuVariantRef(
    val itemId: MenuItemId,
    val variantId: VariantId,
    val recipeRevision: String,
)

enum class RecommendationPlacement {
    CATALOG_HIGHLIGHT,
    CART_COMPLEMENT,
}

enum class ApprovedPairingReason {
    COMPLEMENTS_DISH,
    COMPLETES_MEAL,
}

data class ApprovedPairing(
    val anchor: MenuVariantRef,
    val candidate: MenuVariantRef,
    val reason: ApprovedPairingReason,
)

enum class MerchandisingEvidenceSourceKind {
    POS,
    RESTAURANT_IMPORT,
    DEMO_FIXTURE,
}

/**
 * Restaurant-configured provenance for aggregate merchandising evidence.
 *
 * The source is deliberately separate from a display label supplied by the UI. Sales and
 * co-order signals must reference one of these stable IDs before they may affect ranking.
 */
data class MerchandisingEvidenceSource(
    val id: String,
    val displayName: String,
    val kind: MerchandisingEvidenceSourceKind,
)

data class SalesSignal(
    val variant: MenuVariantRef,
    val orderCount: Long,
    val windowStart: Instant,
    val windowEnd: Instant,
    val observedAt: Instant,
    val sourceId: String,
)

data class BasketAffinitySignal(
    val anchor: MenuVariantRef,
    val candidate: MenuVariantRef,
    val coOrderCount: Long,
    val windowStart: Instant,
    val windowEnd: Instant,
    val observedAt: Instant,
    val sourceId: String,
)

/**
 * Restaurant-controlled merchandising data. POS adapters may populate sales and affinity signals,
 * while approved pairings remain a restaurant decision.
 */
data class MerchandisingSnapshot(
    val menuRevision: String,
    val revision: String,
    val evidenceSources: List<MerchandisingEvidenceSource> = emptyList(),
    val approvedPairings: List<ApprovedPairing> = emptyList(),
    val salesSignals: List<SalesSignal> = emptyList(),
    val affinitySignals: List<BasketAffinitySignal> = emptyList(),
)

data class RecommendationPolicy(
    val maximumSignalAge: Duration = Duration.ofDays(1),
    val maximumFutureSkew: Duration = Duration.ofMinutes(5),
)

data class RecommendationRequest(
    val menu: MenuSnapshot,
    val intent: ConfirmedIntent,
    val merchandising: MerchandisingSnapshot,
    val now: Instant,
    val placement: RecommendationPlacement = RecommendationPlacement.CATALOG_HIGHLIGHT,
    val cart: Set<MenuVariantRef> = emptySet(),
    val dismissed: Set<MenuVariantRef> = emptySet(),
    val safetyPolicy: SafetyPolicy = SafetyPolicy(),
    val recommendationPolicy: RecommendationPolicy = RecommendationPolicy(),
    val requestedLimit: Int = 2,
)

sealed interface RecommendationReason {
    data class ApprovedPairingEvidence(
        val anchor: MenuVariantRef,
        val reason: ApprovedPairingReason,
    ) : RecommendationReason

    data class CurrentAffinityEvidence(
        val anchor: MenuVariantRef,
        val coOrderCount: Long,
        val windowStart: Instant,
        val windowEnd: Instant,
        val observedAt: Instant,
        val source: MerchandisingEvidenceSource,
    ) : RecommendationReason

    data class MatchesConfirmedPreferences(
        val constraintCount: Int,
    ) : RecommendationReason

    data class PopularInWindow(
        val orderCount: Long,
        val windowStart: Instant,
        val windowEnd: Instant,
        val observedAt: Instant,
        val source: MerchandisingEvidenceSource,
    ) : RecommendationReason
}

data class MenuRecommendation(
    val variant: MenuVariantRef,
    val eligibility: Eligibility,
    val reasons: List<RecommendationReason>,
)

enum class RecommendationBlockCode {
    INVALID_RECOMMENDATION_POLICY,
    BLANK_MERCHANDISING_REVISION,
    MENU_REVISION_MISMATCH,
    MERCHANDISING_VARIANT_MISMATCH,
    CART_VARIANT_MISMATCH,
    INTENT_REQUIRES_CLARIFICATION,
    MENU_POLICY_BLOCKED,
}

data class RecommendationBlockReason(
    val code: RecommendationBlockCode,
    val targetId: String? = null,
)

sealed interface RecommendationResolution {
    val intentId: String

    data class Ready(
        override val intentId: String,
        val menuRevision: String,
        val safetyPolicyVersion: String,
        val merchandisingRevision: String,
        val recommendations: List<MenuRecommendation>,
    ) : RecommendationResolution

    data class Blocked(
        override val intentId: String,
        val reasons: List<RecommendationBlockReason>,
    ) : RecommendationResolution
}
