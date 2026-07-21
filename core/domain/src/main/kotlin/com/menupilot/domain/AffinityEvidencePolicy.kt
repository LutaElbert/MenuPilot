package com.menupilot.domain

import java.time.DateTimeException
import java.time.Instant

enum class AffinityEvidenceIssueCode {
    BLANK_SOURCE_ID,
    BLANK_SOURCE_NAME,
    DUPLICATE_SOURCE_ID,
    UNKNOWN_SOURCE,
    NEGATIVE_CO_ORDER_COUNT,
    INVALID_WINDOW,
    WINDOW_AFTER_OBSERVATION,
    FUTURE_EVIDENCE,
    STALE_EVIDENCE,
    MIXED_SOURCE_OR_WINDOW,
    DUPLICATE_PAIR_SIGNAL,
}

data class AffinityEvidenceIssue(
    val code: AffinityEvidenceIssueCode,
    val targetId: String? = null,
)

sealed interface AffinityEvidenceResolution {
    data class Ready(
        val signals: List<BasketAffinitySignal>,
    ) : AffinityEvidenceResolution

    data class Unavailable(
        val issues: List<AffinityEvidenceIssue>,
    ) : AffinityEvidenceResolution
}

/**
 * Validates that co-order counts are comparable before they may qualify or rank an upsell.
 */
class AffinityEvidencePolicy {

    fun resolve(
        snapshot: MerchandisingSnapshot,
        now: Instant,
        policy: RecommendationPolicy = RecommendationPolicy(),
    ): AffinityEvidenceResolution {
        if (snapshot.affinitySignals.isEmpty()) {
            return AffinityEvidenceResolution.Ready(emptyList())
        }

        val issues = mutableListOf<AffinityEvidenceIssue>()
        snapshot.evidenceSources.forEach { source ->
            if (source.id.isBlank()) {
                issues += AffinityEvidenceIssue(AffinityEvidenceIssueCode.BLANK_SOURCE_ID)
            }
            if (source.displayName.isBlank()) {
                issues += AffinityEvidenceIssue(
                    AffinityEvidenceIssueCode.BLANK_SOURCE_NAME,
                    source.id.takeIf(String::isNotBlank),
                )
            }
        }
        snapshot.evidenceSources.groupBy(MerchandisingEvidenceSource::id)
            .filterValues { it.size > 1 }
            .keys
            .sorted()
            .forEach {
                issues += AffinityEvidenceIssue(
                    AffinityEvidenceIssueCode.DUPLICATE_SOURCE_ID,
                    it,
                )
            }
        val sourcesById = snapshot.evidenceSources
            .filter { it.id.isNotBlank() && it.displayName.isNotBlank() }
            .associateBy(MerchandisingEvidenceSource::id)

        snapshot.affinitySignals.forEach { signal ->
            val targetId = signal.pairEvidenceId()
            if (signal.sourceId !in sourcesById) {
                issues += AffinityEvidenceIssue(
                    AffinityEvidenceIssueCode.UNKNOWN_SOURCE,
                    signal.sourceId.ifBlank { targetId },
                )
            }
            if (signal.coOrderCount < 0) {
                issues += AffinityEvidenceIssue(
                    AffinityEvidenceIssueCode.NEGATIVE_CO_ORDER_COUNT,
                    targetId,
                )
            }
            if (!signal.windowStart.isBefore(signal.windowEnd)) {
                issues += AffinityEvidenceIssue(
                    AffinityEvidenceIssueCode.INVALID_WINDOW,
                    targetId,
                )
            }
            if (signal.windowEnd.isAfter(signal.observedAt)) {
                issues += AffinityEvidenceIssue(
                    AffinityEvidenceIssueCode.WINDOW_AFTER_OBSERVATION,
                    targetId,
                )
            }
            when (signal.freshness(now, policy)) {
                AffinityFreshness.CURRENT -> Unit
                AffinityFreshness.FUTURE ->
                    issues += AffinityEvidenceIssue(
                        AffinityEvidenceIssueCode.FUTURE_EVIDENCE,
                        targetId,
                    )
                AffinityFreshness.STALE ->
                    issues += AffinityEvidenceIssue(
                        AffinityEvidenceIssueCode.STALE_EVIDENCE,
                        targetId,
                    )
                AffinityFreshness.INVALID ->
                    issues += AffinityEvidenceIssue(
                        AffinityEvidenceIssueCode.INVALID_WINDOW,
                        targetId,
                    )
            }
        }

        val windows = snapshot.affinitySignals.map {
            ComparableAffinityWindow(it.sourceId, it.windowStart, it.windowEnd)
        }.distinct()
        if (windows.size > 1) {
            issues += AffinityEvidenceIssue(
                AffinityEvidenceIssueCode.MIXED_SOURCE_OR_WINDOW,
            )
        }
        snapshot.affinitySignals
            .groupBy { AffinityPair(it.anchor, it.candidate) }
            .filterValues { it.size > 1 }
            .keys
            .sortedBy(AffinityPair::stableId)
            .forEach {
                issues += AffinityEvidenceIssue(
                    AffinityEvidenceIssueCode.DUPLICATE_PAIR_SIGNAL,
                    it.stableId(),
                )
            }

        val distinctIssues = issues.distinct()
        return if (distinctIssues.isEmpty()) {
            AffinityEvidenceResolution.Ready(
                snapshot.affinitySignals
                    .filter { it.coOrderCount > 0 }
                    .sortedWith(
                        compareBy<BasketAffinitySignal> { it.candidate.stableEvidenceId() }
                            .thenBy { it.anchor.stableEvidenceId() },
                    ),
            )
        } else {
            AffinityEvidenceResolution.Unavailable(distinctIssues)
        }
    }

    private data class ComparableAffinityWindow(
        val sourceId: String,
        val windowStart: Instant,
        val windowEnd: Instant,
    )

    private data class AffinityPair(
        val anchor: MenuVariantRef,
        val candidate: MenuVariantRef,
    ) {
        fun stableId(): String =
            "${anchor.stableEvidenceId()}->${candidate.stableEvidenceId()}"
    }
}

private enum class AffinityFreshness {
    CURRENT,
    FUTURE,
    STALE,
    INVALID,
}

private fun BasketAffinitySignal.freshness(
    now: Instant,
    policy: RecommendationPolicy,
): AffinityFreshness = try {
    when {
        policy.maximumSignalAge.isNegative || policy.maximumFutureSkew.isNegative ->
            AffinityFreshness.INVALID
        observedAt.isAfter(now.plus(policy.maximumFutureSkew)) ||
            windowEnd.isAfter(now.plus(policy.maximumFutureSkew)) ->
            AffinityFreshness.FUTURE
        observedAt.isBefore(now.minus(policy.maximumSignalAge)) ||
            windowEnd.isBefore(now.minus(policy.maximumSignalAge)) ->
            AffinityFreshness.STALE
        else -> AffinityFreshness.CURRENT
    }
} catch (_: DateTimeException) {
    AffinityFreshness.INVALID
} catch (_: ArithmeticException) {
    AffinityFreshness.INVALID
}

private fun BasketAffinitySignal.pairEvidenceId(): String =
    "${anchor.stableEvidenceId()}->${candidate.stableEvidenceId()}"
