package com.menupilot.domain

/**
 * Ranks only variants that the menu policy engine permits. Merchandising evidence may order an
 * allowlisted candidate, but it can never make an ineligible variant recommendable.
 */
class RecommendationEngine(
    private val menuPolicyEngine: MenuPolicyEngine = MenuPolicyEngine(),
    private val salesEvidencePolicy: SalesEvidencePolicy = SalesEvidencePolicy(),
    private val affinityEvidencePolicy: AffinityEvidencePolicy = AffinityEvidencePolicy(),
) {

    fun recommend(request: RecommendationRequest): RecommendationResolution {
        val policyResult = menuPolicyEngine.evaluateMenu(
            snapshot = request.menu,
            intent = request.intent,
            now = request.now,
            policy = request.safetyPolicy,
        )
        val evaluatedCatalog = when (policyResult) {
            is MenuResolution.CatalogReady -> EvaluatedCatalog(
                policyVersion = policyResult.policyVersion,
                evaluations = policyResult.evaluations,
            )
            is MenuResolution.NoMatches -> EvaluatedCatalog(
                policyVersion = policyResult.policyVersion,
                evaluations = policyResult.evaluations,
            )
            is MenuResolution.ClarificationRequired -> {
                return RecommendationResolution.Blocked(
                    intentId = request.intent.id,
                    reasons = listOf(
                        RecommendationBlockReason(
                            RecommendationBlockCode.INTENT_REQUIRES_CLARIFICATION,
                        ),
                    ),
                )
            }
            is MenuResolution.Blocked -> {
                return RecommendationResolution.Blocked(
                    intentId = request.intent.id,
                    reasons = listOf(
                        RecommendationBlockReason(
                            RecommendationBlockCode.MENU_POLICY_BLOCKED,
                        ),
                    ),
                )
            }
        }

        validateRequest(request).takeIf(List<RecommendationBlockReason>::isNotEmpty)?.let {
            return RecommendationResolution.Blocked(request.intent.id, it)
        }

        val limit = request.requestedLimit.coerceIn(0, MAX_RECOMMENDATIONS)
        if (limit == 0) {
            return ready(request, evaluatedCatalog.policyVersion, emptyList())
        }

        val variantsByKey = request.menu.variantRefsByKey()
        val evidenceSources = request.merchandising.evidenceSources.validatedRegistry()
        val currentSales = when (
            val evidence = salesEvidencePolicy.resolve(
                snapshot = request.merchandising,
                now = request.now,
                policy = request.recommendationPolicy,
            )
        ) {
            is SalesEvidenceResolution.Ready -> evidence.signalsByVariant
            is SalesEvidenceResolution.Unavailable -> emptyMap()
        }
        val comparableAffinities = when (
            val evidence = affinityEvidencePolicy.resolve(
                snapshot = request.merchandising,
                now = request.now,
                policy = request.recommendationPolicy,
            )
        ) {
            is AffinityEvidenceResolution.Ready -> evidence.signals
            is AffinityEvidenceResolution.Unavailable -> emptyList()
        }
        val currentAffinities = comparableAffinities
            .groupBy(BasketAffinitySignal::candidate)
            .mapValues { (_, signals) -> signals.bestAffinitySignal(request.cart) }
            .filterValues { it != null }
            .mapValues { (_, signal) -> requireNotNull(signal) }

        val candidates = evaluatedCatalog.evaluations
            .asSequence()
            .filter(ItemEvaluation::isOrderable)
            .mapNotNull { evaluation ->
                val key = VariantKey(evaluation.itemId, evaluation.variantId)
                val variant = variantsByKey[key] ?: return@mapNotNull null
                Candidate(
                    recommendation = MenuRecommendation(
                        variant = variant,
                        eligibility = evaluation.eligibility,
                        reasons = reasonsFor(
                            request = request,
                            evaluation = evaluation,
                            variant = variant,
                            currentSales = currentSales[variant],
                            currentAffinity = currentAffinities[variant],
                            evidenceSources = evidenceSources,
                        ),
                    ),
                    softScore = evaluation.softScore,
                    approvedPairings = approvedPairingsFor(
                        request = request,
                        candidate = variant,
                    ),
                    affinity = currentAffinities[variant],
                    sales = currentSales[variant],
                )
            }
            .filterNot { it.recommendation.variant in request.cart }
            .filterNot { it.recommendation.variant in request.dismissed }
            .filter {
                request.placement != RecommendationPlacement.CART_COMPLEMENT ||
                    it.approvedPairings.isNotEmpty() ||
                    it.affinity != null
            }
            .sortedWith(candidateComparator)
            .take(limit)
            .map(Candidate::recommendation)
            .toList()

        return ready(request, evaluatedCatalog.policyVersion, candidates)
    }

    private fun validateRequest(
        request: RecommendationRequest,
    ): List<RecommendationBlockReason> = buildList {
        if (
            request.recommendationPolicy.maximumSignalAge.isNegative ||
            request.recommendationPolicy.maximumFutureSkew.isNegative
        ) {
            add(
                RecommendationBlockReason(
                    RecommendationBlockCode.INVALID_RECOMMENDATION_POLICY,
                ),
            )
        }
        if (request.merchandising.revision.isBlank()) {
            add(
                RecommendationBlockReason(
                    RecommendationBlockCode.BLANK_MERCHANDISING_REVISION,
                ),
            )
        }
        if (request.merchandising.menuRevision != request.menu.revision) {
            add(
                RecommendationBlockReason(
                    code = RecommendationBlockCode.MENU_REVISION_MISMATCH,
                    targetId = request.merchandising.menuRevision,
                ),
            )
        }

        val variantsByKey = request.menu.variantRefsByKey()
        request.merchandising.allVariantRefs()
            .distinct()
            .sortedBy(MenuVariantRef::stableId)
            .filterNot { variantsByKey[it.key()] == it }
            .forEach {
                add(
                    RecommendationBlockReason(
                        code = RecommendationBlockCode.MERCHANDISING_VARIANT_MISMATCH,
                        targetId = it.stableId(),
                    ),
                )
            }
        request.cart
            .sortedBy(MenuVariantRef::stableId)
            .filterNot { variantsByKey[it.key()] == it }
            .forEach {
                add(
                    RecommendationBlockReason(
                        code = RecommendationBlockCode.CART_VARIANT_MISMATCH,
                        targetId = it.stableId(),
                    ),
                )
            }
    }.distinct()

    private fun reasonsFor(
        request: RecommendationRequest,
        evaluation: ItemEvaluation,
        variant: MenuVariantRef,
        currentSales: SalesSignal?,
        currentAffinity: BasketAffinitySignal?,
        evidenceSources: Map<String, MerchandisingEvidenceSource>,
    ): List<RecommendationReason> = buildList {
        approvedPairingsFor(request, variant).firstOrNull()?.let {
            add(
                RecommendationReason.ApprovedPairingEvidence(
                    anchor = it.anchor,
                    reason = it.reason,
                ),
            )
        }
        currentAffinity?.let { affinity ->
            add(
                RecommendationReason.CurrentAffinityEvidence(
                    anchor = affinity.anchor,
                    coOrderCount = affinity.coOrderCount,
                    windowStart = affinity.windowStart,
                    windowEnd = affinity.windowEnd,
                    observedAt = affinity.observedAt,
                    source = requireNotNull(evidenceSources[affinity.sourceId]),
                ),
            )
        }
        val softConstraints = request.intent.constraints.count {
            it.strength == ConstraintStrength.SOFT
        }
        if (
            softConstraints > 0 &&
            evaluation.reasons.none { it.code == DecisionCode.SOFT_PREFERENCE_MISMATCH }
        ) {
            add(
                RecommendationReason.MatchesConfirmedPreferences(
                    constraintCount = softConstraints,
                ),
            )
        }
        currentSales?.let { sales ->
            add(
                RecommendationReason.PopularInWindow(
                    orderCount = sales.orderCount,
                    windowStart = sales.windowStart,
                    windowEnd = sales.windowEnd,
                    observedAt = sales.observedAt,
                    source = requireNotNull(evidenceSources[sales.sourceId]),
                ),
            )
        }
    }

    private fun approvedPairingsFor(
        request: RecommendationRequest,
        candidate: MenuVariantRef,
    ): List<ApprovedPairing> = request.merchandising.approvedPairings
        .asSequence()
        .filter { it.anchor in request.cart && it.candidate == candidate }
        .sortedWith(
            compareBy<ApprovedPairing> { it.anchor.stableId() }
                .thenBy { it.reason.name },
        )
        .toList()

    private fun ready(
        request: RecommendationRequest,
        safetyPolicyVersion: String,
        recommendations: List<MenuRecommendation>,
    ) = RecommendationResolution.Ready(
        intentId = request.intent.id,
        menuRevision = request.menu.revision,
        safetyPolicyVersion = safetyPolicyVersion,
        merchandisingRevision = request.merchandising.revision,
        recommendations = recommendations,
    )

    private data class EvaluatedCatalog(
        val policyVersion: String,
        val evaluations: List<ItemEvaluation>,
    )

    private data class Candidate(
        val recommendation: MenuRecommendation,
        val softScore: Int,
        val approvedPairings: List<ApprovedPairing>,
        val affinity: BasketAffinitySignal?,
        val sales: SalesSignal?,
    )

    private companion object {
        const val MAX_RECOMMENDATIONS = 2

        val candidateComparator =
            compareByDescending<Candidate> { it.approvedPairings.isNotEmpty() }
                .thenByDescending { it.softScore }
                .thenByDescending { it.affinity?.coOrderCount ?: 0L }
                .thenByDescending { it.sales?.orderCount ?: 0L }
                .thenBy { it.recommendation.variant.itemId.value }
                .thenBy { it.recommendation.variant.variantId.value }
                .thenBy { it.recommendation.variant.recipeRevision }
    }
}

