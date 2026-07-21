package com.menupilot.restaurant.feature.handoff

import com.menupilot.domain.Eligibility
import java.time.Instant

/**
 * Immutable facts prepared for a human conversation. This payload is not an order command.
 */
data class WaiterHandoffPayload(
    val reference: String,
    val createdAt: Instant,
    val sessionId: String,
    val locationLabel: String,
    val placement: HandoffPlacement,
    val purpose: HandoffPurpose = HandoffPurpose.CONVERSATION_AID,
    val deliveryMode: HandoffDeliveryMode = HandoffDeliveryMode.SCREEN_ONLY,
    val liveStaffChannelConfigured: Boolean,
    val menuRevision: String,
    val menuGeneratedAt: Instant,
    val safetyPolicyVersion: String,
    val merchandisingRevision: String,
    val intentFingerprint: String,
    val diningNeeds: List<HandoffDiningNeed>,
    val picks: List<HandoffPick>,
    val unresolvedQuestions: List<HandoffQuestion>,
    val estimatedTotalMinor: Long,
    val staffReview: HandoffStaffReview? = null,
) {
    init {
        require(reference.isNotBlank())
        require(sessionId.isNotBlank())
        require(locationLabel.isNotBlank())
        require(menuRevision.isNotBlank())
        require(safetyPolicyVersion.isNotBlank())
        require(intentFingerprint.isNotBlank())
        require(picks.isNotEmpty())
        require(estimatedTotalMinor >= 0)
    }
}

enum class HandoffPurpose {
    CONVERSATION_AID,
}

enum class HandoffDeliveryMode {
    /**
     * The guest shows this tablet to a waiter or counter staff. No network delivery is claimed.
     */
    SCREEN_ONLY,
}

enum class HandoffDiningNeedKind {
    ALLERGEN,
    DIET,
    PREFERENCE,
    PRICE_LIMIT,
}

data class HandoffDiningNeed(
    val kind: HandoffDiningNeedKind,
    val canonicalId: String,
    val guestLabel: String,
    val safetyCritical: Boolean,
    val budgetScope: HandoffBudgetScope? = null,
)

enum class HandoffBudgetScope {
    PER_DISH,
    WHOLE_SHORTLIST,
}

enum class HandoffPickOrigin {
    GUEST_SELECTION,
    ACCEPTED_OPTIONAL_PAIRING,
}

data class HandoffPick(
    val itemId: String,
    val variantId: String,
    val recipeRevision: String,
    val name: String,
    val priceMinor: Long,
    val eligibility: Eligibility,
    val origin: HandoffPickOrigin,
    val requiresStaffConfirmation: Boolean,
)

enum class HandoffQuestionCode {
    CONFIRM_ALLERGY_AND_CROSS_CONTACT,
    CONFIRM_CURRENT_INGREDIENTS_AND_PREPARATION,
    CONFIRM_AVAILABILITY_AND_FINAL_CHOICE,
}

data class HandoffQuestion(
    val code: HandoffQuestionCode,
    val prompt: String,
    val itemId: String? = null,
    val variantId: String? = null,
)

data class HandoffStaffReview(
    val staffId: String,
    val staffDisplayName: String,
    val acknowledgedAt: Instant,
    val cartFingerprint: String,
)
