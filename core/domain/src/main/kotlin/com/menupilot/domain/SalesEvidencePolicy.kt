package com.menupilot.domain

import java.time.DateTimeException
import java.time.Instant

enum class SalesEvidenceIssueCode {
    NO_SIGNALS,
    BLANK_SOURCE_ID,
    BLANK_SOURCE_NAME,
    DUPLICATE_SOURCE_ID,
    UNKNOWN_SOURCE,
    NEGATIVE_ORDER_COUNT,
    INVALID_WINDOW,
    WINDOW_AFTER_OBSERVATION,
    FUTURE_EVIDENCE,
    STALE_EVIDENCE,
    MIXED_SOURCE_OR_WINDOW,
    DUPLICATE_VARIANT_SIGNAL,
}

data class SalesEvidenceIssue(
    val code: SalesEvidenceIssueCode,
    val targetId: String? = null,
)

sealed interface SalesEvidenceResolution {
    data class Ready(
        val source: MerchandisingEvidenceSource,
        val windowStart: Instant,
        val windowEnd: Instant,
        val observedAt: Instant,
        val signalsByVariant: Map<MenuVariantRef, SalesSignal>,
        val bestsellerVariants: Set<MenuVariantRef>,
    ) : SalesEvidenceResolution {
        fun orderCount(variant: MenuVariantRef): Long? =
            signalsByVariant[variant]?.orderCount

        fun isBestseller(variant: MenuVariantRef): Boolean =
            variant in bestsellerVariants
    }

    data class Unavailable(
        val issues: List<SalesEvidenceIssue>,
    ) : SalesEvidenceResolution
}

/**
 * Resolves a set of comparable, current sales facts for bestseller claims.
 *
 * A bestseller is a comparison, so every included count must share one registered source and one
 * exact aggregation window. Import time alone cannot make an old sales period current.
 */
class SalesEvidencePolicy {

    fun resolve(
        snapshot: MerchandisingSnapshot,
        now: Instant,
        policy: RecommendationPolicy = RecommendationPolicy(),
    ): SalesEvidenceResolution {
        val issues = validateSources(snapshot.evidenceSources).toMutableList()
        val sourcesById = snapshot.evidenceSources
            .filter { it.id.isNotBlank() }
            .associateBy(MerchandisingEvidenceSource::id)
        val signals = snapshot.salesSignals

        if (signals.isEmpty()) {
            issues += SalesEvidenceIssue(SalesEvidenceIssueCode.NO_SIGNALS)
        }

        signals.forEach { signal ->
            val targetId = signal.variant.stableEvidenceId()
            if (signal.sourceId !in sourcesById) {
                issues += SalesEvidenceIssue(
                    SalesEvidenceIssueCode.UNKNOWN_SOURCE,
                    signal.sourceId.ifBlank { targetId },
                )
            }
            if (signal.orderCount < 0) {
                issues += SalesEvidenceIssue(
                    SalesEvidenceIssueCode.NEGATIVE_ORDER_COUNT,
                    targetId,
                )
            }
            if (!signal.windowStart.isBefore(signal.windowEnd)) {
                issues += SalesEvidenceIssue(
                    SalesEvidenceIssueCode.INVALID_WINDOW,
                    targetId,
                )
            }
            if (signal.windowEnd.isAfter(signal.observedAt)) {
                issues += SalesEvidenceIssue(
                    SalesEvidenceIssueCode.WINDOW_AFTER_OBSERVATION,
                    targetId,
                )
            }
            when (signal.freshness(now, policy)) {
                EvidenceFreshness.CURRENT -> Unit
                EvidenceFreshness.FUTURE ->
                    issues += SalesEvidenceIssue(SalesEvidenceIssueCode.FUTURE_EVIDENCE, targetId)
                EvidenceFreshness.STALE ->
                    issues += SalesEvidenceIssue(SalesEvidenceIssueCode.STALE_EVIDENCE, targetId)
                EvidenceFreshness.INVALID ->
                    issues += SalesEvidenceIssue(SalesEvidenceIssueCode.INVALID_WINDOW, targetId)
            }
        }

        val comparableKeys = signals.map {
            ComparableWindow(it.sourceId, it.windowStart, it.windowEnd)
        }.distinct()
        if (comparableKeys.size > 1) {
            issues += SalesEvidenceIssue(SalesEvidenceIssueCode.MIXED_SOURCE_OR_WINDOW)
        }
        signals.groupBy(SalesSignal::variant)
            .filterValues { it.size > 1 }
            .keys
            .sortedBy(MenuVariantRef::stableEvidenceId)
            .forEach {
                issues += SalesEvidenceIssue(
                    SalesEvidenceIssueCode.DUPLICATE_VARIANT_SIGNAL,
                    it.stableEvidenceId(),
                )
            }

        val distinctIssues = issues.distinct()
        if (distinctIssues.isNotEmpty()) {
            return SalesEvidenceResolution.Unavailable(distinctIssues)
        }

        val key = comparableKeys.single()
        val source = requireNotNull(sourcesById[key.sourceId])
        val byVariant = signals
            .sortedBy { it.variant.stableEvidenceId() }
            .associateBy(SalesSignal::variant)
        val maximum = signals.maxOf(SalesSignal::orderCount)
        val bestsellers = signals
            .asSequence()
            .filter { it.orderCount == maximum }
            .map(SalesSignal::variant)
            .toSet()

        return SalesEvidenceResolution.Ready(
            source = source,
            windowStart = key.windowStart,
            windowEnd = key.windowEnd,
            observedAt = signals.maxOf(SalesSignal::observedAt),
            signalsByVariant = byVariant,
            bestsellerVariants = bestsellers,
        )
    }