private data class VariantKey(
    val itemId: MenuItemId,
    val variantId: VariantId,
)

private fun MenuSnapshot.variantRefsByKey(): Map<VariantKey, MenuVariantRef> =
    items.flatMap { item ->
        item.variants.map { variant ->
            VariantKey(item.id, variant.id) to MenuVariantRef(
                itemId = item.id,
                variantId = variant.id,
                recipeRevision = variant.recipeRevision,
            )
        }
    }.toMap()

private fun MenuVariantRef.key() =
    VariantKey(itemId = itemId, variantId = variantId)

private fun MenuVariantRef.stableId(): String =
    "${itemId.value}:${variantId.value}:$recipeRevision"

private fun MerchandisingSnapshot.allVariantRefs(): List<MenuVariantRef> = buildList {
    approvedPairings.forEach {
        add(it.anchor)
        add(it.candidate)
    }
    salesSignals.forEach { add(it.variant) }
    affinitySignals.forEach {
        add(it.anchor)
        add(it.candidate)
    }
}

private fun List<MerchandisingEvidenceSource>.validatedRegistry(): Map<
    String,
    MerchandisingEvidenceSource,
> {
    if (
        any { it.id.isBlank() || it.displayName.isBlank() } ||
        map(MerchandisingEvidenceSource::id).distinct().size != size
    ) {
        return emptyMap()
    }
    return associateBy(MerchandisingEvidenceSource::id)
}

private val ItemEvaluation.isOrderable: Boolean
    get() = eligibility == Eligibility.CANDIDATE ||
        eligibility == Eligibility.CANDIDATE_REQUIRES_STAFF_CONFIRMATION

private fun List<BasketAffinitySignal>.bestAffinitySignal(
    cart: Set<MenuVariantRef>,
): BasketAffinitySignal? = asSequence()
    .filter { it.anchor in cart }
    .maxWithOrNull(
        compareBy<BasketAffinitySignal> { it.coOrderCount }
            .thenBy { it.observedAt }
            .thenBy { it.anchor.stableId() },
    )