    private fun validateSources(
        sources: List<MerchandisingEvidenceSource>,
    ): List<SalesEvidenceIssue> = buildList {
        sources.forEach { source ->
            if (source.id.isBlank()) {
                add(SalesEvidenceIssue(SalesEvidenceIssueCode.BLANK_SOURCE_ID))
            }
            if (source.displayName.isBlank()) {
                add(
                    SalesEvidenceIssue(
                        SalesEvidenceIssueCode.BLANK_SOURCE_NAME,
                        source.id.takeIf(String::isNotBlank),
                    ),
                )
            }
        }
        sources.groupBy(MerchandisingEvidenceSource::id)
            .filterValues { it.size > 1 }
            .keys
            .sorted()
            .forEach {
                add(SalesEvidenceIssue(SalesEvidenceIssueCode.DUPLICATE_SOURCE_ID, it))
            }
    }

    private data class ComparableWindow(
        val sourceId: String,
        val windowStart: Instant,
        val windowEnd: Instant,
    )
}

private enum class EvidenceFreshness {
    CURRENT,
    FUTURE,
    STALE,
    INVALID,
}

private fun SalesSignal.freshness(
    now: Instant,
    policy: RecommendationPolicy,
): EvidenceFreshness = try {
    when {
        policy.maximumSignalAge.isNegative || policy.maximumFutureSkew.isNegative ->
            EvidenceFreshness.INVALID
        observedAt.isAfter(now.plus(policy.maximumFutureSkew)) ||
            windowEnd.isAfter(now.plus(policy.maximumFutureSkew)) ->
            EvidenceFreshness.FUTURE
        observedAt.isBefore(now.minus(policy.maximumSignalAge)) ||
            windowEnd.isBefore(now.minus(policy.maximumSignalAge)) ->
            EvidenceFreshness.STALE
        else -> EvidenceFreshness.CURRENT
    }
} catch (_: DateTimeException) {
    EvidenceFreshness.INVALID
} catch (_: ArithmeticException) {
    EvidenceFreshness.INVALID
}

internal fun MenuVariantRef.stableEvidenceId(): String =
    "${itemId.value}:${variantId.value}:$recipeRevision"
